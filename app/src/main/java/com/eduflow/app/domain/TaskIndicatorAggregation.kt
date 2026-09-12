package com.eduflow.app.domain

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskStatus

sealed interface LessonTaskIndicatorState {
    data object None : LessonTaskIndicatorState
    data class Pending(val count: Int) : LessonTaskIndicatorState
    data object Completed : LessonTaskIndicatorState
}

data class LessonTaskIndicatorCounts(
    val pendingCount: Int = 0,
    val completedCount: Int = 0
) {
    val state: LessonTaskIndicatorState
        get() = when {
            pendingCount > 0 -> LessonTaskIndicatorState.Pending(pendingCount)
            completedCount > 0 -> LessonTaskIndicatorState.Completed
            else -> LessonTaskIndicatorState.None
        }
}

/** Prepared task states keyed by every member of a visual SCHOOL block. */
object TaskIndicatorAggregation {
    fun owners(block: LessonBlock): Pair<Long?, Long?> {
        val active = block.lessons.filter { it.cancellationState != com.eduflow.app.data.local.CancellationState.CANCELLED }
        return active.firstOrNull()?.id to active.lastOrNull()?.id
    }

    fun assignedCountsByLessonId(lessons: List<LessonInstance>, slots: List<ScheduleSlot>, tasks: List<Task>): Map<Long, Int> {
        val counts = tasks.mapNotNull { it.originatingLessonInstanceId }.groupingBy { it }.eachCount()
        return lessons.filter { it.kind == LessonKind.SCHOOL }.groupBy { it.actualDate }.values
            .flatMap { LessonBlockResolver.resolveAll(it, slots).values }
            .distinctBy { it.ids.first() }
            .flatMap { block ->
                val count = block.ids.sumOf { counts[it] ?: 0 }
                if (count == 0) emptyList() else block.ids.map { it to count }
            }.toMap()
    }

    fun statesByLessonId(
        lessons: List<LessonInstance>,
        scheduleSlots: List<ScheduleSlot>,
        tasks: List<Task>
    ): Map<Long, LessonTaskIndicatorState> {
        val schoolLessons = lessons.filter { it.kind == LessonKind.SCHOOL }
        val schoolIds = schoolLessons.mapTo(mutableSetOf()) { it.id }
        val countsByDueId = mutableMapOf<Long, LessonTaskIndicatorCounts>()
        tasks.forEach { task ->
            val dueId = task.dueLessonInstanceId?.takeIf(schoolIds::contains) ?: return@forEach
            val counts = countsByDueId[dueId] ?: LessonTaskIndicatorCounts()
            countsByDueId[dueId] = when (task.status) {
                TaskStatus.PENDING -> counts.copy(pendingCount = counts.pendingCount + 1)
                TaskStatus.COMPLETED -> counts.copy(completedCount = counts.completedCount + 1)
            }
        }

        return schoolLessons.groupBy { it.actualDate }
            .values
            .flatMap { dayLessons -> LessonBlockResolver.resolveAll(dayLessons, scheduleSlots).values }
            .distinctBy { it.ids.first() }
            .flatMap { block ->
                val counts = block.ids
                    .map { countsByDueId[it] ?: LessonTaskIndicatorCounts() }
                    .fold(LessonTaskIndicatorCounts()) { total, member ->
                        LessonTaskIndicatorCounts(total.pendingCount + member.pendingCount, total.completedCount + member.completedCount)
                    }
                when (val state = counts.state) {
                    LessonTaskIndicatorState.None -> emptyList()
                    else -> block.ids.map { it to state }
                }
            }
            .toMap()
    }
}
