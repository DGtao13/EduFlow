@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.data.AcademicYearSettingsRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun AcademicYearScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { AcademicYearSettingsRepository(context) }
    val settings by repository.settings.collectAsState(AcademicYearSettings())
    val scope = rememberCoroutineScope()
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    Scaffold(topBar = { EduFlowChildTopAppBar(title = stringResource(R.string.academic_year), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(stringResource(R.string.academic_year_description), modifier = Modifier.padding(bottom = 12.dp))
            SelectorField(stringResource(R.string.academic_year_start), settings.startDate.toString()) { editingStart = true }
            SelectorField(stringResource(R.string.academic_year_end), settings.endDate.toString()) { editingStart = false }
        }
    }
    editingStart?.let { isStart ->
        val state = rememberDatePickerState(initialSelectedDateMillis = (if (isStart) settings.startDate else settings.endDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { editingStart = null }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { millis ->
            val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            val updated = if (isStart) settings.copy(startDate = selected) else settings.copy(endDate = selected)
            if (!updated.endDate.isBefore(updated.startDate)) scope.launch { repository.set(updated) }
            editingStart = null
        } }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = { editingStart = null }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state = state) }
    }
}
