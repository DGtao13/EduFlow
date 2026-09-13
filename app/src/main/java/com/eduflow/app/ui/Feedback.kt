package com.eduflow.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.eduflow.app.R

enum class FeedbackType { BUG, IDEA }

object FeedbackEmail {
    const val recipient = "danielisrusev@gmail.com"
    fun intent(context: Context, type: FeedbackType): Intent {
        val subject = context.getString(if (type == FeedbackType.BUG) R.string.feedback_bug_subject else R.string.feedback_idea_subject)
        val body = context.getString(if (type == FeedbackType.BUG) R.string.feedback_bug_body else R.string.feedback_idea_body, BuildConfigVersion.version(context), "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE)
        // Gmail reliably consumes subject/body from mailto query parameters. Extras remain
        // as a compatibility fallback for other email clients.
        val uri = Uri.parse("mailto:${Uri.encode(recipient)}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        return Intent(Intent.ACTION_SENDTO, uri).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
    }
}

@Composable
fun FeedbackChooser(onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun launch(type: FeedbackType) {
        try { context.startActivity(FeedbackEmail.intent(context, type)); onDismiss() }
        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, context.getString(R.string.feedback_no_email_app), Toast.LENGTH_LONG).show(); onDismiss() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.feedback_title)) },
        text = { Column { FeedbackChoice(context.getString(R.string.feedback_bug), context.getString(R.string.feedback_bug_supporting), Icons.Default.Info) { launch(FeedbackType.BUG) }; FeedbackChoice(context.getString(R.string.feedback_idea), context.getString(R.string.feedback_idea_supporting), Icons.Default.Edit) { launch(FeedbackType.IDEA) } } },
        confirmButton = {}, dismissButton = {}
    )
}

@Composable private fun FeedbackChoice(title: String, supporting: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary); Column(Modifier.padding(start = 12.dp)) { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall); Text(supporting, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

private object BuildConfigVersion {
    fun version(context: Context): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
}
