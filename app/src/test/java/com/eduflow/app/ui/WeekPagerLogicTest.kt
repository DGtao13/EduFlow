package com.eduflow.app.ui

import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.data.SchoolYear
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
}
