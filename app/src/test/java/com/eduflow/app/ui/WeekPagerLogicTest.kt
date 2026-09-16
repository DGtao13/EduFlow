package com.eduflow.app.ui

import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.data.SchoolYear
import com.eduflow.app.domain.ScheduleCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekPagerLogicTest {
    private val range = WeekPagerRange.around(LocalDate.of(2026, 9, 11), AcademicYearSettings())

    @Test fun indexRoundTripsMonday() {
        val monday = LocalDate.of(2026, 9, 7)
        assertEquals(monday, range.mondayFor(range.indexFor(monday)))
    }

    @Test fun currentDateMapsToItsCalendarWeek() {
        val currentMonday = LocalDate.of(2026, 9, 7)
        assertEquals(currentMonday, range.mondayFor(range.indexFor(currentMonday)))
    }

    @Test fun previousAndNextMoveExactlyOneWeek() {
        val index = range.indexFor(LocalDate.of(2026, 9, 7))
        assertEquals(LocalDate.of(2026, 8, 31), range.mondayFor(index - 1))
        assertEquals(LocalDate.of(2026, 9, 14), range.mondayFor(index + 1))
    }

    @Test fun adjacentPagesHaveDirectionalInnerPositions() {
        assertEquals(AdjacentInnerPosition.RIGHT, desiredAdjacentInnerPosition(4, 5))
        assertEquals(AdjacentInnerPosition.LEFT, desiredAdjacentInnerPosition(6, 5))
        assertEquals(AdjacentInnerPosition.PRESERVE, desiredAdjacentInnerPosition(5, 5))
    }

    @Test fun headerWeekUsesSettledPageUntilAnotherPageSettles() {
        assertEquals(LocalDate.of(2026, 9, 7), settledWeekMonday(range, range.indexFor(LocalDate.of(2026, 9, 7))))
    }

    @Test fun academicYearBoundariesRemainUnchanged() {
        val settings = AcademicYearSettings()
        assertFalse(SchoolYear.containsSchoolDate(settings.startDate.minusDays(1), settings))
        assertTrue(SchoolYear.containsSchoolDate(settings.startDate, settings))
        assertTrue(SchoolYear.containsSchoolDate(settings.endDate, settings))
        assertFalse(SchoolYear.containsSchoolDate(settings.endDate.plusDays(1), settings))
    }

    @Test fun weekdayMappingUsesFridayForWeekend() {
        assertEquals(0, weekdayToTimetableIndex(DayOfWeek.MONDAY))
        assertEquals(1, weekdayToTimetableIndex(DayOfWeek.TUESDAY))
        assertEquals(2, weekdayToTimetableIndex(DayOfWeek.WEDNESDAY))
        assertEquals(3, weekdayToTimetableIndex(DayOfWeek.THURSDAY))
        assertEquals(4, weekdayToTimetableIndex(DayOfWeek.FRIDAY))
        assertEquals(4, weekdayToTimetableIndex(DayOfWeek.SATURDAY))
        assertEquals(4, weekdayToTimetableIndex(DayOfWeek.SUNDAY))
    }

    @Test fun savedActiveOffsetWinsOverFreshDefault() {
        assertEquals(312, resolveActiveInnerOffset(savedOffset = 312, explicitTodayOffset = null, freshTodayOffset = 784))
    }

    @Test fun explicitTodayOffsetOverridesSavedActiveOffset() {
        assertEquals(784, resolveActiveInnerOffset(savedOffset = 312, explicitTodayOffset = 784, freshTodayOffset = 0))
    }

    @Test fun freshActiveWeekUsesTodayWhenNoOffsetExists() {
        assertEquals(784, resolveActiveInnerOffset(savedOffset = null, explicitTodayOffset = null, freshTodayOffset = 784))
    }

    @Test fun diagonalPanRetainsBothAxisComponents() {
        assertEquals(TimetablePanDelta(horizontal = 24f, vertical = 18f), timetablePanDelta(-24f, -18f))
    }

    @Test fun pagerReceivesOnlyUnconsumedHorizontalMovement() {
        assertEquals(12f, pagerBoundaryHandoffDelta(-30f, 4f, 30f, 18f))
        assertEquals(0f, pagerBoundaryHandoffDelta(-30f, -42f, 30f, 0f))
    }

    @Test fun pagerHandoffUsesTheExistingThirtyPercentThreshold() {
        assertEquals(4, pagerHandoffTarget(4, 29f, 100, 0, 8))
        assertEquals(5, pagerHandoffTarget(4, 30f, 100, 0, 8))
        assertEquals(3, pagerHandoffTarget(4, -30f, 100, 0, 8))
    }

    @Test fun acceptedFlingVelocityPreservesBothAxesAndScrollDirection() {
        assertEquals(
            TimetableFlingVelocity(horizontal = 900f, vertical = -700f),
            timetableFlingVelocity(pointerVelocityX = -900f, pointerVelocityY = 700f, minimumFlingVelocity = 50f)
        )
    }

    @Test fun slowVelocityDoesNotStartMomentum() {
        val velocity = timetableFlingVelocity(pointerVelocityX = 49f, pointerVelocityY = -20f, minimumFlingVelocity = 50f)
        assertTrue(velocity.isZero)
        assertFalse(shouldStartTimetableFling(true, true, false, velocity))
    }

    @Test fun flingRequiresReleasedPostSlopDragAndNoPagerHandoff() {
        val velocity = TimetableFlingVelocity(horizontal = 600f, vertical = 400f)
        assertTrue(shouldStartTimetableFling(true, true, false, velocity))
        assertFalse(shouldStartTimetableFling(false, true, false, velocity))
        assertFalse(shouldStartTimetableFling(true, false, false, velocity))
        assertFalse(shouldStartTimetableFling(true, true, true, velocity))
    }

    @Test fun eachFlingAxisStopsOnlyWhenItsRequestedMovementIsUnconsumed() {
        assertFalse(flingAxisRemainsActive(requestedDelta = 12f, consumedDelta = 0f))
        assertTrue(flingAxisRemainsActive(requestedDelta = -9f, consumedDelta = -9f))
        assertFalse(flingAxisRemainsActive(requestedDelta = 6f, consumedDelta = 0f))
    }
}
