package com.eduflow.app.data

import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskChecklistItem
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.UUID

/** A deliberately small, portable one-item format. It is independent from backup JSON. */
object TaskShareFormat {
    const val FORMAT = "eduflow-share"
    const val VERSION = 1
    const val ITEM_TYPE = "TASK"
    const val MIME_TYPE = "application/vnd.eduflow.task+json"
    const val EXTENSION = "eduflowtask"
    const val MAX_BYTES = 128 * 1024
    const val MAX_TITLE = 200
    const val MAX_DESCRIPTION = 10_000
    const val MAX_CHECKLIST = 100
    const val MAX_CHECKLIST_TEXT = 500
    const val MAX_SUBJECT = 200
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
}

/** Last in-app routing guard; Android's MIME/path matching only chooses the activity candidate. */
object TaskShareIntentRouting {
    fun shouldOpen(action: String?, mimeType: String?, displayName: String?, uriPath: String?): Boolean {
        if (action != "android.intent.action.VIEW") return false
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        if (mime == TaskShareFormat.MIME_TYPE) return true
        return listOfNotNull(displayName, uriPath).any { it.trim().endsWith(".${TaskShareFormat.EXTENSION}", ignoreCase = true) }
    }
}

@Serializable data class TaskShareEnvelope(
    val format: String,
    val version: Int,
    val itemType: String,
    val shareId: String,
    val item: SharedTask
)
@Serializable data class SharedTask(
    val title: String,
    val description: String? = null,
    val checklist: List<String> = emptyList(),
    val type: String,
    val priority: String,
    val subjectName: String? = null,
    val deadline: SharedDeadline = SharedDeadline()
)
@Serializable data class SharedDeadline(val kind: String = "NONE", val dueAt: String? = null)

sealed interface TaskShareReadResult {
    data class Success(val envelope: TaskShareEnvelope) : TaskShareReadResult
    data object TooLarge : TaskShareReadResult
    data object Malformed : TaskShareReadResult
    data object UnsupportedVersion : TaskShareReadResult
    data object UnsupportedItemType : TaskShareReadResult
    data object InvalidContent : TaskShareReadResult
}

object TaskShareCodec {
    fun encode(task: Task, subjectName: String?, checklist: List<TaskChecklistItem>, deadlineKind: String): String =
        TaskShareFormat.json.encodeToString(TaskShareEnvelope.serializer(), TaskShareEnvelope(
            TaskShareFormat.FORMAT, TaskShareFormat.VERSION, TaskShareFormat.ITEM_TYPE, UUID.randomUUID().toString(),
            SharedTask(task.title, task.description, checklist.sortedBy { it.position }.map { it.text }, task.type.name,
                task.priority.name, subjectName, SharedDeadline(deadlineKind, task.dueAt?.takeIf { deadlineKind == "ABSOLUTE" }?.toString()))
        ))

    fun decode(text: String): TaskShareReadResult = try {
        val value = TaskShareFormat.json.decodeFromString(TaskShareEnvelope.serializer(), text)
        when {
            value.format != TaskShareFormat.FORMAT -> TaskShareReadResult.InvalidContent
            value.version != TaskShareFormat.VERSION -> TaskShareReadResult.UnsupportedVersion
            value.itemType != TaskShareFormat.ITEM_TYPE -> TaskShareReadResult.UnsupportedItemType
            !isValid(value) -> TaskShareReadResult.InvalidContent
            else -> TaskShareReadResult.Success(value)
        }
    } catch (_: Exception) { TaskShareReadResult.Malformed }

    private fun isValid(v: TaskShareEnvelope): Boolean = runCatching {
        UUID.fromString(v.shareId)
        val item = v.item
        item.title.isNotBlank() && item.title.length <= TaskShareFormat.MAX_TITLE &&
            (item.description?.length ?: 0) <= TaskShareFormat.MAX_DESCRIPTION &&
            item.checklist.size <= TaskShareFormat.MAX_CHECKLIST && item.checklist.all { it.isNotBlank() && it.length <= TaskShareFormat.MAX_CHECKLIST_TEXT } &&
            (item.subjectName?.length ?: 0) <= TaskShareFormat.MAX_SUBJECT &&
            run { TaskType.valueOf(item.type); TaskPriority.valueOf(item.priority); true } &&
            item.deadline.kind in setOf("NONE", "ABSOLUTE", "NEXT", "SECOND_NEXT") &&
            when (item.deadline.kind) { "ABSOLUTE" -> item.deadline.dueAt?.let { LocalDateTime.parse(it) } != null; else -> item.deadline.dueAt == null || LocalDateTime.parse(item.deadline.dueAt) != null }
    }.getOrDefault(false)
}
