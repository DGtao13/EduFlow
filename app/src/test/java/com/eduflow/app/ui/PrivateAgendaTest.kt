package com.eduflow.app.ui

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PrivateAgendaTest {
    private fun lesson(id: Long, date: LocalDate, time: LocalTime, kind: LessonKind = LessonKind.PRIVATE) = LessonInstance(
        id = id,
        actualDate = date,
        actualStartTime = time,
        actualEndTime = time.plusHours(1),
        subjectId = null,
        kind = kind,
        privateLessonName = "Урок $id"
    )

    @Test fun groupsByOccurrenceDateAndSortsDatesAndTimes() {
        val friday = LocalDate.of(2026, 9, 18)
        val sunday = LocalDate.of(2026, 9, 20)
        val groups = groupPrivateAgendaLessons(listOf(
            lesson(3, sunday, LocalTime.of(11, 0)),
            lesson(2, friday, LocalTime.of(17, 0)),
            lesson(1, friday, LocalTime.of(9, 0))
        ))
        assertEquals(listOf(friday, sunday), groups.map { it.date })
        assertEquals(listOf(1L, 2L), groups.first().lessons.map { it.id })
    }

    @Test fun weekendLessonsAreIncludedAndSchoolLessonsAreIgnored() {
        val saturday = LocalDate.of(2026, 9, 19)
        val sunday = LocalDate.of(2026, 9, 20)
        val groups = groupPrivateAgendaLessons(listOf(
            lesson(1, saturday, LocalTime.of(10, 0)),
            lesson(2, sunday, LocalTime.of(11, 0)),
            lesson(3, sunday, LocalTime.of(12, 0), LessonKind.SCHOOL)
        ))
        assertEquals(listOf(saturday, sunday), groups.map { it.date })
        assertEquals(1, groups.first().lessons.size)
        assertEquals(1, groups[1].lessons.size)
    }

    @Test fun countsAreCalculatedPerExactOccurrenceDate() {
        val date = LocalDate.of(2026, 9, 18)
        assertEquals(mapOf(date to 2), privateAgendaCounts(listOf(
            lesson(1, date, LocalTime.of(9, 0)),
            lesson(2, date, LocalTime.of(17, 0)),
            lesson(3, date.plusDays(1), LocalTime.of(10, 0), LessonKind.SCHOOL)
        )))
    }

    @Test fun targetDatesAreSparseAndOmitDatesWithoutPrivateLessons() {
        val friday = LocalDate.of(2026, 9, 11)
        assertEquals(listOf(friday), privateAgendaTargetDates(listOf(
            lesson(1, friday, LocalTime.of(16, 0)),
            lesson(2, friday.plusDays(1), LocalTime.of(10, 0), LessonKind.SCHOOL)
        )))
    }

    @Test fun adjacentWeekBoundaryKeepsFridayTargetOptional() {
        val friday = LocalDate.of(2026, 9, 11)
        val nextMonday = LocalDate.of(2026, 9, 14)
        val targetDates = privateAgendaTargetDates(listOf(lesson(1, friday, LocalTime.of(16, 0))))
        val targets = targetDates.associateWith { Unit }
        assertEquals(Unit, targets[friday])
        assertEquals(null, targets[nextMonday])
    }
}
