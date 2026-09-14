@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.eduflow.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.eduflow.app.R
import com.eduflow.app.ui.TimeRangeDraft
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
fun TimeRangeField(start: LocalTime?, end: LocalTime?, onCommit: (LocalTime, LocalTime) -> Unit) {
    var draft by remember { mutableStateOf<TimeRangeDraft?>(null) }
    val format = remember { DateTimeFormatter.ofPattern("HH:mm") }
    SelectorField(stringResource(R.string.time_range), listOfNotNull(start?.format(format), end?.format(format)).joinToString("–").ifEmpty { stringResource(R.string.choose_time) }) {
        draft = TimeRangeDraft()
    }
    draft?.let { active ->
        key(active.start != null) {
            val initial = if (active.start == null) start ?: LocalTime.of(16, 0) else end ?: active.start!!.plusHours(1)
            val picker = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
            var invalid by remember { mutableStateOf(false) }
            LaunchedEffect(active.start, initial) {
                if (active.start == null) {
                    snapshotFlow { picker.hour to picker.minute }
                        .filter { (_, minute) -> minute != initial.minute }
                        .first()
                        .let { (hour, minute) -> draft = active.chooseStart(LocalTime.of(hour, minute)) }
                }
            }
            AlertDialog(
                onDismissRequest = { draft = null },
                title = { Text(stringResource(if (active.start == null) R.string.private_start_time_picker else R.string.private_end_time_picker)) },
                text = { Column { TimePicker(picker); if (invalid) Text(stringResource(R.string.end_after_start), color = MaterialTheme.colorScheme.error) } },
                confirmButton = { TextButton(onClick = {
                    val value = LocalTime.of(picker.hour, picker.minute)
                    if (active.start == null) draft = active.chooseStart(value)
                    else active.finish(value)?.let { onCommit(it.start, it.end); draft = null } ?: run { invalid = true }
                }) { Text(stringResource(if (active.start == null) R.string.range_next else R.string.save)) } },
                dismissButton = {
                    Row {
                        if (active.start != null) {
                            TextButton(onClick = { draft = active.returnToStart() }) { Text(stringResource(R.string.range_back_to_start)) }
                        }
                        TextButton(onClick = { draft = null }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            )
        }
    }
}
