package com.eduflow.app.domain

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LessonBlockResolverTest {
    private val date = LocalDate.of(2026, 9, 21)
    private fun slot(id: Long, index: Int, subject: Long = 10, block: String? = null) =
        ScheduleSlot(id, 1, 1, index, LocalTime.of(8 + index, 0), LocalTime.of(8 + index, 45), subject, logicalBlockId = block)
    private fun lesson(id: Long, index: Int, subject: Long? = 10, day: LocalDate = date, kind: LessonKind = LessonKind.SCHOOL) =
        LessonInstance(id, day, LocalTime.of(8 + index, 0), LocalTime.of(8 + index, 45), subject, sourceScheduleSlotId = id, kind = kind)
    private fun block(selected: LessonInstance, lessons: List<LessonInstance>, slots: List<ScheduleSlot>) =
        LessonBlockResolver.resolve(selected, lessons, slots)

    @Test fun explicitSameSubjectAdjacentPeriodsFormOneCanonicalBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2)
        assertEquals(listOf(1L, 2L), block(first, listOf(first, second), listOf(slot(1, 1, block = "x"), slot(2, 2, block = "x"))).ids)
    }

    @Test fun explicitMixedSubjectsResolveIdenticallyFromEitherMember() {
        val first = lesson(1, 1, 10); val second = lesson(2, 2, 11)
        val slots = listOf(slot(1, 1, 10, "literature"), slot(2, 2, 11, "literature"))
        val fromFirst = block(first, listOf(first, second), slots)
        val fromSecond = block(second, listOf(first, second), slots)
        assertEquals(listOf(1L, 2L), fromFirst.ids)
        assertEquals(fromFirst.ids, fromSecond.ids)
        assertEquals(LocalTime.of(9, 0), fromFirst.startTime)
        assertEquals(LocalTime.of(10, 45), fromFirst.endTime)
        assertEquals(2, fromFirst.lessons.size)
        assertEquals(10L, first.subjectId)
        assertEquals(11L, second.subjectId)
    }

    @Test fun unlinkingExplicitIdentityReturnsIndependentLessons() {
        val first = lesson(1, 1); val second = lesson(2, 2)
        assertEquals(listOf(1L), block(first, listOf(first, second), listOf(slot(1, 1), slot(2, 2))).ids)
    }

    @Test fun nonAdjacentSlotsWithSameIdentityNeverBecomeOneContinuousBlock() {
        val first = lesson(1, 1); val third = lesson(3, 3, 11)
        assertEquals(listOf(1L), block(first, listOf(first, third), listOf(slot(1, 1, block = "x"), slot(3, 3, 11, "x"))).ids)
    }

    @Test fun threeExplicitAdjacentMembersAreOrderedAndKeepIndividualSubjects() {
        val first = lesson(1, 1, 10); val second = lesson(2, 2, 11); val third = lesson(3, 3, 12)
        val slots = listOf(slot(1, 1, 10, "x"), slot(2, 2, 11, "x"), slot(3, 3, 12, "x"))
        assertEquals(listOf(1L, 2L, 3L), block(second, listOf(third, first, second), slots).ids)
    }

    @Test fun differentDatesAndPrivateLessonsNeverJoin() {
        val school = lesson(1, 1)
        val nextDay = lesson(2, 2, day = date.plusDays(1))
        val private = lesson(3, 2, kind = LessonKind.PRIVATE)
        assertEquals(listOf(1L), block(school, listOf(school, nextDay), listOf(slot(1, 1, block = "x"), slot(2, 2, block = "x"))).ids)
        assertEquals(listOf(3L), block(private, listOf(private), listOf(slot(3, 2, block = "x"))).ids)
    }
}
