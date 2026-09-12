package com.eduflow.app.ui

import com.eduflow.app.data.bootstrap.PersonalTimetableData
import com.eduflow.app.data.local.ScheduleSlot
import java.time.LocalTime

data class SchoolPeriodOption(
    val index: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/** UI-facing view of the existing school-period source of truth. */
val schoolPeriodOptions: List<SchoolPeriodOption>
    get() = PersonalTimetableData.periodTimes
        .toSortedMap()
        .map { (index, times) -> SchoolPeriodOption(index, times.first, times.second) }

fun schoolPeriodOption(index: Int): SchoolPeriodOption? = schoolPeriodOptions.firstOrNull { it.index == index }

fun schoolWeekdayResourceIndex(weekday: Int): Int? = weekday.takeIf { it in 1..5 }?.minus(1)

fun scheduleSlotUsesCustomTimes(slot: ScheduleSlot): Boolean =
    schoolPeriodOption(slot.lessonIndex)?.let { it.startTime != slot.startTime || it.endTime != slot.endTime } ?: true

fun scheduleSlotConflicts(candidate: ScheduleSlot, existing: List<ScheduleSlot>): Boolean = existing.any { current ->
    current.id != candidate.id &&
        current.scheduleTemplateId == candidate.scheduleTemplateId &&
        current.weekday == candidate.weekday &&
        current.lessonIndex == candidate.lessonIndex
}

fun firstAvailableSchoolSlot(existing: List<ScheduleSlot>, templateId: Long): Pair<Int, Int> =
    (1..5).asSequence()
        .flatMap { weekday -> schoolPeriodOptions.asSequence().map { weekday to it.index } }
        .firstOrNull { (weekday, period) ->
            existing.none { it.scheduleTemplateId == templateId && it.weekday == weekday && it.lessonIndex == period }
        }
        ?: (1 to schoolPeriodOptions.first().index)

fun initialSchoolSlotPosition(slot: ScheduleSlot?, occupiedSlots: List<ScheduleSlot>, templateId: Long): Pair<Int, Int> =
    slot?.weekday?.let { it to slot.lessonIndex } ?: firstAvailableSchoolSlot(occupiedSlots, templateId)
