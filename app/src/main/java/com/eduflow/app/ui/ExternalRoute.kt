package com.eduflow.app.ui

data class ExternalRouteEvent(val id: Long, val route: String)

object ExternalRouteParser {
    fun routeFor(action: String?, taskId: Long): String? = when (action) {
        "com.eduflow.app.OPEN_TASK" -> taskId.takeIf { it > 0 }?.let { "task/$it/none" }
        "com.eduflow.app.QUICK_ADD" -> "task/new/none"
        "com.eduflow.app.OPEN_TASKS" -> "tasks"
        else -> null
    }
}
