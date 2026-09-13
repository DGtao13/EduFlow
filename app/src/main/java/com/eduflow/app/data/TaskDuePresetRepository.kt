package com.eduflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eduflow.app.data.local.Task
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.taskDuePresetStore by preferencesDataStore(name = "task_due_presets")

/** Local editor strategy metadata. The concrete intended/effective deadline stays authoritative in Room/archives. */
class TaskDuePresetRepository(context: Context) {
    private val store = context.applicationContext.taskDuePresetStore
    private fun key(id: Long) = stringPreferencesKey("session_$id")
    private fun identity(task: Task) = "${task.subjectId}|${task.intendedDueLessonInstanceId}|${task.createdAt}|"
    fun ordinal(task: Task): Flow<Int> = store.data.map { values ->
        val value = values[key(task.id)]
        if (value == identity(task) + "2") 2 else 1
    }
    suspend fun record(task: Task, ordinal: Int?) {
        store.edit { values ->
            if (ordinal == null || task.intendedDueLessonInstanceId == null) values.remove(key(task.id))
            else values[key(task.id)] = identity(task) + ordinal
        }
    }
    suspend fun remove(id: Long) { store.edit { it.remove(key(id)) } }
    suspend fun clear() { store.edit { it.clear() } }
}
