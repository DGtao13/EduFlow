package com.eduflow.app.ui

import com.eduflow.app.data.local.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SchoolSlotEditorLogicTest {
    private fun slot(
        id: Long = 0,
        templateId: Long = 1,
        weekday: Int = 1,
        period: Int = 1,
        start: LocalTime = LocalTime.of(8, 0),
        end: LocalTime = LocalTime.of(8, 45)
    ) = ScheduleSlot(id, templateId, weekday, period, start, end, subjectId = 10)

    @Test fun standardPeriodsReuseThePersonalTimetableTimes() {
        assertEquals(
            listOf(
                "08:00–08:45", "08:45–09:30", "09:50–10:35", "10:35–11:20",
                "11:40–12:25", "12:25–13:10", "13:30–14:15", "14:15–15:00"
            ),
            schoolPeriodOptions.map { "${it.startTime}–${it.endTime}" }
        )
    }

    @Test fun weekdayValuesMapToTheBulgarianWeekdayArrayOrder() {
        assertEquals(listOf(0, 1, 2, 3, 4), (1..5).mapNotNull(::schoolWeekdayResourceIndex))
        assertEquals(null, schoolWeekdayResourceIndex(0))
        assertEquals(null, schoolWeekdayResourceIndex(6))
    }

    @Test fun standardSlotIsNotCustomButChangedTimesAre() {
        assertFalse(scheduleSlotUsesCustomTimes(slot()))
        assertTrue(scheduleSlotUsesCustomTimes(slot(start = LocalTime.of(8, 5))))
    }

    @Test fun conflictMatchesTemplateWeekdayAndPeriodOnly() {
        val existing = slot(id = 3, templateId = 7, weekday = 3, period = 4)
        assertTrue(scheduleSlotConflicts(slot(templateId = 7, weekday = 3, period = 4), listOf(existing)))
        assertFalse(scheduleSlotConflicts(slot(templateId = 8, weekday = 3, period = 4), listOf(existing)))
        assertFalse(scheduleSlotConflicts(slot(id = 3, templateId = 7, weekday = 3, period = 4), listOf(existing)))
    }

    @Test fun addDefaultFindsFirstFreeWeekdayAndPeriod() {
        val occupied = (1..8).map { period -> slot(templateId = 5, weekday = 1, period = period) }
        assertEquals(2 to 1, firstAvailableSchoolSlot(occupied, templateId = 5))
    }

    @Test fun editPositionUsesPersistedSlotInsteadOfFirstFree() {
        val persisted = slot(id = 9, templateId = 5, weekday = 4, period = 7)
        val occupied = (1..8).map { period -> slot(templateId = 5, weekday = 1, period = period) }
        assertEquals(4 to 7, initialSchoolSlotPosition(persisted, occupied, templateId = 5))
    }
}
