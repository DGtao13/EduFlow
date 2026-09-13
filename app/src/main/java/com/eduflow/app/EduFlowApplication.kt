package com.eduflow.app

import android.app.Application
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.notifications.EduFlowNotifications
import com.eduflow.app.notifications.TaskReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.eduflow.app.data.AppDataSession

class EduFlowApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        EduFlowNotifications.createChannels(this)
        LauncherShortcuts.publish(this)
        val database = EduFlowDatabase.getInstance(this)
        applicationScope.launch { AppDataSession.runtimeMutex.withLock {
            com.eduflow.app.data.local.SchoolLessonSourceRepair.repair(database)
            TaskReminderScheduler(this@EduFlowApplication, database).syncAllPending()
        } }
        applicationScope.launch { com.eduflow.app.notifications.NotificationReconciliation(this@EduFlowApplication, database).observeChanges() }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }
    companion object { lateinit var appContext: android.content.Context; private set }
}
