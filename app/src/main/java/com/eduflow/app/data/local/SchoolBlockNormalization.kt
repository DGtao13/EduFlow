package com.eduflow.app.data.local

import androidx.room.withTransaction

/** A slot snapshot with its effective teacher and room, used only to preserve v9's old block semantics once. */
internal data class LegacySchoolBlockSlot(
    val id: Long,
    val templateId: Long,
    val weekday: Int,
    val lessonIndex: Int,
    val subjectId: Long?,
    val effectiveTeacher: String?,
    val effectiveRoom: String?,
    val groupInfo: String?,
    val logicalBlockId: String?
)

/** Converts only legacy implicit same-subject runs into durable, explicit block identities. */
internal object SchoolBlockNormalization {
    fun legacyAssignments(slots: List<LegacySchoolBlockSlot>): Map<Long, String> {
        val assignments = mutableMapOf<Long, String>()
        slots.groupBy { it.templateId to it.weekday }.values.forEach { daySlots ->
            val ordered = daySlots.sortedBy { it.lessonIndex }
            var index = 0
            while (index < ordered.size) {
                val first = ordered[index]
                if (first.logicalBlockId.normalizedId() != null) {
                    index++
                    continue
                }
                val run = mutableListOf(first)
                var expectedPeriod = first.lessonIndex + 1
                var next = index + 1
                while (next < ordered.size) {
                    val candidate = ordered[next]
                    if (candidate.logicalBlockId.normalizedId() != null || candidate.lessonIndex != expectedPeriod || !legacyCompatible(first, candidate)) break
                    run += candidate
                    expectedPeriod++
                    next++
                }
                if (run.size > 1) {
                    val id = "legacy-block-${first.templateId}-${first.weekday}-${first.id}"
                    run.forEach { assignments[it.id] = id }
                }
                index = if (run.size > 1) next else index + 1
            }
        }
        return assignments
    }

    /** Makes malformed or edited block IDs canonical: only contiguous runs of two or more remain blocks. */
    suspend fun repairTopology(database: EduFlowDatabase, templateId: Long? = null) = database.withTransaction {
        val slots = (templateId?.let { database.scheduleSlotDao().getForTemplate(it) } ?: database.scheduleSlotDao().getAll())
        slots.groupBy { Triple(it.scheduleTemplateId, it.weekday, it.logicalBlockId.normalizedId()) }
            .filterKeys { it.third != null }
            .values
            .forEach { members ->
                val ordered = members.sortedBy { it.lessonIndex }
                val runs = mutableListOf<MutableList<ScheduleSlot>>()
                ordered.forEach { slot ->
                    if (runs.lastOrNull()?.lastOrNull()?.lessonIndex == slot.lessonIndex - 1) runs.last() += slot
                    else runs += mutableListOf(slot)
                }
                runs.forEachIndexed { runIndex, run ->
                    val replacement = when {
                        run.size < 2 -> null
                        runIndex == 0 -> run.first().logicalBlockId.normalizedId()
                        else -> "slot-block-${run.first().id}"
                    }
                    run.filter { it.logicalBlockId.normalizedId() != replacement }
                        .forEach { database.scheduleSlotDao().update(it.copy(logicalBlockId = replacement)) }
                }
            }
    }

    suspend fun normalizeLegacySlots(database: EduFlowDatabase) = database.withTransaction {
        val subjects = database.subjectDao().getAll().associateBy { it.id }
        val slots = database.scheduleSlotDao().getAll()
        val assignments = legacyAssignments(slots.map { slot ->
            val subject = slot.subjectId?.let(subjects::get)
            LegacySchoolBlockSlot(
                id = slot.id,
                templateId = slot.scheduleTemplateId,
                weekday = slot.weekday,
                lessonIndex = slot.lessonIndex,
                subjectId = slot.subjectId,
                effectiveTeacher = slot.teacherOverride ?: subject?.defaultTeacher,
                effectiveRoom = slot.roomOverride ?: subject?.defaultRoom,
                groupInfo = slot.groupInfo,
                logicalBlockId = slot.logicalBlockId
            )
        })
        slots.filter { assignments.containsKey(it.id) }
            .forEach { database.scheduleSlotDao().update(it.copy(logicalBlockId = assignments.getValue(it.id))) }
    }

    private fun legacyCompatible(first: LegacySchoolBlockSlot, candidate: LegacySchoolBlockSlot): Boolean =
        first.subjectId == candidate.subjectId &&
            compatible(first.effectiveTeacher, candidate.effectiveTeacher) &&
            compatible(first.effectiveRoom, candidate.effectiveRoom) &&
            compatible(first.groupInfo, candidate.groupInfo)

    private fun compatible(first: String?, second: String?): Boolean {
        val left = first.normalizedId()
        val right = second.normalizedId()
        return left == null || right == null || left == right
    }
}

internal fun String?.normalizedId(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
