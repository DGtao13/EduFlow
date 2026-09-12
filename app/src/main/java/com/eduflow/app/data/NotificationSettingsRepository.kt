package com.eduflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class NotificationSettings(val taskRemindersEnabled: Boolean = false, val dailySummaryEnabled: Boolean = false, val summaryHour: Int = 8, val summaryMinute: Int = 0)

private val Context.notificationSettingsStore by preferencesDataStore(name = "notification_settings")

class NotificationSettingsRepository(context: Context) {
    private val store = context.applicationContext.notificationSettingsStore
    val settings: Flow<NotificationSettings> = store.data.map { values -> NotificationSettings(values[TASK_REMINDERS] ?: false, values[DAILY_SUMMARY] ?: false, values[SUMMARY_HOUR] ?: 8, values[SUMMARY_MINUTE] ?: 0) }
    suspend fun setTaskReminders(value: Boolean) { store.edit { it[TASK_REMINDERS] = value } }
    suspend fun setDailySummary(value: Boolean) { store.edit { it[DAILY_SUMMARY] = value } }
    suspend fun setSummaryTime(hour: Int, minute: Int) { store.edit { it[SUMMARY_HOUR] = hour; it[SUMMARY_MINUTE] = minute } }
    suspend fun snapshot(): NotificationSettings = settings.first()
    suspend fun clear() { store.edit { it.clear() } }
    suspend fun restore(value: NotificationSettings) { store.edit { it[TASK_REMINDERS] = value.taskRemindersEnabled; it[DAILY_SUMMARY] = value.dailySummaryEnabled; it[SUMMARY_HOUR] = value.summaryHour; it[SUMMARY_MINUTE] = value.summaryMinute } }
    companion object {
        private val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
        private val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        private val SUMMARY_HOUR = intPreferencesKey("summary_hour")
        private val SUMMARY_MINUTE = intPreferencesKey("summary_minute")
    }
}
