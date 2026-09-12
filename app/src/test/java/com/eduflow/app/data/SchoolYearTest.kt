package com.eduflow.app.data

import com.eduflow.app.domain.ScheduleCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchoolYearTest {
    @Test fun schoolDatesRespectInclusiveBounds() {
        assertFalse(SchoolYear.containsSchoolDate(LocalDate.of(2026, 9, 14)))
        assertTrue(SchoolYear.containsSchoolDate(LocalDate.of(2026, 9, 15)))
        assertTrue(SchoolYear.containsSchoolDate(LocalDate.of(2027, 5, 13)))
        assertFalse(SchoolYear.containsSchoolDate(LocalDate.of(2027, 5, 14)))
    }

    @Test fun partialFirstWeekIsCheckedPerDate() {
        assertFalse(SchoolYear.containsSchoolDate(LocalDate.of(2026, 9, 14)))
        assertTrue(SchoolYear.containsSchoolDate(LocalDate.of(2026, 9, 15)))
    }

    @Test fun boundaryRulesDoNotChangeCycleResolution() {
        val anchor = LocalDate.of(2026, 9, 21)
        val cycle = listOf(10L, 20L)
        assertEquals(10L, ScheduleCycle.resolveTemplateId(anchor, anchor, 10L, cycle))
        assertEquals(20L, ScheduleCycle.resolveTemplateId(anchor.plusWeeks(1), anchor, 10L, cycle))
    }
}
