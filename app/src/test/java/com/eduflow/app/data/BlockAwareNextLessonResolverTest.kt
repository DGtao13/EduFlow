package com.eduflow.app.data

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BlockAwareNextLessonResolverTest {
    private val subjectId = 10L
    private val academicYear = AcademicYearSettings(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 5, 31))
    private val slots = (1..8).map { index ->
        val start = LocalTime.of(8, 0).plusMinutes((index - 1) * 45L)
        ScheduleSlot(id = 100L + index, scheduleTemplateId = 1, weekday = 1, lessonIndex = index, startTime = start, endTime = start.plusMinutes(45), subjectId = subjectId, logicalBlockId = "fixture-block")
    }

    private fun lesson(id: Long, date: LocalDate, period: Int, cancelled: Boolean = false) = LessonInstance(
        id = id,
        actualDate = date,
        actualStartTime = slots.first { it.lessonIndex == period }.startTime,
        actualEndTime = slots.first { it.lessonIndex == period }.endTime,
        subjectId = subjectId,
        sourceScheduleSlotId = 100L + period,
        kind = LessonKind.SCHOOL,
        cancellationState = if (cancelled) CancellationState.CANCELLED else CancellationState.ACTIVE
    )

    private fun resolve(origin: LessonInstance, lessons: List<LessonInstance>, noSchool: Set<LocalDate> = emptySet(), year: AcademicYearSettings = academicYear) =
        NextLessonResolver.chooseSchoolBlockAware(origin, subjectId, lessons, slots, noSchool, year)

    @Test fun singlePeriodSelectsTheNextSinglePeriod() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 4)
        val future = lesson(2, LocalDate.of(2026, 9, 18), 3)
        assertEquals(future.id, resolve(current, listOf(current, future))?.id)
    }

    @Test fun firstPeriodOfDoubleBlockSkipsItsSecondPeriod() {
        val first = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val second = lesson(2, LocalDate.of(2026, 9, 14), 2)
        val future = lesson(3, LocalDate.of(2026, 9, 17), 1)
        assertEquals(future.id, resolve(first, listOf(first, second, future))?.id)
    }

    @Test fun secondPeriodOfDoubleBlockAlsoSkipsTheWholeCurrentBlock() {
        val first = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val second = lesson(2, LocalDate.of(2026, 9, 14), 2)
        val future = lesson(3, LocalDate.of(2026, 9, 17), 1)
        assertEquals(future.id, resolve(second, listOf(first, second, future))?.id)
    }

    @Test fun mixedSubjectSiblingIsNotTreatedAsTheNextClass() {
        val day = LocalDate.of(2026, 9, 14)
        val first = LessonInstance(1, day, LocalTime.of(8, 0), LocalTime.of(8, 45), subjectId, 101, kind = LessonKind.SCHOOL)
        val sibling = LessonInstance(2, day, LocalTime.of(8, 45), LocalTime.of(9, 30), 11, 102, kind = LessonKind.SCHOOL)
        val future = LessonInstance(3, day.plusDays(3), LocalTime.of(8, 0), LocalTime.of(8, 45), subjectId, 103, kind = LessonKind.SCHOOL)
        val mixedSlots = listOf(
            ScheduleSlot(101, 1, 1, 1, first.actualStartTime, first.actualEndTime, subjectId, logicalBlockId = "mixed"),
            ScheduleSlot(102, 1, 1, 2, sibling.actualStartTime, sibling.actualEndTime, 11, logicalBlockId = "mixed"),
            ScheduleSlot(103, 1, 4, 1, future.actualStartTime, future.actualEndTime, subjectId)
        )
        assertEquals(future.id, NextLessonResolver.chooseSchoolBlockAware(first, subjectId, listOf(first, sibling, future), mixedSlots, emptySet(), academicYear)?.id)
    }

    @Test fun threePeriodCurrentBlockIsExcludedFromAnyMember() {
        val first = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val middle = lesson(2, LocalDate.of(2026, 9, 14), 2)
        val last = lesson(3, LocalDate.of(2026, 9, 14), 3)
        val future = lesson(4, LocalDate.of(2026, 9, 21), 1)
        assertEquals(future.id, resolve(middle, listOf(first, middle, last, future))?.id)
    }

    @Test fun futureMultiPeriodBlockUsesItsFirstActiveMemberAsCanonicalAnchor() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val targetFirst = lesson(2, LocalDate.of(2026, 9, 17), 1)
        val targetSecond = lesson(3, LocalDate.of(2026, 9, 17), 2)
        assertEquals(targetFirst.id, resolve(current, listOf(current, targetFirst, targetSecond))?.id)
    }

    @Test fun fullyCancelledFutureBlockIsSkipped() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val cancelledFirst = lesson(2, LocalDate.of(2026, 9, 17), 1, cancelled = true)
        val cancelledSecond = lesson(3, LocalDate.of(2026, 9, 17), 2, cancelled = true)
        val later = lesson(4, LocalDate.of(2026, 9, 21), 1)
        assertEquals(later.id, resolve(current, listOf(current, cancelledFirst, cancelledSecond, later))?.id)
    }

    @Test fun partiallyCancelledFutureBlockUsesFirstActiveMember() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val cancelledFirst = lesson(2, LocalDate.of(2026, 9, 17), 1, cancelled = true)
        val activeSecond = lesson(3, LocalDate.of(2026, 9, 17), 2)
        assertEquals(activeSecond.id, resolve(current, listOf(current, cancelledFirst, activeSecond))?.id)
    }

    @Test fun noSchoolAndVacationDatesAreSkipped() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val noSchool = lesson(2, LocalDate.of(2026, 9, 17), 1)
        val vacation = lesson(3, LocalDate.of(2026, 12, 25), 1)
        val valid = lesson(4, LocalDate.of(2027, 1, 4), 1)
        assertEquals(valid.id, resolve(current, listOf(current, noSchool, vacation, valid), setOf(noSchool.actualDate, vacation.actualDate))?.id)
    }

    @Test fun consecutiveNoSchoolAndCancelledSessionsAreSkipped() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val noSchoolTuesday = lesson(2, LocalDate.of(2026, 9, 15), 1)
        val cancelledWednesday = lesson(3, LocalDate.of(2026, 9, 16), 1, cancelled = true)
        val validFriday = lesson(4, LocalDate.of(2026, 9, 18), 1)
        assertEquals(
            validFriday.id,
            resolve(current, listOf(current, noSchoolTuesday, cancelledWednesday, validFriday), setOf(noSchoolTuesday.actualDate))?.id
        )
    }

    @Test fun noSchoolMixedBlockSkipsTheWholeLogicalSession() {
        val day = LocalDate.of(2026, 9, 14)
        val first = LessonInstance(1, day, LocalTime.of(8, 0), LocalTime.of(8, 45), subjectId, 101, kind = LessonKind.SCHOOL)
        val sibling = LessonInstance(2, day, LocalTime.of(8, 45), LocalTime.of(9, 30), 11, 102, kind = LessonKind.SCHOOL)
        val future = LessonInstance(3, day.plusDays(3), LocalTime.of(8, 0), LocalTime.of(8, 45), subjectId, 103, kind = LessonKind.SCHOOL)
        val mixedSlots = listOf(
            ScheduleSlot(101, 1, 1, 1, first.actualStartTime, first.actualEndTime, subjectId, logicalBlockId = "mixed"),
            ScheduleSlot(102, 1, 1, 2, sibling.actualStartTime, sibling.actualEndTime, 11, logicalBlockId = "mixed"),
            ScheduleSlot(103, 1, 4, 1, future.actualStartTime, future.actualEndTime, subjectId)
        )
        assertEquals(future.id, NextLessonResolver.chooseSchoolBlockAware(first, subjectId, listOf(first, sibling, future), mixedSlots, setOf(day), academicYear)?.id)
    }

    @Test fun datedAbGapFindsTheLaterRealOccurrence() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val nextWeekA = lesson(2, LocalDate.of(2026, 9, 28), 1)
        assertEquals(nextWeekA.id, resolve(current, listOf(current, nextWeekA))?.id)
    }

    @Test fun schoolYearEndPreventsADeadlineBeyondTheConfiguredRange() {
        val current = lesson(1, LocalDate.of(2026, 9, 14), 1)
        val afterYear = lesson(2, LocalDate.of(2026, 9, 21), 1)
        val shortYear = AcademicYearSettings(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 18))
        assertNull(resolve(current, listOf(current, afterYear), year = shortYear))
    }
}
