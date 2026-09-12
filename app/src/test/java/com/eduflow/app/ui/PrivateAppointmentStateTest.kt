package com.eduflow.app.ui

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PrivateAppointmentStateTest {
    private val date = LocalDate.of(2026, 9, 20)
    private fun lesson(state: CancellationState = CancellationState.ACTIVE) = LessonInstance(
        id = 1, actualDate = date, actualStartTime = LocalTime.of(10, 0), actualEndTime = LocalTime.of(11, 0),
        subjectId = null, kind = LessonKind.PRIVATE, cancellationState = state
    )

    @Test fun endedAppointmentIsPast() = assertEquals(PrivateAppointmentState.PAST, privateAppointmentState(lesson(), LocalDateTime.of(date, LocalTime.NOON)))
    @Test fun futureAppointmentIsUpcoming() = assertEquals(PrivateAppointmentState.UPCOMING, privateAppointmentState(lesson(), LocalDateTime.of(date, LocalTime.of(10, 30))))
    @Test fun cancelledAppointmentWinsOverPastState() = assertEquals(PrivateAppointmentState.CANCELLED, privateAppointmentState(lesson(CancellationState.CANCELLED), LocalDateTime.of(date, LocalTime.NOON)))
}
