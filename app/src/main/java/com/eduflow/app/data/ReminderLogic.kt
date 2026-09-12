package com.eduflow.app.data

import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskReminder
import com.eduflow.app.data.local.TaskReminderKind
import com.eduflow.app.data.local.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

object ReminderLogic {
    fun triggerAt(task: Task, reminder: TaskReminder): LocalDateTime? = when (reminder.kind) {
        TaskReminderKind.AT_DEADLINE -> task.dueAt
        TaskReminderKind.ONE_HOUR_BEFORE -> task.dueAt?.minusHours(1)
        TaskReminderKind.ONE_DAY_BEFORE -> task.dueAt?.minusDays(1)
        TaskReminderKind.THREE_DAYS_BEFORE -> task.dueAt?.minusDays(3)
        TaskReminderKind.CUSTOM -> reminder.customTriggerAt
    }
    fun isSchedulable(task: Task, reminder: TaskReminder, now: LocalDateTime): Boolean = task.status == TaskStatus.PENDING && reminder.enabled && triggerAt(task, reminder)?.isAfter(now) == true
    fun suggestedKinds(priority: TaskPriority): List<TaskReminderKind> = when (priority) {
        TaskPriority.MUST -> listOf(TaskReminderKind.ONE_DAY_BEFORE, TaskReminderKind.AT_DEADLINE)
        TaskPriority.SHOULD -> listOf(TaskReminderKind.ONE_DAY_BEFORE)
        TaskPriority.OPTIONAL -> emptyList()
    }
}

object WidgetTaskLogic {
    fun sorted(tasks: List<Task>, now: LocalDateTime): List<Task> = tasks.filter { it.status == TaskStatus.PENDING }.sortedWith(
        compareBy<Task> {
            when {
                it.priority == TaskPriority.MUST && TaskLogic.isOverdue(it, now) -> 0
                it.priority == TaskPriority.MUST && it.dueAt?.toLocalDate() == now.toLocalDate() -> 1
                it.priority == TaskPriority.MUST -> 2
                it.priority == TaskPriority.SHOULD -> 3
                else -> 4
            }
        }.thenBy { it.dueAt == null }.thenBy { it.dueAt }.thenBy { it.createdAt }.thenBy { it.id }
    )
}

data class DailySummaryCounts(val overdue: Int, val dueToday: Int, val upcomingMust: Int)
object DailySummaryLogic {
    fun calculate(tasks: List<Task>, now: LocalDateTime): DailySummaryCounts {
        val pending = tasks.filter { it.status == TaskStatus.PENDING }
        return DailySummaryCounts(
            overdue = pending.count { TaskLogic.isOverdue(it, now) },
            dueToday = pending.count { it.dueAt?.toLocalDate() == now.toLocalDate() && !TaskLogic.isOverdue(it, now) },
            upcomingMust = pending.count { it.priority == TaskPriority.MUST && it.dueAt?.toLocalDate()?.isAfter(now.toLocalDate()) == true }
        )
    }
}
