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
    private fun slot(id: Long, index: Int, group: String? = null) = ScheduleSlot(id, 1, 1, index, LocalTime.of(8 + index, 0), LocalTime.of(8 + index, 45), 10, groupInfo = group)
    private fun lesson(id: Long, index: Int, subject: Long? = 10, day: LocalDate = date, teacher: String? = "Иванов", room: String? = "56", kind: LessonKind = LessonKind.SCHOOL) = LessonInstance(id, day, LocalTime.of(8 + index, 0), LocalTime.of(8 + index, 45), subject, sourceScheduleSlotId = id, kind = kind, actualTeacher = teacher, actualRoom = room)
    private fun resolved(selected: LessonInstance, lessons: List<LessonInstance>, slots: List<ScheduleSlot>) = LessonBlockResolver.resolve(selected, lessons, slots).ids

    @Test fun adjacentMatchingSchoolPeriodsFormOneBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2)
        assertEquals(listOf(1L, 2L), resolved(first, listOf(first, second), listOf(slot(1, 1), slot(2, 2))))
    }

    @Test fun threeAdjacentMatchingPeriodsFormOneBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2); val third = lesson(3, 3)
        assertEquals(listOf(1L, 2L, 3L), resolved(second, listOf(first, second, third), listOf(slot(1, 1), slot(2, 2), slot(3, 3))))
    }

    @Test fun differentSubjectBreaksBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2, subject = 11)
        assertEquals(listOf(1L), resolved(first, listOf(first, second), listOf(slot(1, 1), slot(2, 2))))
    }

    @Test fun sameSubjectAfterGapDoesNotJoin() {
        val first = lesson(1, 1); val third = lesson(3, 3)
        assertEquals(listOf(1L), resolved(first, listOf(first, third), listOf(slot(1, 1), slot(3, 3))))
    }

    @Test fun differentDateDoesNotJoin() {
        val first = lesson(1, 1); val nextDay = lesson(2, 2, day = date.plusDays(1))
        assertEquals(listOf(1L), resolved(first, listOf(first, nextDay), listOf(slot(1, 1), slot(2, 2))))
    }

    @Test fun differentGroupBreaksBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2)
        assertEquals(listOf(1L), resolved(first, listOf(first, second), listOf(slot(1, 1, "A"), slot(2, 2, "B"))))
    }

    @Test fun differentSpecifiedTeacherBreaksBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2, teacher = "Петров")
        assertEquals(listOf(1L), resolved(first, listOf(first, second), listOf(slot(1, 1), slot(2, 2))))
    }

    @Test fun differentSpecifiedRoomBreaksBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2, room = "57")
        assertEquals(listOf(1L), resolved(first, listOf(first, second), listOf(slot(1, 1), slot(2, 2))))
    }

    @Test fun selectingSecondMemberResolvesSameFullBlock() {
        val first = lesson(1, 1); val second = lesson(2, 2)
        assertEquals(listOf(1L, 2L), resolved(second, listOf(first, second), listOf(slot(1, 1), slot(2, 2))))
    }

    @Test fun singlePeriodResolvesToItself() {
        val only = lesson(1, 1)
        assertEquals(listOf(1L), resolved(only, listOf(only), listOf(slot(1, 1))))
    }

    @Test fun privateLessonsAreNeverGrouped() {
        val first = lesson(1, 1, kind = LessonKind.PRIVATE); val second = lesson(2, 2, kind = LessonKind.PRIVATE)
        assertEquals(listOf(1L), resolved(first, listOf(first, second), listOf(slot(1, 1), slot(2, 2))))
    }
}
