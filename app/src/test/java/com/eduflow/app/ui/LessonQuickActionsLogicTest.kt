package com.eduflow.app.ui

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.domain.LessonBlock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LessonQuickActionsLogicTest {
    private fun lesson(id: Long, cancelled: Boolean = false) = LessonInstance(
        id = id,
        actualDate = LocalDate.of(2026, 9, 12),
        actualStartTime = LocalTime.of(8, 0).plusMinutes(id),
        actualEndTime = LocalTime.of(8, 45).plusMinutes(id),
        subjectId = 1,
        kind = LessonKind.SCHOOL,
        cancellationState = if (cancelled) CancellationState.CANCELLED else CancellationState.ACTIVE
    )

    @Test fun activeSingleLessonHasNoWholeBlockAction() {
        val selected = lesson(1)
        assertEquals(listOf(LessonQuickActionKind.OPEN, LessonQuickActionKind.ADD_TASK, LessonQuickActionKind.CANCEL_LESSON), lessonQuickActionKinds(selected, LessonBlock(listOf(selected))))
    }

    @Test fun activeMultiPeriodLessonHasBothCancellationScopes() {
        val selected = lesson(1)
        val block = LessonBlock(listOf(selected, lesson(2)))
        assertEquals(listOf(LessonQuickActionKind.OPEN, LessonQuickActionKind.ADD_TASK, LessonQuickActionKind.CANCEL_LESSON, LessonQuickActionKind.CANCEL_BLOCK), lessonQuickActionKinds(selected, block))
    }

    @Test fun cancelledSingleLessonUsesRestoreAction() {
        val selected = lesson(1, cancelled = true)
        assertEquals(listOf(LessonQuickActionKind.OPEN, LessonQuickActionKind.ADD_TASK, LessonQuickActionKind.RESTORE_LESSON), lessonQuickActionKinds(selected, LessonBlock(listOf(selected))))
    }

    @Test fun cancelledMultiPeriodLessonOffersTappedAndWholeBlockRestore() {
        val selected = lesson(1, cancelled = true)
        val block = LessonBlock(listOf(selected, lesson(2)))
        assertEquals(listOf(LessonQuickActionKind.OPEN, LessonQuickActionKind.ADD_TASK, LessonQuickActionKind.RESTORE_LESSON, LessonQuickActionKind.RESTORE_BLOCK), lessonQuickActionKinds(selected, block))
    }
}
