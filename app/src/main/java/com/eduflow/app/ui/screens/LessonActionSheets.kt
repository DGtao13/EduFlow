@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eduflow.app.R
import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.TimetableEvent
import com.eduflow.app.ui.EventQuickActionKind
import com.eduflow.app.ui.PrivateQuickActionKind
import com.eduflow.app.ui.eventQuickActionKinds
import com.eduflow.app.ui.formatBulgarianDate
import com.eduflow.app.ui.privateQuickActionKinds

@Composable
fun PrivateLessonActionsSheet(
    lesson: LessonInstance,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onAddSimilar: () -> Unit,
    onToggleCancellation: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(lesson.privateLessonName ?: stringResource(R.string.private_lesson), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatBulgarianDate(lesson.actualDate)} · ${lesson.actualStartTime}–${lesson.actualEndTime}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            privateQuickActionKinds(lesson).forEach { action ->
                when (action) {
                    PrivateQuickActionKind.OPEN -> onOpen?.let { PrivateLessonActionRow(stringResource(R.string.open_lesson), Icons.Default.Info, it) }
                    PrivateQuickActionKind.EDIT -> PrivateLessonActionRow(stringResource(R.string.edit), Icons.Default.Edit, onEdit)
                    PrivateQuickActionKind.ADD_SIMILAR -> PrivateLessonActionRow(stringResource(R.string.add_similar_private_lesson), Icons.Default.Add, onAddSimilar)
                    PrivateQuickActionKind.TOGGLE_CANCELLATION -> {
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                        PrivateLessonActionRow(
                            stringResource(if (lesson.cancellationState == CancellationState.CANCELLED) R.string.restore else R.string.cancel_lesson),
                            if (lesson.cancellationState == CancellationState.CANCELLED) Icons.Default.CheckCircle else Icons.Default.Delete,
                            onToggleCancellation,
                            destructive = lesson.cancellationState != CancellationState.CANCELLED
                        )
                    }
                    PrivateQuickActionKind.DELETE -> PrivateLessonActionRow(stringResource(R.string.delete), Icons.Default.Delete, onDelete, destructive = true)
                }
            }
        }
    }
}

@Composable
fun EventActionsSheet(
    event: TimetableEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(event.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatBulgarianDate(event.date)} · ${if (event.isAllDay) stringResource(R.string.school_event_all_day) else event.startTime ?: stringResource(R.string.time_to_be_announced)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            eventQuickActionKinds().forEach { action ->
                when (action) {
                    EventQuickActionKind.EDIT -> PrivateLessonActionRow(stringResource(R.string.edit_event), Icons.Default.Edit, onEdit)
                    EventQuickActionKind.DELETE -> PrivateLessonActionRow(stringResource(R.string.delete), Icons.Default.Delete, onDelete, destructive = true)
                }
            }
        }
    }
}

@Composable
private fun PrivateLessonActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
