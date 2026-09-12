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
    fun resolve(selected: LessonInstance, dayLessons: List<LessonInstance>, slots: List<ScheduleSlot>): LessonBlock =
        resolveAll(dayLessons, slots)[selected.id] ?: LessonBlock(listOf(selected))

    /** Resolves each day once, allowing timetable cells to look up block metadata in O(1). */
    fun resolveAll(dayLessons: List<LessonInstance>, slots: List<ScheduleSlot>): Map<Long, LessonBlock> {
        val slotById = slots.associateBy { it.id }
        val ordered = dayLessons.sortedBy { it.actualStartTime }
        val resolved = mutableMapOf<Long, LessonBlock>()
        var index = 0
        while (index < ordered.size) {
            val first = ordered[index]
            val firstSlot = first.sourceScheduleSlotId?.let(slotById::get)
            if (first.kind != LessonKind.SCHOOL || firstSlot == null) {
                resolved[first.id] = LessonBlock(listOf(first))
                index++
                continue
            }
            val members = mutableListOf(first)
            var expectedIndex = firstSlot.lessonIndex + 1
            var next = index + 1
            while (next < ordered.size) {
                val candidate = ordered[next]
                val candidateSlot = candidate.sourceScheduleSlotId?.let(slotById::get) ?: break
                if (candidateSlot.lessonIndex != expectedIndex || !matches(first, firstSlot, candidate, candidateSlot)) break
                members += candidate
                expectedIndex++
                next++
            }
            val block = LessonBlock(members)
            members.forEach { resolved[it.id] = block }
            index = next
        }
        return resolved
    }

    private fun matches(
        reference: LessonInstance,
        referenceSlot: ScheduleSlot,
        candidate: LessonInstance,
        candidateSlot: ScheduleSlot
    ): Boolean = reference.actualDate == candidate.actualDate &&
        reference.kind == LessonKind.SCHOOL && candidate.kind == LessonKind.SCHOOL &&
        reference.subjectId == candidate.subjectId &&
        compatible(reference.actualTeacher, candidate.actualTeacher) &&
        compatible(reference.actualRoom, candidate.actualRoom) &&
        compatible(referenceSlot.groupInfo, candidateSlot.groupInfo)

    /** A missing optional attribute is not evidence of a different class; two specified values must agree. */
    private fun compatible(first: String?, second: String?): Boolean {
        val left = first?.trim()?.takeIf { it.isNotEmpty() }
        val right = second?.trim()?.takeIf { it.isNotEmpty() }
        return left == null || right == null || left == right
    }
}
