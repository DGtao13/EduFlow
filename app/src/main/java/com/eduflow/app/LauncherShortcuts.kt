package com.eduflow.app

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.eduflow.app.notifications.EduFlowNotifications

/** Publishes explicit same-package shortcuts, so debug never opens the production app. */
object LauncherShortcuts {
    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        fun shortcut(id: String, labelRes: Int, iconRes: Int, action: String, rank: Int) = ShortcutInfo.Builder(context, id)
            .setShortLabel(context.getString(labelRes))
            .setIcon(Icon.createWithResource(context, iconRes))
            .setRank(rank)
            .setIntent(Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            .build()
        manager.dynamicShortcuts = listOf(
            shortcut("add_task", R.string.add_task, R.drawable.shortcut_add_task, EduFlowNotifications.ACTION_QUICK_ADD, 0),
            shortcut("add_private", R.string.add_private_lesson, R.drawable.shortcut_add_private, "com.eduflow.app.QUICK_ADD_PRIVATE", 1),
            shortcut("add_event", R.string.add_timetable_event, R.drawable.shortcut_add_event, "com.eduflow.app.QUICK_ADD_EVENT", 2),
            shortcut("today_schedule", R.string.today_schedule, R.drawable.shortcut_today, "com.eduflow.app.OPEN_TODAY_SCHEDULE", 3)
        )
    }
}
