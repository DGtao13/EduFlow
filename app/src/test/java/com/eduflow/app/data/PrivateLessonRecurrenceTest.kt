package com.eduflow.app.data

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.RecurringPrivateLesson
import com.eduflow.app.data.local.PrivateLessonLocationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PrivateLessonRecurrenceTest {
    private fun definition(interval: Int = 1, enabled: Boolean = true, end: LocalDate? = null) = RecurringPrivateLesson(id = 7, subjectId = 1, weekday = 7, startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 30), startDate = LocalDate.of(2026, 9, 13), endDate = end, intervalWeeks = interval, enabled = enabled)
    private fun lesson(id: Long, date: LocalDate, kind: LessonKind, source: Long? = null) = LessonInstance(id = id, actualDate = date, actualStartTime = LocalTime.of(16, 0), actualEndTime = LocalTime.of(17, 30), subjectId = 1, kind = kind, sourcePrivateLessonId = source)

    @Test fun weeklyPrivateRecurrenceUsesItsWeekdayAndStartBoundary() {
        assertTrue(PrivateLessonRecurrence.occursOn(definition(), LocalDate.of(2026, 9, 13)))
        assertTrue(PrivateLessonRecurrence.occursOn(definition(), LocalDate.of(2026, 9, 20)))
        assertFalse(PrivateLessonRecurrence.occursOn(definition(), LocalDate.of(2026, 9, 14)))
        assertFalse(PrivateLessonRecurrence.occursOn(definition(), LocalDate.of(2026, 9, 6)))
    }

    @Test fun twoWeekPrivateRecurrenceSkipsAlternatingWeeksAndRespectsEndDate() {
        val recurring = definition(interval = 2, end = LocalDate.of(2026, 9, 27))
        assertTrue(PrivateLessonRecurrence.occursOn(recurring, LocalDate.of(2026, 9, 13)))
        assertFalse(PrivateLessonRecurrence.occursOn(recurring, LocalDate.of(2026, 9, 20)))
        assertTrue(PrivateLessonRecurrence.occursOn(recurring, LocalDate.of(2026, 9, 27)))
        assertFalse(PrivateLessonRecurrence.occursOn(recurring, LocalDate.of(2026, 10, 11)))
    }

    @Test fun saturdayPrivateRecurrenceUsesTheStoredWeekendWeekday() {
        val saturday = definition().copy(weekday = 6, startDate = LocalDate.of(2026, 9, 12))
        assertTrue(PrivateLessonRecurrence.occursOn(saturday, LocalDate.of(2026, 9, 12)))
        assertFalse(PrivateLessonRecurrence.occursOn(saturday, LocalDate.of(2026, 9, 13)))
    }

    @Test fun disabledPrivateRecurrenceNeverMaterializesNewOccurrences() {
        assertFalse(PrivateLessonRecurrence.occursOn(definition(enabled = false), LocalDate.of(2026, 9, 13)))
    }

    @Test fun weekdayEditStopsMatchingTheOldFutureOccurrenceDates() {
        val monday = definition().copy(weekday = 1, startDate = LocalDate.of(2026, 9, 14))
        val wednesday = monday.copy(weekday = 3)
        val oldDates = listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21))
        assertTrue(oldDates.all { PrivateLessonRecurrence.occursOn(monday, it) })
        assertTrue(oldDates.none { PrivateLessonRecurrence.occursOn(wednesday, it) })
        assertTrue(PrivateLessonRecurrence.occursOn(wednesday, LocalDate.of(2026, 9, 16)))
    }

    @Test fun recurringOccurrenceKeepsItsOwnOverridesAndGetsSafePrivateIdentity() {
        val recurring = definition().copy(privateLessonName = "Мрежова практика", teacherOverride = "Частен преподавател", roomOverride = "При преподавателя", privateLocationKind = PrivateLessonLocationKind.IN_PERSON)
        val occurrence = recurringPrivateOccurrence(recurring, LocalDate.of(2026, 9, 13), "Математика")
        assertEquals("Мрежова практика", occurrence.privateLessonName)
        assertEquals("Частен преподавател", occurrence.actualTeacher)
        assertEquals("При преподавателя", occurrence.actualRoom)
        assertEquals(PrivateLessonLocationKind.IN_PERSON, occurrence.privateLocationKind)
    }

    @Test fun recurringOccurrenceWithoutLegacyIdentityGetsAnIndependentGenericName() {
        val occurrence = recurringPrivateOccurrence(definition().copy(subjectId = null, privateLessonName = null), LocalDate.of(2026, 9, 13), null)
        assertEquals("Частен урок", occurrence.privateLessonName)
        assertEquals(null, occurrence.subjectId)
    }

    @Test fun onlineUnlinkedRuleCopiesOnlyPrivateMetadata() {
        val recurring = definition().copy(subjectId = null, privateLessonName = "Урок по английски", teacherOverride = "г-жа Николова", roomOverride = "Google Meet", privateLocationKind = PrivateLessonLocationKind.ONLINE)
        val occurrence = recurringPrivateOccurrence(recurring, LocalDate.of(2026, 9, 13), null)
        assertEquals("Урок по английски", occurrence.privateLessonName)
        assertEquals(null, occurrence.subjectId)
        assertEquals(7L, occurrence.sourcePrivateLessonId)
        assertEquals(LocalDate.of(2026, 9, 13), occurrence.actualDate)
        assertEquals(LocalTime.of(16, 0), occurrence.actualStartTime)
        assertEquals(LocalTime.of(17, 30), occurrence.actualEndTime)
        assertEquals("г-жа Николова", occurrence.actualTeacher)
        assertEquals("Google Meet", occurrence.actualRoom)
        assertEquals(PrivateLessonLocationKind.ONLINE, occurrence.privateLocationKind)
    }

    @Test fun linkedRuleNeverUsesSubjectTeacherOrRoomWithoutPrivateOverrides() {
        val occurrence = recurringPrivateOccurrence(definition().copy(privateLessonName = "Подготовка за матура", teacherOverride = null, roomOverride = null), LocalDate.of(2026, 9, 13), "Математика")
        assertEquals(null, occurrence.actualTeacher)
        assertEquals(null, occurrence.actualRoom)
        assertEquals(PrivateLessonLocationKind.UNSPECIFIED, occurrence.privateLocationKind)
    }

    @Test fun privateOriginOnlyUsesSamePrivateSeriesNotSchoolLesson() {
        val origin = lesson(1, LocalDate.of(2026, 9, 13), LessonKind.PRIVATE, source = 7)
        val candidates = NextLessonResolver.candidatesForOrigin(origin, listOf(lesson(2, LocalDate.of(2026, 9, 17), LessonKind.SCHOOL), lesson(3, LocalDate.of(2026, 9, 20), LessonKind.PRIVATE, source = 7), lesson(4, LocalDate.of(2026, 9, 20), LessonKind.PRIVATE, source = 8)))
        assertEquals(listOf(3L), candidates.map { it.id })
    }

    @Test fun oneTimePrivateOriginDoesNotInventAnotherOccurrence() {
        val origin = lesson(1, LocalDate.of(2026, 9, 13), LessonKind.PRIVATE)
        val candidates = NextLessonResolver.candidatesForOrigin(origin, listOf(
            lesson(2, LocalDate.of(2026, 9, 17), LessonKind.SCHOOL),
            lesson(3, LocalDate.of(2026, 9, 20), LessonKind.PRIVATE, source = 7)
        ))
        assertEquals(emptyList<Long>(), candidates.map { it.id })
    }

    @Test fun schoolOriginOnlyUsesSchoolOccurrencesAndGeneralUsesEarliestKind() {
        val schoolOrigin = lesson(1, LocalDate.of(2026, 9, 10), LessonKind.SCHOOL)
        val privateFuture = lesson(2, LocalDate.of(2026, 9, 14), LessonKind.PRIVATE, source = 7)
        val schoolFuture = lesson(3, LocalDate.of(2026, 9, 15), LessonKind.SCHOOL)
        assertEquals(listOf(3L), NextLessonResolver.candidatesForOrigin(schoolOrigin, listOf(privateFuture, schoolFuture)).map { it.id })
        assertEquals(2L, NextLessonResolver.choose(NextLessonResolver.candidatesForOrigin(null, listOf(schoolFuture, privateFuture)), LocalDateTime.of(2026, 9, 13, 17, 0), emptySet())?.id)
    }
}
