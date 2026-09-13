package com.eduflow.app.ui

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.PrivateLessonLocationKind
import java.time.LocalDate
import java.time.LocalTime

fun oneOffDefaultDate(contextDate: LocalDate?, today: LocalDate): LocalDate = contextDate ?: today

fun isValidPrivateLessonTimeRange(start: LocalTime?, end: LocalTime?): Boolean =
    start != null && end != null && end.isAfter(start)

data class PrivateLessonFormValues(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subjectId: Long?,
    val name: String,
    val teacher: String?,
    val location: String?,
    val locationKind: PrivateLessonLocationKind
)

fun updateOneOffPrivateLesson(current: LessonInstance, values: PrivateLessonFormValues): LessonInstance =
    current.copy(
        actualDate = values.date,
        actualStartTime = values.start,
        actualEndTime = values.end,
        subjectId = values.subjectId,
        actualTeacher = values.teacher,
        actualRoom = values.location,
        privateLessonName = values.name,
        privateLocationKind = values.locationKind
    )

fun newOneOffPrivateLesson(values: PrivateLessonFormValues): LessonInstance = LessonInstance(
    actualDate = values.date,
    actualStartTime = values.start,
    actualEndTime = values.end,
    subjectId = values.subjectId,
    kind = LessonKind.PRIVATE,
    cancellationState = CancellationState.ACTIVE,
    actualTeacher = values.teacher,
    actualRoom = values.location,
    privateLessonName = values.name,
    privateLocationKind = values.locationKind
)
