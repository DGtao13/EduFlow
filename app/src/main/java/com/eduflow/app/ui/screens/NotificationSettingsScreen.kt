@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.eduflow.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.data.NotificationSettings
import com.eduflow.app.data.NotificationSettingsRepository
import com.eduflow.app.notifications.EduFlowNotifications
import com.eduflow.app.notifications.TaskReminderScheduler
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { NotificationSettingsRepository(context) }
    val settings by repository.settings.collectAsState(NotificationSettings())
    val scope = rememberCoroutineScope()
    var permissionExplanation by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun enableWithPermission(action: () -> Unit) { action(); if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) permissionExplanation = true }
    Scaffold(topBar = { EduFlowChildTopAppBar(title = stringResource(R.string.notifications), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (!EduFlowNotifications.isAllowed(context)) Surface(modifier = Modifier.padding(top = 12.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer) { Text(stringResource(R.string.notifications_unavailable), modifier = Modifier.padding(12.dp)) }
            SettingsSection(stringResource(R.string.notifications)) { Column {
                SettingsRow(stringResource(R.string.task_reminders), stringResource(R.string.task_reminders_supporting), Icons.Default.Notifications, action = { Switch(settings.taskRemindersEnabled, { enabled -> if (enabled) enableWithPermission { scope.launch { repository.setTaskReminders(true); TaskReminderScheduler(context).syncAllPending() } } else scope.launch { repository.setTaskReminders(false); TaskReminderScheduler(context).cancelAllPending() } }) })
                SettingsRow(stringResource(R.string.daily_summary), stringResource(R.string.daily_summary_supporting), Icons.Default.Notifications, action = { Switch(settings.dailySummaryEnabled, { enabled -> if (enabled) enableWithPermission { scope.launch { repository.setDailySummary(true); TaskReminderScheduler(context).scheduleDailySummary(settings.summaryHour, settings.summaryMinute) } } else scope.launch { repository.setDailySummary(false); TaskReminderScheduler(context).cancelDailySummary() } }) })
                SettingsRow(stringResource(R.string.daily_summary_time), "%02d:%02d".format(settings.summaryHour, settings.summaryMinute), Icons.Default.MoreVert, onClick = { showTimePicker = true })
            } }
        }
    }
    if (showTimePicker) {
        val picker = rememberTimePickerState(settings.summaryHour, settings.summaryMinute, is24Hour = true)
        AlertDialog(onDismissRequest = { showTimePicker = false }, title = { Text(stringResource(R.string.daily_summary_time)) }, text = { TimePicker(picker) }, confirmButton = { TextButton(onClick = { scope.launch { repository.setSummaryTime(picker.hour, picker.minute); if (settings.dailySummaryEnabled) TaskReminderScheduler(context).scheduleDailySummary(picker.hour, picker.minute) }; showTimePicker = false }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (permissionExplanation) AlertDialog(onDismissRequest = { permissionExplanation = false }, text = { Text(stringResource(R.string.permission_explanation)) }, confirmButton = { TextButton(onClick = { permissionExplanation = false; if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.allow_notifications)) } }, dismissButton = { TextButton(onClick = { permissionExplanation = false }) { Text(stringResource(R.string.cancel)) } })
}
