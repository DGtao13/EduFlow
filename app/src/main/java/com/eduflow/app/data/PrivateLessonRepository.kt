package com.eduflow.app.data

import androidx.room.withTransaction
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.RecurringPrivateLesson
import com.eduflow.app.data.local.PrivateLessonLocationKind
import com.eduflow.app.domain.ScheduleCycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object PrivateLessonRecurrence {
    fun occursOn(definition: RecurringPrivateLesson, date: LocalDate): Boolean {
        if (!definition.enabled || date < definition.startDate || (definition.endDate != null && date > definition.endDate)) return false
        if (date.dayOfWeek.value != definition.weekday || definition.intervalWeeks < 1) return false
        return ChronoUnit.WEEKS.between(ScheduleCycle.mondayOf(definition.startDate), ScheduleCycle.mondayOf(date)) % definition.intervalWeeks == 0L
    }
}

class PrivateLessonRepository(private val database: EduFlowDatabase) {
    suspend fun materializeWeek(weekMonday: LocalDate) = database.withTransaction {
        val subjects = database.subjectDao().getAll().associateBy { it.id }
        val dates = List(7) { ScheduleCycle.mondayOf(weekMonday).plusDays(it.toLong()) }
        database.recurringPrivateLessonDao().getAll().forEach { definition ->
            dates.filter { PrivateLessonRecurrence.occursOn(definition, it) }.forEach { date ->
                database.lessonInstanceDao().insertIfAbsent(recurringPrivateOccurrence(definition, date, definition.subjectId?.let(subjects::get)?.name))
            }
        }
    }
}

fun recurringPrivateOccurrence(definition: RecurringPrivateLesson, date: LocalDate, subjectName: String?): LessonInstance = LessonInstance(
    actualDate = date,
    actualStartTime = definition.startTime,
    actualEndTime = definition.endTime,
    subjectId = definition.subjectId,
    sourcePrivateLessonId = definition.id,
    kind = LessonKind.PRIVATE,
    actualTeacher = definition.teacherOverride,
    actualRoom = definition.roomOverride,
    // Older recurring definitions can theoretically have neither a label nor a linked
    // Subject. Keep those materialized occurrences independently intelligible without
    // manufacturing a Subject or inheriting any school metadata.
    privateLessonName = definition.privateLessonName?.trim()?.takeIf { it.isNotEmpty() } ?: subjectName ?: "Частен урок",
    privateLocationKind = definition.privateLocationKind
)

class WeekMaterializer(private val database: EduFlowDatabase) {
    private val school = ScheduleRepository(database)
    private val privateLessons = PrivateLessonRepository(database)
    suspend fun materializeWeek(weekMonday: LocalDate) {
        school.materializeWeek(weekMonday)
        privateLessons.materializeWeek(weekMonday)
    }
    suspend fun configureCycle(anchorMonday: LocalDate, anchorTemplateId: Long, templateIds: List<Long>) = school.configureCycle(anchorMonday, anchorTemplateId, templateIds)
}
