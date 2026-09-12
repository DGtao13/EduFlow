package com.eduflow.app.data

import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskReminder
import com.eduflow.app.data.local.TaskReminderKind
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderLogicTest {
    private val now = LocalDateTime.of(2026, 9, 21, 8, 0)
    private fun task(id: Long = 1, priority: TaskPriority = TaskPriority.MUST, due: LocalDateTime? = now.plusDays(2), status: TaskStatus = TaskStatus.PENDING) = Task(id = id, title = "$id", type = TaskType.HOMEWORK, priority = priority, dueAt = due, status = status, createdAt = now)
    private fun reminder(kind: TaskReminderKind) = TaskReminder(id = 1, taskId = 1, kind = kind, createdAt = now)

    @Test fun relativeTriggersUseConcreteDeadline() {
        val due = now.plusDays(3)
        assertEquals(due, ReminderLogic.triggerAt(task(due = due), reminder(TaskReminderKind.AT_DEADLINE)))
        assertEquals(due.minusHours(1), ReminderLogic.triggerAt(task(due = due), reminder(TaskReminderKind.ONE_HOUR_BEFORE)))
        assertEquals(due.minusDays(1), ReminderLogic.triggerAt(task(due = due), reminder(TaskReminderKind.ONE_DAY_BEFORE)))
        assertEquals(due.minusDays(3), ReminderLogic.triggerAt(task(due = due), reminder(TaskReminderKind.THREE_DAYS_BEFORE)))
    }

    @Test fun pastTriggersAndCompletedTasksAreNotSchedulableButReopenIs() {
        assertFalse(ReminderLogic.isSchedulable(task(due = now.plusMinutes(30)), reminder(TaskReminderKind.ONE_HOUR_BEFORE), now))
        assertFalse(ReminderLogic.isSchedulable(task(status = TaskStatus.COMPLETED), reminder(TaskReminderKind.AT_DEADLINE), now))
        assertTrue(ReminderLogic.isSchedulable(task(status = TaskStatus.PENDING), reminder(TaskReminderKind.AT_DEADLINE), now))
    }

    @Test fun widgetSortsMustUrgencyBeforeOtherPriorities() {
        val sorted = WidgetTaskLogic.sorted(listOf(task(5, TaskPriority.OPTIONAL), task(4, TaskPriority.SHOULD), task(3, TaskPriority.MUST, now.plusDays(2)), task(2, TaskPriority.MUST, now.plusHours(2)), task(1, TaskPriority.MUST, now.minusMinutes(1))), now)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sorted.map { it.id })
    }

    @Test fun dailySummaryUsesOnlyLivePendingTasks() {
        val counts = DailySummaryLogic.calculate(listOf(task(1, due = now.minusMinutes(1)), task(2, due = now.plusHours(2)), task(3, due = now.plusDays(2)), task(4, due = now, status = TaskStatus.COMPLETED)), now)
        assertEquals(1, counts.overdue); assertEquals(1, counts.dueToday); assertEquals(1, counts.upcomingMust)
    }
}
