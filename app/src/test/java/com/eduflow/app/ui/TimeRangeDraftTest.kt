package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TimeRangeDraftTest {
    @Test fun startStageDoesNotAdvanceUntilStartIsCommitted() {
        assertEquals(TimeRangeDraftStage.START, TimeRangeDraft().stage)
    }

    @Test fun completingStartAdvancesExactlyOnceToEndStage() {
        val draft = TimeRangeDraft().chooseStart(LocalTime.of(16, 30))
        assertEquals(TimeRangeDraftStage.END, draft.stage)
        assertEquals(draft, draft.chooseStart(LocalTime.of(16, 30)))
    }

    @Test fun returningToStartAllowsCorrectionWithoutChangingSavedRange() {
        val draft = TimeRangeDraft().chooseStart(LocalTime.of(16, 0)).returnToStart()
        assertEquals(TimeRangeDraftStage.START, draft.stage)
        assertNull(draft.start)
    }

    @Test fun endStageProducesOneCommittedRange() {
        val range = TimeRangeDraft().chooseStart(LocalTime.of(16, 0)).finish(LocalTime.of(17, 0))
        assertEquals(TimeRange(LocalTime.of(16, 0), LocalTime.of(17, 0)), range)
    }

    @Test fun invalidEndDoesNotProducePartialRange() {
        assertNull(TimeRangeDraft().chooseStart(LocalTime.of(17, 0)).finish(LocalTime.of(17, 0)))
        assertNull(TimeRangeDraft().chooseStart(LocalTime.of(17, 0)).finish(LocalTime.of(16, 0)))
    }
}
