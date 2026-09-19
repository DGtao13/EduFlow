@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.eduflow.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.data.BackupException
import com.eduflow.app.data.BackupRepository
import com.eduflow.app.data.DataExportService
import com.eduflow.app.data.PortableBackup
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eduflow.app.data.PackageType
import com.eduflow.app.data.AppResetRepository
import java.time.LocalDate

@Composable
fun BackupScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { BackupRepository(context) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PortableBackup?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exportType by remember { mutableStateOf(PackageType.FULL_ARCHIVE) }
    var showExportTypes by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    fun failure(e: Exception): String {
        val backupError = e as? BackupException
        if (backupError != null) Log.w("EduFlowBackup", "Backup failure: ${backupError.failure}; ${backupError.message}", backupError.cause)
        return context.getString(when (backupError?.failure) {
        com.eduflow.app.data.BackupFailure.UNSUPPORTED_FORMAT -> R.string.backup_newer
        com.eduflow.app.data.BackupFailure.NOT_EMPTY -> R.string.package_not_empty
        com.eduflow.app.data.BackupFailure.INCOMPATIBLE_PROGRAM -> R.string.package_incompatible
        else -> R.string.backup_invalid
        })
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { stream -> repository.write(stream, exportType) } ?: error("io") }
                message = context.getString(R.string.backup_success)
            } catch (e: Exception) { message = failure(e) } finally { busy = false }
        } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { stream -> DataExportService(repository).write(stream) } ?: error("io") }
                message = context.getString(R.string.export_success)
            } catch (e: Exception) { message = failure(e) } finally { busy = false }
        } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch {
            busy = true
            try {
                preview = withContext(Dispatchers.IO) {
                    val parsed = repository.read(it)
                    repository.preview(parsed)
                    parsed
                }
            } catch (e: Exception) { message = failure(e) } finally { busy = false }
        } }
    }
    Scaffold(topBar = { EduFlowChildTopAppBar(title = stringResource(R.string.data_backup_title), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.backup_explanation), modifier = Modifier.padding(top = 12.dp))
            SettingsSection(stringResource(R.string.data_section)) { Column {
                SettingsRow(stringResource(R.string.create_backup), stringResource(R.string.create_backup_supporting), Icons.Default.Settings, onClick = { if (!busy) showExportTypes = true })
                SettingsRow(stringResource(R.string.package_import), stringResource(R.string.restore_backup_supporting), Icons.Default.Edit, onClick = { if (!busy) restore.launch("application/*") })
                SettingsRow(stringResource(R.string.export_data), stringResource(R.string.export_data_supporting), Icons.AutoMirrored.Filled.List, onClick = { if (!busy) export.launch("EduFlow-export-${LocalDate.now()}.zip") })
                SettingsRow(stringResource(R.string.reset_app), stringResource(R.string.reset_warning), Icons.Default.Delete, destructive = true, onClick = { if (!busy) confirmReset = true })
            } }
            if (busy) { CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp)); Text(stringResource(R.string.working), modifier = Modifier.padding(top = 8.dp)) }
            message?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
    if (showExportTypes) {
        ModalBottomSheet(onDismissRequest = { showExportTypes = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_backup),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                PackageType.entries.forEach { type ->
                    Card(
                        onClick = {
                            exportType = type
                            showExportTypes = false
                            createBackup.launch("EduFlow-" + type.name + "-" + LocalDate.now() + ".eduflow")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = packageIcon(type),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier.padding(start = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(stringResource(packageTitle(type)), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(packageChooserHint(type)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    preview?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) preview = null },
            title = { Text(stringResource(packageTitle(backup.manifest.packageType))) },
            text = { Column {
                Text(stringResource(packageHint(backup.manifest.packageType)))
                Text(stringResource(R.string.package_counts, backup.data.subjects.size, backup.data.lessons.size, backup.data.tasks.size))
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
                busy = true
                try { repository.restore(backup); message = context.getString(R.string.restore_success) }
                catch (e: Exception) { message = failure(e) }
                finally { busy = false; preview = null }
            } }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { if (!busy) confirmReset = false },
        title = { Text(stringResource(R.string.reset_app), color = MaterialTheme.colorScheme.error) },
        text = { Text(stringResource(R.string.reset_warning)) },
        confirmButton = { Button(enabled = !busy, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = { scope.launch {
            busy = true
            try { AppResetRepository(context).reset() } catch (e: Exception) { message = failure(e) }
            finally { busy = false; confirmReset = false }
        } }) { Text(stringResource(R.string.reset_confirm)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun packageTitle(type: PackageType) = when (type) {
    PackageType.FULL_ARCHIVE -> R.string.package_full
    PackageType.SCHOOL_PROGRAM -> R.string.package_program
    PackageType.STUDY_DATA -> R.string.package_study
}
private fun packageHint(type: PackageType) = when (type) {
    PackageType.FULL_ARCHIVE -> R.string.package_full_hint
    PackageType.SCHOOL_PROGRAM -> R.string.package_program_hint
    PackageType.STUDY_DATA -> R.string.package_study_hint
}

private fun packageChooserHint(type: PackageType) = when (type) {
    PackageType.FULL_ARCHIVE -> R.string.package_full_chooser_hint
    PackageType.SCHOOL_PROGRAM -> R.string.package_program_chooser_hint
    PackageType.STUDY_DATA -> R.string.package_study_chooser_hint
}

private fun packageIcon(type: PackageType) = when (type) {
    PackageType.FULL_ARCHIVE -> Icons.Default.Settings
    PackageType.SCHOOL_PROGRAM -> Icons.Default.DateRange
    PackageType.STUDY_DATA -> Icons.AutoMirrored.Filled.List
}
