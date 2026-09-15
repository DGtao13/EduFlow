package com.eduflow.app.data

import android.app.Application
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskShareIntentResolutionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun manifestFilterMatches(uri: String, mime: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime)
        val component = ComponentName(context.packageName, "com.eduflow.app.MainActivity")
        val filters = Shadows.shadowOf(context.packageManager).getIntentFiltersForActivity(component).orEmpty()
        return filters.any { filter ->
            filter.match(intent.action, intent.type, intent.scheme, intent.data, intent.categories, "share-filter-test") >= 0
        }
    }
    @Test fun customMimeIsMatchedEvenWhenProviderUsesOpaqueContentUri() {
        assertTrue(manifestFilterMatches("content://files.example/document/492", TaskShareFormat.MIME_TYPE))
    }

    @Test fun genericMimeIsMatchedOnlyWhenContentUriPathHasEduFlowExtension() {
        assertTrue(manifestFilterMatches("content://files.example/document/primary%3ADownload%2FEduFlow-task.eduflowtask", "application/octet-stream"))
        assertTrue(manifestFilterMatches("content://files.example/document/primary%3ADownload%2FEduFlow-task.eduflowtask", "application/json"))
        assertTrue(manifestFilterMatches("content://files.example/document/primary%3ADownload%2FEduFlow-task.eduflowtask", "text/plain"))
        assertTrue(manifestFilterMatches("content://files.example/document/primary%3ADownload%2FEduFlow-task.eduflowtask", null))
        assertFalse(manifestFilterMatches("content://files.example/document/primary%3ADownload%2Fnotes.json", "application/octet-stream"))
    }

}
