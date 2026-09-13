package com.eduflow.app.data.local

import androidx.room.withTransaction
import com.eduflow.app.domain.ScheduleCycle

/** Repairs only unambiguous source references lost by the former slot REPLACE write.
 * Never changes occurrence identity/content or chooses between duplicate historical records. */
internal object SchoolLessonSourceRepair {
    suspend fun repair(database: EduFlowDatabase) = database.withTransaction {
        val configuration = database.cycleDao().getConfiguration() ?: return@withTransaction
        val entries = database.cycleDao().getEntries().map { it.templateId }
        val slots = database.scheduleSlotDao().getAll()
        val lessons = database.lessonInstanceDao().getAll().filter { it.kind == LessonKind.SCHOOL }
        lessons.groupBy { it.actualDate }.forEach { (date, day) ->
            val template = ScheduleCycle.resolveTemplateId(ScheduleCycle.mondayOf(date), configuration.anchorMonday, configuration.anchorTemplateId, entries)
            val daySlots = slots.filter { it.scheduleTemplateId == template && it.weekday == date.dayOfWeek.value }
            day.filter { it.sourceScheduleSlotId == null }.forEach orphanLoop@ { orphan ->
                val candidates = daySlots.filter { it.subjectId == orphan.subjectId && it.startTime == orphan.actualStartTime && it.endTime == orphan.actualEndTime }
                val source = candidates.singleOrNull() ?: return@orphanLoop
                // A source already claimed, or duplicate detached occurrences, is ambiguous.
                if (day.any { it.sourceScheduleSlotId == source.id }) return@orphanLoop
                if (day.count { it.subjectId == orphan.subjectId && it.actualStartTime == orphan.actualStartTime && it.actualEndTime == orphan.actualEndTime } != 1) return@orphanLoop
                database.lessonInstanceDao().update(orphan.copy(sourceScheduleSlotId = source.id))
            }
        }
    }
}
