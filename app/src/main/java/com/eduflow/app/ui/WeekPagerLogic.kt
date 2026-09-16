package com.eduflow.app.ui

import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.domain.ScheduleCycle
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

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

data class ScheduleDateSelection(val monday: LocalDate, val schoolWeekdayIndex: Int?)

fun scheduleDateSelection(date: LocalDate): ScheduleDateSelection = ScheduleDateSelection(
    monday = ScheduleCycle.mondayOf(date),
    schoolWeekdayIndex = date.dayOfWeek.takeIf { it.value <= DayOfWeek.FRIDAY.value }?.let { it.value - 1 }
)

/** Explicit today navigation wins over a saved active-week position; otherwise restore it. */
fun resolveActiveInnerOffset(savedOffset: Int?, explicitTodayOffset: Int?, freshTodayOffset: Int): Int =
    explicitTodayOffset ?: savedOffset ?: freshTodayOffset

/** The header and settled-week data must follow this page, never a partially dragged page. */
fun settledWeekMonday(range: WeekPagerRange, settledPage: Int): LocalDate = range.mondayFor(settledPage)

enum class AdjacentInnerPosition { LEFT, RIGHT, PRESERVE }

/** Converts a finger movement into the corresponding scroll deltas for the timetable. */
data class TimetablePanDelta(val horizontal: Float, val vertical: Float)

fun timetablePanDelta(fingerDeltaX: Float, fingerDeltaY: Float): TimetablePanDelta =
    TimetablePanDelta(horizontal = -fingerDeltaX, vertical = -fingerDeltaY)

/**
 * Allows the pager to receive only horizontal movement that the bounded timetable could not
 * consume. Keeping diagonal/vertical gestures out of this path prevents accidental week changes.
 */
fun pagerBoundaryHandoffDelta(
    fingerDeltaX: Float,
    fingerDeltaY: Float,
    requestedHorizontal: Float,
    consumedByTimetable: Float
): Float = if (abs(fingerDeltaX) > abs(fingerDeltaY)) {
    requestedHorizontal - consumedByTimetable
} else {
    0f
}

fun pagerHandoffTarget(
    startPage: Int,
    accumulatedPagerScroll: Float,
    pageSizePx: Int,
    minimumPage: Int,
    maximumPage: Int,
    snapThreshold: Float = 0.30f
): Int {
    if (pageSizePx <= 0 || abs(accumulatedPagerScroll) / pageSizePx < snapThreshold) {
        return startPage.coerceIn(minimumPage, maximumPage)
    }
    return (startPage + if (accumulatedPagerScroll > 0f) 1 else -1)
        .coerceIn(minimumPage, maximumPage)
}

data class TimetableFlingVelocity(val horizontal: Float, val vertical: Float) {
    val isZero: Boolean get() = horizontal == 0f && vertical == 0f
}

/** Converts accepted pointer velocity into the existing positive-scroll coordinate system. */
fun timetableFlingVelocity(
    pointerVelocityX: Float,
    pointerVelocityY: Float,
    minimumFlingVelocity: Float
): TimetableFlingVelocity = TimetableFlingVelocity(
    horizontal = if (abs(pointerVelocityX) >= minimumFlingVelocity) -pointerVelocityX else 0f,
    vertical = if (abs(pointerVelocityY) >= minimumFlingVelocity) -pointerVelocityY else 0f
)

fun shouldStartTimetableFling(
    dragCrossedTouchSlop: Boolean,
    releasedNormally: Boolean,
    pagerHandoffActive: Boolean,
    velocity: TimetableFlingVelocity
): Boolean = dragCrossedTouchSlop && releasedNormally && !pagerHandoffActive && !velocity.isZero

/** A bounded axis is finished only when it cannot consume a meaningful requested frame delta. */
fun flingAxisRemainsActive(requestedDelta: Float, consumedDelta: Float): Boolean =
    abs(requestedDelta) < 0.01f || abs(requestedDelta - consumedDelta) < 0.01f
