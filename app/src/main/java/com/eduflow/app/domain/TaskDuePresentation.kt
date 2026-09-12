package com.eduflow.app.domain

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.ScheduleSlot
import java.time.LocalTime

/** Derived display metadata only; Task persistence remains anchored to a lesson instance. */
data class TaskDueTimeRange(val start: LocalTime, val end: LocalTime)

object TaskDuePresentation {
    fun rangesByLessonId(
        lessons: List<LessonInstance>,
        slots: List<ScheduleSlot>
    ): Map<Long, TaskDueTimeRange> {
        val ranges = mutableMapOf<Long, TaskDueTimeRange>()
        lessons.groupBy { it.actualDate }.values.forEach { dayLessons ->
            LessonBlockResolver.resolveAll(dayLessons, slots).values
                .distinctBy { it.ids.first() }
                .filter { it.isMultiPeriod }
                .forEach { block ->
                    val range = TaskDueTimeRange(block.startTime, block.endTime)
                    block.ids.forEach { lessonId -> ranges[lessonId] = range }
                }
        }
        return ranges
    }
}
