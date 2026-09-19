package com.eduflow.app.data

import android.content.pm.ApplicationInfo
import com.eduflow.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BackupPolicyConfigTest {
    @Test fun appDisablesAutomaticBackupAndSuppliesBothRuleFormats() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertEquals("full-backup-content", rootElement(R.xml.backup_rules))
        assertEquals("data-extraction-rules", rootElement(R.xml.data_extraction_rules))
    }

    @Test fun modernRulesExcludeCloudAndDeviceTransferData() {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(R.xml.data_extraction_rules)
        var cloud = false
        var device = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.name == "cloud-backup") cloud = true
                if (parser.name == "device-transfer") device = true
            }
            parser.next()
        }
        parser.close()
        assertTrue(cloud)
        assertTrue(device)
    }

    private fun rootElement(resource: Int): String {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(resource)
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()
        val root = parser.name
        parser.close()
        return root
    }
}
