package com.eduflow.app.ui

import com.eduflow.app.domain.ScheduleCycle
import java.time.LocalDate

enum class SchoolWeekRangeState {
    BEFORE_YEAR,
    ACTIVE_RANGE,
    AFTER_YEAR
}

fun classifySchoolWeek(
    weekMonday: LocalDate,
    academicYearStart: LocalDate,
    academicYearEnd: LocalDate
): SchoolWeekRangeState {
    val monday = ScheduleCycle.mondayOf(weekMonday)
    val friday = monday.plusDays(4)
    return when {
        friday.isBefore(academicYearStart) -> SchoolWeekRangeState.BEFORE_YEAR
        monday.isAfter(academicYearEnd) -> SchoolWeekRangeState.AFTER_YEAR
        else -> SchoolWeekRangeState.ACTIVE_RANGE
    }
}
