package com.eduflow.app.ui

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import java.time.LocalDate

data class PrivateAgendaGroup(val date: LocalDate, val lessons: List<LessonInstance>)

fun groupPrivateAgendaLessons(lessons: List<LessonInstance>): List<PrivateAgendaGroup> =
    lessons
        .filter { it.kind == LessonKind.PRIVATE }
        .sortedWith(compareBy<LessonInstance> { it.actualDate }.thenBy { it.actualStartTime }.thenBy { it.id })
        .groupBy { it.actualDate }
        .toSortedMap()
        .map { (date, dateLessons) -> PrivateAgendaGroup(date, dateLessons) }

fun privateAgendaCounts(lessons: List<LessonInstance>): Map<LocalDate, Int> =
    lessons.filter { it.kind == LessonKind.PRIVATE }.groupingBy { it.actualDate }.eachCount()

/** Dates with a real agenda target; dates without private lessons are intentionally absent. */
fun privateAgendaTargetDates(lessons: List<LessonInstance>): List<LocalDate> =
    groupPrivateAgendaLessons(lessons).map { it.date }
