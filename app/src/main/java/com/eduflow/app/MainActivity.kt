package com.eduflow.app

import android.graphics.Color
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eduflow.app.ui.EduFlowApp
import com.eduflow.app.ui.ExternalRouteEvent
import com.eduflow.app.ui.ExternalRouteParser
import com.eduflow.app.notifications.EduFlowNotifications
import com.eduflow.app.ui.theme.EduFlowTheme

class MainActivity : ComponentActivity() {
    private var requestedRoute by mutableStateOf<ExternalRouteEvent?>(null)
    private var nextExternalEventId = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedRoute = consumeRoute(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            EduFlowTheme {
                EduFlowApp(requestedRoute) { id -> if (requestedRoute?.id == id) requestedRoute = null }
            }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); requestedRoute = consumeRoute(intent) }

    private fun consumeRoute(intent: android.content.Intent?): ExternalRouteEvent? {
        if (intent?.action == android.content.Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data ?: return null
            val displayName = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (!com.eduflow.app.data.TaskShareIntentRouting.shouldOpen(
                    intent.action, intent.type, displayName, uri.path
                )) return null
            intent.action = null
            intent.data = null
            intent.flags = intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION.inv()
            intent.let(::setIntent)
            return ExternalRouteEvent(++nextExternalEventId, "share/import", uri.toString())
        }
        val route = ExternalRouteParser.routeFor(intent?.action, intent?.getLongExtra(EduFlowNotifications.EXTRA_TASK_ID, -1) ?: -1) ?: return null
        intent?.action = null
        intent?.removeExtra(EduFlowNotifications.EXTRA_TASK_ID)
        intent?.let(::setIntent)
        return ExternalRouteEvent(++nextExternalEventId, route)
    }
}
