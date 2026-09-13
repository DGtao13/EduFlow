package com.eduflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.eduflow.app.data.local.EduFlowDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardingStore by preferencesDataStore(name = "onboarding")
private val onboardingCompleteKey = booleanPreferencesKey("complete")

/** Keeps first-use presentation state outside Room and leaves existing data untouched. */
class OnboardingRepository(private val context: Context) {
    val complete: Flow<Boolean> = context.onboardingStore.data.map { it[onboardingCompleteKey] == true }

    suspend fun markComplete() { context.onboardingStore.edit { it[onboardingCompleteKey] = true } }

    suspend fun shouldAutoShow(database: EduFlowDatabase): Boolean {
        val complete = context.onboardingStore.data.map { it[onboardingCompleteKey] == true }.firstValue()
        if (complete) return false
        return !hasExistingUserData(database)
    }

    private suspend fun hasExistingUserData(database: EduFlowDatabase): Boolean =
        database.subjectDao().getAll().isNotEmpty() ||
            database.scheduleTemplateDao().getAll().isNotEmpty() ||
            database.scheduleSlotDao().getAll().isNotEmpty() ||
            database.lessonInstanceDao().getAll().isNotEmpty() ||
            database.taskDao().getAll().isNotEmpty() ||
            database.recurringPrivateLessonDao().getAll().isNotEmpty()
}

internal fun onboardingShouldAutoShow(complete: Boolean, hasExistingUserData: Boolean) = !complete && !hasExistingUserData

private suspend fun <T> Flow<T>.firstValue(): T = first()
