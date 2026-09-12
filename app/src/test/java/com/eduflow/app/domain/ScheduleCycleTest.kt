package com.eduflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleCycleTest {
    private val anchor = LocalDate.of(2026, 10, 5)
    private val twoWeekCycle = listOf(10L, 20L)

    @Test fun anchorAndForwardOffsetsAlternate() {
        assertEquals(10L, ScheduleCycle.resolveTemplateId(anchor, anchor, 10L, twoWeekCycle))
        assertEquals(20L, ScheduleCycle.resolveTemplateId(anchor.plusWeeks(1), anchor, 10L, twoWeekCycle))
        assertEquals(10L, ScheduleCycle.resolveTemplateId(anchor.plusWeeks(2), anchor, 10L, twoWeekCycle))
    }

    @Test fun negativeOffsetsWrapCorrectly() {
        assertEquals(20L, ScheduleCycle.resolveTemplateId(anchor.minusWeeks(1), anchor, 10L, twoWeekCycle))
        assertEquals(20L, ScheduleCycle.resolveTemplateId(anchor.minusWeeks(3), anchor, 10L, twoWeekCycle))
    }

    @Test fun arbitraryCycleLengthWraps() {
        val cycle = listOf(1L, 2L, 3L)
        assertEquals(3L, ScheduleCycle.resolveTemplateId(anchor.minusWeeks(1), anchor, 1L, cycle))
        assertEquals(2L, ScheduleCycle.resolveTemplateId(anchor.plusWeeks(4), anchor, 1L, cycle))
    }
}
