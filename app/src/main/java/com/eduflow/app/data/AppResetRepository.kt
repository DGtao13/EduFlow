package com.eduflow.app.data

import android.app.NotificationManager
import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.widget.EduFlowWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppDataSession {
    val generation = MutableStateFlow(0)
    val runtimeMutex = Mutex()
}

/** Invoked only after the user confirms the reset UI; external document files are untouched. */
class AppResetRepository(private val context: Context, private val database: EduFlowDatabase = EduFlowDatabase.getInstance(context)) {
    suspend fun reset() = withContext(Dispatchers.IO) {
        AppDataSession.runtimeMutex.withLock {
            NotificationSettingsRepository(context).clear()
            WorkManager.getInstance(context).cancelAllWork().result.get()
            database.withTransaction {
                database.taskReminderDao().clear()
                database.taskChecklistDao().clear()
                database.taskDao().clear()
                database.lessonInstanceDao().clear()
                database.recurringPrivateLessonDao().clear()
                database.scheduleSlotDao().clear()
                database.cycleDao().clearEntries()
                database.cycleDao().clearConfiguration()
                database.timetableEventDao().clear()
                database.dayExceptionDao().clear()
                database.scheduleTemplateDao().clear()
                database.subjectDao().clear()
            }
            AcademicYearSettingsRepository(context).clear()
            context.getSystemService(NotificationManager::class.java).cancelAll()
            EduFlowWidgetUpdater.update(context)
            AppDataSession.generation.value += 1
        }
    }
}
