package com.eduflow.app.ui

import com.eduflow.app.data.NotificationSettings
import com.eduflow.app.data.ReminderLogic
import com.eduflow.app.data.local.*
import java.time.*

enum class TaskDueMode { NONE, EXACT, NEXT, SECOND_NEXT }

/** Only editable, saveable values: picker state and dynamic preview are deliberately absent. */
data class TaskDraft(
    val title: String, val description: String?, val subjectId: Long?, val type: TaskType,
    val priority: TaskPriority, val dueMode: TaskDueMode, val concreteDue: LocalDateTime?,
    val checklist: List<Pair<String, Boolean>>, val reminders: Set<ReminderDraft>
) {
    companion object {
        fun from(task: Task, mode: TaskDueMode, date: LocalDate?, time: LocalTime?,
                 items: List<TaskChecklistItem>, reminders: List<TaskReminder>): TaskDraft = TaskDraft(
            task.title.trim(), task.description?.trim()?.ifBlank { null }, task.subjectId, task.type, task.priority,
            mode, if (mode == TaskDueMode.EXACT && date != null && time != null) date.atTime(time) else null,
            items.map { it.text to it.isCompleted },
            saveableTaskReminders(reminders, mode != TaskDueMode.NONE)
                .map { ReminderDraft(it.kind, it.enabled, it.customTriggerAt.takeIf { _ -> it.kind == TaskReminderKind.CUSTOM }) }.toSet()
        )
    }
}
data class ReminderDraft(val kind: TaskReminderKind, val enabled: Boolean, val customAt: LocalDateTime?)

/** Strictly forward, including an exact quarter-hour; ZonedDateTime handles midnight and DST. */
fun concreteDueDefault(now: ZonedDateTime): LocalDateTime {
    var candidate = now.toLocalDateTime().withSecond(0).withNano(0).plusMinutes((15 - now.minute % 15).toLong())
    // Tasks store LocalDateTime (no offset). Use the same earliest-overlap interpretation
    // as scheduling, and skip ambiguous values that would consequently be in the past.
    while (!candidate.atZone(now.zone).toInstant().isAfter(now.toInstant())) candidate = candidate.plusMinutes(15)
    return candidate.atZone(now.zone).toLocalDateTime()
}

/**
 * Initializes an empty concrete draft regardless of whether the Task is new or existing.
 * Once either picker has supplied a value, the session draft wins over the clock.
 */
fun concreteDueSelection(date: LocalDate?, time: LocalTime?, now: ZonedDateTime): Pair<LocalDate?, LocalTime?> {
    if (date != null || time != null) return date to time
    val prefill = concreteDueDefault(now)
    return prefill.toLocalDate() to prefill.toLocalTime()
}

// A disabled, valid custom record encodes an explicitly empty personal set using the
// existing schema and archive format. It never creates a trigger or falls back to globals.
private val emptyPersonalAt = LocalDateTime.of(1970, 1, 1, 0, 0)
fun personalReminderMarker(taskId: Long): TaskReminder = TaskReminder(taskId = taskId,
    kind = TaskReminderKind.CUSTOM, enabled = false, customTriggerAt = emptyPersonalAt, createdAt = LocalDateTime.now())
fun TaskReminder.isModeMarker(): Boolean = kind == TaskReminderKind.CUSTOM && !enabled && customTriggerAt == emptyPersonalAt

fun saveableTaskReminders(reminders: List<TaskReminder>, hasDue: Boolean): List<TaskReminder> {
    val usable = if (hasDue) reminders else reminders.filter { it.kind == TaskReminderKind.CUSTOM }
    return if (reminders.isNotEmpty() && usable.isEmpty()) listOf(personalReminderMarker(reminders.first().taskId)) else usable
}

fun taskReminderSummary(settings: NotificationSettings, priority: TaskPriority, hasDue: Boolean,
                        reminders: List<TaskReminder>): String {
    if (reminders.isNotEmpty()) {
        val count = saveableTaskReminders(reminders, hasDue).count { it.enabled && !it.isModeMarker() }
        return if (count == 0) "Персонални · няма активни напомняния"
        else "Персонални · $count ${if (count == 1) "напомняне" else "напомняния"}"
    }
    val minutes = if (settings.taskLeadsConfigured) settings.taskLeadMinutes else ReminderLogic.suggestedKinds(priority)
        .mapNotNull { when (it) { TaskReminderKind.AT_DEADLINE -> 0; TaskReminderKind.ONE_HOUR_BEFORE -> 60; TaskReminderKind.ONE_DAY_BEFORE -> 1440; else -> null } }.toSet()
    if (!settings.taskRemindersEnabled || !hasDue || minutes.isEmpty()) return "Няма активни глобални напомняния"
    val labels = mapOf(0 to "при срока", 30 to "30 мин. по-рано", 60 to "1 ч. по-рано", 1440 to "1 ден по-рано")
    return "По глобалните настройки · " + minutes.sorted().mapNotNull(labels::get).joinToString(" и ")
}
