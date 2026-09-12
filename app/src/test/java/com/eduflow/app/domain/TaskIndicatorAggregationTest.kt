package com.eduflow.app.domain

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskIndicatorAggregationTest {
    private val subjectId = 10L
    private val date = LocalDate.of(2026, 9, 14)
    private val slots = (1..8).map { period ->
        val start = LocalTime.of(8, 0).plusMinutes((period - 1) * 45L)
        ScheduleSlot(id = 100L + period, scheduleTemplateId = 1, weekday = 1, lessonIndex = period, startTime = start, endTime = start.plusMinutes(45), subjectId = subjectId)
    }

    private fun lesson(id: Long, period: Int, cancelled: Boolean = false) = LessonInstance(
        id = id,
        actualDate = date,
        actualStartTime = slots.first { it.lessonIndex == period }.startTime,
        actualEndTime = slots.first { it.lessonIndex == period }.endTime,
        subjectId = subjectId,
        sourceScheduleSlotId = 100L + period,
        kind = LessonKind.SCHOOL,
        cancellationState = if (cancelled) CancellationState.CANCELLED else CancellationState.ACTIVE
    )

    private fun task(id: Long, dueId: Long?, originId: Long? = null, status: TaskStatus = TaskStatus.PENDING) = Task(
        id = id,
        title = "Task $id",
        subjectId = subjectId,
        originatingLessonInstanceId = originId,
        dueLessonInstanceId = dueId,
        type = TaskType.HOMEWORK,
        priority = TaskPriority.MUST,
        status = status,
        createdAt = LocalDateTime.of(2026, 9, 1, 8, 0)
    )

    private fun states(lessons: List<LessonInstance>, tasks: List<Task>) =
        TaskIndicatorAggregation.statesByLessonId(lessons, slots, tasks)

    @Test fun onePendingTaskShowsCountOneForSingleSchoolLesson() {
        val target = lesson(1, 1)
        assertEquals(LessonTaskIndicatorState.Pending(1), states(listOf(target), listOf(task(1, target.id)))[target.id])
    }

    @Test fun multiplePendingTasksShowCombinedCount() {
        val target = lesson(1, 1)
        assertEquals(LessonTaskIndicatorState.Pending(2), states(listOf(target), listOf(task(1, target.id), task(2, target.id)))[target.id])
    }

    @Test fun completedOnlyProducesCompletedState() {
        val target = lesson(1, 1)
        assertEquals(LessonTaskIndicatorState.Completed, states(listOf(target), listOf(task(1, target.id, status = TaskStatus.COMPLETED)))[target.id])
    }

    @Test fun multipleCompletedTasksStillProduceOneCompletedState() {
        val target = lesson(1, 1)
        assertEquals(LessonTaskIndicatorState.Completed, states(listOf(target), listOf(
            task(1, target.id, status = TaskStatus.COMPLETED),
            task(2, target.id, status = TaskStatus.COMPLETED),
            task(3, target.id, status = TaskStatus.COMPLETED)
        ))[target.id])
    }

    @Test fun pendingStateWinsOverCompletedStateAndCountsOnlyPending() {
        val target = lesson(1, 1)
        assertEquals(LessonTaskIndicatorState.Pending(1), states(listOf(target), listOf(
            task(1, target.id),
            task(2, target.id, status = TaskStatus.COMPLETED),
            task(3, target.id, status = TaskStatus.COMPLETED)
        ))[target.id])
    }

    @Test fun finalPendingCompletionChangesStateToCompleted() {
        val target = lesson(1, 1)
        val completed = listOf(task(1, target.id, status = TaskStatus.COMPLETED), task(2, target.id, status = TaskStatus.COMPLETED))
        assertEquals(LessonTaskIndicatorState.Pending(1), states(listOf(target), completed + task(3, target.id))[target.id])
        assertEquals(LessonTaskIndicatorState.Completed, states(listOf(target), completed + task(3, target.id, status = TaskStatus.COMPLETED))[target.id])
    }

    @Test fun reopeningCompletedTaskChangesStateBackToPending() {
        val target = lesson(1, 1)
        assertEquals(LessonTaskIndicatorState.Completed, states(listOf(target), listOf(task(1, target.id, status = TaskStatus.COMPLETED)))[target.id])
        assertEquals(LessonTaskIndicatorState.Pending(1), states(listOf(target), listOf(task(1, target.id, status = TaskStatus.PENDING)))[target.id])
    }

    @Test fun dueLessonIsCountedButOriginLessonIsNot() {
        val origin = lesson(1, 1)
        val due = lesson(2, 4)
        val result = states(listOf(origin, due), listOf(task(1, due.id, origin.id)))
        assertEquals(null, result[origin.id])
        assertEquals(LessonTaskIndicatorState.Pending(1), result[due.id])
    }

    @Test fun firstMemberDueAnchorCreatesOneSharedDoubleBlockCount() {
        val first = lesson(1, 1)
        val second = lesson(2, 2)
        val result = states(listOf(first, second), listOf(task(1, first.id)))
        assertEquals(LessonTaskIndicatorState.Pending(1), result[first.id])
        assertEquals(LessonTaskIndicatorState.Pending(1), result[second.id])
    }

    @Test fun differentMembersOfSameBlockCombineIntoOneCount() {
        val first = lesson(1, 1)
        val second = lesson(2, 2)
        val result = states(listOf(first, second), listOf(task(1, first.id), task(2, second.id)))
        assertEquals(LessonTaskIndicatorState.Pending(2), result[first.id])
        assertEquals(LessonTaskIndicatorState.Pending(2), result[second.id])
    }

    @Test fun threePeriodBlockAggregatesAllMembers() {
        val first = lesson(1, 1)
        val middle = lesson(2, 2)
        val last = lesson(3, 3)
        val result = states(listOf(first, middle, last), listOf(task(1, first.id), task(2, middle.id), task(3, last.id)))
        assertEquals(mapOf(first.id to LessonTaskIndicatorState.Pending(3), middle.id to LessonTaskIndicatorState.Pending(3), last.id to LessonTaskIndicatorState.Pending(3)), result)
    }

    @Test fun cancelledTargetStillCountsWithoutChangingLessonOrTask() {
        val target = lesson(1, 1, cancelled = true)
        val pending = task(1, target.id)
        assertEquals(LessonTaskIndicatorState.Pending(1), states(listOf(target), listOf(pending))[target.id])
        assertEquals(target.id, pending.dueLessonInstanceId)
    }

    @Test fun noTasksProducesNoIndicatorState() {
        assertEquals(emptyMap<Long, LessonTaskIndicatorState>(), states(listOf(lesson(1, 1)), emptyList()))
    }

    @Test fun assignedTasksAggregateOnBlockAndKeepCompletedHistory() {
        val first = lesson(1, 1)
        val second = lesson(2, 2)
        val tasks = listOf(task(1, null, first.id), task(2, null, second.id, TaskStatus.COMPLETED))
        val result = TaskIndicatorAggregation.assignedCountsByLessonId(listOf(first, second), slots, tasks)
        assertEquals(2, result[first.id])
        assertEquals(2, result[second.id])
        assertEquals(second.id, TaskIndicatorAggregation.owners(LessonBlock(listOf(first, second))).second)
    }

    @Test fun cancelledBlockHasNoIndicatorOwner() {
        val first = lesson(1, 1, cancelled = true)
        val second = lesson(2, 2, cancelled = true)
        assertEquals(null to null, TaskIndicatorAggregation.owners(LessonBlock(listOf(first, second))))
    }
}
