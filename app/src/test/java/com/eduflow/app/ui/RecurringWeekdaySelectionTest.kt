package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringWeekdaySelectionTest {
    @Test fun selectionIsSetBasedAndTogglesWithoutDuplicates() {
        var selected = emptySet<Int>()
        selected = RecurringWeekdaySelection.toggle(selected, 1)
        selected = RecurringWeekdaySelection.toggle(selected, 3)
        selected = RecurringWeekdaySelection.toggle(selected, 1)
        selected = RecurringWeekdaySelection.toggle(selected, 1)
        assertEquals(setOf(1, 3), selected)
    }

    @Test fun summaryUsesCalendarOrderAndBulgarianLabels() {
        assertEquals("Пон, Сря, Пет", RecurringWeekdaySelection.bulgarianSummary(setOf(5, 1, 3)))
        assertTrue(RecurringWeekdaySelection.ordered(emptySet()).isEmpty())
    }
}
