package com.eduflow.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.ImportedTaskShare
import com.eduflow.app.data.local.Subject
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskChecklistItem
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.TaskType
import java.io.File
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import com.eduflow.app.notifications.TaskReminderScheduler
import com.eduflow.app.widget.EduFlowWidgetUpdater

data class SubjectMatch(val selected: Subject?, val ambiguous: Boolean)
object SharedSubjectMapping {
    private fun normalized(name: String) = name.trim().lowercase().replace(Regex("\\s+"), " ")
    fun match(hint: String?, subjects: List<Subject>): SubjectMatch {
        if (hint.isNullOrBlank()) return SubjectMatch(null, false)
        val matches = subjects.filter { normalized(it.name) == normalized(hint) }
        return SubjectMatch(matches.singleOrNull(), matches.size > 1)
    }
}

class TaskShareRepository(private val context: Context, private val database: EduFlowDatabase) {
    suspend fun createShareUri(task: Task): Uri {
        val subject = task.subjectId?.let { id -> database.subjectDao().getAll().firstOrNull { it.id == id } }?.name
        val checklist = database.taskChecklistDao().getForTask(task.id)
        val ordinal = TaskDuePresetRepository(context).ordinal(task).first()
        val deadline = when {
            task.intendedDueLessonInstanceId != null && ordinal == 2 -> "SECOND_NEXT"
            task.intendedDueLessonInstanceId != null -> "NEXT"
            task.dueAt != null -> "ABSOLUTE"
            else -> "NONE"
        }
        val dir = File(context.cacheDir, "shared-items").apply { mkdirs() }
        // Keep only the current artifact; cache eviction and this replacement avoid permanent accumulation.
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
        val file = File(dir, "EduFlow-task.${TaskShareFormat.EXTENSION}")
        file.writeText(TaskShareCodec.encode(task, subject, checklist, deadline), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }

    suspend fun read(uri: Uri): TaskShareReadResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    if (output.size() + count > TaskShareFormat.MAX_BYTES) return@use TaskShareReadResult.TooLarge
                    output.write(buffer, 0, count)
                }
                TaskShareCodec.decode(output.toString(Charsets.UTF_8.name()))
            } ?: TaskShareReadResult.Malformed
        } catch (_: Exception) { TaskShareReadResult.Malformed }
    }

    suspend fun imported(shareId: String) = database.importedTaskShareDao().getByShareId(shareId)

    /** Imported checklists intentionally start unchecked and imported tasks always start pending. */
    suspend fun import(envelope: TaskShareEnvelope, subjectId: Long?, resolvedDue: com.eduflow.app.data.local.LessonInstance?, allowDuplicate: Boolean): Long? {
        var saved: Task? = null
        val id = database.withTransaction {
        if (!allowDuplicate && database.importedTaskShareDao().getByShareId(envelope.shareId) != null) return@withTransaction null
        val item = envelope.item
        val kind = item.deadline.kind
        val task = Task(
            title = item.title.trim(), description = item.description?.trim()?.ifBlank { null }, subjectId = subjectId,
            dueLessonInstanceId = if (kind == "NEXT" || kind == "SECOND_NEXT") resolvedDue?.id else null,
            intendedDueLessonInstanceId = if (kind == "NEXT" || kind == "SECOND_NEXT") resolvedDue?.id else null,
            type = TaskType.valueOf(item.type), priority = TaskPriority.valueOf(item.priority), status = TaskStatus.PENDING,
            dueAt = when (kind) { "ABSOLUTE" -> LocalDateTime.parse(item.deadline.dueAt); "NEXT", "SECOND_NEXT" -> resolvedDue?.let { LocalDateTime.of(it.actualDate, it.actualStartTime) }; else -> null },
            createdAt = LocalDateTime.now()
        )
        saved = task
        val id = TaskRepository(database).saveWithChecklist(task, item.checklist.mapIndexed { index, text -> TaskChecklistItem(taskId = 0, text = text.trim(), position = index) })
        // A repeated import remains detectable even if the user deliberately created another copy.
        database.importedTaskShareDao().upsert(ImportedTaskShare(shareId = envelope.shareId, taskId = id, importedAt = LocalDateTime.now()))
        id
        }
        val taskId = id ?: return null
        saved?.copy(id = taskId)?.let { task ->
            TaskDuePresetRepository(context).record(task, if (envelope.item.deadline.kind == "SECOND_NEXT") 2 else if (task.intendedDueLessonInstanceId != null) 1 else null)
        }
        database.taskDao().getById(taskId)?.let { TaskReminderScheduler(context, database).syncTask(it) }
        EduFlowWidgetUpdater.update(context)
        return taskId
    }
}
