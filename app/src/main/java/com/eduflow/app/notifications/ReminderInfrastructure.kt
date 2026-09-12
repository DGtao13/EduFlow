package com.eduflow.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eduflow.app.MainActivity
import com.eduflow.app.R
import com.eduflow.app.data.DailySummaryLogic
import com.eduflow.app.data.NotificationSettingsRepository
import com.eduflow.app.data.ReminderLogic
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskReminder
import com.eduflow.app.data.local.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.eduflow.app.data.AppDataSession
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object EduFlowNotifications {
    const val TASK_CHANNEL = "task_reminders"
    const val SUMMARY_CHANNEL = "daily_summary"
    const val EXTRA_TASK_ID = "task_id"
    const val ACTION_OPEN_TASK = "com.eduflow.app.OPEN_TASK"
    const val ACTION_QUICK_ADD = "com.eduflow.app.QUICK_ADD"
    const val ACTION_OPEN_TASKS = "com.eduflow.app.OPEN_TASKS"
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(TASK_CHANNEL, context.getString(R.string.notification_channel_tasks), NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.notification_channel_tasks_description) })
            manager.createNotificationChannel(NotificationChannel(SUMMARY_CHANNEL, context.getString(R.string.notification_channel_summary), NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.notification_channel_summary_description) })
        }
    }
    fun isAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission") fun notify(context: Context, id: Int, notification: android.app.Notification) { if (isAllowed(context)) androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification) }
    fun taskIntent(context: Context, taskId: Long): PendingIntent = PendingIntent.getActivity(context, taskId.toInt(), Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_TASK).putExtra(EXTRA_TASK_ID, taskId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class TaskReminderScheduler(private val context: Context, private val database: EduFlowDatabase = EduFlowDatabase.getInstance(context)) {
    private val workManager = WorkManager.getInstance(context)
    private val settings = NotificationSettingsRepository(context)
    private fun name(id: Long) = "task_reminder_$id"
    suspend fun syncTask(task: Task) {
        val reminders = database.taskReminderDao().getForTask(task.id)
        val enabled = settings.settings.first().taskRemindersEnabled
        reminders.forEach { reminder ->
            workManager.cancelUniqueWork(name(reminder.id))
            if (enabled && ReminderLogic.isSchedulable(task, reminder, LocalDateTime.now())) schedule(task, reminder)
        }
    }
    suspend fun cancelTask(taskId: Long) { database.taskReminderDao().getForTask(taskId).forEach { workManager.cancelUniqueWork(name(it.id)) } }
    fun cancelReminder(id: Long) { workManager.cancelUniqueWork(name(id)) }
    private fun schedule(task: Task, reminder: TaskReminder) {
        val trigger = ReminderLogic.triggerAt(task, reminder) ?: return
        val delay = Duration.between(LocalDateTime.now(), trigger).toMillis()
        if (delay <= 0) return
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(Data.Builder().putLong("reminder", reminder.id).build()).build()
        workManager.enqueueUniqueWork(name(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }
    suspend fun syncAllPending() { database.taskDao().getPending().forEach { syncTask(it) } }
    suspend fun cancelAllPending() { database.taskDao().getPending().forEach { cancelTask(it.id) } }
    fun scheduleDailySummary(hour: Int, minute: Int) {
        val now = LocalDateTime.now(); var target = now.toLocalDate().atTime(hour, minute); if (!target.isAfter(now)) target = target.plusDays(1)
        val delay = Duration.between(now, target).toMinutes().coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS).setInitialDelay(delay, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork("daily_summary", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun cancelDailySummary() { workManager.cancelUniqueWork("daily_summary") }
}

class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AppDataSession.runtimeMutex.withLock {
        val database = EduFlowDatabase.getInstance(applicationContext); val id = inputData.getLong("reminder", -1)
        val reminder = database.taskReminderDao().getById(id) ?: return@withLock Result.success(); val task = database.taskDao().getById(reminder.taskId) ?: return@withLock Result.success()
        if (!NotificationSettingsRepository(applicationContext).settings.first().taskRemindersEnabled || !reminder.enabled || task.status != TaskStatus.PENDING || !EduFlowNotifications.isAllowed(applicationContext)) return@withLock Result.success()
        val complete = PendingIntent.getBroadcast(applicationContext, task.id.toInt(), Intent(applicationContext, TaskCompleteReceiver::class.java).putExtra(EduFlowNotifications.EXTRA_TASK_ID, task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = task.dueAt?.let { applicationContext.getString(R.string.reminder_deadline, it.toLocalDate().toString(), it.toLocalTime().toString()) } ?: applicationContext.getString(R.string.reminder_pending)
        val notification = NotificationCompat.Builder(applicationContext, EduFlowNotifications.TASK_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(task.title).setContentText(body).setContentIntent(EduFlowNotifications.taskIntent(applicationContext, task.id)).setAutoCancel(true).addAction(0, applicationContext.getString(R.string.open), EduFlowNotifications.taskIntent(applicationContext, task.id)).addAction(0, applicationContext.getString(R.string.complete), complete).build()
        EduFlowNotifications.notify(applicationContext, id.toInt(), notification)
        return@withLock Result.success()
    }
}

class TaskCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync(); val taskId = intent.getLongExtra(EduFlowNotifications.EXTRA_TASK_ID, -1)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try { val db = EduFlowDatabase.getInstance(context); db.taskDao().getById(taskId)?.takeIf { it.status == TaskStatus.PENDING }?.let { task -> com.eduflow.app.data.TaskLifecycleRepository(context, db).toggle(task) } } finally { pending.finish() }
        }
    }
}

class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = AppDataSession.runtimeMutex.withLock {
        val settings = NotificationSettingsRepository(applicationContext).settings.first(); if (!settings.dailySummaryEnabled || !EduFlowNotifications.isAllowed(applicationContext)) return@withLock Result.success()
        val counts = DailySummaryLogic.calculate(EduFlowDatabase.getInstance(applicationContext).taskDao().getPending(), LocalDateTime.now())
        if (counts.overdue + counts.dueToday + counts.upcomingMust == 0) return@withLock Result.success()
        val content = applicationContext.getString(R.string.daily_summary_body, counts.overdue, counts.dueToday)
        val openTasks = PendingIntent.getActivity(applicationContext, 991, Intent(applicationContext, MainActivity::class.java).setAction(EduFlowNotifications.ACTION_OPEN_TASKS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        EduFlowNotifications.notify(applicationContext, 991, NotificationCompat.Builder(applicationContext, EduFlowNotifications.SUMMARY_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(applicationContext.getString(R.string.daily_summary)).setContentText(content).setContentIntent(openTasks).setAutoCancel(true).build())
        return@withLock Result.success()
    }
}
