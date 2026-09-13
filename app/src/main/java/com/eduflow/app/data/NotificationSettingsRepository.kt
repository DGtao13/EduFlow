package com.eduflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class NotificationSettings(
    val taskRemindersEnabled: Boolean = false, val dailySummaryEnabled: Boolean = false, val summaryHour: Int = 8, val summaryMinute: Int = 0,
    val taskLeadMinutes: Set<Int> = setOf(0, 1440), val overdueEnabled: Boolean = false,
    val schoolEnabled: Boolean = false, val privateEnabled: Boolean = false, val lessonLeadMinutes: Int = 15,
    val summaryDays: Set<Int> = (1..7).toSet(), val quietEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60 + 30, val quietEndMinute: Int = 7 * 60,
    val excludedPrivateOccurrences: Set<String> = emptySet(), val taskLeadsConfigured: Boolean = false,
    val schoolLeadMinutes: Int = lessonLeadMinutes, val privateLeadMinutes: Int = lessonLeadMinutes
)

private val Context.notificationSettingsStore by preferencesDataStore(name = "notification_settings")

class NotificationSettingsRepository(context: Context) {
    private val store = context.applicationContext.notificationSettingsStore
    val settings: Flow<NotificationSettings> = store.data.map(::decode)
    suspend fun update(transform: (NotificationSettings) -> NotificationSettings) { store.edit { encode(it, transform(decode(it))) } }
    suspend fun setTaskReminders(value: Boolean) { store.edit { it[TASK_REMINDERS] = value } }
    suspend fun setDailySummary(value: Boolean) { store.edit { it[DAILY_SUMMARY] = value } }
    suspend fun setSummaryTime(hour: Int, minute: Int) { store.edit { it[SUMMARY_HOUR] = hour; it[SUMMARY_MINUTE] = minute } }
    suspend fun snapshot(): NotificationSettings = settings.first()
    suspend fun clear() { store.edit { it.clear() } }
    suspend fun restore(value: NotificationSettings) { store.edit { it[TASK_REMINDERS] = value.taskRemindersEnabled; it[DAILY_SUMMARY] = value.dailySummaryEnabled; it[SUMMARY_HOUR] = value.summaryHour; it[SUMMARY_MINUTE] = value.summaryMinute; it.remove(EXCLUDED_PRIVATE) } }
    suspend fun suppressDeletedPrivate(lessons: List<com.eduflow.app.data.local.LessonInstance>) = update { s -> s.copy(excludedPrivateOccurrences = s.excludedPrivateOccurrences + lessons.map { "${it.id}:${it.actualDate}" }) }
    suspend fun allowPrivate(id: Long) = update { s -> s.copy(excludedPrivateOccurrences = s.excludedPrivateOccurrences.filterNot { it.startsWith("$id:") }.toSet()) }
    companion object {
        private val LEADS = stringSetPreferencesKey("task_lead_minutes_v1")
        private val OVERDUE = booleanPreferencesKey("overdue_enabled")
        private val SCHOOL = booleanPreferencesKey("school_enabled")
        private val PRIVATE = booleanPreferencesKey("private_enabled")
        private val LESSON_LEAD = intPreferencesKey("lesson_lead_minutes")
        private val SCHOOL_LEAD = intPreferencesKey("school_lead_minutes_v1")
        private val PRIVATE_LEAD = intPreferencesKey("private_lead_minutes_v1")
        private val DAYS = stringSetPreferencesKey("summary_days_v1")
        private val QUIET = booleanPreferencesKey("quiet_enabled")
        private val QUIET_START = intPreferencesKey("quiet_start_minute")
        private val QUIET_END = intPreferencesKey("quiet_end_minute")
        private val EXCLUDED_PRIVATE = stringSetPreferencesKey("excluded_deleted_private_v1")
        internal fun decode(p: Preferences) = NotificationSettings(
            p[TASK_REMINDERS] ?: false, p[DAILY_SUMMARY] ?: false, (p[SUMMARY_HOUR] ?: 8).coerceIn(0,23), (p[SUMMARY_MINUTE] ?: 0).coerceIn(0,59),
            p[LEADS]?.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in setOf(0,30,60,1440) } }?.toSet() ?: setOf(0,1440),
            p[OVERDUE] ?: false, p[SCHOOL] ?: false, p[PRIVATE] ?: false, (p[LESSON_LEAD] ?: 15).takeIf { it in setOf(10,15,30,60) } ?: 15,
            p[DAYS]?.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 1..7 } }?.toSet() ?: (1..7).toSet(),
            p[QUIET] ?: false, (p[QUIET_START] ?: 1350).coerceIn(0,1439), (p[QUIET_END] ?: 420).coerceIn(0,1439), p[EXCLUDED_PRIVATE] ?: emptySet(), p[LEADS] != null,
            (p[SCHOOL_LEAD] ?: p[LESSON_LEAD] ?: 15).takeIf(LessonReminderLead::valid) ?: 15,
            (p[PRIVATE_LEAD] ?: p[LESSON_LEAD] ?: 15).takeIf(LessonReminderLead::valid) ?: 15)
        internal fun encode(p: MutablePreferences, s: NotificationSettings) {
            require(LessonReminderLead.valid(s.schoolLeadMinutes) && LessonReminderLead.valid(s.privateLeadMinutes))
            p[TASK_REMINDERS] = s.taskRemindersEnabled; p[DAILY_SUMMARY] = s.dailySummaryEnabled
            p[SUMMARY_HOUR] = s.summaryHour; p[SUMMARY_MINUTE] = s.summaryMinute
            if (s.taskLeadsConfigured) p[LEADS] = s.taskLeadMinutes.map { it.toString() }.toSet() else p.remove(LEADS)
            p[OVERDUE] = s.overdueEnabled
            p[SCHOOL] = s.schoolEnabled; p[PRIVATE] = s.privateEnabled; p[LESSON_LEAD] = s.lessonLeadMinutes
            p[SCHOOL_LEAD] = s.schoolLeadMinutes; p[PRIVATE_LEAD] = s.privateLeadMinutes
            p[DAYS] = s.summaryDays.map { it.toString() }.toSet(); p[QUIET] = s.quietEnabled
            p[QUIET_START] = s.quietStartMinute; p[QUIET_END] = s.quietEndMinute
            p[EXCLUDED_PRIVATE] = s.excludedPrivateOccurrences
        }
        private val TASK_REMINDERS = booleanPreferencesKey("task_reminders")
        private val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        private val SUMMARY_HOUR = intPreferencesKey("summary_hour")
        private val SUMMARY_MINUTE = intPreferencesKey("summary_minute")
    }
}
