package com.eduflow.app.data

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskLogicTest {
    private val now = LocalDateTime.of(2026, 9, 21, 10, 0)
    private fun task(id: Long, priority: TaskPriority, due: LocalDateTime? = null) = Task(id = id, title = "$id", type = TaskType.HOMEWORK, priority = priority, dueAt = due, createdAt = now)
    private fun lesson(id: Long, date: LocalDate, time: LocalTime, cancelled: Boolean = false) = LessonInstance(id = id, actualDate = date, actualStartTime = time, actualEndTime = time.plusMinutes(45), subjectId = 1, kind = LessonKind.SCHOOL, cancellationState = if (cancelled) CancellationState.CANCELLED else CancellationState.ACTIVE)

    @Test fun pendingSortsPriorityThenOverdueThenDeadline() {
        val sorted = TaskLogic.sortedPending(listOf(task(3, TaskPriority.SHOULD), task(2, TaskPriority.MUST, now.minusMinutes(1)), task(1, TaskPriority.MUST, now.plusDays(1))), now)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }
    @Test fun completionAndReopenTransitionTimestamp() {
        val done = TaskLogic.complete(task(1, TaskPriority.MUST), now)
        assertEquals(TaskStatus.COMPLETED, done.status); assertEquals(now, done.completedAt)
        assertEquals(TaskStatus.PENDING, TaskLogic.reopen(done).status); assertNull(TaskLogic.reopen(done).completedAt)
    }
    @Test fun nextLessonSkipsCancelledAndNoSchool() {
        val selected = NextLessonResolver.choose(listOf(lesson(1, LocalDate.of(2026, 9, 22), LocalTime.of(8, 0), true), lesson(2, LocalDate.of(2026, 9, 23), LocalTime.of(8, 0)), lesson(3, LocalDate.of(2026, 9, 24), LocalTime.of(8, 0))), now, setOf(LocalDate.of(2026, 9, 23)))
        assertEquals(3L, selected?.id)
    }
    @Test fun nextLessonChoosesEarliestActualOccurrenceAcrossWeeks() {
        val selected = NextLessonResolver.choose(listOf(lesson(3, LocalDate.of(2026, 10, 5), LocalTime.of(8, 0)), lesson(2, LocalDate.of(2026, 9, 25), LocalTime.of(9, 0)), lesson(1, LocalDate.of(2026, 9, 24), LocalTime.of(10, 0))), now, emptySet())
        assertEquals(1L, selected?.id)
    }
    @Test fun nextLessonReturnsNullWhenNoFutureOccurrenceExists() {
        assertNull(NextLessonResolver.choose(listOf(lesson(1, LocalDate.of(2026, 9, 21), LocalTime.of(9, 0))), now, emptySet()))
    }
    @Test fun overdueRequiresADeadlineStrictlyBeforeNow() {
        assertEquals(false, TaskLogic.isOverdue(task(1, TaskPriority.MUST, now), now))
        assertEquals(true, TaskLogic.isOverdue(task(2, TaskPriority.MUST, now.minusNanos(1)), now))
    }
}
