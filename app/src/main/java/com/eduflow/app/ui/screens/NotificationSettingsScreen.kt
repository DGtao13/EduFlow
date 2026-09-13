@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.eduflow.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.data.NotificationSettings
import com.eduflow.app.data.NotificationSettingsRepository
import com.eduflow.app.data.LessonReminderLead
import com.eduflow.app.ui.theme.EduFlowColorRole
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.OutlinedTextField
import com.eduflow.app.notifications.TaskReminderScheduler
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { NotificationSettingsRepository(context) }
    val settings by repository.settings.collectAsState(NotificationSettings())
    val scope = rememberCoroutineScope()
    var permissionExplanation by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var timeTarget by remember { mutableStateOf("summary") }
    var choiceTarget by remember { mutableStateOf<String?>(null) }
    var selectedLeads by remember { mutableStateOf(settings.taskLeadMinutes) }
    var selectedLessonLead by remember { mutableStateOf(15) }
    var customLeadTarget by remember { mutableStateOf<String?>(null) }
    var customLeadValue by remember { mutableStateOf("") }
    var customLeadHours by remember { mutableStateOf(false) }
    var customLeadError by remember { mutableStateOf(false) }
    var customLeadRoundedToHours by remember { mutableStateOf(false) }
    var notificationsAllowed by remember(context) { mutableStateOf(notificationPermissionState(context)) }
    fun refreshNotificationPermission() { notificationsAllowed = notificationPermissionState(context) }
    LaunchedEffect(context) { refreshNotificationPermission() }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshNotificationPermission() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshNotificationPermission()
    }
    fun enableWithPermission(enabled: Boolean, action: () -> Unit) { action(); if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) permissionExplanation = true }
    fun update(transform: (NotificationSettings) -> NotificationSettings) { scope.launch { repository.update(transform); com.eduflow.app.notifications.NotificationReconciliation(context).requestReconciliation() } }
    fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    val leadLabels = mapOf(0 to "В момента на срока", 30 to "30 минути преди", 60 to "1 час преди", 1440 to "1 ден преди")
    val dayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Нд")
    Scaffold(topBar = { EduFlowChildTopAppBar(title = "Настройки за известия", navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!notificationsAllowed) Surface(modifier = Modifier.padding(top = 12.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer) { Text(stringResource(R.string.notifications_unavailable), modifier = Modifier.padding(12.dp)) }
            SettingsSection("Задачи") {
                SettingsRow("Напомняния за задачи", "Персоналните напомняния се запазват", Icons.Default.CheckCircle, action = { Switch(settings.taskRemindersEnabled, { enabled -> enableWithPermission(enabled) { update { it.copy(taskRemindersEnabled = enabled) } } }) })
                if (settings.taskRemindersEnabled) {
                    SettingsRow("Напомняй", if (!settings.taskLeadsConfigured) "По подразбиране според приоритета" else settings.taskLeadMinutes.sortedDescending().mapNotNull(leadLabels::get).joinToString(" · ").ifBlank { "Без автоматични напомняния" }, Icons.Default.Notifications, onClick = { selectedLeads = settings.taskLeadMinutes; choiceTarget = "task" }, accent = EduFlowColorRole.ATTENTION)
                    SettingsRow("Напомняй за просрочени задачи", "В дневния преглед или един дневен обзор", Icons.Default.CheckCircle, action = { Switch(settings.overdueEnabled, { enabled -> update { it.copy(overdueEnabled = enabled) } }) })
                }
            }
            SettingsSection("Уроци") {
                SettingsRow("Училищни часове", "Едно известие за общ блок", Icons.Default.DateRange, action = { Switch(settings.schoolEnabled, { enabled -> enableWithPermission(enabled) { update { it.copy(schoolEnabled = enabled) } } }) })
                if (settings.schoolEnabled) SettingsRow("Преди училищния час", LessonReminderLead.label(settings.schoolLeadMinutes), Icons.Default.Notifications, onClick = { selectedLessonLead = settings.schoolLeadMinutes; choiceTarget = "school" }, accent = EduFlowColorRole.ATTENTION)
                SettingsRow("Частни уроци", "Еднократни и повтарящи се", Icons.Default.DateRange, action = { Switch(settings.privateEnabled, { enabled -> enableWithPermission(enabled) { update { it.copy(privateEnabled = enabled) } } }) })
                if (settings.privateEnabled) SettingsRow("Преди частния урок", LessonReminderLead.label(settings.privateLeadMinutes), Icons.Default.Notifications, onClick = { selectedLessonLead = settings.privateLeadMinutes; choiceTarget = "private" }, accent = EduFlowColorRole.ATTENTION)
            }
            SettingsSection("Дневен преглед") {
                SettingsRow("Дневен преглед", "Уроци и незавършени задачи за деня", Icons.Default.DateRange, action = { Switch(settings.dailySummaryEnabled, { enabled -> enableWithPermission(enabled) { update { it.copy(dailySummaryEnabled = enabled) } } }) }, accent = if (settings.dailySummaryEnabled) EduFlowColorRole.ACTIVE else EduFlowColorRole.NEUTRAL)
                if (settings.dailySummaryEnabled || settings.overdueEnabled && settings.taskRemindersEnabled) SettingsRow("Час на прегледа", clock(settings.summaryHour * 60 + settings.summaryMinute), Icons.Default.MoreVert, onClick = { timeTarget = "summary"; showTimePicker = true })
                if (settings.dailySummaryEnabled) SettingsRow("Дни", settings.summaryDays.sorted().joinToString(" · ") { dayLabels[it - 1] }.ifBlank { "Няма избрани дни" }, Icons.Default.DateRange, onClick = { choiceTarget = "days" })
            }
            SettingsSection("Тихи часове") {
                SettingsRow("Тихи часове", "Полезните известия се отлагат; закъснелите за урок се пропускат", Icons.Default.Clear, action = { Switch(settings.quietEnabled, { enabled -> update { it.copy(quietEnabled = enabled) } }) })
                if (settings.quietEnabled) {
                    SettingsRow("Начало", clock(settings.quietStartMinute), Icons.Default.MoreVert, onClick = { timeTarget = "start"; showTimePicker = true })
                    SettingsRow("Край", clock(settings.quietEndMinute), Icons.Default.MoreVert, onClick = { timeTarget = "end"; showTimePicker = true })
                }
            }
        }
    }
    if (showTimePicker) {
        val minute = when (timeTarget) { "start" -> settings.quietStartMinute; "end" -> settings.quietEndMinute; else -> settings.summaryHour * 60 + settings.summaryMinute }
        val picker = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { showTimePicker = false }, title = { Text(when (timeTarget) { "start" -> "Начало на тихите часове"; "end" -> "Край на тихите часове"; else -> "Час на прегледа" }) }, text = { TimePicker(picker) }, confirmButton = { TextButton(onClick = { update { when (timeTarget) { "start" -> it.copy(quietStartMinute = picker.hour * 60 + picker.minute); "end" -> it.copy(quietEndMinute = picker.hour * 60 + picker.minute); else -> it.copy(summaryHour = picker.hour, summaryMinute = picker.minute) } }; showTimePicker = false }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } })
    }
    choiceTarget?.let { target ->
        AlertDialog(onDismissRequest = { choiceTarget = null }, title = { Text(when (target) { "task" -> "Напомняй"; "school" -> "Преди училищния час"; "private" -> "Преди частния урок"; else -> "Дни на прегледа" }) }, text = {
            Column {
                if (target == "task") leadLabels.forEach { (minutes, label) -> Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(minutes in selectedLeads, { checked -> selectedLeads = if (checked) selectedLeads + minutes else selectedLeads - minutes }); Text(label) } }
                if (target == "school" || target == "private") {
                    LessonReminderLead.presets.forEach { minutes -> LessonLeadOptionRow(LessonReminderLead.label(minutes), selectedLessonLead == minutes) { selectedLessonLead = minutes } }
                    LessonLeadOptionRow("По избор…", selectedLessonLead !in LessonReminderLead.presets) {
                        customLeadTarget = target
                        customLeadHours = selectedLessonLead % 60 == 0
                        customLeadValue = (if (customLeadHours) selectedLessonLead / 60 else selectedLessonLead).toString()
                        customLeadError = false
                        customLeadRoundedToHours = false
                        choiceTarget = null
                    }
                }
                if (target == "days") dayLabels.forEachIndexed { index, label -> Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(index + 1 in settings.summaryDays, { checked -> update { it.copy(summaryDays = if (checked) it.summaryDays + (index + 1) else it.summaryDays - (index + 1)) } }); Text(label) } }
            }
        }, confirmButton = { TextButton(onClick = {
            when (target) {
                "task" -> update { it.copy(taskLeadMinutes = selectedLeads, taskLeadsConfigured = true) }
                "school" -> update { it.copy(schoolLeadMinutes = selectedLessonLead) }
                "private" -> update { it.copy(privateLeadMinutes = selectedLessonLead) }
            }
            choiceTarget = null
        }) { Text(stringResource(R.string.save)) } }, dismissButton = {
            if (target == "school" || target == "private") TextButton(onClick = { choiceTarget = null }) { Text(stringResource(R.string.cancel)) }
        })
    }
    customLeadTarget?.let { target ->
        val parsedMinutes = LessonReminderLead.parse(customLeadValue, customLeadHours)
        val invalid = parsedMinutes == null
        fun selectUnit(hours: Boolean) {
            if (hours == customLeadHours) return
            val number = customLeadValue.trim().toIntOrNull()
            customLeadRoundedToHours = false
            if (number != null) {
                customLeadValue = when {
                    hours && !customLeadHours -> {
                        customLeadRoundedToHours = number % 60 != 0
                        ((number + 59) / 60).toString()
                    }
                    !hours && customLeadHours -> (number * 60).toString()
                    else -> customLeadValue
                }
            }
            customLeadHours = hours
            customLeadError = false
        }
        AlertDialog(onDismissRequest = { customLeadTarget = null }, modifier = Modifier.imePadding(), title = { Text("Персонално време за напомняне", style = androidx.compose.material3.MaterialTheme.typography.titleMedium) }, text = {
            Column(Modifier.fillMaxWidth()) {
                Text(if (target == "school") "Училищни часове" else "Частни уроци", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.heightIn(min = 6.dp))
                OutlinedTextField(customLeadValue, { customLeadValue = it; customLeadError = false; customLeadRoundedToHours = false }, label = { Text("Колко по-рано") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = customLeadError || invalid, modifier = Modifier.widthIn(min = 144.dp, max = 190.dp))
                Spacer(Modifier.heightIn(min = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CompactDurationUnit("Минути", !customLeadHours, Modifier.weight(1f)) { selectUnit(false) }
                    CompactDurationUnit("Часове", customLeadHours, Modifier.weight(1f)) { selectUnit(true) }
                }
                Spacer(Modifier.heightIn(min = 10.dp))
                Text(if (customLeadError || invalid) "Въведи стойност между 1 минута и 24 часа." else "Между 1 минута и 24 часа преди урока.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = if (customLeadError || invalid) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                if (customLeadRoundedToHours) Text("Минутите се закръглят нагоре при преминаване към часове.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }, confirmButton = { Button(onClick = {
            if (parsedMinutes == null) customLeadError = true else { update { if (target == "school") it.copy(schoolLeadMinutes = parsedMinutes) else it.copy(privateLeadMinutes = parsedMinutes) }; customLeadTarget = null }
        }, enabled = !invalid) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { customLeadTarget = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (permissionExplanation) AlertDialog(onDismissRequest = { permissionExplanation = false }, text = { Text(stringResource(R.string.permission_explanation)) }, confirmButton = { TextButton(onClick = { permissionExplanation = false; if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.allow_notifications)) } }, dismissButton = { TextButton(onClick = { permissionExplanation = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun LessonLeadOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onSelect).padding(horizontal = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CompactDurationUnit(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 40.dp), colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
        containerColor = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surface
    )) { Text(label) }
}

private fun notificationPermissionState(context: android.content.Context): Boolean {
    val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    return runtimePermissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}
