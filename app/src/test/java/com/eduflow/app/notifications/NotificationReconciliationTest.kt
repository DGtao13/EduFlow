package com.eduflow.app.notifications

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import com.eduflow.app.data.NotificationSettingsRepository
import com.eduflow.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import androidx.work.*
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.WorkDatabase
import androidx.work.impl.utils.WorkProgressUpdater
import androidx.work.impl.utils.WorkForegroundUpdater
import android.app.NotificationManager
import org.robolectric.Shadows.shadowOf
import com.eduflow.app.ui.TaskDraft
import com.eduflow.app.ui.TaskDueMode
import com.eduflow.app.data.TaskRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class NotificationReconciliationTest {
    private suspend fun productionWorkerReconstructsDurableStateWithoutUi() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val wm = WorkManager.getInstance(context) as WorkManagerImpl
        val settings = NotificationSettingsRepository(context)
        settings.clear()
        settings.update { it.copy(taskRemindersEnabled=true,taskLeadMinutes=setOf(60),taskLeadsConfigured=true) }
        var db = EduFlowDatabase.getInstance(context)
        val singleton = EduFlowDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        try {
            val task = Task(id=100,title="Runtime reminder",type=TaskType.HOMEWORK,priority=TaskPriority.MUST,
                createdAt=LocalDateTime.now(),dueAt=LocalDateTime.now().plusDays(2))
            db.taskDao().upsert(task)
            NotificationReconciliation(context,db).reconcile()
            val info = wm.getWorkInfosByTag(NotificationReconciliation.TAG).get().single { !it.state.isFinished }
            val spec = wm.workDatabase.workSpecDao().getWorkSpec(info.id.toString())!!
            assertEquals(PlannedNotificationWorker::class.java.name,spec.workerClassName)
            assertTrue(spec.initialDelay>0); assertFalse(spec.hasConstraints())
            assertEquals(setOf("at","name","signature"),spec.input.keyValueMap.keys)
            // Independently reopen the real on-disk WorkManager database, not a planner cache.
            val persisted = Room.databaseBuilder(context,WorkDatabase::class.java,wm.workDatabase.openHelper.writableDatabase.path)
                .allowMainThreadQueries().build()
            try {
                    assertEquals(spec.input,persisted.workSpecDao().getWorkSpec(info.id.toString())!!.input)
                    assertTrue(persisted.workTagDao().getTagsForWorkSpecId(info.id.toString()).contains(NotificationReconciliation.TAG))
            } finally { persisted.close() }
            db.close(); singleton.set(null,null)
            db = EduFlowDatabase.getInstance(context)
            assertEquals(task,db.taskDao().getById(100))
            assertTrue(NotificationSettingsRepository(context).snapshot().taskRemindersEnabled)
            NotificationReconciliation(context,db).reconcile()
            assertEquals(info.id,wm.getWorkInfosByTag(NotificationReconciliation.TAG).get().single { !it.state.isFinished }.id)

            val now = java.time.Instant.now()
            val dueTask = task.copy(dueAt=now.atZone(ZoneId.systemDefault()).toLocalDateTime().plusHours(1).minusSeconds(5))
            db.taskDao().update(dueTask)
            val engine = NotificationReconciliation(context,db)
            val notice = engine.snapshot(now.minusSeconds(10)).single()
            val input = Data.Builder().putString("name",notice.name).putString("signature",notice.signature).putLong("at",notice.at.toEpochMilli()).build()
            val request = OneTimeWorkRequestBuilder<PlannedNotificationWorker>().setInputData(input)
                .setInitialDelay(1,TimeUnit.DAYS).addTag(NotificationReconciliation.TAG).addTag(notice.name).addTag(notice.signature).build()
            wm.enqueueUniqueWork(notice.name,ExistingWorkPolicy.REPLACE,request).result.get()
            engine.reconcile()
            assertFalse(wm.getWorkInfoById(request.id).get().state.isFinished)
            fun worker(): PlannedNotificationWorker {
                val parameters = WorkerParameters(request.id,input,listOf(notice.name),WorkerParameters.RuntimeExtras(),0,0,
                    wm.configuration.executor,wm.workTaskExecutor,wm.configuration.workerFactory,
                    WorkProgressUpdater(wm.workDatabase,wm.workTaskExecutor),WorkForegroundUpdater(wm.workDatabase,wm.processor,wm.workTaskExecutor))
                return wm.configuration.workerFactory.createWorkerWithDefaultFallback(context,PlannedNotificationWorker::class.java.name,parameters) as PlannedNotificationWorker
            }
            val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
            assertEquals(ListenableWorker.Result.success(),worker().doWork())
            assertEquals("Runtime reminder",notifications.allNotifications.single().extras.getString("android.title"))
            db.taskDao().update(dueTask.copy(status=TaskStatus.COMPLETED))
            assertEquals(ListenableWorker.Result.success(),worker().doWork())
            assertEquals(1,notifications.allNotifications.size)
            db.taskDao().delete(dueTask)
            assertEquals(ListenableWorker.Result.success(),worker().doWork())
            assertEquals(1,notifications.allNotifications.size)

            // Save replaces the complete staged checklist/reminder set; Discard/Stay never call save.
            val custom = TaskReminder(taskId=100,kind=TaskReminderKind.THREE_DAYS_BEFORE,createdAt=task.createdAt)
            val savedId = TaskRepository(db).saveWithChecklist(task,listOf(TaskChecklistItem(taskId=100,text="Done",isCompleted=true,position=0)),listOf(custom))
            val original = db.taskDao().getById(savedId)!!
            val current = original.copy(title="Changed",priority=TaskPriority.SHOULD,type=TaskType.GENERAL)
            val draft = TaskDraft.from(current,TaskDueMode.EXACT,current.dueAt!!.toLocalDate(),current.dueAt.toLocalTime(),emptyList(),emptyList())
            assertEquals(original,db.taskDao().getById(savedId)) // discard or stay has no write
            TaskRepository(db).saveWithChecklist(current.copy(title=draft.title),emptyList(),emptyList())
            assertEquals(current,db.taskDao().getById(savedId))
            assertTrue(db.taskChecklistDao().getForTask(savedId).isEmpty())
            assertTrue(db.taskReminderDao().getForTask(savedId).isEmpty())
        } finally { settings.clear(); wm.cancelAllWork().result.get(); db.close(); singleton.set(null,null) }
    }
    private suspend fun runtimeSnapshotsPreservePrivateSourcesAndFollowCurrentCalendarAndRecurringDefinitions() {
        val context = RuntimeEnvironment.getApplication()
        try { WorkManager.getInstance(context) } catch (_: IllegalStateException) { WorkManager.initialize(context, Configuration.Builder().build()) }
        val db = Room.inMemoryDatabaseBuilder(context,EduFlowDatabase::class.java).allowMainThreadQueries().build()
        val settings = NotificationSettingsRepository(context)
        settings.clear()
        val date = LocalDate.of(2026,9,21)
        try {
            db.scheduleTemplateDao().upsert(ScheduleTemplate(1,"A")); db.scheduleTemplateDao().upsert(ScheduleTemplate(2,"B"))
            db.cycleDao().saveConfiguration(CycleConfiguration(anchorMonday=date,anchorTemplateId=1))
            db.cycleDao().insertEntries(listOf(CycleEntry(position=0,templateId=1),CycleEntry(position=1,templateId=2)))
            listOf(1L,2L).forEach { id ->
                db.scheduleSlotDao().upsert(ScheduleSlot(id,id,1,1,LocalTime.of(9,0),LocalTime.of(9,45),null))
                db.lessonInstanceDao().insertIfAbsent(LessonInstance(id,date,LocalTime.of(9,0),LocalTime.of(9,45),null,id,kind=LessonKind.SCHOOL))
            }
            val definition = RecurringPrivateLesson(1,null,1,LocalTime.of(17,0),LocalTime.of(18,0),date,privateLessonName="Tutor")
            db.recurringPrivateLessonDao().upsert(definition)
            val occurrence = LessonInstance(3,date,LocalTime.of(17,0),LocalTime.of(18,0),null,sourcePrivateLessonId=1,kind=LessonKind.PRIVATE,notes="keep")
            db.lessonInstanceDao().insertIfAbsent(occurrence)
            db.recurringPrivateLessonDao().upsert(definition.copy(startTime=LocalTime.of(19,0),endTime=LocalTime.of(20,0)))
            assertEquals(occurrence,db.lessonInstanceDao().getById(3))
            val engine = NotificationReconciliation(context,db)
            assertEquals(listOf(1L,3L),engine.readLessons(date,date).map { it.id })
            assertEquals(LocalTime.of(19,0),engine.readLessons(date,date).first { it.id==3L }.actualStartTime)
            db.recurringPrivateLessonDao().update(definition.copy(enabled=false))
            assertEquals(listOf(1L),engine.readLessons(date,date).map { it.id })
            settings.update { it.copy(privateEnabled=true) }
            settings.suppressDeletedPrivate(listOf(occurrence))
            db.recurringPrivateLessonDao().delete(definition)
            assertTrue(engine.snapshot(date.atTime(8,0).atZone(ZoneId.of("Europe/Sofia")).toInstant(),ZoneId.of("Europe/Sofia")).none { it.kind=="private" })
            assertEquals("keep",db.lessonInstanceDao().getById(3)!!.notes)
        } finally { settings.clear(); db.close() }
    }
    @Test(timeout=60000) fun realWorkManagerKeepsCorrectJobsAndCancelsObsoleteReplacements() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val wm = try { WorkManager.getInstance(context) } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build()); WorkManager.getInstance(context)
        }
        // Keep WorkManager and its SQLite handles in one Robolectric application lifecycle.
        runtimeSnapshotsPreservePrivateSourcesAndFollowCurrentCalendarAndRecurringDefinitions()
        productionWorkerReconstructsDurableStateWithoutUi()
        val db = Room.inMemoryDatabaseBuilder(context,EduFlowDatabase::class.java).allowMainThreadQueries().build()
        val settings = NotificationSettingsRepository(context)
        settings.clear()
        try {
            settings.update { it.copy(taskRemindersEnabled=true,taskLeadMinutes=setOf(60),taskLeadsConfigured=true) }
            val task = Task(id=1,title="Test",type=TaskType.HOMEWORK,priority=TaskPriority.MUST,createdAt=LocalDateTime.now(),dueAt=LocalDateTime.now().plusDays(2))
            db.taskDao().upsert(task)
            val engine = NotificationReconciliation(context,db)
            fun active() = wm.getWorkInfosByTag(NotificationReconciliation.TAG).get(10,TimeUnit.SECONDS).filter { !it.state.isFinished }
            engine.reconcile()
            val first = active().single()
            engine.reconcile()
            assertEquals(first.id,active().single().id)
            // Cold process startup can reconcile after the persisted trigger became due.
            val scheduled = engine.snapshot().single()
            engine.reconcile(scheduled.at.plusSeconds(5))
            assertEquals("Startup must not cancel due-but-unexpired persisted work", first.id, active().single().id)
            settings.update { it.copy(taskLeadMinutes=setOf(30)) }
            engine.reconcile()
            assertNotEquals(first.id,active().single().id)
            assertEquals(androidx.work.WorkInfo.State.CANCELLED,wm.getWorkInfoById(first.id).get(10,TimeUnit.SECONDS).state)
            settings.update { it.copy(taskLeadMinutes=setOf(60)) }
            engine.reconcile()
            assertEquals(1,active().size)
            db.taskDao().update(task.copy(status=TaskStatus.COMPLETED))
            engine.reconcile()
            assertTrue(active().isEmpty())
            db.taskDao().delete(task)
            engine.reconcile()
            assertTrue(active().isEmpty())
            val date = LocalDate.of(2026,9,21)
            val now = date.minusDays(1).atTime(8,0).atZone(ZoneId.systemDefault()).toInstant()
            com.eduflow.app.EduFlowApplication::class.java.getDeclaredField("appContext").apply { isAccessible=true }.set(null,context)
            db.scheduleTemplateDao().upsert(ScheduleTemplate(1,"A"))
            db.subjectDao().upsert(Subject(1,"Math",color=0xFF123456))
            db.cycleDao().saveConfiguration(CycleConfiguration(anchorMonday=date,anchorTemplateId=1))
            db.cycleDao().insertEntries(listOf(CycleEntry(position=0,templateId=1)))
            for (index in 1..2) {
                val start = LocalTime.of(9,0).plusMinutes((index-1)*45L)
                db.scheduleSlotDao().upsert(ScheduleSlot(index.toLong(),1,1,index,start,start.plusMinutes(45),1,logicalBlockId="lead-test-block"))
            }
            db.lessonInstanceDao().insertIfAbsent(LessonInstance(777,date,LocalTime.of(17,0),LocalTime.of(18,0),null,kind=LessonKind.PRIVATE))
            settings.update { it.copy(taskRemindersEnabled=false,schoolEnabled=true,privateEnabled=true,schoolLeadMinutes=10,privateLeadMinutes=60) }
            fun job(kind: String): androidx.work.WorkInfo {
                val notice = runBlocking { engine.snapshot(now) }.single { it.kind==kind }
                return active().single { notice.name in it.tags }
            }
            engine.reconcile(now)
            assertEquals(2,active().size)
            val school = job("school"); val private = job("private")
            settings.update { it.copy(schoolLeadMinutes=25) }; engine.reconcile(now)
            assertEquals(androidx.work.WorkInfo.State.CANCELLED,wm.getWorkInfoById(school.id).get().state)
            val replacementSchool = job("school")
            assertNotEquals(school.id,replacementSchool.id)
            assertEquals(private.id,job("private").id)
            settings.update { it.copy(privateLeadMinutes=90) }; engine.reconcile(now)
            assertEquals(androidx.work.WorkInfo.State.CANCELLED,wm.getWorkInfoById(private.id).get().state)
            assertNotEquals(private.id,job("private").id)
            assertEquals(replacementSchool.id,job("school").id)
            assertEquals(2,active().size)
            val dated = db.lessonInstanceDao().getForDate(date)
            db.lessonInstanceDao().updateCancellationStates(dated.filter { it.kind==LessonKind.SCHOOL }.map { it.id },CancellationState.CANCELLED)
            engine.reconcile(now)
            assertEquals(1,active().size)
            db.lessonInstanceDao().updateCancellationStates(listOf(777),CancellationState.CANCELLED)
            engine.reconcile(now)
            assertTrue(active().isEmpty())
        } finally { settings.clear(); wm.cancelAllWork().result.get(10,TimeUnit.SECONDS); db.close() }
    }
}
