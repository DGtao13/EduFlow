package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PrivateLessonEditorLogicTest {
    @Test fun validTimeRangeIsAccepted() {
        assertTrue(isValidPrivateLessonTimeRange(LocalTime.of(17, 0), LocalTime.of(18, 0)))
    }

    @Test fun equalTimesAreRejected() {
        assertFalse(isValidPrivateLessonTimeRange(LocalTime.of(17, 0), LocalTime.of(17, 0)))
    }

    @Test fun endBeforeStartIsRejected() {
        assertFalse(isValidPrivateLessonTimeRange(LocalTime.of(18, 0), LocalTime.of(17, 0)))
    }

    @Test fun explicitContextDateWinsOverToday() {
        val contextDate = LocalDate.of(2026, 9, 20)
        assertEquals(contextDate, oneOffDefaultDate(contextDate, LocalDate.of(2026, 9, 12)))
    }

    @Test fun missingContextDateUsesToday() {
        val today = LocalDate.of(2026, 9, 12)
        assertEquals(today, oneOffDefaultDate(null, today))
    }
}
