package com.eduflow.app.domain

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleSlot
import java.time.LocalTime

/** A presentation/action grouping for adjacent dated SCHOOL occurrences; it is never persisted. */
data class LessonBlock(val lessons: List<LessonInstance>) {
    val ids: List<Long> get() = lessons.map { it.id }
    val isMultiPeriod: Boolean get() = lessons.size > 1
    val startTime: LocalTime get() = lessons.first().actualStartTime
    val endTime: LocalTime get() = lessons.last().actualEndTime
}

object LessonBlockResolver {
    /** Prefer the intact dated source occurrence over detached duplicates, independent of DAO tie order. */
    fun scheduleMembersByStart(dayLessons: List<LessonInstance>, slots: List<ScheduleSlot>): Map<LocalTime, LessonInstance> {
        val sourceIds = slots.mapTo(mutableSetOf()) { it.id }
        return dayLessons.groupBy { it.actualStartTime }.mapValues { (_, occurrences) ->
            occurrences.minWith(compareBy<LessonInstance> { if (it.sourceScheduleSlotId in sourceIds) 0 else 1 }.thenBy { it.id })
        }
    }

    fun resolve(selected: LessonInstance, dayLessons: List<LessonInstance>, slots: List<ScheduleSlot>): LessonBlock =
        resolveAll(dayLessons, slots)[selected.id] ?: LessonBlock(listOf(selected))

    /** Resolves each day once, allowing timetable cells to look up block metadata in O(1). */
    fun resolveAll(dayLessons: List<LessonInstance>, slots: List<ScheduleSlot>): Map<Long, LessonBlock> {
        val slotById = slots.associateBy { it.id }
        val resolved = mutableMapOf<Long, LessonBlock>()
        // Detached historical duplicates must not interrupt intact source-backed sessions.
        val sourceBacked = dayLessons.filter { it.kind == LessonKind.SCHOOL && it.sourceScheduleSlotId in slotById }
        val sourceBackedIds = sourceBacked.mapTo(mutableSetOf()) { it.id }
        dayLessons.filterNot { it.id in sourceBackedIds }.forEach { resolved[it.id] = LessonBlock(listOf(it)) }
        sourceBacked.groupBy {
            val slot = slotById.getValue(it.sourceScheduleSlotId!!)
            Triple(it.actualDate, slot.scheduleTemplateId, slot.weekday)
        }.values.forEach { occurrence ->
            val ordered = occurrence.sortedWith(compareBy<LessonInstance> { slotById.getValue(it.sourceScheduleSlotId!!).lessonIndex }
                .thenBy { it.actualStartTime }.thenBy { it.id })
            var index = 0
            while (index < ordered.size) {
                val first = ordered[index]
                val firstSlot = slotById.getValue(first.sourceScheduleSlotId!!)
                val members = mutableListOf(first)
                var expectedIndex = firstSlot.lessonIndex + 1
                var next = index + 1
                while (next < ordered.size) {
                    val candidate = ordered[next]
                    val candidateSlot = slotById.getValue(candidate.sourceScheduleSlotId!!)
                    if (candidateSlot.lessonIndex != expectedIndex || !matches(first, firstSlot, candidate, candidateSlot)) break
                    members += candidate
                    expectedIndex++
                    next++
                }
                val block = LessonBlock(members)
                members.forEach { resolved[it.id] = block }
                index = next
            }
        }
        return resolved
    }

    private fun matches(
        reference: LessonInstance,
        referenceSlot: ScheduleSlot,
        candidate: LessonInstance,
        candidateSlot: ScheduleSlot
    ): Boolean {
        if (reference.actualDate != candidate.actualDate || reference.kind != LessonKind.SCHOOL || candidate.kind != LessonKind.SCHOOL) return false
        if (referenceSlot.scheduleTemplateId != candidateSlot.scheduleTemplateId || referenceSlot.weekday != candidateSlot.weekday) return false
        val explicitId = referenceSlot.logicalBlockId?.trim()?.takeIf { it.isNotEmpty() }
        return explicitId != null && explicitId == candidateSlot.logicalBlockId?.trim()
    }

}
