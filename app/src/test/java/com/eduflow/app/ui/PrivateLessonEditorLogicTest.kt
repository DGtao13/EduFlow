package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.PrivateLessonLocationKind
import java.time.LocalDate
import java.time.LocalTime

class PrivateLessonEditorLogicTest {
    @Test fun validTimeRangeIsAccepted() {
        assertTrue(isValidPrivateLessonTimeRange(LocalTime.of(17, 0), LocalTime.of(18, 0)))
    }

    @Test fun equalTimesAreRejected() {
        assertFalse(isValidPrivateLessonTimeRange(LocalTime.of(17, 0), LocalTime.of(17, 0)))
    }

    @Test fun endBeforeStartIsRejected() {
        assertFalse(isValidPrivateLessonTimeRange(LocalTime.of(18, 0), LocalTime.of(17, 0)))
    }

    @Test fun explicitContextDateWinsOverToday() {
        val contextDate = LocalDate.of(2026, 9, 20)
        assertEquals(contextDate, oneOffDefaultDate(contextDate, LocalDate.of(2026, 9, 12)))
    }

    @Test fun missingContextDateUsesToday() {
        val today = LocalDate.of(2026, 9, 12)
        assertEquals(today, oneOffDefaultDate(null, today))
    }

    @Test fun editingPreservesIdentityCancellationAndUneditedHistory() {
        val current = LessonInstance(
            id = 42,
            actualDate = LocalDate.of(2026, 9, 20),
            actualStartTime = LocalTime.of(16, 0),
            actualEndTime = LocalTime.of(17, 0),
            subjectId = 3,
            sourcePrivateLessonId = 9,
            kind = LessonKind.PRIVATE,
            cancellationState = CancellationState.CANCELLED,
            topic = "Записка",
            notes = "История",
            privateLessonName = "Старо име"
        )
        val updated = updateOneOffPrivateLesson(current, PrivateLessonFormValues(
            date = LocalDate.of(2026, 9, 21),
            start = LocalTime.of(18, 0),
            end = LocalTime.of(19, 30),
            subjectId = 4,
            name = "Ново име",
            teacher = "Нов преподавател",
            location = "Google Meet",
            locationKind = PrivateLessonLocationKind.ONLINE
        ))

        assertEquals(42L, updated.id)
        assertEquals(CancellationState.CANCELLED, updated.cancellationState)
        assertEquals(9L, updated.sourcePrivateLessonId)
        assertEquals("Записка", updated.topic)
        assertEquals("История", updated.notes)
        assertEquals(LocalDate.of(2026, 9, 21), updated.actualDate)
        assertEquals(LocalTime.of(18, 0), updated.actualStartTime)
        assertEquals(LocalTime.of(19, 30), updated.actualEndTime)
        assertEquals("Ново име", updated.privateLessonName)
    }

    @Test fun similarLessonHasNewIdentityAndDoesNotCopySourceState() {
        val similar = newOneOffPrivateLesson(PrivateLessonFormValues(
            date = LocalDate.of(2026, 9, 22),
            start = LocalTime.of(18, 0),
            end = LocalTime.of(19, 0),
            subjectId = 4,
            name = "Подобен урок",
            teacher = "Преподавател",
            location = "Адрес",
            locationKind = PrivateLessonLocationKind.IN_PERSON
        ))

        assertEquals(0, similar.id)
        assertEquals(LessonKind.PRIVATE, similar.kind)
        assertEquals(CancellationState.ACTIVE, similar.cancellationState)
        assertEquals(null, similar.sourcePrivateLessonId)
        assertEquals(LocalDate.of(2026, 9, 22), similar.actualDate)
        assertEquals(LocalTime.of(18, 0), similar.actualStartTime)
        assertEquals("Подобен урок", similar.privateLessonName)
    }
}
