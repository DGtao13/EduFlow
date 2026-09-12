package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CycleEditorLogicTest {
    private val anchor = LocalDate.of(2026, 9, 23)

    @Test fun movingEntryUpAndDownPreservesOrder() {
        assertEquals(listOf(2L, 1L, 3L), moveCycleEntry(listOf(1L, 2L, 3L), 0, 1))
        assertEquals(listOf(1L, 3L, 2L), moveCycleEntry(listOf(1L, 2L, 3L), 1, 1))
        assertEquals(listOf(2L, 1L, 3L), moveCycleEntry(listOf(1L, 2L, 3L), 1, -1))
        assertEquals(listOf(1L, 2L, 3L), moveCycleEntry(listOf(1L, 2L, 3L), 0, -1))
    }

    @Test fun previewNormalizesAnyAnchorDateToItsMonday() {
        val preview = cyclePreview(anchor, 10L, listOf(10L, 20L))
        assertEquals(LocalDate.of(2026, 9, 21), preview.first().monday)
        assertEquals(listOf(10L, 20L, 10L), preview.map { it.templateId })
    }

    @Test fun previewRepeatsLongerCycles() {
        val preview = cyclePreview(anchor, 1L, listOf(1L, 2L, 3L), weeks = 5)
        assertEquals(listOf(1L, 2L, 3L, 1L, 2L), preview.map { it.templateId })
    }
}
