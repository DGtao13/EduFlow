package com.eduflow.app.ui

import java.time.LocalDate
import java.time.LocalTime

fun oneOffDefaultDate(contextDate: LocalDate?, today: LocalDate): LocalDate = contextDate ?: today

fun isValidPrivateLessonTimeRange(start: LocalTime?, end: LocalTime?): Boolean =
    start != null && end != null && end.isAfter(start)
