@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.data.TaskLogic
import com.eduflow.app.data.ReminderLogic
import com.eduflow.app.domain.TaskDuePresentation
import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskChecklistItem
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.TaskType
import com.eduflow.app.data.local.TaskReminder
import com.eduflow.app.data.local.TaskReminderKind
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.ui.formatBulgarianDate
import com.eduflow.app.ui.formatBulgarianDateWithYear
import com.eduflow.app.ui.EduFlowRootTopAppBar
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.data.displayLocation
import com.eduflow.app.data.displayTitle
import com.eduflow.app.data.displayTutor
import com.eduflow.app.ui.viewmodel.LessonDetailsFactory
import com.eduflow.app.ui.viewmodel.LessonDetailsViewModel
import com.eduflow.app.ui.viewmodel.TaskEditorFactory
import com.eduflow.app.ui.viewmodel.TaskEditorViewModel
import com.eduflow.app.ui.viewmodel.TasksViewModel
import com.eduflow.app.ui.viewmodel.DatabaseViewModelFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneOffset

private enum class DeadlineMode { NONE, EXACT, NEXT }
private enum class TaskEditorSheet { SUBJECT, TYPE, PRIORITY, DEADLINE, REMINDERS }
private val taskTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun LessonDetailsScreen(database: EduFlowDatabase, lessonId: Long, navController: NavController, focus: String = "") {
    val vm: LessonDetailsViewModel = viewModel(factory = LessonDetailsFactory(database, lessonId))
    val lesson by vm.lesson.collectAsState()
    val subjects by vm.subjects.collectAsState()
    val dueTasks by vm.dueTasks.collectAsState()
    val originTasks by vm.originTasks.collectAsState()
    val block by vm.block.collectAsState()
    val item = lesson ?: return
    var topic by remember(item.id, item.topic) { mutableStateOf(item.topic.orEmpty()) }
    var notes by remember(item.id, item.notes) { mutableStateOf(item.notes.orEmpty()) }
    val subject = subjects.firstOrNull { it.id == item.subjectId }
    var confirmBlockCancellation by remember(item.id) { mutableStateOf(false) }
    val sectionAnchor = remember(item.id, focus) { BringIntoViewRequester() }
    var sectionPlaced by remember(item.id, focus) { mutableStateOf(false) }
    LaunchedEffect(sectionPlaced, focus) {
        if (sectionPlaced && focus in listOf("assigned", "due")) sectionAnchor.bringIntoView()
    }
    Scaffold(topBar = { EduFlowChildTopAppBar(title = stringResource(R.string.lesson_details), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }, actions = { TextButton(onClick = { vm.toggleCancellation(item) }) { Text(stringResource(if (item.cancellationState == CancellationState.CANCELLED) R.string.restore else R.string.cancel_lesson)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.displayTitle(subject, stringResource(R.string.private_lesson), stringResource(R.string.lesson)), style = MaterialTheme.typography.headlineSmall)
            if (item.kind == LessonKind.PRIVATE) Text(stringResource(R.string.private_lesson), color = MaterialTheme.colorScheme.primary)
            Text("${formatBulgarianDate(item.actualDate)} · ${item.actualStartTime}–${item.actualEndTime}")
            item.displayLocation()?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item.displayTutor()?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (item.cancellationState == CancellationState.CANCELLED) Text(stringResource(R.string.cancelled), color = MaterialTheme.colorScheme.error)
            block?.takeIf { it.isMultiPeriod }?.let { resolved ->
                Text(stringResource(R.string.lesson_block_hint, resolved.startTime.toString(), resolved.endTime.toString(), resolved.lessons.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { if (item.cancellationState == CancellationState.CANCELLED) vm.setBlockCancellation(item, CancellationState.ACTIVE) else confirmBlockCancellation = true }) {
                    Text(stringResource(if (item.cancellationState == CancellationState.CANCELLED) R.string.restore_whole_block else R.string.cancel_whole_block))
                }
            }
            SectionTitle(stringResource(R.string.what_we_did))
            OutlinedTextField(topic, { topic = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            SectionTitle(stringResource(R.string.notes))
            OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Button(onClick = { vm.saveLesson(item.copy(topic = topic.trim().ifBlank { null }, notes = notes.trim().ifBlank { null })) }) { Text(stringResource(R.string.save_lesson)) }
            Row(Modifier.fillMaxWidth().then(if (focus == "assigned") Modifier.bringIntoViewRequester(sectionAnchor).onGloballyPositioned { sectionPlaced = true } else Modifier), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.assigned_from_lesson))
                TextButton(onClick = { navController.navigate("task/new/$lessonId") }) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add_task)) }
            }
            LessonTaskList(originTasks, subjects.associateBy { it.id }, vm::toggleTask) { navController.navigate("task/${it}/none") }
            Column(Modifier.then(if (focus == "due") Modifier.bringIntoViewRequester(sectionAnchor).onGloballyPositioned { sectionPlaced = true } else Modifier)) { SectionTitle(stringResource(R.string.due_for_lesson)) }
            LessonTaskList(dueTasks, subjects.associateBy { it.id }, vm::toggleTask) { navController.navigate("task/${it}/none") }
        }
    }
    if (confirmBlockCancellation) AlertDialog(
        onDismissRequest = { confirmBlockCancellation = false },
        title = { Text(stringResource(R.string.cancel_whole_block)) },
        text = { Text(stringResource(R.string.cancel_whole_block_question, item.displayTitle(subject, stringResource(R.string.private_lesson), stringResource(R.string.lesson)), block?.startTime.toString(), block?.endTime.toString())) },
        confirmButton = { TextButton(onClick = { vm.setBlockCancellation(item, CancellationState.CANCELLED); confirmBlockCancellation = false }) { Text(stringResource(R.string.cancel_whole_block)) } },
        dismissButton = { TextButton(onClick = { confirmBlockCancellation = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable private fun SectionTitle(value: String) { Text(value, style = MaterialTheme.typography.titleMedium) }

@Composable
private fun LessonTaskList(tasks: List<Task>, subjects: Map<Long, com.eduflow.app.data.local.Subject>, onToggle: (Task) -> Unit, onOpen: (Long) -> Unit) {
    if (tasks.isEmpty()) Text(stringResource(R.string.no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(TaskStatus.PENDING, TaskStatus.COMPLETED).forEach { status ->
        val group = tasks.filter { it.status == status }
        if (group.isNotEmpty()) {
            Text(stringResource(if (status == TaskStatus.PENDING) R.string.pending_tasks else R.string.completed_tasks), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            group.forEach { TaskRow(it, subjects[it.subjectId]?.shortName ?: subjects[it.subjectId]?.name, onToggle, { onOpen(it.id) }) }
        }
    }
}

@Composable
fun TasksScreen(database: EduFlowDatabase, navController: NavController) {
    val vm: TasksViewModel = viewModel(factory = DatabaseViewModelFactory(database))
    val pending by vm.pending.collectAsState(); val completed by vm.completed.collectAsState(); val subjects by vm.subjects.collectAsState(); val checklist by vm.checklist.collectAsState(); val lessons by vm.lessons.collectAsState(); val scheduleSlots by vm.scheduleSlots.collectAsState()
    var completedTab by remember { mutableStateOf(false) }
    val subjectMap = remember(subjects) { subjects.associateBy { it.id } }
    val checklistByTask = remember(checklist) { checklist.groupBy { it.taskId } }
    val dueRanges = remember(lessons, scheduleSlots) { TaskDuePresentation.rangesByLessonId(lessons, scheduleSlots) }
    Scaffold(topBar = { EduFlowRootTopAppBar(stringResource(R.string.nav_tasks)) }, floatingActionButton = { androidx.compose.material3.FloatingActionButton(onClick = { navController.navigate("task/new/none") }) { Icon(Icons.Default.Add, stringResource(R.string.add_task)) } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 88.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !completedTab, onClick = { completedTab = false }, label = { Text(stringResource(R.string.pending_tasks)) })
                FilterChip(selected = completedTab, onClick = { completedTab = true }, label = { Text(stringResource(R.string.completed_tasks)) })
            } }
            val tasks = if (completedTab) completed else TaskLogic.sortedPending(pending, LocalDateTime.now())
            if (!completedTab) {
                if (tasks.isEmpty()) item { Text(stringResource(R.string.no_pending_tasks), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else TaskPriority.entries.forEach { priority ->
                    val group = tasks.filter { it.priority == priority }
                    if (group.isNotEmpty()) { item { SectionTitle(priorityLabel(priority)) }; items(group, key = { it.id }) { task -> TaskRow(task, subjectMap[task.subjectId]?.shortName ?: subjectMap[task.subjectId]?.name, vm::toggleTask, { navController.navigate("task/${task.id}/none") }, checklistByTask[task.id].orEmpty(), dueRanges[task.dueLessonInstanceId]) } }
                }
            } else if (tasks.isEmpty()) item { Text(stringResource(R.string.no_completed_tasks), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else items(tasks, key = { it.id }) { task -> TaskRow(task, subjectMap[task.subjectId]?.shortName ?: subjectMap[task.subjectId]?.name, vm::toggleTask, { navController.navigate("task/${task.id}/none") }, checklistByTask[task.id].orEmpty(), dueRanges[task.dueLessonInstanceId]) }
        }
    }
}

@Composable
private fun TaskRow(task: Task, subject: String?, onToggle: (Task) -> Unit, onOpen: () -> Unit, checklist: List<TaskChecklistItem> = emptyList(), dueRange: com.eduflow.app.domain.TaskDueTimeRange? = null) {
    val overdue = TaskLogic.isOverdue(task, LocalDateTime.now())
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(task.status == TaskStatus.COMPLETED, { onToggle(task) })
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(subject, typeLabel(task.type)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            task.dueAt?.let { dueAt ->
                val displayedTime = dueRange?.let { "${it.start.format(taskTimeFormat)}–${it.end.format(taskTimeFormat)}" } ?: dueAt.toLocalTime().format(taskTimeFormat)
                Text("${formatBulgarianDate(dueAt.toLocalDate())} · $displayedTime", style = MaterialTheme.typography.bodySmall, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (checklist.isNotEmpty()) Text(stringResource(R.string.checklist_progress, checklist.count { it.isCompleted }, checklist.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (overdue) Text(stringResource(R.string.overdue), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}

@Composable
fun TaskEditorScreen(database: EduFlowDatabase, taskId: Long?, originId: Long?, navController: NavController, initialSubjectId: Long? = null) {
    val vm: TaskEditorViewModel = viewModel(factory = TaskEditorFactory(database, taskId, originId))
    val stored by vm.task.collectAsState(); val subjects by vm.subjects.collectAsState(); val origin by vm.originLesson.collectAsState(); val checklist by vm.checklist.collectAsState(); val reminders by vm.reminders.collectAsState(); val error by vm.error.collectAsState()
    val base = stored ?: Task(title = "", subjectId = origin?.subjectId ?: initialSubjectId, originatingLessonInstanceId = originId, type = TaskType.HOMEWORK, priority = TaskPriority.MUST, createdAt = LocalDateTime.now())
    var title by remember(base.id) { mutableStateOf(base.title) }; var description by remember(base.id) { mutableStateOf(base.description.orEmpty()) }; var subjectId by remember(base.id, origin?.subjectId) { mutableStateOf(base.subjectId) }
    var type by remember(base.id) { mutableStateOf(base.type) }; var priority by remember(base.id) { mutableStateOf(base.priority) }
    var mode by remember(base.id) { mutableStateOf(if (base.dueLessonInstanceId != null) DeadlineMode.NEXT else if (base.dueAt != null) DeadlineMode.EXACT else DeadlineMode.NONE) }
    var deadlineChanged by remember(base.id) { mutableStateOf(false) }
    var exactDate by remember(base.id) { mutableStateOf(base.dueAt?.toLocalDate()) }; var exactTime by remember(base.id) { mutableStateOf(base.dueAt?.toLocalTime()) }
    var draftItem by remember { mutableStateOf("") }; var localItems by remember(checklist) { mutableStateOf(checklist) }
    var localReminders by remember(reminders) { mutableStateOf(reminders) }
    var showExactDatePicker by remember(base.id) { mutableStateOf(false) }; var showExactTimePicker by remember(base.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selector by remember { mutableStateOf<TaskEditorSheet?>(null) }
    var subjectSearch by remember { mutableStateOf("") }
    LaunchedEffect(taskId, mode, priority) {
        if (taskId == null && mode != DeadlineMode.NONE && localReminders.isEmpty()) localReminders = ReminderLogic.suggestedKinds(priority).map { TaskReminder(taskId = base.id, kind = it, createdAt = LocalDateTime.now()) }
    }
    Scaffold(topBar = { EduFlowChildTopAppBar(title = if (taskId == null) stringResource(R.string.add_task) else stringResource(R.string.edit), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.task_title)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.task_description)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            SelectorField(stringResource(R.string.subject_optional), subjects.firstOrNull { it.id == subjectId }?.let { it.shortName ?: it.name } ?: stringResource(R.string.no_subject)) { selector = TaskEditorSheet.SUBJECT }
            SelectorField(stringResource(R.string.task_type), typeLabel(type)) { selector = TaskEditorSheet.TYPE }
            SelectorField(stringResource(R.string.priority), priorityLabel(priority)) { selector = TaskEditorSheet.PRIORITY }
            SelectorField(stringResource(R.string.deadline), when (mode) { DeadlineMode.NONE -> stringResource(R.string.no_deadline); DeadlineMode.EXACT -> stringResource(R.string.exact_deadline); DeadlineMode.NEXT -> stringResource(R.string.next_lesson_deadline) }) { selector = TaskEditorSheet.DEADLINE }
            if (mode == DeadlineMode.EXACT) {
                SelectorField(stringResource(R.string.deadline_date), exactDate?.let(::formatBulgarianDateWithYear) ?: stringResource(R.string.choose_date)) { showExactDatePicker = true; vm.error.value = null }
                SelectorField(stringResource(R.string.deadline_time), exactTime?.format(taskTimeFormat) ?: stringResource(R.string.choose_time)) { showExactTimePicker = true; vm.error.value = null }
            }
            SelectorField(stringResource(R.string.reminders), when { localReminders.isEmpty() -> stringResource(R.string.none); localReminders.size == 1 -> reminderLabel(localReminders.first().kind); else -> stringResource(R.string.reminder_count, localReminders.size) }) { selector = TaskEditorSheet.REMINDERS }
            SectionTitle(stringResource(R.string.checklist)); localItems.forEachIndexed { index, item -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(item.isCompleted, { localItems = localItems.toMutableList().also { it[index] = item.copy(isCompleted = it[index].isCompleted.not()) } }); OutlinedTextField(item.text, { text -> localItems = localItems.toMutableList().also { it[index] = item.copy(text = text) } }, modifier = Modifier.weight(1f)); IconButton(onClick = { vm.deleteChecklist(item); localItems = localItems - item }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } }
            Row { OutlinedTextField(draftItem, { draftItem = it }, label = { Text(stringResource(R.string.add_checklist_item)) }, modifier = Modifier.weight(1f)); TextButton(onClick = { if (draftItem.isNotBlank()) { localItems = localItems + TaskChecklistItem(taskId = base.id, text = draftItem.trim(), position = localItems.size); draftItem = "" } }) { Text(stringResource(R.string.add)) } }
            error?.let { Text(when (it) { "NO_NEXT" -> stringResource(R.string.no_next_lesson); "TITLE" -> stringResource(R.string.task_required); else -> stringResource(R.string.invalid_deadline) }, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val due = if (mode == DeadlineMode.EXACT) exactDate?.let { date -> exactTime?.let { time -> LocalDateTime.of(date, time) } } else null
                if (mode == DeadlineMode.EXACT && due == null) { vm.error.value = "INVALID" } else if (title.isBlank()) vm.error.value = "TITLE" else vm.save(base.copy(title = title.trim(), description = description.trim().ifBlank { null }, subjectId = subjectId, type = type, priority = priority, dueAt = when (mode) { DeadlineMode.EXACT -> due; DeadlineMode.NEXT -> base.dueAt; DeadlineMode.NONE -> null }, dueLessonInstanceId = if (mode == DeadlineMode.NEXT) base.dueLessonInstanceId else null, intendedDueLessonInstanceId = if (deadlineChanged) null else base.intendedDueLessonInstanceId), localItems, localReminders, mode == DeadlineMode.NEXT) { navController.popBackStack() }
            }) { Text(stringResource(R.string.save)) }
            stored?.let { TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_task_question)) },
        text = { Text(stringResource(R.string.delete_task_message)) },
        confirmButton = { TextButton(onClick = { stored?.let { vm.deleteTask(it) { navController.popBackStack() } } }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
    )
    selector?.let { sheet ->
        ModalBottomSheet(onDismissRequest = { selector = null }) {
            when (sheet) {
                TaskEditorSheet.SUBJECT -> SubjectPickerSheet(subjects, subjectSearch, subjectId, true, { subjectSearch = it }, { subjectId = it; selector = null })
                TaskEditorSheet.TYPE -> OptionSheet(stringResource(R.string.task_type), TaskType.entries.toList(), typeLabel(type), { typeLabel(it) }) { type = it; selector = null }
                TaskEditorSheet.PRIORITY -> OptionSheet(stringResource(R.string.priority), TaskPriority.entries.toList(), priorityLabel(priority), { priorityLabel(it) }) { priority = it; selector = null }
                TaskEditorSheet.DEADLINE -> DeadlinePickerSheet(subjectId != null) { mode = it; deadlineChanged = true; selector = null }
                TaskEditorSheet.REMINDERS -> ReminderPickerSheet(localReminders, mode != DeadlineMode.NONE, { localReminders = it }, base.id)
            }
        }
    }
    if (showExactDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = exactDate?.toPickerMillis())
        DatePickerDialog(onDismissRequest = { showExactDatePicker = false }, confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { exactDate = it.toLocalDateFromPicker() }; showExactDatePicker = false }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { showExactDatePicker = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
    }
    if (showExactTimePicker) {
        val current = exactTime ?: LocalTime.now().withSecond(0).withNano(0)
        val picker = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { showExactTimePicker = false }, title = { Text(stringResource(R.string.deadline_time)) }, text = { TimePicker(picker) }, confirmButton = { TextButton(onClick = { exactTime = LocalTime.of(picker.hour, picker.minute); showExactTimePicker = false }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { showExactTimePicker = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun <T> OptionSheet(title: String, options: List<T>, selected: String, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    options.forEach { option -> TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text(label(option), color = if (label(option) == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }
}

@Composable
private fun DeadlinePickerSheet(hasSubject: Boolean, onSelect: (DeadlineMode) -> Unit) {
    Text(stringResource(R.string.deadline), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    val options: List<DeadlineMode> = if (hasSubject) listOf(DeadlineMode.NONE, DeadlineMode.EXACT, DeadlineMode.NEXT) else listOf(DeadlineMode.NONE, DeadlineMode.EXACT)
    options.forEach { option ->
        TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text(when (option) { DeadlineMode.NONE -> stringResource(R.string.no_deadline); DeadlineMode.EXACT -> stringResource(R.string.exact_deadline); DeadlineMode.NEXT -> stringResource(R.string.next_lesson_deadline) }) }
    }
}

@Composable
private fun ReminderPickerSheet(reminders: List<TaskReminder>, hasDeadline: Boolean, onChanged: (List<TaskReminder>) -> Unit, taskId: Long) {
    var customDate by remember { mutableStateOf(LocalDate.now()) }
    var customTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var customError by remember { mutableStateOf(false) }
    Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    reminders.forEach { reminder -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(reminder.enabled, { checked -> onChanged(reminders.map { if (it == reminder) it.copy(enabled = checked) else it }) }); Text(reminderLabel(reminder.kind), Modifier.weight(1f)); IconButton(onClick = { onChanged(reminders - reminder) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } } }
    if (hasDeadline) listOf(TaskReminderKind.AT_DEADLINE, TaskReminderKind.ONE_HOUR_BEFORE, TaskReminderKind.ONE_DAY_BEFORE, TaskReminderKind.THREE_DAYS_BEFORE).filter { kind -> reminders.none { it.kind == kind } }.forEach { kind -> TextButton(onClick = { onChanged(reminders + TaskReminder(taskId = taskId, kind = kind, createdAt = LocalDateTime.now())) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.add_reminder_format, reminderLabel(kind))) } }
    SelectorField(stringResource(R.string.custom_reminder_date), formatBulgarianDateWithYear(customDate)) { showDatePicker = true; customError = false }
    SelectorField(stringResource(R.string.custom_reminder_time_label), customTime.format(taskTimeFormat)) { showTimePicker = true; customError = false }
    TextButton(onClick = {
        val trigger = LocalDateTime.of(customDate, customTime)
        if (trigger.isAfter(LocalDateTime.now())) onChanged(reminders + TaskReminder(taskId = taskId, kind = TaskReminderKind.CUSTOM, customTriggerAt = trigger, createdAt = LocalDateTime.now()))
        else customError = true
    }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(stringResource(R.string.add)) }
    if (customError) Text(stringResource(R.string.invalid_reminder_time), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = customDate.toPickerMillis())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { customDate = it.toLocalDateFromPicker() }; showDatePicker = false }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
    }
    if (showTimePicker) {
        val picker = rememberTimePickerState(initialHour = customTime.hour, initialMinute = customTime.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { showTimePicker = false }, title = { Text(stringResource(R.string.custom_reminder_time_label)) }, text = { TimePicker(picker) }, confirmButton = { TextButton(onClick = { customTime = LocalTime.of(picker.hour, picker.minute); showTimePicker = false }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDateFromPicker(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable private fun typeLabel(type: TaskType): String = stringResource(when (type) { TaskType.HOMEWORK -> R.string.homework; TaskType.ASSIGNMENT -> R.string.assignment; TaskType.PRESENTATION -> R.string.presentation; TaskType.REVISION -> R.string.revision; TaskType.GENERAL -> R.string.other })
@Composable private fun priorityLabel(priority: TaskPriority): String = stringResource(when (priority) { TaskPriority.MUST -> R.string.must; TaskPriority.SHOULD -> R.string.should; TaskPriority.OPTIONAL -> R.string.optional })
@Composable private fun reminderLabel(kind: TaskReminderKind): String = stringResource(when (kind) { TaskReminderKind.AT_DEADLINE -> R.string.at_deadline; TaskReminderKind.ONE_HOUR_BEFORE -> R.string.one_hour_before; TaskReminderKind.ONE_DAY_BEFORE -> R.string.one_day_before; TaskReminderKind.THREE_DAYS_BEFORE -> R.string.three_days_before; TaskReminderKind.CUSTOM -> R.string.custom_time })
