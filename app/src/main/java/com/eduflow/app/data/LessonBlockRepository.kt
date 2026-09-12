package com.eduflow.app.data

import android.content.Context
import androidx.room.withTransaction
import com.eduflow.app.data.local.*
import com.eduflow.app.domain.LessonBlockResolver
import com.eduflow.app.notifications.TaskReminderScheduler
import com.eduflow.app.widget.EduFlowWidgetUpdater

/** Period, selected-period and whole-block mutations share one transaction. */
class SchoolLessonCancellationRepository(private val context: Context, private val database: EduFlowDatabase) {
    suspend fun setLessonCancellation(lesson: LessonInstance, state: CancellationState) =
        mutate(listOf(lesson), state, wholeBlock = false)

    suspend fun setCancellationForBlock(lesson: LessonInstance, state: CancellationState) =
        mutate(listOf(lesson), state, wholeBlock = true)

    suspend fun setCancellationForLessons(lessons: List<LessonInstance>, state: CancellationState) =
        mutate(lessons, state, wholeBlock = false)

    private suspend fun mutate(lessons: List<LessonInstance>, state: CancellationState, wholeBlock: Boolean) {
        val updates = database.withTransaction {
            val selected = lessons.mapNotNull { database.lessonInstanceDao().getById(it.id) }
            val affected = if (wholeBlock) selected.flatMap {
                LessonBlockResolver.resolve(it, database.lessonInstanceDao().getForDate(it.actualDate), database.scheduleSlotDao().getAll()).lessons
            } else selected
            database.lessonInstanceDao().updateCancellationStates(affected.map { it.id }.distinct(), state)
            val schoolSubjects = affected.filter { it.kind == LessonKind.SCHOOL }.mapNotNull { it.subjectId }.toSet()
            val repository = TaskRepository(database)
            database.taskDao().getPending().mapNotNull { task ->
                val intended = task.intendedDueLessonInstanceId?.let { database.lessonInstanceDao().getById(it) }
                if (intended?.kind != LessonKind.SCHOOL || intended.subjectId !in schoolSubjects) null
                else repository.recomputeEffectiveDue(task).takeIf { it != task }?.also { database.taskDao().update(it) }
            }
        }
        val scheduler = TaskReminderScheduler(context, database)
        updates.forEach { scheduler.syncTask(it) }
        EduFlowWidgetUpdater.update(context)
    }
}
