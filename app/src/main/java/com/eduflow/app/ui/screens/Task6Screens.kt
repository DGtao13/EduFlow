@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.ui.isValidPrivateLessonTimeRange
import com.eduflow.app.ui.oneOffDefaultDate
import com.eduflow.app.ui.PrivateLessonFormValues
import com.eduflow.app.ui.newOneOffPrivateLesson
import com.eduflow.app.data.TaskLogic
import com.eduflow.app.data.SchoolYear
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.PrivateLessonLocationKind
import com.eduflow.app.data.local.RecurringPrivateLesson
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.ui.formatBulgarianDate
import com.eduflow.app.ui.formatBulgarianDateWithYear
import com.eduflow.app.data.displayTitle
import com.eduflow.app.data.privateLessonNameAfterSubjectSelection
import com.eduflow.app.ui.viewmodel.DatabaseViewModelFactory
import com.eduflow.app.ui.viewmodel.PrivateLessonEditorFactory
import com.eduflow.app.ui.viewmodel.PrivateLessonEditorViewModel
import com.eduflow.app.ui.viewmodel.PrivateLessonsViewModel
import com.eduflow.app.ui.viewmodel.SubjectDetailsFactory
import com.eduflow.app.ui.viewmodel.SubjectDetailsViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val privateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private enum class PrivateLessonSelector { SUBJECT, WEEKDAY, INTERVAL, LOCATION }
private const val defaultOneOffStartHour = 17
private const val defaultOneOffEndHour = 18

@Composable
fun PrivateLessonsScreen(database: EduFlowDatabase, navController: NavController) {
    val vm: PrivateLessonsViewModel = viewModel(factory = DatabaseViewModelFactory(database))
    val lessons by vm.lessons.collectAsState(); val subjects by vm.subjects.collectAsState()
    var deleting by remember { mutableStateOf<RecurringPrivateLesson?>(null) }
    val subjectMap = subjects.associateBy { it.id }
    Scaffold(
        topBar = { EduFlowChildTopAppBar(title = stringResource(R.string.recurring_private_lessons), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Button(onClick = { navController.navigate("private/oneoff") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.add_private_lesson), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { navController.navigate("private/new") }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_recurring_private_lesson))
                }
            }
            if (lessons.isEmpty()) {
                item { Text(stringResource(R.string.no_recurring_private_lessons), modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(lessons, key = { it.id }) { lesson ->
                Row(Modifier.fillMaxWidth().clickable { navController.navigate("private/${lesson.id}") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lesson.privateLessonName ?: subjectMap[lesson.subjectId]?.shortName ?: subjectMap[lesson.subjectId]?.name ?: stringResource(R.string.private_lesson), style = MaterialTheme.typography.titleSmall)
                        Text("${weekdayText(lesson.weekday)} · ${lesson.startTime.format(privateTimeFormat)}–${lesson.endTime.format(privateTimeFormat)} · ${stringResource(if (lesson.intervalWeeks == 2) R.string.every_two_weeks else R.string.every_week)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = lesson.enabled, onCheckedChange = { vm.toggle(lesson) })
                    IconButton(onClick = { deleting = lesson }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_private_lesson_series)) }
                }
            }
        }
    }
    deleting?.let { lesson -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_private_lesson)) }, text = { Text(stringResource(R.string.private_delete_message)) }, confirmButton = { DestructiveConfirmationButton(stringResource(R.string.delete_private_lesson_series), onClick = { vm.delete(lesson); deleting = null }) }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
fun PrivateLessonEditorScreen(database: EduFlowDatabase, id: Long?, oneOff: Boolean, navController: NavController, similarFromId: Long? = null) {
    val vm: PrivateLessonEditorViewModel = viewModel(factory = PrivateLessonEditorFactory(database, id, oneOff))
    val existingRecurring by vm.lesson.collectAsState(); val existingOneOff by vm.oneOffLesson.collectAsState(); val subjects by vm.subjects.collectAsState()
    if (id != null && (if (oneOff) existingOneOff == null else existingRecurring == null)) {
        androidx.compose.material3.CircularProgressIndicator(); return
    }
    val editingOneOff = oneOff && id != null && similarFromId == null
    val recurringBase = existingRecurring
    val oneOffBase = existingOneOff
    val baseId = if (oneOff) oneOffBase?.id else recurringBase?.id
    val initialRecurringStartDate = recurringBase?.startDate ?: LocalDate.now()
    var subjectId by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (oneOff) oneOffBase?.subjectId else recurringBase?.subjectId) }
    var privateName by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (oneOff) oneOffBase?.privateLessonName.orEmpty() else recurringBase?.privateLessonName.orEmpty()) }
    var nameAutoDerived by remember(baseId, oneOff) { mutableStateOf(!oneOff && recurringBase?.privateLessonName.isNullOrBlank()) }
    var weekday by remember(baseId) { mutableStateOf(recurringBase?.weekday ?: initialRecurringStartDate.dayOfWeek.value) }
    var start by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (oneOff) oneOffBase?.let { if (editingOneOff) it.actualStartTime.format(privateTimeFormat) else "" } ?: "" else recurringBase?.startTime?.format(privateTimeFormat) ?: LocalTime.of(defaultOneOffStartHour, 0).format(privateTimeFormat)) }
    var end by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (oneOff) oneOffBase?.let { if (editingOneOff) it.actualEndTime.format(privateTimeFormat) else "" } ?: "" else recurringBase?.endTime?.format(privateTimeFormat) ?: LocalTime.of(defaultOneOffEndHour, 0).format(privateTimeFormat)) }
    var startDate by remember(baseId) { mutableStateOf(initialRecurringStartDate) }
    var oneOffDate by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (editingOneOff) oneOffBase?.actualDate ?: oneOffDefaultDate(null, LocalDate.now()) else oneOffDefaultDate(null, LocalDate.now())) }
    var endDate by remember(baseId) { mutableStateOf(recurringBase?.endDate) }
    var interval by remember(baseId) { mutableStateOf(recurringBase?.intervalWeeks ?: 1) }
    var teacher by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (oneOff) oneOffBase?.actualTeacher.orEmpty() else recurringBase?.teacherOverride.orEmpty()) }
    var locationKind by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (oneOff) oneOffBase?.privateLocationKind ?: PrivateLessonLocationKind.UNSPECIFIED else recurringBase?.privateLocationKind ?: PrivateLessonLocationKind.UNSPECIFIED) }
    var locationText by remember(baseId, oneOff, similarFromId) { mutableStateOf(if (oneOff) oneOffBase?.actualRoom.orEmpty() else recurringBase?.roomOverride.orEmpty()) }
    var enabled by remember(baseId) { mutableStateOf(recurringBase?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deletingSeries by remember { mutableStateOf(false) }
    var selector by remember { mutableStateOf<PrivateLessonSelector?>(null) }
    var subjectSearch by remember { mutableStateOf("") }
    var datePickerTarget by remember { mutableStateOf<PrivateDatePickerTarget?>(null) }
    val draftValues = listOf(subjectId, privateName.trim(), start, end, teacher.trim().ifBlank { null }, locationKind,
        locationText.trim().ifBlank { null }.takeIf { locationKind != PrivateLessonLocationKind.UNSPECIFIED }) +
        if (oneOff) listOf(oneOffDate) else listOf(weekday, startDate, endDate, interval, enabled)
    val originalDraft = remember(baseId, oneOff, similarFromId) { draftValues }
    val leave = protectedEditorExit(draftValues != originalDraft) { navController.popBackStack() }
    com.eduflow.app.ui.EditorBackHandler(leave)
    Scaffold(topBar = { EduFlowChildTopAppBar(title = stringResource(if (editingOneOff) R.string.edit_private_lesson else if (similarFromId != null) R.string.add_similar_private_lesson else if (oneOff) R.string.add_one_off_lesson else if (id == null) R.string.add_recurring_private_lesson else R.string.edit_private_lesson), navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (oneOff) {
                OutlinedTextField(privateName, { privateName = it; nameAutoDerived = false }, label = { Text(stringResource(R.string.private_lesson_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                SelectorField(stringResource(R.string.linked_school_subject), subjects.firstOrNull { it.id == subjectId }?.let { it.shortName ?: it.name } ?: stringResource(R.string.none), supporting = stringResource(R.string.optional)) { selector = PrivateLessonSelector.SUBJECT }
                SelectorField(stringResource(R.string.private_date_picker), stringResource(R.string.private_date_value, formatBulgarianDateWithYear(oneOffDate))) { datePickerTarget = PrivateDatePickerTarget.ONE_OFF; error = null }
                TimeRangeField(runCatching { LocalTime.parse(start) }.getOrNull(), runCatching { LocalTime.parse(end) }.getOrNull()) { s, e -> start = s.format(privateTimeFormat); end = e.format(privateTimeFormat); error = null }
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text(stringResource(R.string.private_tutor)) },
                    supportingText = { Text(stringResource(R.string.optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                SelectorField(stringResource(R.string.private_location), privateLocationKindText(locationKind)) { selector = PrivateLessonSelector.LOCATION }
                if (locationKind != PrivateLessonLocationKind.UNSPECIFIED) OutlinedTextField(locationText, { locationText = it }, label = { Text(stringResource(if (locationKind == PrivateLessonLocationKind.IN_PERSON) R.string.private_location_address else R.string.private_location_platform)) }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(privateName, { privateName = it; nameAutoDerived = false }, label = { Text(stringResource(R.string.private_lesson_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                SelectorField(stringResource(R.string.linked_school_subject), subjects.firstOrNull { it.id == subjectId }?.let { it.shortName ?: it.name } ?: stringResource(R.string.none), supporting = stringResource(R.string.optional)) { selector = PrivateLessonSelector.SUBJECT }
                OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text(stringResource(R.string.private_tutor)) }, supportingText = { Text(stringResource(R.string.optional)) }, modifier = Modifier.fillMaxWidth())
                SelectorField(stringResource(R.string.private_location), privateLocationKindText(locationKind)) { selector = PrivateLessonSelector.LOCATION }
                if (locationKind != PrivateLessonLocationKind.UNSPECIFIED) OutlinedTextField(locationText, { locationText = it }, label = { Text(stringResource(if (locationKind == PrivateLessonLocationKind.IN_PERSON) R.string.private_location_address else R.string.private_location_platform)) }, modifier = Modifier.fillMaxWidth())
                SelectorField(stringResource(R.string.weekday), weekdayText(weekday)) { selector = PrivateLessonSelector.WEEKDAY }
                SelectorField(stringResource(R.string.recurrence), stringResource(if (interval == 2) R.string.every_two_weeks else R.string.every_week)) { selector = PrivateLessonSelector.INTERVAL }
                SelectorField(stringResource(R.string.private_start_date), stringResource(R.string.private_date_value, formatBulgarianDateWithYear(startDate))) { datePickerTarget = PrivateDatePickerTarget.RECURRING_START; error = null }
                SelectorField(stringResource(R.string.private_end_date), endDate?.let { stringResource(R.string.private_date_value, formatBulgarianDateWithYear(it)) } ?: stringResource(R.string.none)) { datePickerTarget = PrivateDatePickerTarget.RECURRING_END; error = null }
                if (endDate != null) TextButton(onClick = { endDate = null }) { Text(stringResource(R.string.private_no_end_date)) }
                TimeRangeField(runCatching { LocalTime.parse(start) }.getOrNull(), runCatching { LocalTime.parse(end) }.getOrNull()) { s, e -> start = s.format(privateTimeFormat); end = e.format(privateTimeFormat); error = null }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text(stringResource(R.string.active), Modifier.padding(start = 8.dp)) }
                if (recurringBase != null) TextButton(
                    onClick = { deletingSeries = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete_private_lesson_series)) }
            }
            error?.let { Text(stringResource(when (it) { "NAME" -> R.string.private_lesson_name_required; "SUBJECT" -> R.string.choose_subject; "TIME_ORDER" -> R.string.private_time_order_invalid; else -> R.string.private_invalid }), color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val parsedStart = runCatching { LocalTime.parse(start, privateTimeFormat) }.getOrNull(); val parsedEnd = runCatching { LocalTime.parse(end, privateTimeFormat) }.getOrNull()
                val values = parsedStart?.let { startTime -> parsedEnd?.let { endTime -> PrivateLessonFormValues(oneOffDate, startTime, endTime, subjectId, privateName.trim(), teacher.trim().ifBlank { null }, if (locationKind == PrivateLessonLocationKind.UNSPECIFIED) null else locationText.trim().ifBlank { null }, locationKind) } }
                if (privateName.isBlank()) error = "NAME" else if (parsedStart == null || parsedEnd == null) error = "INVALID" else if (!isValidPrivateLessonTimeRange(parsedStart, parsedEnd)) error = "TIME_ORDER" else if (oneOff && editingOneOff) vm.updateOneOff(values!!) { navController.popBackStack() } else if (oneOff) vm.createOneOff(newOneOffPrivateLesson(values!!)) { navController.popBackStack() } else {
                    if (endDate != null && endDate!!.isBefore(startDate)) error = "INVALID" else vm.save(RecurringPrivateLesson(id = recurringBase?.id ?: 0, subjectId = subjectId, weekday = weekday, startTime = parsedStart, endTime = parsedEnd, startDate = startDate, endDate = endDate, intervalWeeks = interval, teacherOverride = teacher.trim().ifBlank { null }, roomOverride = if (locationKind == PrivateLessonLocationKind.UNSPECIFIED) null else locationText.trim().ifBlank { null }, privateLessonName = privateName.trim(), enabled = enabled, privateLocationKind = locationKind)) { navController.popBackStack() }
                }
            }) { Text(stringResource(R.string.save)) }
        }
    }
    if (deletingSeries) AlertDialog(
        onDismissRequest = { deletingSeries = false },
        title = { Text(stringResource(R.string.delete_private_lesson)) },
        text = { Text(stringResource(R.string.private_delete_message)) },
        confirmButton = { DestructiveConfirmationButton(stringResource(R.string.delete_private_lesson_series_confirm), onClick = {
            recurringBase?.let { source -> vm.deleteSeries(source) { navController.popBackStack() } }
            deletingSeries = false
        }) },
        dismissButton = { TextButton(onClick = { deletingSeries = false }) { Text(stringResource(R.string.cancel)) } }
    )
    selector?.let { activeSelector ->
        ModalBottomSheet(onDismissRequest = { selector = null }) {
            when (activeSelector) {
                PrivateLessonSelector.SUBJECT -> SubjectPickerSheet(subjects, subjectSearch, subjectId, true, { subjectSearch = it }, { selected ->
                    subjectId = selected
                    val draft = privateLessonNameAfterSubjectSelection(privateName, nameAutoDerived, selected?.let { id -> subjects.firstOrNull { it.id == id }?.name })
                    privateName = draft.value
                    nameAutoDerived = draft.isAutoDerived
                    selector = null
                }, emptyLabel = stringResource(R.string.none))
                PrivateLessonSelector.WEEKDAY -> PrivateChoiceSheet(stringResource(R.string.weekday), (1..7).toList(), weekday, { day -> weekdayText(day) }) { weekday = it; selector = null }
                PrivateLessonSelector.INTERVAL -> PrivateChoiceSheet(stringResource(R.string.recurrence), listOf(1, 2), interval, { stringResource(if (it == 2) R.string.every_two_weeks else R.string.every_week) }) { interval = it; selector = null }
                PrivateLessonSelector.LOCATION -> PrivateChoiceSheet(stringResource(R.string.private_location), PrivateLessonLocationKind.entries.toList(), locationKind, { privateLocationKindText(it) }) { locationKind = it; selector = null }
            }
        }
    }
    datePickerTarget?.let { target ->
        val selectedDate = when (target) { PrivateDatePickerTarget.ONE_OFF -> oneOffDate; PrivateDatePickerTarget.RECURRING_START -> startDate; PrivateDatePickerTarget.RECURRING_END -> endDate ?: startDate }
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { millis -> val picked = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(); when (target) { PrivateDatePickerTarget.ONE_OFF -> oneOffDate = picked; PrivateDatePickerTarget.RECURRING_START -> startDate = picked; PrivateDatePickerTarget.RECURRING_END -> endDate = picked } }; datePickerTarget = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
}

private enum class PrivateDatePickerTarget { ONE_OFF, RECURRING_START, RECURRING_END }

@Composable
private fun <T> PrivateChoiceSheet(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    options.forEach { option ->
        TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(label(option), color = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun SubjectDetailsScreen(database: EduFlowDatabase, subjectId: Long, navController: NavController) {
    val vm: SubjectDetailsViewModel = viewModel(factory = SubjectDetailsFactory(database, subjectId))
    val subject by vm.subject.collectAsState(); val tasks by vm.tasks.collectAsState(); val lessons by vm.lessons.collectAsState()
    val item = subject ?: return
    val academicYear by vm.academicYear.collectAsState()
    var tab by remember { mutableStateOf(0) }
    val visibleLessons = remember(lessons, academicYear) { lessons.filter { it.kind != LessonKind.SCHOOL || SchoolYear.containsSchoolDate(it.actualDate, academicYear) } }
    val now = LocalDate.now(); val upcoming = remember(visibleLessons, now) { visibleLessons.filter { !it.actualDate.isBefore(now) }.sortedWith(compareBy<LessonInstance> { it.actualDate }.thenBy { it.actualStartTime }) }; val history = remember(visibleLessons, now) { visibleLessons.filter { it.actualDate.isBefore(now) }.sortedWith(compareByDescending<LessonInstance> { it.actualDate }.thenByDescending { it.actualStartTime }) }
    Scaffold(topBar = { EduFlowChildTopAppBar(title = item.name, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.foundation.layout.Box(Modifier.padding(end = 8.dp).then(Modifier)) { Text("●", color = Color(item.color)) }; Column { item.shortName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }; listOfNotNull(item.defaultTeacher, item.defaultRoom).joinToString(" · ").takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            item {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.overview), maxLines = 1) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_tab), maxLines = 1) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.nav_tasks), maxLines = 1) })
                }
            }
            when (tab) {
                0 -> { item { Text(stringResource(R.string.next_lesson), style = MaterialTheme.typography.titleMedium) }; upcoming.firstOrNull()?.let { lesson -> item { LessonHistoryRow(lesson) { navController.navigate("lesson/${lesson.id}") } } } ?: item { Text(stringResource(R.string.no_upcoming_lessons)) }; item { Text(stringResource(R.string.pending_tasks), style = MaterialTheme.typography.titleMedium); Text(tasks.count { it.status == TaskStatus.PENDING }.toString()) }; item { Text(stringResource(R.string.lessons_history), style = MaterialTheme.typography.titleMedium) }; items(history.take(3), key = { it.id }) { lesson -> LessonHistoryRow(lesson) { navController.navigate("lesson/${lesson.id}") } } }
                1 -> { if (history.isEmpty()) item { Text(stringResource(R.string.no_lesson_history)) } else items(history, key = { it.id }) { lesson -> LessonHistoryRow(lesson) { navController.navigate("lesson/${lesson.id}") } } }
                else -> { item { OutlinedButton(onClick = { navController.navigate("task/new/subject_$subjectId") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add_task), Modifier.padding(start = 6.dp)) } }; val pending = TaskLogic.sortedPending(tasks.filter { it.status == TaskStatus.PENDING }, java.time.LocalDateTime.now()); items(pending, key = { it.id }) { task -> SubjectTaskRow(task, vm::toggleTask) { navController.navigate("task/${task.id}/none") } }; val completed = tasks.filter { it.status == TaskStatus.COMPLETED }.sortedByDescending { it.completedAt }; if (completed.isNotEmpty()) { item { Text(stringResource(R.string.completed_tasks), style = MaterialTheme.typography.titleMedium) }; items(completed, key = { it.id }) { task -> SubjectTaskRow(task, vm::toggleTask) { navController.navigate("task/${task.id}/none") } } } }
            }
        }
    }
}

@Composable private fun LessonHistoryRow(lesson: LessonInstance, onOpen: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${formatBulgarianDate(lesson.actualDate)} · ${lesson.actualStartTime.format(privateTimeFormat)} · ${lesson.displayTitle(null, stringResource(R.string.private_lesson), stringResource(R.string.school_lesson))}"); lesson.topic?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }; if (lesson.notes != null) Text(stringResource(R.string.notes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun SubjectTaskRow(task: Task, toggle: (Task) -> Unit, open: () -> Unit) = Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(task.status == TaskStatus.COMPLETED, { toggle(task) }); Text(task.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
@Composable private fun weekdayText(day: Int): String = stringResource(when (day) { 1 -> R.string.weekday_monday; 2 -> R.string.weekday_tuesday; 3 -> R.string.weekday_wednesday; 4 -> R.string.weekday_thursday; 5 -> R.string.weekday_friday; 6 -> R.string.weekday_saturday; else -> R.string.weekday_sunday })
@Composable private fun privateLocationKindText(kind: PrivateLessonLocationKind): String = stringResource(when (kind) { PrivateLessonLocationKind.UNSPECIFIED -> R.string.private_location_unspecified; PrivateLessonLocationKind.IN_PERSON -> R.string.private_location_in_person; PrivateLessonLocationKind.ONLINE -> R.string.private_location_online })
