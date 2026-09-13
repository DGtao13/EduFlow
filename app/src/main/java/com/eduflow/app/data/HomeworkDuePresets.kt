package com.eduflow.app.data

import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.data.local.TaskType
import com.eduflow.app.data.local.LessonInstance
import java.time.LocalDateTime

object HomeworkDuePresets {
    suspend fun resolve(subjectId: Long, after: LocalDateTime, origin: LessonInstance?, ordinal: Int,
        lookup: suspend (Long, LocalDateTime, LessonInstance?) -> LessonInstance?): LessonInstance? {
        require(ordinal in 1..2)
        val next = lookup(subjectId, after, origin) ?: return null
        return if (ordinal == 1) next else lookup(subjectId, LocalDateTime.of(next.actualDate, next.actualStartTime), next)
    }
    fun defaultToNext(isNew: Boolean, type: TaskType, subjectId: Long?, slots: List<ScheduleSlot>, userSelectedDeadline: Boolean): Boolean =
        isNew && !userSelectedDeadline && type == TaskType.HOMEWORK && subjectId != null && slots.any { it.subjectId == subjectId }
}
