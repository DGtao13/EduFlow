package com.eduflow.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalRouteParserTest {
    @Test fun normalLauncherActionHasNoExternalRoute() {
        assertNull(ExternalRouteParser.routeFor("android.intent.action.MAIN", -1))
    }

    @Test fun quickAddAndTasksRoutesAreParsed() {
        assertEquals("task/new/none", ExternalRouteParser.routeFor("com.eduflow.app.QUICK_ADD", -1))
        assertEquals("tasks", ExternalRouteParser.routeFor("com.eduflow.app.OPEN_TASKS", -1))
    }

    @Test fun taskRouteRequiresPositiveTaskId() {
        assertEquals("task/42/none", ExternalRouteParser.routeFor("com.eduflow.app.OPEN_TASK", 42))
        assertNull(ExternalRouteParser.routeFor("com.eduflow.app.OPEN_TASK", -1))
    }
}
