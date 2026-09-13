package com.eduflow.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolBlockNormalizationTest {
    private fun slot(id: Long, period: Int, subject: Long = 1, teacher: String? = "A", room: String? = "1", group: String? = null, block: String? = null) =
        LegacySchoolBlockSlot(id, 1, 1, period, subject, teacher, room, group, block)

    @Test fun legacyCompatibleSameSubjectRunGetsOneStableExplicitIdentity() {
        val assignments = SchoolBlockNormalization.legacyAssignments(listOf(slot(10, 3), slot(11, 4)))
        assertEquals(mapOf(10L to "legacy-block-1-1-10", 11L to "legacy-block-1-1-10"), assignments)
    }

    @Test fun independentOrIncompatibleLegacySlotsAreNotInventedAsBlocks() {
        assertTrue(SchoolBlockNormalization.legacyAssignments(listOf(slot(1, 1), slot(2, 2, teacher = "B"))).isEmpty())
        assertTrue(SchoolBlockNormalization.legacyAssignments(listOf(slot(1, 1), slot(3, 3))).isEmpty())
    }

    @Test fun explicitSlotsAreLeftUntouchedMakingNormalizationIdempotent() {
        assertTrue(SchoolBlockNormalization.legacyAssignments(listOf(slot(1, 1, block = "existing"), slot(2, 2, block = "existing"))).isEmpty())
    }
}
