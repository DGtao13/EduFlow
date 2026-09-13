package com.eduflow.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.eduflow.app.MainActivity
import com.eduflow.app.R
import com.eduflow.app.data.*
import com.eduflow.app.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.*
import java.util.concurrent.TimeUnit

class NotificationReconciliation(private val context: Context, private val db: EduFlowDatabase = EduFlowDatabase.getInstance(context)) {
    private val wm = WorkManager.getInstance(context)
    fun requestReconciliation() {
        wm.enqueueUniqueWork("eduflow_notification_reconcile", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NotificationReconcileWorker>().build())
    }
    private fun maintain() {
        wm.enqueueUniquePeriodicWork("eduflow_notification_maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NotificationReconcileWorker>(12, TimeUnit.HOURS).build())
    }
    suspend fun readLessons(from: LocalDate, to: LocalDate): List<LessonInstance> {
        val definitions = db.recurringPrivateLessonDao().getAll().associateBy { it.id }
        val slots = db.scheduleSlotDao().getAll().associateBy { it.id }
        val subjects = db.subjectDao().getAll().associateBy { it.id }
        val configuration = db.cycleDao().getConfiguration()
        val entries = db.cycleDao().getEntries().map { it.templateId }
        return db.lessonInstanceDao().getForDateRange(from, to).mapNotNull { lesson ->
            if (lesson.kind == LessonKind.SCHOOL) {
                val source = lesson.sourceScheduleSlotId?.let(slots::get) ?: return@mapNotNull null
                val cycle = configuration ?: return@mapNotNull null
                val template = com.eduflow.app.domain.ScheduleCycle.resolveTemplateId(com.eduflow.app.domain.ScheduleCycle.mondayOf(lesson.actualDate), cycle.anchorMonday, cycle.anchorTemplateId, entries)
                if (source.scheduleTemplateId == template && source.weekday == lesson.actualDate.dayOfWeek.value) {
                    val subject = source.subjectId?.let(subjects::get)
                    lesson.copy(subjectId = source.subjectId, actualStartTime = source.startTime, actualEndTime = source.endTime,
                        actualTeacher = source.teacherOverride ?: subject?.defaultTeacher,
                        actualRoom = source.roomOverride ?: subject?.defaultRoom)
                } else null
            } else if (lesson.sourcePrivateLessonId == null) lesson
            else {
                val definition = definitions[lesson.sourcePrivateLessonId] ?: return@mapNotNull null
                if (!PrivateLessonRecurrence.occursOn(definition, lesson.actualDate)) null
                else lesson.copy(actualStartTime = definition.startTime, actualEndTime = definition.endTime,
                    subjectId = definition.subjectId, privateLessonName = definition.privateLessonName ?: lesson.privateLessonName)
            }
        }
    }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    suspend fun observeChanges() {
        val sources: List<Flow<Any>> = listOf(NotificationSettingsRepository(context).settings, db.taskDao().observeAll(),
            db.taskReminderDao().observeAll(), db.lessonInstanceDao().observeAll(), db.scheduleSlotDao().observeAll(),
            db.recurringPrivateLessonDao().observeAll(), db.subjectDao().observeAll(), db.cycleDao().observeEntries(),
            db.cycleDao().observeConfiguration().map { it ?: "unconfigured" },
            db.dayExceptionDao().observeForDateRange(LocalDate.of(2000,1,1), LocalDate.of(2100,1,1)),
            AcademicYearSettingsRepository(context).settings)
        combine(sources.map { it.distinctUntilChanged() }) { Unit }.debounce(300).collect {
            try { AppDataSession.runtimeMutex.withLock { reconcile() } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { requestReconciliation() }
        }
    }
    suspend fun snapshot(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<PlannedNotification> = db.withTransaction {
        NotificationPlan.calculate(NotificationSettingsRepository(context).snapshot(), db.taskDao().getAll(), db.taskReminderDao().getAll(),
            readLessons(now.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate().plusDays(7)),
            db.scheduleSlotDao().getAll(), db.subjectDao().getAll(),
            db.dayExceptionDao().getForDateRange(now.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate().plusDays(7))
                .filter { it.type == DayExceptionType.NO_SCHOOL }.map { it.date }.toSet(), AcademicYearSettingsRepository(context).snapshot(), now, zone)
    }

    suspend fun reconcile(now: Instant = Instant.now()) = withContext(Dispatchers.IO) { reconciliationMutex.withLock {
        val settings = NotificationSettingsRepository(context).snapshot()
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        if (settings.schoolEnabled || settings.privateEnabled || settings.dailySummaryEnabled) {
            val materializer = WeekMaterializer(db)
            materializer.materializeWeek(com.eduflow.app.domain.ScheduleCycle.mondayOf(today))
            materializer.materializeWeek(com.eduflow.app.domain.ScheduleCycle.mondayOf(today.plusDays(7)))
        }
        // Retire legacy jobs, without altering any TaskReminder records or channel settings.
        db.taskReminderDao().getAll().forEach { wm.cancelUniqueWork("task_reminder_${it.id}").result.get() }
        wm.cancelUniqueWork("daily_summary").result.get()
        maintain()
        val desired = snapshot(now).associateBy { it.name }.toMutableMap()
        val existing = wm.getWorkInfosByTag(TAG).get().filter { !it.state.isFinished }
        // A cold WorkManager process starts Application observers before its due worker.
        // Future-only planning must not cancel persisted work that is due but still useful.
        // Reconstruct at its original trigger, then validate current data/signature/expiry.
        val dueNames = existing.flatMap { it.tags }.filter { it.startsWith("eduflow_notice_v1_") && it !in desired }
        val duePlans = mutableMapOf<Instant, List<PlannedNotification>>()
        for (name in dueNames) {
            val at = name.substringAfterLast('_').toLongOrNull()?.let(Instant::ofEpochSecond) ?: continue
            if (at.isAfter(now)) continue
            val plan = duePlans[at] ?: snapshot(at.minusMillis(1)).also { duePlans[at] = it }
            val notice = plan.firstOrNull { it.name == name && now.isBefore(it.expires) } ?: continue
            if (existing.any { name in it.tags && notice.signature in it.tags }) desired[name] = notice
        }
        existing.filter { info -> info.tags.none { it in desired && desired.getValue(it).signature in info.tags } }
            .forEach { wm.cancelWorkById(it.id).result.get() }
        desired.values.forEach { notice ->
            if (existing.any { notice.name in it.tags && notice.signature in it.tags }) return@forEach
            val data = Data.Builder().putString("name", notice.name).putString("signature", notice.signature)
                .putLong("at", notice.at.toEpochMilli()).build()
            val request = OneTimeWorkRequestBuilder<PlannedNotificationWorker>().setInputData(data)
                .setInitialDelay(Duration.between(Instant.now(), notice.at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(TAG).addTag(notice.name).addTag(notice.signature).addTag("notice_${notice.kind}_${notice.entityId}").build()
            wm.enqueueUniqueWork(notice.name, ExistingWorkPolicy.REPLACE, request).result.get()
        }
    } }
    companion object { const val TAG = "eduflow_notifications_v1"; private val reconciliationMutex = Mutex() }
}

class NotificationReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AppDataSession.runtimeMutex.withLock {
        try { NotificationReconciliation(applicationContext).reconcile(); Result.success() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}

class PlannedNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AppDataSession.runtimeMutex.withLock {
        try { deliver() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
    private suspend fun deliver(): Result {
        EduFlowNotifications.createChannels(applicationContext)
        val expected = Instant.ofEpochMilli(inputData.getLong("at", -1))
        val now = Instant.now()
        val engine = NotificationReconciliation(applicationContext)
        // Revalidate current settings/data/zone, not stale enqueue-time content.
        val notice = engine.snapshot(expected.minusMillis(1)).firstOrNull {
            it.name == inputData.getString("name") && it.signature == inputData.getString("signature")
        } ?: return Result.success()
        if (now.isBefore(notice.at)) return Result.retry()
        if (!now.isBefore(notice.expires) || !EduFlowNotifications.isAllowed(applicationContext)) return Result.success()
        val db = EduFlowDatabase.getInstance(applicationContext)
        val settings = NotificationSettingsRepository(applicationContext).snapshot()
        val date = expected.atZone(ZoneId.systemDefault()).toLocalDate()
        var body = notice.body
        if (notice.kind == "summary") {
            body = NotificationPlan.overview(settings, db.taskDao().getPending(), engine.readLessons(date, date), db.scheduleSlotDao().getAll(), date,
                db.dayExceptionDao().getByDate(date)?.type == DayExceptionType.NO_SCHOOL || !SchoolYear.containsSchoolDate(date, AcademicYearSettingsRepository(applicationContext).snapshot()))
        }
        if (notice.kind == "overdue") {
            val count = db.taskDao().getPending().count { it.dueAt?.atZone(ZoneId.systemDefault())?.toInstant()?.isBefore(now) == true }
            if (count == 0) return Result.success()
            body = "Имаш $count ${if (count == 1) "просрочена задача" else "просрочени задачи"}."
        }
        val task = notice.kind == "task"
        val channel = when (notice.kind) { "task", "overdue" -> EduFlowNotifications.TASK_CHANNEL; "summary" -> EduFlowNotifications.SUMMARY_CHANNEL; else -> EduFlowNotifications.LESSON_CHANNEL }
        val open = if (task) EduFlowNotifications.taskIntent(applicationContext, notice.entityId) else PendingIntent.getActivity(applicationContext, notice.name.hashCode(),
            Intent(applicationContext, MainActivity::class.java).setAction(if (notice.kind in setOf("school", "private")) "com.eduflow.app.OPEN_TODAY_SCHEDULE" else EduFlowNotifications.ACTION_OPEN_TASKS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(applicationContext, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notice.title).setContentText(body).setContentIntent(open).setAutoCancel(true)
        if (task) {
            val complete = PendingIntent.getBroadcast(applicationContext, notice.entityId.toInt(), Intent(applicationContext, TaskCompleteReceiver::class.java)
                .putExtra(EduFlowNotifications.EXTRA_TASK_ID, notice.entityId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, applicationContext.getString(R.string.complete), complete)
        }
        EduFlowNotifications.notifyTagged(applicationContext, notice.name, builder.build())
        return Result.success()
    }
}

/** WorkManager persists jobs across reboot; wall-clock changes require recomputing local schedules. */
class NotificationClockReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) NotificationReconciliation(context).requestReconciliation()
    }
}
