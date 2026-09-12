package com.eduflow.app.data

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.PrivateLessonLocationKind
import com.eduflow.app.data.local.Subject

data class PrivateLessonNameDraft(val value: String, val isAutoDerived: Boolean)

fun privateLessonNameAfterSubjectSelection(
    currentName: String,
    isAutoDerived: Boolean,
    selectedSubjectName: String?
): PrivateLessonNameDraft =
    if (selectedSubjectName != null && (currentName.isBlank() || isAutoDerived)) {
        PrivateLessonNameDraft(selectedSubjectName, true)
    } else {
        PrivateLessonNameDraft(currentName, isAutoDerived)
    }

fun LessonInstance.requireKindInvariant() {
    require(kind != LessonKind.SCHOOL || subjectId != null) { "SCHOOL lessons require a Subject" }
}

fun LessonInstance.displayTitle(subject: Subject?, privateFallback: String, schoolFallback: String): String = when (kind) {
    LessonKind.PRIVATE -> privateLessonName?.trim()?.takeIf { it.isNotEmpty() }
        ?: subject?.shortName
        ?: subject?.name
        ?: privateFallback
    LessonKind.SCHOOL -> subject?.shortName ?: subject?.name ?: schoolFallback
}

fun LessonInstance.displayTutor(): String? = actualTeacher?.trim()?.takeIf { it.isNotEmpty() }

fun LessonInstance.displayLocation(): String? = when (kind) {
    LessonKind.PRIVATE -> if (privateLocationKind == PrivateLessonLocationKind.UNSPECIFIED) null else actualRoom?.trim()?.takeIf { it.isNotEmpty() }
    LessonKind.SCHOOL -> actualRoom?.trim()?.takeIf { it.isNotEmpty() }
}

fun restoredPrivateLessonName(kind: LessonKind, savedName: String?, subjectName: String?): String? =
    if (kind == LessonKind.PRIVATE) savedName?.trim()?.takeIf { it.isNotEmpty() } ?: subjectName else null

fun restoredPrivateLocationKind(kind: LessonKind, savedKind: String?, room: String?): PrivateLessonLocationKind =
    savedKind?.let { runCatching { PrivateLessonLocationKind.valueOf(it) }.getOrNull() }
        ?: if (kind == LessonKind.PRIVATE && !room.isNullOrBlank()) PrivateLessonLocationKind.IN_PERSON else PrivateLessonLocationKind.UNSPECIFIED
