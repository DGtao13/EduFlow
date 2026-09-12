package com.eduflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.eduflow.app.MainActivity
import com.eduflow.app.R
import com.eduflow.app.data.WidgetTaskLogic
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.notifications.EduFlowNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.eduflow.app.data.AppDataSession
import java.time.LocalDateTime

object EduFlowWidgetUpdater {
    fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = android.content.ComponentName(context, EduFlowWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isNotEmpty()) context.sendBroadcast(Intent(context, EduFlowWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
    }
}

class EduFlowWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> render(context, manager, id) }
    }
    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        CoroutineScope(Dispatchers.IO).launch { AppDataSession.runtimeMutex.withLock {
            val db = EduFlowDatabase.getInstance(context); val tasks = WidgetTaskLogic.sorted(db.taskDao().getPending(), LocalDateTime.now()).take(3); val subjects = db.subjectDao().getAll().associateBy { it.id }
            val views = RemoteViews(context.packageName, R.layout.widget_eduflow)
            val rows = listOf(R.id.widget_task_one, R.id.widget_task_two, R.id.widget_task_three)
            rows.forEachIndexed { index, viewId ->
                val task = tasks.getOrNull(index)
                views.setTextViewText(viewId, task?.let { listOfNotNull(it.title, subjects[it.subjectId]?.shortName).joinToString(" · ") } ?: if (index == 0 && tasks.isEmpty()) context.getString(R.string.widget_empty) else "")
                if (task != null) views.setOnClickPendingIntent(viewId, EduFlowNotifications.taskIntent(context, task.id))
            }
            views.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(context, 601, Intent(context, MainActivity::class.java).setAction(EduFlowNotifications.ACTION_OPEN_TASKS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, 602, Intent(context, MainActivity::class.java).setAction(EduFlowNotifications.ACTION_QUICK_ADD), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id, views)
        } }
    }
}
