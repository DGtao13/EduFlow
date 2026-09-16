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

    /**
     * A recurring definition is the series identity.  Its materialized future rows
     * must be reconciled when that definition changes; insert-if-absent alone would
     * otherwise retain dates that no longer belong to the series.
     */
    suspend fun save(definition: RecurringPrivateLesson, today: LocalDate = LocalDate.now()): Long = database.withTransaction {
        val previousFuture = if (definition.id == 0L) emptyList() else database.lessonInstanceDao()
            .getForPrivateSourceInRange(definition.id, today, LocalDate.of(2100, 1, 1))
        // Room's @Upsert return is an insert row id, not a stable existing-row id.
        // On editor updates it must not be used for source-owned cleanup.
        val sourceId = if (definition.id == 0L) {
            database.recurringPrivateLessonDao().upsert(definition)
        } else {
            database.recurringPrivateLessonDao().update(definition)
            definition.id
        }
        // Use the same authoritative source-owned cleanup as disable/delete. The
        // former selective loop could leave stale materialized weekday rows visible
        // until a later disable/re-enable performed this bulk cleanup.
        database.lessonInstanceDao().deleteFutureForPrivateSource(sourceId, today)
        val saved = database.recurringPrivateLessonDao().getById(sourceId) ?: return@withTransaction sourceId
        val subjectName = saved.subjectId?.let { id -> database.subjectDao().getAll().firstOrNull { it.id == id }?.name }
        // Recreate affected materialized weeks immediately, carrying forward a
        // still-valid occurrence's per-instance cancellation and annotations.
        val preserved = previousFuture.associateBy { it.actualDate }
        if (saved.enabled) {
            (previousFuture.map { ScheduleCycle.mondayOf(it.actualDate) } +
                listOf(ScheduleCycle.mondayOf(today), ScheduleCycle.mondayOf(today.plusWeeks(1))))
                .distinct().forEach { monday ->
                List(7) { monday.plusDays(it.toLong()) }
                    .filter { PrivateLessonRecurrence.occursOn(saved, it) }
                    .forEach { date ->
                        val old = preserved[date]
                        database.lessonInstanceDao().insertIfAbsent(recurringPrivateOccurrence(saved, date, subjectName).copy(
                            cancellationState = old?.cancellationState ?: com.eduflow.app.data.local.CancellationState.ACTIVE,
                            topic = old?.topic,
                            notes = old?.notes
                        ))
                    }
            }
        }
        sourceId
    }

    suspend fun setEnabled(definition: RecurringPrivateLesson, enabled: Boolean, today: LocalDate = LocalDate.now()) = database.withTransaction {
        database.recurringPrivateLessonDao().update(definition.copy(enabled = enabled))
        // Materialized rows are not the authority. Remove future rows so disabling
        // cannot leave a visible/schedulable PRIVATE lesson; enabling rematerializes
        // only dates that satisfy the same source definition.
        database.lessonInstanceDao().deleteFutureForPrivateSource(definition.id, today)
    }

    suspend fun deleteSeries(definition: RecurringPrivateLesson, today: LocalDate = LocalDate.now()) = database.withTransaction {
        // Delete owned future occurrences before deleting the source. Deleting the
        // source first would SET NULL and turn them into apparent one-time lessons.
        database.lessonInstanceDao().deleteFutureForPrivateSource(definition.id, today)
        database.recurringPrivateLessonDao().delete(definition)
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
