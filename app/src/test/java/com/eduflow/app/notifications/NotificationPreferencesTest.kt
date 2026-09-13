package com.eduflow.app.notifications

import android.app.Application
import com.eduflow.app.data.NotificationSettingsRepository
import com.eduflow.app.data.TaskDuePresetRepository
import com.eduflow.app.data.local.*
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class NotificationPreferencesTest {
    @Test fun savedSecondNextStrategyReloadsWithoutReinterpretingLegacyOrForeignTasks() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repository = TaskDuePresetRepository(context)
        val task = Task(id=1,title="Homework",subjectId=1,type=TaskType.HOMEWORK,priority=TaskPriority.MUST,
            intendedDueLessonInstanceId=2,dueLessonInstanceId=2,createdAt=LocalDateTime.of(2026,9,21,8,0))
        assertEquals(1,repository.ordinal(task).first())
        repository.record(task,2)
        assertEquals(2,TaskDuePresetRepository(context).ordinal(task).first())
        assertEquals(1,repository.ordinal(task.copy(createdAt=task.createdAt.plusDays(1))).first())
        assertEquals(1,repository.ordinal(task.copy(intendedDueLessonInstanceId=3)).first())
        repository.clear()
    }
    @Test fun dataStorePersistsMultiValuesDaysAndQuietTimesAcrossRepositoryReload() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val first = NotificationSettingsRepository(context)
        first.clear()
        first.update { it.copy(taskRemindersEnabled=true,taskLeadMinutes=setOf(30,1440),taskLeadsConfigured=true,dailySummaryEnabled=true,
            summaryHour=6,summaryMinute=30,summaryDays=setOf(1,3,5),quietEnabled=true,quietStartMinute=1350,quietEndMinute=420,schoolLeadMinutes=25,privateLeadMinutes=180) }
        val second = NotificationSettingsRepository(context)
        assertEquals(first.snapshot(),second.snapshot())
        assertEquals(setOf(30,1440),second.snapshot().taskLeadMinutes)
        assertEquals(setOf(1,3,5),second.snapshot().summaryDays)
        assertEquals(1350,second.snapshot().quietStartMinute)
        assertEquals(25,second.snapshot().schoolLeadMinutes);assertEquals(180,second.snapshot().privateLeadMinutes)
        first.clear()
    }
}
