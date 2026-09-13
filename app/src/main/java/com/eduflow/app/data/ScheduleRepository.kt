package com.eduflow.app.data

import androidx.room.withTransaction
import com.eduflow.app.EduFlowApplication
import com.eduflow.app.data.local.CycleConfiguration
import com.eduflow.app.data.local.CycleEntry
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.domain.ScheduleCycle
import java.time.LocalDate

class ScheduleRepository(private val database: EduFlowDatabase) {
    suspend fun configureCycle(anchorMonday: LocalDate, anchorTemplateId: Long, templateIds: List<Long>) {
        require(templateIds.isNotEmpty() && anchorTemplateId in templateIds)
        database.withTransaction {
            database.cycleDao().saveConfiguration(CycleConfiguration(anchorMonday = ScheduleCycle.mondayOf(anchorMonday), anchorTemplateId = anchorTemplateId))
            database.cycleDao().clearEntries()
            database.cycleDao().insertEntries(templateIds.distinct().mapIndexed { index, id -> CycleEntry(position = index, templateId = id) })
        }
    }

    suspend fun materializeWeek(weekMonday: LocalDate): Long? {
        com.eduflow.app.data.local.SchoolLessonSourceRepair.repair(database)
        val academicYear = AcademicYearSettingsRepository(EduFlowApplication.appContext).snapshot()
        return database.withTransaction {
        val configuration = database.cycleDao().getConfiguration() ?: return@withTransaction null
        val entries = database.cycleDao().getEntries()
        val templateId = ScheduleCycle.resolveTemplateId(weekMonday, configuration.anchorMonday, configuration.anchorTemplateId, entries.map { it.templateId }) ?: return@withTransaction null
        val subjects = database.subjectDao().getAll().associateBy { it.id }
        database.scheduleSlotDao().getForTemplate(templateId).forEach { slot ->
            val actualDate = ScheduleCycle.mondayOf(weekMonday).plusDays((slot.weekday - 1).toLong())
            if (!SchoolYear.containsSchoolDate(actualDate, academicYear)) return@forEach
            val subject = slot.subjectId?.let(subjects::get)
            val lesson = LessonInstance(
                    actualDate = actualDate,
                    actualStartTime = slot.startTime,
                    actualEndTime = slot.endTime,
                    subjectId = slot.subjectId,
                    sourceScheduleSlotId = slot.id,
                    kind = LessonKind.SCHOOL,
                    actualTeacher = slot.teacherOverride ?: subject?.defaultTeacher,
                    actualRoom = slot.roomOverride ?: subject?.defaultRoom
                )
            lesson.requireKindInvariant()
            // Do not manufacture another occurrence while an ambiguous historical orphan exists.
            if (database.lessonInstanceDao().getForDate(actualDate).any {
                it.kind == LessonKind.SCHOOL && it.sourceScheduleSlotId == null &&
                    it.subjectId == slot.subjectId && it.actualStartTime == slot.startTime && it.actualEndTime == slot.endTime
            }) return@forEach
            database.lessonInstanceDao().insertIfAbsent(lesson)
        }
        templateId
        }
    }
}
