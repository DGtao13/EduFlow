package com.eduflow.app.data

import android.content.Context
import androidx.room.withTransaction
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskChecklistItem
import com.eduflow.app.data.local.TaskReminder
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.TaskReminderKind
import com.eduflow.app.notifications.TaskReminderScheduler
import com.eduflow.app.widget.EduFlowWidgetUpdater
import java.time.LocalDateTime

class TaskLifecycleRepository(private val context: Context, private val database: EduFlowDatabase) {
    private val scheduler = TaskReminderScheduler(context, database)
    private val repository = TaskRepository(database)
    suspend fun toggle(task: Task) {
        val updated = database.withTransaction {
            val current = database.taskDao().getById(task.id) ?: return@withTransaction task
            val value = if (current.status == TaskStatus.PENDING) TaskLogic.complete(current, LocalDateTime.now()) else repository.recomputeEffectiveDue(TaskLogic.reopen(current))
            database.taskDao().update(value)
            value
        }
        if (updated.status == TaskStatus.COMPLETED) scheduler.cancelTask(updated.id) else scheduler.syncTask(updated)
        EduFlowWidgetUpdater.update(context)
    }
    suspend fun save(task: Task, checklist: List<TaskChecklistItem>, reminders: List<TaskReminder>): Long {
        val id = database.withTransaction { repository.saveWithChecklist(repository.recomputeEffectiveDue(task), checklist, reminders) }
        database.taskDao().getById(id)?.let { scheduler.syncTask(it) }
        EduFlowWidgetUpdater.update(context)
        return id
    }
    suspend fun delete(task: Task) { scheduler.cancelTask(task.id); database.taskDao().delete(task); TaskDuePresetRepository(context).remove(task.id); EduFlowWidgetUpdater.update(context) }
    suspend fun deleteReminder(reminder: TaskReminder) { scheduler.cancelReminder(reminder.id); if (reminder.id != 0L) database.taskReminderDao().delete(reminder) }
    suspend fun clearReminders(taskId: Long) { database.taskReminderDao().getForTask(taskId).forEach { scheduler.cancelReminder(it.id); database.taskReminderDao().delete(it) } }
    suspend fun clearRelativeReminders(taskId: Long) { database.taskReminderDao().getForTask(taskId).filter { it.kind != TaskReminderKind.CUSTOM }.forEach { scheduler.cancelReminder(it.id); database.taskReminderDao().delete(it) } }
}
