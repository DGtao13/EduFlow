package com.eduflow.app.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import com.eduflow.app.ui.screens.SetupGuideScreen
import com.eduflow.app.ui.theme.EduFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SetupGuideLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyStepUsesSharedActionAreaAndScrollableContent() {
        compose.setContent {
            EduFlowTheme {
                SetupGuideScreen(rememberNavController(), firstUse = true)
            }
        }

        compose.onNodeWithTag("setup_primary_actions").assertExists()
        compose.onNodeWithTag("setup_scroll_content", useUnmergedTree = true).assertExists()
        compose.onNode(hasScrollAction(), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Напред").assertExists().performClick()
        compose.onNodeWithTag("setup_primary_actions").assertExists()
        compose.onNodeWithText("Напред").assertExists()
    }

    @Test
    fun backReturnsToThePreviousWizardStep() {
        compose.setContent {
            EduFlowTheme {
                SetupGuideScreen(rememberNavController(), firstUse = true)
            }
        }

        compose.onNodeWithText("Напред").performClick()
        compose.onAllNodesWithText("Учебна година").assertCountEquals(2)
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Добре дошъл в EduFlow").assertExists()
    }
}
