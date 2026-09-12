package com.eduflow.app.ui

import com.eduflow.app.data.displayLocation
import com.eduflow.app.data.displayTitle
import com.eduflow.app.data.privateLessonNameAfterSubjectSelection
import com.eduflow.app.data.requireKindInvariant
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.PrivateLessonLocationKind
import com.eduflow.app.data.local.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LessonPresentationTest {
    private val subject = Subject(id = 3, name = "Математика", defaultTeacher = "Иванов", defaultRoom = "31 (Етаж 3)", color = 0)
    private fun lesson(
        kind: LessonKind = LessonKind.PRIVATE,
        subjectId: Long? = null,
        name: String? = "Подготовка за матура",
        tutor: String? = null,
        location: String? = null,
        locationKind: PrivateLessonLocationKind = PrivateLessonLocationKind.UNSPECIFIED
    ) = LessonInstance(
        actualDate = LocalDate.of(2026, 9, 12),
        actualStartTime = LocalTime.of(17, 0),
        actualEndTime = LocalTime.of(18, 0),
        subjectId = subjectId,
        kind = kind,
        actualTeacher = tutor,
        actualRoom = location,
        privateLessonName = name,
        privateLocationKind = locationKind
    )

    @Test fun schoolWithoutSubjectIsRejected() {
        runCatching { lesson(kind = LessonKind.SCHOOL, subjectId = null).requireKindInvariant() }.onSuccess { throw AssertionError("Expected a school invariant failure") }
    }

    @Test fun privateWithoutSubjectIsValid() {
        lesson(subjectId = null).requireKindInvariant()
    }

    @Test fun privateNameWinsForUnlinkedAndLinkedLessons() {
        assertEquals("Подготовка за матура", lesson().displayTitle(null, "Частен урок", "Час"))
        assertEquals("Мрежова практика", lesson(subjectId = subject.id, name = "Мрежова практика").displayTitle(subject, "Частен урок", "Час"))
    }

    @Test fun privateLocationNeverFallsBackToSchoolRoom() {
        assertNull(lesson(subjectId = subject.id).displayLocation())
        assertEquals("При преподавателя", lesson(location = "При преподавателя", locationKind = PrivateLessonLocationKind.IN_PERSON).displayLocation())
        assertEquals("Google Meet", lesson(location = "Google Meet", locationKind = PrivateLessonLocationKind.ONLINE).displayLocation())
    }

    @Test fun linkedAndUnlinkedPrivateHistoryMembershipUsesOnlySubjectId() {
        val linked = lesson(subjectId = subject.id)
        val unlinked = lesson(subjectId = null)
        assertTrue(linked.subjectId == subject.id)
        assertFalse(unlinked.subjectId == subject.id)
    }

    @Test fun subjectPrefillNeverOverwritesAManuallyEditedPrivateName() {
        val prefilled = privateLessonNameAfterSubjectSelection("", false, "Математика")
        assertEquals("Математика", prefilled.value)
        assertTrue(prefilled.isAutoDerived)
        assertEquals("Подготовка за матура", privateLessonNameAfterSubjectSelection("Подготовка за матура", false, "Английски").value)
        assertEquals("Подготовка за матура", privateLessonNameAfterSubjectSelection("Подготовка за матура", false, null).value)
    }
}
