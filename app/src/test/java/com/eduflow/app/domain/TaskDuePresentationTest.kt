package com.eduflow.app.domain

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TaskDuePresentationTest {
    private val day = LocalDate.of(2026, 9, 21)
    private fun slot(id: Long, index: Int) = ScheduleSlot(id, 1, 1, index, LocalTime.of(8 + index, 0), LocalTime.of(8 + index, 45), subjectId = 1)
    private fun lesson(id: Long, index: Int, subjectId: Long? = 1) = LessonInstance(
        id = id,
        actualDate = day,
        actualStartTime = LocalTime.of(8 + index, 0),
        actualEndTime = LocalTime.of(8 + index, 45),
        subjectId = subjectId,
        sourceScheduleSlotId = id,
        kind = LessonKind.SCHOOL
    )

    @Test fun multiPeriodDueAnchorsExposeTheFullDerivedRange() {
        val first = lesson(1, 1)
        val second = lesson(2, 2)

        val ranges = TaskDuePresentation.rangesByLessonId(listOf(first, second), listOf(slot(1, 1), slot(2, 2)))

        assertEquals(TaskDueTimeRange(LocalTime.of(9, 0), LocalTime.of(10, 45)), ranges[first.id])
        assertEquals(ranges[first.id], ranges[second.id])
    }

    @Test fun singlePeriodDueAnchorDoesNotReplaceItsRawTime() {
        val only = lesson(1, 1)

        assertFalse(TaskDuePresentation.rangesByLessonId(listOf(only), listOf(slot(1, 1))).containsKey(only.id))
    }
}
