@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.data.local.ScheduleTemplate
import com.eduflow.app.data.local.Subject
import com.eduflow.app.ui.viewmodel.DatabaseViewModelFactory
import com.eduflow.app.ui.viewmodel.TimetableViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.ui.initialSchoolSlotPosition
import com.eduflow.app.ui.scheduleSlotConflicts
import com.eduflow.app.ui.scheduleSlotUsesCustomTimes
import com.eduflow.app.ui.schoolPeriodOption
import com.eduflow.app.ui.schoolPeriodOptions
import com.eduflow.app.ui.schoolWeekdayResourceIndex
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
@Composable
fun TimetableSetupScreen(database: EduFlowDatabase, navController: NavController) {
    val viewModel: TimetableViewModel = viewModel(factory = DatabaseViewModelFactory(database))
    val templates by viewModel.templates.collectAsState()
    var templateEditor by remember { mutableStateOf<ScheduleTemplate?>(null) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ScheduleTemplate?>(null) }

    Scaffold(topBar = { EduFlowChildTopAppBar(title = stringResource(R.string.timetable_setup_title), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                if (templates.isEmpty()) Text(stringResource(R.string.template_empty_message))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Button(onClick = { templateEditor = null; showTemplateEditor = true }) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add_template), Modifier.padding(start = 6.dp)) }
                    if (templates.none { it.name.equals("Седмица А", true) } && templates.none { it.name.equals("Седмица Б", true) }) {
                        TextButton(onClick = { viewModel.saveTemplate(ScheduleTemplate(name = "Седмица А")); viewModel.saveTemplate(ScheduleTemplate(name = "Седмица Б")) }) { Text(stringResource(R.string.create_weeks)) }
                    }
                }
            }
            items(templates, key = { it.id }) { template ->
                var menuOpen by remember(template.id) { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    tonalElevation = 1.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(template.name, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.recurring_lesson_pattern), style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(onClick = { navController.navigate("timetable_setup/${template.id}") }, modifier = Modifier.heightIn(min = 40.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) { Text(stringResource(R.string.edit_slots)) }
                        androidx.compose.foundation.layout.Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.template_actions)) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; templateEditor = template; showTemplateEditor = true })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; deleting = template })
                            }
                        }
                    }
                }
            }
        }
    }
    if (showTemplateEditor) {
        TemplateEditorDialog(templateEditor, { showTemplateEditor = false }, { viewModel.saveTemplate(it); showTemplateEditor = false })
    }
    deleting?.let { template -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_template_question, template.name)) }, text = { Text(stringResource(R.string.template_delete_message)) }, confirmButton = { DestructiveConfirmationButton(stringResource(R.string.delete), onClick = { viewModel.deleteTemplate(template); deleting = null }) }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
fun SlotEditorScreen(database: EduFlowDatabase, templateId: Long, navController: NavController) {
    val viewModel: TimetableViewModel = viewModel(factory = DatabaseViewModelFactory(database))
    val scope = rememberCoroutineScope()
    val template by remember(database, templateId) { database.scheduleTemplateDao().observeById(templateId) }.collectAsState(initial = null)
    val slots by viewModel.slots(templateId).collectAsState()
    val subjects by remember(database) { database.subjectDao().observeAll() }.collectAsState(initial = emptyList())
    var editorSession by remember { mutableStateOf<SlotEditorSession?>(null) }
    var nextEditorSessionId by remember { mutableStateOf(0L) }
    var deleting by remember { mutableStateOf<ScheduleSlot?>(null) }
    // Keep the long template list anchored while slot data refreshes after a dialog save.
    val slotListState = rememberLazyListState()
    val setupWeekdays = stringArrayResource(R.array.weekday_names)
    fun openAddEditor() {
        scope.launch {
            val snapshot = database.scheduleSlotDao().getForTemplate(templateId)
            nextEditorSessionId += 1
            editorSession = SlotEditorSession(
                id = nextEditorSessionId,
                templateId = templateId,
                slot = null,
                occupiedSlots = snapshot,
                initialPosition = initialSchoolSlotPosition(null, snapshot, templateId)
            )
        }
    }

    Scaffold(topBar = { EduFlowChildTopAppBar(title = template?.name ?: stringResource(R.string.template), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }, floatingActionButton = { androidx.compose.material3.FloatingActionButton(onClick = ::openAddEditor) { Icon(Icons.Default.Add, stringResource(R.string.add_lesson)) } }) { padding ->
        if (subjects.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.add_subject_before_lessons)) }
        } else if (slots.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.no_recurring_lessons)); Button(onClick = ::openAddEditor, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.add_lesson)) } }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), state = slotListState, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(slots, key = { it.id }) { slot ->
                    val subject = subjects.firstOrNull { it.id == slot.subjectId }
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp).width(4.dp).background(subject?.color?.let(::Color) ?: MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)))
                            Column(Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, bottom = 10.dp)) {
                                Text("${setupWeekdays.getOrElse(slot.weekday - 1) { "Ден" }} · ${stringResource(R.string.lesson)} ${slot.lessonIndex}", style = MaterialTheme.typography.titleSmall)
                                Text("${subject?.name ?: stringResource(R.string.missing_subject)} · ${slot.startTime}–${slot.endTime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (slot.logicalBlockId != null) Text(stringResource(R.string.part_of_logical_block), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                nextEditorSessionId += 1
                                editorSession = SlotEditorSession(nextEditorSessionId, templateId, slot, slots, initialSchoolSlotPosition(slot, slots, templateId))
                            }) { Icon(Icons.Default.Edit, stringResource(R.string.edit_lesson)) }
                            IconButton(onClick = { deleting = slot }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_lesson_slot_question)) }
                        }
                    }
                }
            }
        }
    }
    editorSession?.let { session ->
        SlotEditorDialog(session, subjects, template?.name.orEmpty(), { editorSession = null }, { slot, previous -> viewModel.saveSlot(slot, previous); editorSession = null })
    }
    deleting?.let { slot -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_lesson_slot_question)) }, confirmButton = { DestructiveConfirmationButton(stringResource(R.string.delete), onClick = { viewModel.deleteSlot(slot); deleting = null }) }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable private fun TemplateEditorDialog(template: ScheduleTemplate?, onDismiss: () -> Unit, onSave: (ScheduleTemplate) -> Unit) {
    var name by remember(template) { mutableStateOf(template?.name.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (template == null) R.string.add_template else R.string.rename)) }, text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(ScheduleTemplate(template?.id ?: 0, name.trim())) }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private data class SlotEditorSession(
    val id: Long,
    val templateId: Long,
    val slot: ScheduleSlot?,
    val occupiedSlots: List<ScheduleSlot>,
    val initialPosition: Pair<Int, Int>
)

private enum class SlotEditorSelector { WEEKDAY, PERIOD, SUBJECT }
private enum class SlotEditorTimeTarget { START, END }

@Composable
private fun SlotEditorDialog(
    session: SlotEditorSession,
    subjects: List<Subject>,
    templateName: String,
    onDismiss: () -> Unit,
    onSave: (ScheduleSlot, ScheduleSlot?) -> Unit
) {
    val slot = session.slot
    val existingSlots = session.occupiedSlots
    val templateId = session.templateId
    val weekdays = stringArrayResource(R.array.weekday_names)
    val defaultPeriod = schoolPeriodOption(session.initialPosition.second) ?: schoolPeriodOptions.first()
    var weekday by remember(session.id) { mutableStateOf(session.initialPosition.first) }
    var period by remember(session.id) { mutableStateOf(session.initialPosition.second) }
    var start by remember(session.id) { mutableStateOf(slot?.startTime ?: defaultPeriod.startTime) }
    var end by remember(session.id) { mutableStateOf(slot?.endTime ?: defaultPeriod.endTime) }
    var customTimes by remember(session.id) { mutableStateOf(slot?.let(::scheduleSlotUsesCustomTimes) ?: false) }
    var subjectId by remember(session.id) { mutableStateOf(slot?.subjectId ?: subjects.firstOrNull()?.id) }
    var teacher by remember(session.id) { mutableStateOf(slot?.teacherOverride.orEmpty()) }
    var room by remember(session.id) { mutableStateOf(slot?.roomOverride.orEmpty()) }
    var group by remember(session.id) { mutableStateOf(slot?.groupInfo.orEmpty()) }
    val initialPreviousSlot = existingSlots.firstOrNull {
        it.id != slot?.id && it.scheduleTemplateId == templateId &&
            it.weekday == (slot?.weekday ?: session.initialPosition.first) &&
            it.lessonIndex == (slot?.lessonIndex ?: session.initialPosition.second) - 1
    }
    var linkWithPrevious by remember(session.id) { mutableStateOf(slot?.logicalBlockId != null && slot.logicalBlockId == initialPreviousSlot?.logicalBlockId) }
    var selector by remember { mutableStateOf<SlotEditorSelector?>(null) }
    var subjectSearch by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val weekdayError = stringResource(R.string.weekday_error)
    val lessonNumberError = stringResource(R.string.lesson_number_error)
    val periodError = stringResource(R.string.school_period_error)
    val endAfterStartError = stringResource(R.string.end_after_start)
    val subjectRequiredError = stringResource(R.string.choose_subject)
    val conflictError = stringResource(R.string.schedule_slot_conflict)
    val periodOption = schoolPeriodOption(period)
    val periodValue = periodOption?.let {
        stringResource(R.string.school_period_value, it.index, it.startTime.format(timeFormatter), it.endTime.format(timeFormatter))
    } ?: stringResource(R.string.custom_school_period)
    val subjectValue = subjects.firstOrNull { it.id == subjectId }?.let { it.shortName ?: it.name }
        ?: stringResource(R.string.choose_subject_value)
    val previousSlot = existingSlots.firstOrNull {
        it.id != slot?.id && it.scheduleTemplateId == templateId && it.weekday == weekday && it.lessonIndex == period - 1
    }

    val initialSubject = slot?.subjectId ?: subjects.firstOrNull()?.id
    val initialLinked = slot?.logicalBlockId != null && slot.logicalBlockId == initialPreviousSlot?.logicalBlockId
    val dirty = weekday != session.initialPosition.first || period != session.initialPosition.second ||
        start != (slot?.startTime ?: defaultPeriod.startTime) || end != (slot?.endTime ?: defaultPeriod.endTime) ||
        subjectId != initialSubject || teacher.trim().ifBlank { null } != slot?.teacherOverride ||
        room.trim().ifBlank { null } != slot?.roomOverride || group.trim().ifBlank { null } != slot?.groupInfo || linkWithPrevious != initialLinked
    val leave = protectedEditorExit(dirty, onDismiss)

    AlertDialog(
        onDismissRequest = leave,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(if (slot == null) R.string.add_schedule_slot else R.string.edit_schedule_slot))
                if (templateName.isNotBlank()) Text(templateName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SelectorField(stringResource(R.string.weekday), weekdays.getOrElse(schoolWeekdayResourceIndex(weekday) ?: 0) { weekdays.first() }) {
                        selector = SlotEditorSelector.WEEKDAY
                    }
                }
                item {
                    SelectorField(stringResource(R.string.school_period), periodValue) {
                        selector = SlotEditorSelector.PERIOD
                    }
                }
                item {
                    SelectorField(stringResource(R.string.subject), subjectValue) {
                        subjectSearch = ""
                        selector = SlotEditorSelector.SUBJECT
                    }
                }
                item {
                        OutlinedButton(onClick = {
                            customTimes = !customTimes
                            if (!customTimes) periodOption?.let { start = it.startTime; end = it.endTime }
                            error = null
                        }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (customTimes) R.string.use_standard_school_times else R.string.custom_school_times))
                    }
                }
                if (customTimes) {
                    item {
                        TimeRangeField(start, end) { s, e -> start = s; end = e; error = null }
                    }
                }
                item {
                    OutlinedTextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = { Text(stringResource(R.string.slot_teacher)) },
                        supportingText = { Text(stringResource(R.string.optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                previousSlot?.let {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(if (linkWithPrevious) R.string.part_of_logical_block else R.string.link_previous_lesson), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(if (linkWithPrevious) R.string.unlink_previous_lesson_supporting else R.string.link_previous_lesson_supporting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = linkWithPrevious, onCheckedChange = { linkWithPrevious = it })
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text(stringResource(R.string.slot_room)) },
                        supportingText = { Text(stringResource(R.string.optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = group,
                        onValueChange = { group = it },
                        label = { Text(stringResource(R.string.group_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                val blockId = if (linkWithPrevious && previousSlot != null) {
                    previousSlot.logicalBlockId?.trim()?.takeIf { it.isNotEmpty() } ?: "slot-block-${previousSlot.id}"
                } else if (slot != null && weekday == slot.weekday && period == slot.lessonIndex &&
                    slot.logicalBlockId != initialPreviousSlot?.logicalBlockId) slot.logicalBlockId else null
                val candidate = ScheduleSlot(
                    id = slot?.id ?: 0,
                    scheduleTemplateId = templateId,
                    weekday = weekday,
                    lessonIndex = period,
                    startTime = start,
                    endTime = end,
                    subjectId = subjectId,
                    teacherOverride = teacher.trim().ifBlank { null },
                    roomOverride = room.trim().ifBlank { null },
                    groupInfo = group.trim().ifBlank { null },
                    logicalBlockId = blockId
                )
                error = when {
                    weekday !in 1..5 -> weekdayError
                    period < 1 -> lessonNumberError
                    !customTimes && periodOption == null -> periodError
                    customTimes && end <= start -> endAfterStartError
                    subjectId == null || subjects.none { it.id == subjectId } -> subjectRequiredError
                    scheduleSlotConflicts(candidate, existingSlots) -> conflictError
                    else -> null
                }
                if (error == null) onSave(candidate, previousSlot.takeIf { linkWithPrevious })
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = leave) { Text(stringResource(R.string.cancel)) } }
    )

    selector?.let { currentSelector ->
        ModalBottomSheet(onDismissRequest = { selector = null }) {
            when (currentSelector) {
                SlotEditorSelector.WEEKDAY -> SchoolWeekdayPickerSheet(weekdays, weekday, { chosen -> weekday = chosen; selector = null; error = null })
                SlotEditorSelector.PERIOD -> SchoolPeriodPickerSheet(period, { chosen ->
                    period = chosen
                    schoolPeriodOption(chosen)?.let { chosenPeriod -> start = chosenPeriod.startTime; end = chosenPeriod.endTime }
                    customTimes = false
                    selector = null
                    error = null
                })
                SlotEditorSelector.SUBJECT -> SubjectPickerSheet(subjects, subjectSearch, subjectId, false, { subjectSearch = it }, { chosen -> subjectId = chosen; selector = null; error = null })
            }
        }
    }

}

@Composable
private fun SchoolWeekdayPickerSheet(weekdays: Array<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.choose_weekday), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        items((1..5).toList(), key = { it }) { weekday ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(weekday) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(weekdays[weekday - 1], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (weekday == selected) Icon(Icons.Default.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SchoolPeriodPickerSheet(selected: Int, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.choose_school_period), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        items(schoolPeriodOptions, key = { it.index }) { period ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(period.index) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.school_period_number, period.index), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.school_period_range, period.startTime.format(timeFormatter), period.endTime.format(timeFormatter)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (period.index == selected) Icon(Icons.Default.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
