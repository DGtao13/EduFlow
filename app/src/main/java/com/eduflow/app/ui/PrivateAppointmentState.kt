package com.eduflow.app.ui

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import java.time.LocalDateTime

enum class PrivateAppointmentState { CANCELLED, PAST, UPCOMING }
fun privateAppointmentState(lesson: LessonInstance, now: LocalDateTime): PrivateAppointmentState = when {
    lesson.cancellationState == CancellationState.CANCELLED -> PrivateAppointmentState.CANCELLED
    now.isAfter(LocalDateTime.of(lesson.actualDate, lesson.actualEndTime)) -> PrivateAppointmentState.PAST
    else -> PrivateAppointmentState.UPCOMING
}
