package com.eduflow.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import com.eduflow.app.data.local.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class EditorBackIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualNavHostAndSystemDispatcherRespectDirtyCleanRevertedAndPredictiveCommit() {
        val original = Task(title="Task",type=TaskType.HOMEWORK,priority=TaskPriority.MUST,createdAt=LocalDateTime.of(2026,9,21,8,0))
        var persisted = original
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = rememberNavController()
            EditorBackBoundary {
                NavHost(nav,startDestination="root") {
                    composable("root") { androidx.compose.foundation.layout.Column {
                        Button(onClick={nav.navigate("task")}) { Text("Edit") }
                        Button(onClick={nav.navigate("ordinary")}) { Text("Browse") }
                    } }
                    composable("ordinary") { Text("Ordinary screen") }
                    composable("task") {
                        var current by remember { mutableStateOf(original) }
                        var confirm by remember { mutableStateOf(false) }
                        val dirty = TaskDraft.from(current,TaskDueMode.NONE,null,null,emptyList(),emptyList()) != TaskDraft.from(original,TaskDueMode.NONE,null,null,emptyList(),emptyList())
                        val latestDirty = rememberUpdatedState(dirty)
                        fun leave() { if (latestDirty.value) confirm=true else nav.popBackStack() }
                        EditorBackHandler(::leave)
                        androidx.compose.foundation.layout.Column {
                            Button(onClick=::leave) { Text("Toolbar Back") }
                            Button(onClick={current=current.copy(priority=if(current.priority==original.priority) TaskPriority.SHOULD else original.priority)}) { Text("Priority") }
                            if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Незапазени промени")},
                                confirmButton={Button(onClick={persisted=current;nav.popBackStack()}) { Text("Save") }},
                                dismissButton={androidx.compose.foundation.layout.Row { TextButton(onClick={nav.popBackStack()}) { Text("Discard") };TextButton(onClick={confirm=false}) { Text("Stay") } }})
                        }
                    }
                }
            }
        }
        fun open() { compose.onNodeWithText("Edit").performClick(); compose.waitForIdle() }
        fun systemBack() { compose.waitForIdle(); compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }; compose.waitForIdle() }
        compose.onNodeWithText("Browse").performClick(); systemBack()
        compose.runOnIdle { assertEquals("Ordinary navigation must pop", "root",nav.currentDestination?.route) }
        compose.onNodeWithText("Edit").assertExists()
        open(); compose.onNodeWithText("Priority").performClick()
        compose.onNodeWithText("Toolbar Back").performClick()
        compose.onNodeWithText("Незапазени промени").assertExists()
        compose.onNodeWithText("Stay").performClick(); systemBack()
        compose.runOnIdle { assertEquals("Dirty system Back must not pop", "task",nav.currentDestination?.route) }
        compose.onNodeWithText("Незапазени промени").assertExists()
        compose.runOnIdle { assertEquals("task",nav.currentDestination?.route) }
        compose.onNodeWithText("Stay").performClick()
        compose.onNodeWithText("Priority").performClick(); systemBack() // reverted
        compose.onNodeWithText("Edit").assertExists()
        open(); compose.onNodeWithText("Toolbar Back").performClick() // clean toolbar
        compose.onNodeWithText("Edit").assertExists()
        open(); systemBack() // clean hardware/system
        compose.onNodeWithText("Edit").assertExists()
        open(); compose.onNodeWithText("Priority").performClick()
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,0f,0f,BackEventCompat.EDGE_LEFT))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(0f,0f,.5f,BackEventCompat.EDGE_LEFT))
            compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        compose.waitForIdle(); compose.onNodeWithText("Незапазени промени").assertDoesNotExist()
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,0f,0f,BackEventCompat.EDGE_LEFT))
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle(); compose.onNodeWithText("Незапазени промени").assertExists()
        compose.onNodeWithText("Discard").performClick(); assertEquals(original,persisted)
        open(); compose.onNodeWithText("Priority").performClick(); systemBack()
        compose.onNodeWithText("Save").performClick(); assertEquals(TaskPriority.SHOULD,persisted.priority)
    }
}
