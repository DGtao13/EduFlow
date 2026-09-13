@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.eduflow.app.R
import com.eduflow.app.data.local.Subject
import androidx.compose.ui.res.stringResource
import com.eduflow.app.ui.theme.EduFlowColorRole
import com.eduflow.app.ui.theme.eduFlowColor

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 16.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = eduFlowColor(EduFlowColorRole.NEUTRAL))
        // Surface lays out multiple direct children on top of one another. A dedicated
        // Column gives every settings row its own measured height.
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    trailing: String? = null,
    action: @Composable (() -> Unit)? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    accent: EduFlowColorRole = EduFlowColorRole.NEUTRAL
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { Icon(it, null, modifier = Modifier.padding(end = 14.dp), tint = if (destructive) MaterialTheme.colorScheme.error else eduFlowColor(accent)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = contentColor, maxLines = 2)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (action != null) action.invoke() else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DestructiveConfirmationButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) { Text(label) }
}

@Composable
fun SelectorField(label: String, value: String, supporting: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SubjectPickerSheet(
    subjects: List<Subject>,
    search: String,
    selectedSubjectId: Long?,
    allowNoSubject: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (Long?) -> Unit,
    emptyLabel: String = stringResource(R.string.no_subject)
) {
    val filtered = subjects.filter { subject ->
        val query = search.trim()
        query.isBlank() || subject.name.contains(query, ignoreCase = true) || subject.shortName?.contains(query, ignoreCase = true) == true
    }
    Text(
        stringResource(if (allowNoSubject) R.string.subject_optional else R.string.subject),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
    androidx.compose.material3.OutlinedTextField(
        value = search,
        onValueChange = onSearch,
        label = { Text(stringResource(R.string.search_subjects)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    )
    if (allowNoSubject) TextButton(onClick = { onSelect(null) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(emptyLabel) }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).padding(horizontal = 20.dp).imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
    ) {
        items(filtered, key = { it.id }) { subject ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(subject.id) }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box(Modifier.size(12.dp).background(Color(subject.color)))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(subject.shortName ?: subject.name, color = if (subject.id == selectedSubjectId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (subject.shortName != null) Text(subject.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
