package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SchoolWeekRangeLogicTest {
    private val start = LocalDate.of(2026, 9, 15)
    private val end = LocalDate.of(2027, 5, 13)

    @Test fun fullWeekBeforeSchoolYearIsBefore() {
        assertEquals(SchoolWeekRangeState.BEFORE_YEAR, classifySchoolWeek(LocalDate.of(2026, 8, 31), start, end))
        assertEquals(SchoolWeekRangeState.BEFORE_YEAR, classifySchoolWeek(LocalDate.of(2026, 9, 7), start, end))
    }

    @Test fun weekContainingSchoolStartIsActive() {
        assertEquals(SchoolWeekRangeState.ACTIVE_RANGE, classifySchoolWeek(LocalDate.of(2026, 9, 14), start, end))
    }

    @Test fun middleWeekAndWeekContainingEndAreActive() {
        assertEquals(SchoolWeekRangeState.ACTIVE_RANGE, classifySchoolWeek(LocalDate.of(2026, 11, 2), start, end))
        assertEquals(SchoolWeekRangeState.ACTIVE_RANGE, classifySchoolWeek(LocalDate.of(2027, 5, 10), start, end))
    }

    @Test fun fullWeekAfterSchoolYearIsAfter() {
        assertEquals(SchoolWeekRangeState.AFTER_YEAR, classifySchoolWeek(LocalDate.of(2027, 5, 17), start, end))
    }
}
