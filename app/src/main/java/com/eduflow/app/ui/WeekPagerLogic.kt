package com.eduflow.app.ui

import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.domain.ScheduleCycle
import java.time.DayOfWeek
import java.time.LocalDate

data class WeekPagerRange(val firstMonday: LocalDate, val lastMonday: LocalDate) {
    val pageCount: Int get() = java.time.temporal.ChronoUnit.WEEKS.between(firstMonday, lastMonday).toInt() + 1
    fun mondayFor(index: Int): LocalDate = firstMonday.plusWeeks(index.toLong())
    fun indexFor(monday: LocalDate): Int = java.time.temporal.ChronoUnit.WEEKS.between(firstMonday, ScheduleCycle.mondayOf(monday)).toInt()

    companion object {
        fun around(today: LocalDate, academicYear: AcademicYearSettings): WeekPagerRange {
            val current = ScheduleCycle.mondayOf(today)
            val start = minOf(current.minusWeeks(8), ScheduleCycle.mondayOf(academicYear.startDate).minusWeeks(8))
            val end = maxOf(current.plusWeeks(8), ScheduleCycle.mondayOf(academicYear.endDate).plusWeeks(8))
            return WeekPagerRange(start, end)
        }
    }
}

fun desiredAdjacentInnerPosition(pageIndex: Int, settledPage: Int): AdjacentInnerPosition = when {
    pageIndex < settledPage -> AdjacentInnerPosition.RIGHT
    pageIndex > settledPage -> AdjacentInnerPosition.LEFT
    else -> AdjacentInnerPosition.PRESERVE
}

fun weekdayToTimetableIndex(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> 0
    DayOfWeek.TUESDAY -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY -> 3
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 4
}

/** Explicit today navigation wins over a saved active-week position; otherwise restore it. */
fun resolveActiveInnerOffset(savedOffset: Int?, explicitTodayOffset: Int?, freshTodayOffset: Int): Int =
    explicitTodayOffset ?: savedOffset ?: freshTodayOffset

/** The header and settled-week data must follow this page, never a partially dragged page. */
fun settledWeekMonday(range: WeekPagerRange, settledPage: Int): LocalDate = range.mondayFor(settledPage)

enum class AdjacentInnerPosition { LEFT, RIGHT, PRESERVE }
