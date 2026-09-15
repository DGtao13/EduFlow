@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.eduflow.app.data.SharedSubjectMapping
import com.eduflow.app.data.TaskRepository
import com.eduflow.app.data.TaskShareEnvelope
import com.eduflow.app.data.TaskShareReadResult
import com.eduflow.app.data.TaskShareRepository
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.Subject
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.ui.formatBulgarianDateWithYear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun TaskShareImportScreen(database: EduFlowDatabase, uri: Uri, navController: NavController) {
    val context = LocalContext.current
    val repository = remember { TaskShareRepository(context, database) }
    var result by remember(uri) { mutableStateOf<TaskShareReadResult?>(null) }
    var subjects by remember { mutableStateOf<List<Subject>>(emptyList()) }
    var selected by remember { mutableStateOf<Subject?>(null) }
    var ambiguous by remember { mutableStateOf(false) }
    var resolved by remember { mutableStateOf<com.eduflow.app.data.local.LessonInstance?>(null) }
    var duplicate by remember { mutableStateOf(false) }
    var choosingSubject by remember { mutableStateOf(false) }
    var confirmDuplicate by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var allowDuplicate by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        result = withContext(Dispatchers.IO) { repository.read(uri) }
        val ok = result as? TaskShareReadResult.Success ?: return@LaunchedEffect
        subjects = withContext(Dispatchers.IO) { database.subjectDao().getAll() }
        val match = SharedSubjectMapping.match(ok.envelope.item.subjectName, subjects)
        selected = match.selected; ambiguous = match.ambiguous
        duplicate = withContext(Dispatchers.IO) { repository.imported(ok.envelope.shareId) != null }
    }
    val envelope = (result as? TaskShareReadResult.Success)?.envelope
    LaunchedEffect(envelope, selected?.id) {
        val item = envelope?.item ?: return@LaunchedEffect
        resolved = if (item.deadline.kind == "NEXT" || item.deadline.kind == "SECOND_NEXT") selected?.let { subject ->
            withContext(Dispatchers.IO) { TaskRepository(database).resolveFutureSession(subject.id, LocalDateTime.now(), null, if (item.deadline.kind == "SECOND_NEXT") 2 else 1) }
        } else null
    }
    Scaffold(topBar = { EduFlowChildTopAppBar("Импортиране на задача", navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }) }) { padding ->
        if (envelope == null) {
            val message = when (result) { null -> "Отваряне на споделения файл…"; TaskShareReadResult.TooLarge -> "Файлът е твърде голям."; TaskShareReadResult.UnsupportedVersion -> "Тази версия на споделения файл не се поддържа."; TaskShareReadResult.UnsupportedItemType -> "Този тип споделен елемент не се поддържа."; else -> "Файлът не е валидна споделена задача от EduFlow." }
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) { Text(message, color = MaterialTheme.colorScheme.error) }
        } else {
            val item = envelope.item
            val requiresSubject = item.deadline.kind == "NEXT" || item.deadline.kind == "SECOND_NEXT"
            val importAllowed = !importing && (!requiresSubject || (selected != null && resolved != null))
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                item.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                Text("Тип: ${item.type} · Приоритет: ${item.priority}")
                if (item.checklist.isNotEmpty()) { Text("Контролен списък", style = MaterialTheme.typography.titleMedium); item.checklist.forEach { Text("• $it") } }
                Text("Предмет от изпращача: ${item.subjectName ?: "Няма"}")
                OutlinedButton(onClick = { choosingSubject = true }, modifier = Modifier.fillMaxWidth()) { Text("Локален предмет: ${selected?.name ?: if (ambiguous) "Изберете предмет (нееднозначно)" else "Не е избран"}") }
                Text("Срок: " + when (item.deadline.kind) {
                    "ABSOLUTE" -> item.deadline.dueAt?.let { formatBulgarianDateWithYear(LocalDateTime.parse(it).toLocalDate()) + " · " + LocalDateTime.parse(it).toLocalTime() }
                    "NEXT" -> "Следващ учебен час"
                    "SECOND_NEXT" -> "Втори следващ учебен час"
                    else -> "Без срок"
                })
                if (requiresSubject) Text(resolved?.let { "Срок при получателя: ${formatBulgarianDateWithYear(it.actualDate)} · ${it.actualStartTime}" } ?: "Изберете предмет с бъдещ учебен час, за да продължите.", color = if (resolved == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Напомняния: използват се настройките на получателя.", style = MaterialTheme.typography.bodySmall)
                if (duplicate) Text("Този споделен файл вече е импортиран. Можете изрично да добавите още едно копие.", color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    if (duplicate) confirmDuplicate = true else {
                        importing = true
                    }
                }, enabled = importAllowed, modifier = Modifier.fillMaxWidth()) { Text("Импортирай") }
                TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Отказ") }
                // The actual write stays in a composition coroutine; parsing and preview above never write data.
                if (importing) LaunchedEffect(importing) {
                    val imported = withContext(Dispatchers.IO) { repository.import(envelope, selected?.id, resolved, allowDuplicate) }
                    importing = false; if (imported != null) success = true else confirmDuplicate = true
                }
            }
            if (choosingSubject) ModalBottomSheet(onDismissRequest = { choosingSubject = false }) { Column(Modifier.padding(20.dp)) {
                TextButton(onClick = { selected = null; ambiguous = false; choosingSubject = false }) { Text("Без предмет") }
                subjects.forEach { subject -> TextButton(onClick = { selected = subject; ambiguous = false; choosingSubject = false }, modifier = Modifier.fillMaxWidth()) { Text(subject.name) } }
            } }
            if (confirmDuplicate) AlertDialog(onDismissRequest = { confirmDuplicate = false }, title = { Text("Вече е импортирано") }, text = { Text("Да се добави ли още едно копие на задачата?") }, confirmButton = { Button(onClick = { allowDuplicate = true; confirmDuplicate = false; importing = true }) { Text("Импортирай копие") } }, dismissButton = { TextButton(onClick = { confirmDuplicate = false }) { Text("Отказ") } })
            if (success) AlertDialog(onDismissRequest = { navController.navigate("tasks") { popUpTo("tasks") { inclusive = false } } }, title = { Text("Задачата е импортирана") }, confirmButton = { Button(onClick = { navController.navigate("tasks") { popUpTo("tasks") { inclusive = false } } }) { Text("Готово") } })
        }
    }
}
