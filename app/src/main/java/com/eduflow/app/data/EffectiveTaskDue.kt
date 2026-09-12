package com.eduflow.app.data

import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskStatus
import java.time.LocalDateTime

object EffectiveTaskDue {
    /** No candidate preserves both the last known effective deadline and the intention. */
    fun apply(task: Task, candidate: LessonInstance?): Task =
        if (task.status != TaskStatus.PENDING || candidate == null) task
        else task.copy(dueLessonInstanceId = candidate.id, dueAt = LocalDateTime.of(candidate.actualDate, candidate.actualStartTime))
}
