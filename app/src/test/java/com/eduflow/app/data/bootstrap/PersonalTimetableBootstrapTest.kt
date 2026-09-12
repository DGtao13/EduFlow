package com.eduflow.app.data.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class PersonalTimetableBootstrapTest {
    @Test fun subjectDefinitionsAreUniqueAndComplete() {
        assertEquals(25, PersonalTimetableData.subjects.size)
        assertEquals(25, PersonalTimetableData.subjects.map { it.name }.distinct().size)
    }

    @Test fun timetableCountsMatchTheSuppliedSchedule() {
        assertEquals(35, PersonalTimetableData.weekA.size)
        assertEquals(37, PersonalTimetableData.weekB.size)
    }

    @Test fun standardPeriodsUseExactTimesAndStaySeparate() {
        assertEquals(LocalTime.of(8, 0) to LocalTime.of(8, 45), PersonalTimetableData.periodTimes.getValue(1))
        assertEquals(LocalTime.of(9, 50) to LocalTime.of(10, 35), PersonalTimetableData.periodTimes.getValue(3))
        val MondayGo = PersonalTimetableData.weekA.filter { it.weekday == 1 && it.subjectName == "ГО" }
        assertEquals(listOf(1, 2), MondayGo.map { it.period })
        assertTrue(MondayGo[0] !== MondayGo[1])
    }

    @Test fun groupOneIsKeptOnAllSpecifiedRows() {
        val groupedSlots = PersonalTimetableData.weekA.count { it.group == PersonalTimetableData.GROUP_ONE } +
            PersonalTimetableData.weekB.count { it.group == PersonalTimetableData.GROUP_ONE }
        assertEquals(26, groupedSlots)
    }
}
