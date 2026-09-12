package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TimeRangeDraftTest {
    @Test fun endStageProducesOneCommittedRange() {
        val range = TimeRangeDraft().chooseStart(LocalTime.of(16, 0)).finish(LocalTime.of(17, 0))
        assertEquals(TimeRange(LocalTime.of(16, 0), LocalTime.of(17, 0)), range)
    }

    @Test fun invalidEndDoesNotProducePartialRange() {
        assertNull(TimeRangeDraft().chooseStart(LocalTime.of(17, 0)).finish(LocalTime.of(17, 0)))
        assertNull(TimeRangeDraft().chooseStart(LocalTime.of(17, 0)).finish(LocalTime.of(16, 0)))
    }
}
