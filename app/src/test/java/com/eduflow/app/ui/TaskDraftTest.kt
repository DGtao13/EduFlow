package com.eduflow.app.ui

import com.eduflow.app.data.NotificationSettings
import com.eduflow.app.data.ReminderLogic
import com.eduflow.app.data.*
import com.eduflow.app.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskDraftTest {
    private val date = LocalDate.of(2026, 9, 21)
    private val task = Task(id=1, title="Title", description="Description", subjectId=1,
        type=TaskType.HOMEWORK, priority=TaskPriority.MUST, dueAt=date.atTime(12,0), createdAt=date.atStartOfDay())
    private val items = listOf(TaskChecklistItem(id=1,taskId=1,text="First",position=0))
    private fun draft(value: Task=task, mode: TaskDueMode=TaskDueMode.EXACT,
                      at: LocalDateTime=task.dueAt!!, checklist: List<TaskChecklistItem> = items,
                      reminders: List<TaskReminder> = emptyList()) = TaskDraft.from(value,mode,at.toLocalDate(),at.toLocalTime(),checklist,reminders)

    @Test fun everyEditablePersistedFieldParticipatesAndRevertingReturnsClean() {
        val original = draft()
        val custom = TaskReminder(taskId=1,kind=TaskReminderKind.ONE_HOUR_BEFORE,createdAt=date.atStartOfDay())
        val changed = listOf(
            draft(task.copy(title="Other")), draft(task.copy(description="Other")),
            draft(task.copy(subjectId=2)), draft(task.copy(priority=TaskPriority.SHOULD)),
            draft(task.copy(type=TaskType.GENERAL)), draft(mode=TaskDueMode.NONE),
            draft(mode=TaskDueMode.NEXT), draft(mode=TaskDueMode.SECOND_NEXT),
            draft(at=task.dueAt!!.plusDays(1)), draft(at=task.dueAt!!.plusMinutes(1)),
            draft(reminders=listOf(personalReminderMarker(1))), draft(reminders=listOf(custom)),
            draft(reminders=listOf(custom.copy(enabled=false))),
            draft(checklist=items+TaskChecklistItem(taskId=1,text="Second",position=1)),
            draft(checklist=emptyList()), draft(checklist=items.map { it.copy(text="Changed") }),
            draft(checklist=items.map { it.copy(isCompleted=true) })
        )
        changed.forEachIndexed { index, value -> assertNotEquals("Changed field $index", original, value) }
        assertNotEquals(draft(mode=TaskDueMode.NEXT),draft(mode=TaskDueMode.SECOND_NEXT))
        changed.forEach { assertEquals(original,draft()) }
        assertEquals(original,draft(task.copy(title=" Title ",description=" Description ")))
        assertEquals(draft(task.copy(description=null)),draft(task.copy(description=" ")))
    }
    @Test fun irrelevantCachedConcreteValuesAndReminderOrderingDoNotCreateDirtyState() {
        assertEquals(draft(mode=TaskDueMode.NEXT),draft(mode=TaskDueMode.NEXT,at=task.dueAt!!.plusDays(3)))
        val one = TaskReminder(taskId=1,kind=TaskReminderKind.ONE_HOUR_BEFORE,createdAt=date.atStartOfDay())
        val day = one.copy(kind=TaskReminderKind.ONE_DAY_BEFORE)
        assertEquals(draft(reminders=listOf(one,day)),draft(reminders=listOf(day,one)))
        assertNotEquals(draft(checklist=items+items.first().copy(text="Second")),draft(checklist=listOf(items.first().copy(text="Second"))+items))
    }
    @Test fun concreteDefaultIsStrictlyForwardAndRollsMidnightAndDst() {
        val zone = ZoneId.of("Europe/Sofia")
        assertEquals(date.atTime(11,45),concreteDueDefault(date.atTime(11,36).atZone(zone)))
        assertEquals(date.atTime(12,0),concreteDueDefault(date.atTime(11,45).atZone(zone)))
        assertEquals(date.plusDays(1).atStartOfDay(),concreteDueDefault(date.atTime(23,58).atZone(zone)))
        val spring = LocalDate.of(2026,3,29).atTime(2,58).atZone(zone)
        assertTrue(concreteDueDefault(spring).atZone(zone).toInstant().isAfter(spring.toInstant()))
        val autumn = LocalDate.of(2026,10,25).atTime(3,5).atZone(zone).withLaterOffsetAtOverlap()
        assertEquals(autumn.toLocalDate().atTime(4,0),concreteDueDefault(autumn))
        assertTrue(concreteDueDefault(autumn).atZone(zone).toInstant().isAfter(autumn.toInstant()))
        val selected = concreteDueSelection(null,null,date.atTime(11,36).atZone(zone))
        assertEquals(date to LocalTime.of(11,45),selected)
        assertEquals(selected,concreteDueSelection(selected.first,selected.second,date.plusDays(1).atTime(16,0).atZone(zone)))
        assertEquals(task.dueAt!!.toLocalDate() to task.dueAt.toLocalTime(),concreteDueSelection(task.dueAt.toLocalDate(),task.dueAt.toLocalTime(),spring))
        assertEquals(date to null,concreteDueSelection(date,null,spring))
    }
    @Test fun existingNonConcreteTasksInitializeOnceAndKeepDirtyComparisonSemantic() {
        val zone = ZoneId.of("Europe/Sofia")
        val now = date.atTime(23,58).atZone(zone)
        val generated = concreteDueSelection(null,null,now)
        assertEquals(date.plusDays(1), generated.first)
        assertEquals(LocalTime.MIDNIGHT, generated.second)
        // An existing dynamic Task has no concrete cache, so it receives the same default as new Tasks.
        assertEquals(generated, concreteDueSelection(null,null,now))
        val edited = concreteDueSelection(generated.first,generated.second,date.plusDays(3).atTime(12,0).atZone(zone))
        assertEquals(generated, edited)
        val nonConcrete = task.copy(dueAt=null,dueLessonInstanceId=2)
        listOf(TaskDueMode.NONE, TaskDueMode.NEXT, TaskDueMode.SECOND_NEXT).forEach { originalMode ->
            val original = TaskDraft.from(nonConcrete,originalMode,null,null,items,emptyList())
            assertNotEquals(original,TaskDraft.from(nonConcrete,TaskDueMode.EXACT,generated.first,generated.second,items,emptyList()))
            assertEquals(original,TaskDraft.from(nonConcrete,originalMode,generated.first,generated.second,items,emptyList()))
        }
    }
    @Test fun summariesDescribeActualGlobalOrReplacementCustomMode() {
        val active = NotificationSettings(taskRemindersEnabled=true,taskLeadsConfigured=true,taskLeadMinutes=setOf(60,1440))
        assertEquals("По глобалните настройки · 1 ч. по-рано и 1 ден по-рано",taskReminderSummary(active,task.priority,true,emptyList()))
        assertEquals("По глобалните настройки · при срока",taskReminderSummary(active.copy(taskLeadMinutes=setOf(0)),task.priority,true,emptyList()))
        assertEquals("Няма активни глобални напомняния",taskReminderSummary(active.copy(taskRemindersEnabled=false),task.priority,true,emptyList()))
        assertEquals("Няма активни глобални напомняния",taskReminderSummary(active,task.priority,false,emptyList()))
        assertEquals("Персонални · няма активни напомняния",taskReminderSummary(active,task.priority,true,listOf(personalReminderMarker(1))))
        val custom = TaskReminder(taskId=1,kind=TaskReminderKind.THREE_DAYS_BEFORE,createdAt=date.atStartOfDay())
        assertEquals("Персонални · 1 напомняне",taskReminderSummary(active,task.priority,true,listOf(custom)))
        assertEquals(task.dueAt!!.minusDays(3),ReminderLogic.triggerAt(task,custom))
        val at = date.atTime(10,0)
        assertEquals(at,ReminderLogic.triggerAt(task,custom.copy(kind=TaskReminderKind.CUSTOM,customTriggerAt=at)))
        assertTrue(saveableTaskReminders(listOf(custom),false).single().isModeMarker())
        assertTrue(saveableTaskReminders(emptyList(),false).isEmpty())
    }
    @Test fun explicitlyEmptyPersonalModeUsesAnArchiveValidDisabledRecord() {
        val marker = personalReminderMarker(1).copy(id=1)
        val data = BackupData(emptyList(),emptyList(),null,emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),
            listOf(TaskDto(1,task.title,null,null,null,null,task.type.name,task.priority.name,task.status.name,task.dueAt.toString(),null,task.createdAt.toString())),
            emptyList(),listOf(ReminderDto(1,1,marker.kind.name,marker.customTriggerAt.toString(),false,marker.createdAt.toString())))
        val backup = PortableBackup(BackupManifest(createdAt=task.createdAt.toString(),roomVersion=10),data,BackupSettings(true,false,8,0))
        val json = kotlinx.serialization.json.Json
        val restored = json.decodeFromString(PortableBackup.serializer(),json.encodeToString(PortableBackup.serializer(),backup))
        BackupPackageLogic.validate(restored)
        assertFalse(restored.data.reminders.single().enabled)
        assertTrue(marker.isModeMarker())
    }
}
