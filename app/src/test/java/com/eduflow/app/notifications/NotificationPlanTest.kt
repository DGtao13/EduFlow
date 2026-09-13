package com.eduflow.app.notifications

import com.eduflow.app.data.*
import com.eduflow.app.data.local.*
import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class NotificationPlanTest {
    private val zone = ZoneId.of("Europe/Sofia")
    private val date = LocalDate.of(2026,9,21)
    private val now = date.atTime(8,0).atZone(zone).toInstant()
    private val year = AcademicYearSettings(date, date.plusMonths(3))
    private val task = Task(id = 1, title = "Домашно", subjectId = 1, type = TaskType.HOMEWORK, priority = TaskPriority.MUST,
        createdAt = date.atStartOfDay(), dueAt = date.plusDays(2).atTime(12,0))
    private val slots = listOf(
        ScheduleSlot(1,1,1,3,LocalTime.of(9,50),LocalTime.of(10,35),1,logicalBlockId="block"),
        ScheduleSlot(2,1,1,4,LocalTime.of(10,35),LocalTime.of(11,20),2,logicalBlockId="block"))
    private val lessons = slots.map { LessonInstance(it.id,date,it.startTime,it.endTime,it.subjectId,it.id,kind=LessonKind.SCHOOL) }
    private fun plan(s: NotificationSettings = NotificationSettings(), tasks: List<Task> = listOf(task), reminders: List<TaskReminder> = emptyList(),
        dated: List<LessonInstance> = lessons, sources: List<ScheduleSlot> = slots, instant: Instant = now) =
        NotificationPlan.calculate(s,tasks,reminders,dated,sources,listOf(Subject(1,"БЕЛ",color=1),Subject(2,"БЕЛ ИУЧ",color=2)),emptySet(),year,instant,zone)

    @Test fun freshAndLegacyDefaultsPreserveMasterStateAndOverviewTime() {
        assertEquals(NotificationSettings(), NotificationSettingsRepository.decode(emptyPreferences()))
        val legacy = mutablePreferencesOf(booleanPreferencesKey("task_reminders") to true, booleanPreferencesKey("daily_summary") to true,
            intPreferencesKey("summary_hour") to 6, intPreferencesKey("summary_minute") to 30)
        val settings = NotificationSettingsRepository.decode(legacy)
        assertTrue(settings.taskRemindersEnabled); assertTrue(settings.dailySummaryEnabled)
        assertEquals(6,settings.summaryHour); assertEquals(30,settings.summaryMinute)
        assertFalse(settings.schoolEnabled); assertFalse(settings.privateEnabled); assertFalse(settings.quietEnabled)
        assertEquals((1..7).toSet(),settings.summaryDays)
    }
    @Test fun allPreferenceMultiValuesAndTimesRoundTrip() {
        val value = NotificationSettings(true,true,7,15,setOf(30,60),true,true,true,30,setOf(1,3,5),true,1350,420,taskLeadsConfigured=true)
        val prefs = mutablePreferencesOf(); NotificationSettingsRepository.encode(prefs,value)
        assertEquals(value,NotificationSettingsRepository.decode(prefs))
    }
    @Test fun globalLeadTimesProduceFourDistinctTriggersWithoutDuplicates() {
        val result = plan(NotificationSettings(taskRemindersEnabled=true,taskLeadMinutes=setOf(0,30,60,1440),taskLeadsConfigured=true))
        assertEquals(setOf(0L,30L,60L,1440L),result.map { Duration.between(it.at,task.dueAt!!.atZone(zone).toInstant()).toMinutes() }.toSet())
        assertEquals(result.size,result.map { it.name }.distinct().size)
    }
    @Test fun explicitRemindersOverrideDefaultsAndIdenticalTriggersDeduplicate() {
        val explicit = listOf(TaskReminder(1,1,TaskReminderKind.ONE_HOUR_BEFORE,createdAt=date.atStartOfDay()),
            TaskReminder(2,1,TaskReminderKind.CUSTOM,customTriggerAt=task.dueAt!!.minusHours(1),createdAt=date.atStartOfDay()))
        assertEquals(1,plan(NotificationSettings(taskRemindersEnabled=true),reminders=explicit).size)
        assertEquals(0,plan(NotificationSettings(taskRemindersEnabled=true),reminders=explicit.map { it.copy(enabled=false) }).size)
    }
    @Test fun completionDeletionAndDueChangesInvalidateOldDesiredWork() {
        val settings = NotificationSettings(taskRemindersEnabled=true)
        val old = plan(settings)
        assertTrue(plan(settings,tasks=listOf(task.copy(status=TaskStatus.COMPLETED))).isEmpty())
        assertTrue(plan(settings,tasks=emptyList()).isEmpty())
        val new = plan(settings,tasks=listOf(task.copy(dueAt=task.dueAt!!.plusHours(2))))
        assertTrue(old.map { it.name }.toSet().intersect(new.map { it.name }.toSet()).isEmpty())
    }
    @Test fun schoolAndPrivateTogglesLeadTimesCancellationAndBlockUnlinkWork() {
        assertTrue(plan().isEmpty())
        LessonReminderLead.presets.forEach { lead ->
            val school = plan(NotificationSettings(schoolEnabled=true,schoolLeadMinutes=lead), instant=now.minusSeconds(14400))
            assertEquals(1,school.size)
            assertEquals(lead.toLong(),Duration.between(school.single().at,school.single().event).toMinutes())
            val privateLesson = lessons.first().copy(kind=LessonKind.PRIVATE,sourceScheduleSlotId=null,privateLessonName="Урок")
            val privatePlan = plan(NotificationSettings(privateEnabled=true,privateLeadMinutes=lead),dated=listOf(privateLesson),instant=now.minusSeconds(14400))
            assertEquals(1,privatePlan.size)
            assertEquals(lead.toLong(),Duration.between(privatePlan.single().at,privatePlan.single().event).toMinutes())
            assertTrue(plan(NotificationSettings(privateEnabled=true),dated=listOf(privateLesson.copy(cancellationState=CancellationState.CANCELLED))).isEmpty())
        }
        assertTrue(plan(NotificationSettings(schoolEnabled=true),dated=lessons.map { it.copy(cancellationState=CancellationState.CANCELLED) }).isEmpty())
        assertEquals(2,plan(NotificationSettings(schoolEnabled=true),sources=slots.map { it.copy(logicalBlockId=null) }).size)
        val privateLesson = lessons.first().copy(kind=LessonKind.PRIVATE,sourceScheduleSlotId=null)
        assertTrue(plan(NotificationSettings(privateEnabled=true,excludedPrivateOccurrences=setOf("1:$date")),dated=listOf(privateLesson)).isEmpty())
    }
    @Test fun schoolAndPrivateLeadsAreIndependentAndCustomChangesPreserveOtherDesiredWork() {
        val privateLesson=lessons.first().copy(id=3,kind=LessonKind.PRIVATE,sourceScheduleSlotId=null,actualStartTime=LocalTime.of(17,0))
        val settings=NotificationSettings(schoolEnabled=true,privateEnabled=true,schoolLeadMinutes=10,privateLeadMinutes=60)
        val result=plan(settings,dated=lessons+privateLesson)
        assertEquals(2,result.size)
        assertEquals(10,Duration.between(result.first{it.kind=="school"}.at,result.first{it.kind=="school"}.event).toMinutes().toInt())
        assertEquals(60,Duration.between(result.first{it.kind=="private"}.at,result.first{it.kind=="private"}.event).toMinutes().toInt())
        val changed=plan(settings.copy(privateLeadMinutes=90),dated=lessons+privateLesson)
        assertEquals(result.first{it.kind=="school"},changed.first{it.kind=="school"})
        assertNotEquals(result.first{it.kind=="private"}.name,changed.first{it.kind=="private"}.name)
        val schoolChanged=plan(settings.copy(schoolLeadMinutes=25),dated=lessons+privateLesson)
        assertEquals(result.first{it.kind=="private"},schoolChanged.first{it.kind=="private"})
        assertEquals(1,schoolChanged.count{it.kind=="school"})
    }
    @Test fun overviewWeekdaysAndConciseCountsDoNotExposeNotesOrDescriptions() {
        val overview = plan(NotificationSettings(dailySummaryEnabled=true,summaryHour=9,summaryDays=setOf(1,3)))
        assertEquals(setOf(1,3),overview.map { it.at.atZone(zone).dayOfWeek.value }.toSet())
        assertTrue(plan(NotificationSettings(dailySummaryEnabled=true,summaryDays=emptySet())).isEmpty())
        val text = NotificationPlan.overview(NotificationSettings(),listOf(task.copy(dueAt=date.atTime(12,0),description="SECRET")),
            lessons + lessons.first().copy(id=3,kind=LessonKind.PRIVATE,sourceScheduleSlotId=null,notes="SECRET"),slots,date,false)
        assertEquals("Днес имаш 1 учебна сесия, 1 частен урок и 1 задача.",text)
        assertFalse(text.contains("SECRET")); assertFalse(text.contains("null"))
    }
    @Test fun quietHoursNormalAndOvernightBoundaries() {
        listOf(780 to 900,1350 to 420).forEach { (start,end) ->
            val s = NotificationSettings(quietEnabled=true,quietStartMinute=start,quietEndMinute=end)
            val atStart = date.atTime(start/60,start%60).atZone(zone)
            val atEnd = date.atTime(end/60,end%60).atZone(zone)
            assertNotEquals(atStart,NotificationPlan.quietEnd(atStart,s))
            assertEquals(atEnd,NotificationPlan.quietEnd(atEnd,s))
            assertEquals(atStart.minusMinutes(1),NotificationPlan.quietEnd(atStart.minusMinutes(1),s))
            assertEquals(atEnd.plusMinutes(1),NotificationPlan.quietEnd(atEnd.plusMinutes(1),s))
        }
        val midnight = date.atStartOfDay(zone)
        assertEquals(date.atTime(7,0).atZone(zone),NotificationPlan.quietEnd(midnight,NotificationSettings(quietEnabled=true)))
    }
    @Test fun quietHoursDelayUsefulTasksAndOverviewButSkipStaleLessons() {
        val early = date.atTime(5,0).atZone(zone).toInstant()
        val settings = NotificationSettings(taskRemindersEnabled=true,taskLeadMinutes=setOf(60),taskLeadsConfigured=true,quietEnabled=true,dailySummaryEnabled=true,summaryHour=6,summaryMinute=30)
        val result = plan(settings,tasks=listOf(task.copy(dueAt=date.atTime(7,30))),instant=early)
        assertEquals(date.atTime(7,0).atZone(zone).toInstant(),result.first { it.kind=="task" }.at)
        assertEquals(date.atTime(7,0).atZone(zone).toInstant(),result.first { it.kind=="summary" }.at)
        val school = lessons.first().copy(actualStartTime=LocalTime.of(6,50))
        val source = slots.first().copy(startTime=school.actualStartTime)
        assertTrue(plan(settings.copy(schoolEnabled=true,taskRemindersEnabled=false,dailySummaryEnabled=false),dated=listOf(school),sources=listOf(source),instant=early).isEmpty())
        assertTrue(plan(settings.copy(privateEnabled=true,taskRemindersEnabled=false,dailySummaryEnabled=false),dated=listOf(school.copy(kind=LessonKind.PRIVATE,sourceScheduleSlotId=null)),instant=early).isEmpty())
    }
    @Test fun repeatedSettingChangesHaveStableFinalNamesAndNoAccumulatedWork() {
        val a = NotificationSettings(schoolEnabled=true,lessonLeadMinutes=15)
        val b = a.copy(schoolLeadMinutes=30)
        val desired = plan(a)
        assertNotEquals(desired.map { it.name },plan(b).map { it.name })
        assertEquals(desired,plan(a))
        assertTrue(plan(a.copy(schoolEnabled=false)).isEmpty())
        assertEquals(desired.size,desired.distinctBy { it.name }.size)
    }
    @Test fun dstQuietEndUsesLocalCalendarAndRealElapsedTime() {
        val spring = LocalDate.of(2026,3,29).atTime(1,0).atZone(zone)
        val end = NotificationPlan.quietEnd(spring,NotificationSettings(quietEnabled=true))
        assertEquals(spring.toLocalDate(),end.toLocalDate()); assertEquals(7,end.hour)
        assertEquals(5L,Duration.between(spring,end).toHours())
        val fall = LocalDate.of(2026,10,25).atTime(1,0).atZone(zone)
        assertEquals(7L,Duration.between(fall,NotificationPlan.quietEnd(fall,NotificationSettings(quietEnabled=true))).toHours())
        val repeated = LocalDate.of(2026,10,25).atTime(3,0).atZone(zone).withLaterOffsetAtOverlap()
        val repeatedEnd = NotificationPlan.quietEnd(repeated,NotificationSettings(quietEnabled=true,quietStartMinute=180,quietEndMinute=195))
        assertEquals(15L,Duration.between(repeated,repeatedEnd).toMinutes())
    }
}
