package com.eduflow.app.notifications

import com.eduflow.app.data.*
import com.eduflow.app.data.local.*
import com.eduflow.app.domain.LessonBlockResolver
import java.time.*

data class PlannedNotification(val kind: String, val entityId: Long, val at: Instant, val expires: Instant, val event: Instant,
    val title: String, val body: String) {
    val name: String get() = "eduflow_notice_v1_${kind}_${entityId}_${at.epochSecond}"
    val signature: String get() = "signature_${listOf(expires, event, title, body).hashCode()}"
}

object NotificationPlan {
    fun quietEnd(candidate: ZonedDateTime, s: NotificationSettings): ZonedDateTime {
        if (!s.quietEnabled || s.quietStartMinute == s.quietEndMinute) return candidate
        val minute = candidate.hour * 60 + candidate.minute
        val start = s.quietStartMinute; val end = s.quietEndMinute
        val inside = if (start < end) minute >= start && minute < end else minute >= start || minute < end
        if (!inside) return candidate
        val day = if (start > end && minute >= start) candidate.toLocalDate().plusDays(1) else candidate.toLocalDate()
        val target = day.atTime(end / 60, end % 60).atZone(candidate.zone)
        return if (target.toInstant().isBefore(candidate.toInstant())) target.withLaterOffsetAtOverlap() else target
    }

    fun calculate(s: NotificationSettings, tasks: List<Task>, reminders: List<TaskReminder>, lessons: List<LessonInstance>,
        slots: List<ScheduleSlot>, subjects: List<Subject>, noSchool: Set<LocalDate>, year: AcademicYearSettings,
        now: Instant, zone: ZoneId, horizonDays: Int = 7): List<PlannedNotification> {
        val desired = linkedMapOf<String, PlannedNotification>()
        val today = now.atZone(zone).toLocalDate()
        val subjectNames = subjects.associate { it.id to it.name }
        fun add(kind: String, id: Long, trigger: ZonedDateTime, expiry: ZonedDateTime, event: ZonedDateTime, title: String, body: String) {
            val adjusted = quietEnd(trigger, s)
            if (!adjusted.toInstant().isAfter(now) || !adjusted.toInstant().isBefore(expiry.toInstant())) return
            val n = PlannedNotification(kind, id, adjusted.toInstant(), expiry.toInstant(), event.toInstant(), title, body)
            desired.putIfAbsent(n.name, n)
        }
        if (s.taskRemindersEnabled) tasks.filter { it.status == TaskStatus.PENDING }.forEach { task ->
            val explicit = reminders.filter { it.taskId == task.id }
            val triggers = if (explicit.isNotEmpty()) explicit.filter { it.enabled }.mapNotNull { ReminderLogic.triggerAt(task, it) }
                else if (s.taskLeadsConfigured) s.taskLeadMinutes.sortedDescending().mapNotNull { task.dueAt?.minusMinutes(it.toLong()) }
                else ReminderLogic.suggestedKinds(task.priority).mapNotNull { kind -> ReminderLogic.triggerAt(task, TaskReminder(taskId = task.id, kind = kind, createdAt = task.createdAt)) }
            triggers.distinct().forEach { local ->
                val trigger = local.atZone(zone)
                val event = task.dueAt?.atZone(zone) ?: trigger.plusDays(1)
                // Deadline-time reminders have a one-minute late-work tolerance, never quiet-hour-delayed past due.
                val adjusted = quietEnd(trigger, s)
                if (task.dueAt != null && adjusted.isAfter(event)) return@forEach
                add("task", task.id, trigger, if (task.dueAt != null) event.plusMinutes(1) else trigger.plusDays(1), event,
                    task.title, task.dueAt?.let { "Срок: ${it.toLocalDate()} · ${it.toLocalTime()}" } ?: "Незавършена задача")
            }
        }
        val slotsById = slots.associateBy { it.id }
        lessons.filter { it.kind == LessonKind.SCHOOL }.groupBy { it.actualDate }.values.forEach { day ->
            if (!s.schoolEnabled) return@forEach
            LessonBlockResolver.resolveAll(LessonBlockResolver.scheduleMembersByStart(day, slots).values.toList(), slots).values.distinctBy { it.ids.first() }.forEach blockLoop@ { block ->
                val first = block.lessons.firstOrNull { it.cancellationState == CancellationState.ACTIVE } ?: return@blockLoop
                if (first.actualDate in noSchool || !SchoolYear.containsSchoolDate(first.actualDate, year)) return@blockLoop
                val source = first.sourceScheduleSlotId?.let(slotsById::get) ?: return@blockLoop
                val event = first.actualDate.atTime(source.startTime).atZone(zone)
                if (first.actualDate !in today..today.plusDays(horizonDays.toLong())) return@blockLoop
                val members = block.lessons.filter { it.cancellationState == CancellationState.ACTIVE }
                val names = members.mapNotNull { subjectNames[it.subjectId] }.distinct().joinToString(" / ")
                add("school", block.ids.first(), event.minusMinutes(s.schoolLeadMinutes.toLong()), event, event,
                    names.ifBlank { "Училищен час" }, listOfNotNull(source.startTime.toString(), first.actualRoom?.trim()?.takeIf { it.isNotEmpty() }?.let { "стая $it" }).joinToString(" · "))
            }
        }
        if (s.privateEnabled) lessons.filter { it.kind == LessonKind.PRIVATE && it.cancellationState == CancellationState.ACTIVE && "${it.id}:${it.actualDate}" !in s.excludedPrivateOccurrences && it.actualDate in today..today.plusDays(horizonDays.toLong()) }.forEach { lesson ->
            val event = lesson.actualDate.atTime(lesson.actualStartTime).atZone(zone)
            add("private", lesson.id, event.minusMinutes(s.privateLeadMinutes.toLong()), event, event,
                lesson.privateLessonName?.takeIf { it.isNotBlank() } ?: "Частен урок",
                listOfNotNull(subjectNames[lesson.subjectId], lesson.actualStartTime.toString()).joinToString(" · "))
        }
        repeat(horizonDays) { offset ->
            val date = today.plusDays(offset.toLong())
            val trigger = date.atTime(s.summaryHour, s.summaryMinute).atZone(zone)
            val expiry = date.plusDays(1).atStartOfDay(zone)
            if (s.dailySummaryEnabled && date.dayOfWeek.value in s.summaryDays) add("summary", date.toEpochDay(), trigger, expiry, trigger, "Дневен преглед", "")
            if (s.taskRemindersEnabled && s.overdueEnabled && !s.dailySummaryEnabled) add("overdue", date.toEpochDay(), trigger, expiry, trigger, "Просрочени задачи", "")
        }
        return desired.values.sortedBy { it.at }
    }

    fun overview(s: NotificationSettings, tasks: List<Task>, lessons: List<LessonInstance>, slots: List<ScheduleSlot>, date: LocalDate, noSchool: Boolean): String {
        val schoolDay = lessons.filter { it.actualDate == date && it.kind == LessonKind.SCHOOL }
        val school = if (noSchool) 0 else LessonBlockResolver.resolveAll(LessonBlockResolver.scheduleMembersByStart(schoolDay, slots).values.toList(), slots)
            .values.distinctBy { it.ids.first() }.count { it.lessons.any { member -> member.cancellationState == CancellationState.ACTIVE } }
        val privateCount = lessons.count { it.actualDate == date && it.kind == LessonKind.PRIVATE && it.cancellationState == CancellationState.ACTIVE && "${it.id}:${it.actualDate}" !in s.excludedPrivateOccurrences }
        val relevant = tasks.count { it.status == TaskStatus.PENDING && (it.dueAt == null || it.dueAt.toLocalDate() == date || (s.taskRemindersEnabled && s.overdueEnabled && it.dueAt.toLocalDate().isBefore(date))) }
        return "Днес имаш $school ${if (school == 1) "учебна сесия" else "учебни сесии"}, $privateCount ${if (privateCount == 1) "частен урок" else "частни уроци"} и $relevant ${if (relevant == 1) "задача" else "задачи"}."
    }
}
