@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.eduflow.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.Subject
import com.eduflow.app.ui.viewmodel.DatabaseViewModelFactory
import com.eduflow.app.ui.viewmodel.SubjectViewModel
import com.eduflow.app.ui.EduFlowRootTopAppBar
import androidx.navigation.NavController

private val subjectColors = listOf(
    0xFF1565C0L, // blue
    0xFF3949ABL, // indigo
    0xFF6A1B9AL, // purple
    0xFFD81B60L, // pink
    0xFFC62828L, // red
    0xFFEF6C00L, // orange
    0xFFF9C74FL, // amber
    0xFF9CCC65L, // lime
    0xFF2E7D32L, // green
    0xFF00838FL  // teal
)

@Composable
fun SubjectsManagementScreen(database: EduFlowDatabase, navController: NavController) {
    val viewModel: SubjectViewModel = viewModel(factory = DatabaseViewModelFactory(database))
    val subjects by viewModel.subjects.collectAsState()
    var editing by remember { mutableStateOf<Subject?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subject?>(null) }

    Scaffold(
        topBar = { EduFlowRootTopAppBar(stringResource(R.string.nav_subjects)) },
        floatingActionButton = {
            if (subjects.isNotEmpty()) {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.add_subject))
            }
            }
        }
    ) { padding ->
        if (subjects.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.subjects_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.subjects_empty_message), Modifier.padding(top = 8.dp))
                Button(onClick = { editing = null; showEditor = true }, Modifier.padding(top = 20.dp)) {
                    Icon(Icons.Default.Add, null)
                    Text(stringResource(R.string.add_subject), Modifier.padding(start = 8.dp))
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(subjects, key = { it.id }) { subject ->
                    var expanded by remember(subject.id) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().clickable { navController.navigate("subject/${subject.id}") }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(Modifier.size(width = 5.dp, height = 42.dp).background(Color(subject.color)))
                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
                            Text(subject.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            val details = listOfNotNull(subject.shortName, subject.defaultTeacher, subject.defaultRoom).joinToString(" · ")
                            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Box { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.subject_actions)) }; DropdownMenu(expanded, { expanded = false }) { DropdownMenuItem(text = { Text(stringResource(R.string.edit_subject)) }, onClick = { expanded = false; editing = subject; showEditor = true }); DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { expanded = false; deleting = subject }) } }
                    }
                }
            }
        }
    }

    if (showEditor) {
        SubjectEditorDialog(editing, onDismiss = { showEditor = false }, onSave = { viewModel.save(it); showEditor = false })
    }
    deleting?.let { subject ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_subject_question, subject.name)) },
            text = { Text(stringResource(R.string.subject_delete_message)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(subject); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SubjectEditorDialog(subject: Subject?, onDismiss: () -> Unit, onSave: (Subject) -> Unit) {
    var name by remember(subject) { mutableStateOf(subject?.name.orEmpty()) }
    var shortName by remember(subject) { mutableStateOf(subject?.shortName.orEmpty()) }
    var teacher by remember(subject) { mutableStateOf(subject?.defaultTeacher.orEmpty()) }
    var room by remember(subject) { mutableStateOf(subject?.defaultRoom.orEmpty()) }
    var color by remember(subject) { mutableStateOf(subject?.color ?: subjectColors.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    val requiredNameError = stringResource(R.string.name_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (subject == null) R.string.add_subject else R.string.edit_subject)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, isError = error != null, supportingText = { error?.let { Text(it) } })
                OutlinedTextField(shortName, { shortName = it }, label = { Text(stringResource(R.string.short_name_optional)) }, singleLine = true)
                OutlinedTextField(teacher, { teacher = it }, label = { Text(stringResource(R.string.default_teacher_optional)) }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text(stringResource(R.string.default_room_optional)) }, singleLine = true)
                Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 5,
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subjectColors.forEach { option ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { color = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(option))
                                    .border(
                                        width = if (color == option) 3.dp else 1.dp,
                                        color = if (color == option) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (color == option) {
                                    Text("✓", color = if (option == 0xFFF9C74FL || option == 0xFF9CCC65L) Color.Black else Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (name.isBlank()) error = requiredNameError else onSave(Subject(subject?.id ?: 0, name.trim(), shortName.trim().ifBlank { null }, teacher.trim().ifBlank { null }, room.trim().ifBlank { null }, color))
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
