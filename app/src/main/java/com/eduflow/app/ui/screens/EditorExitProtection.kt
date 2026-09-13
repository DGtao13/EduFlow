package com.eduflow.app.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.eduflow.app.R

/** Used only by staged explicit-save editors, never immediate-save settings. */
@Composable
fun protectedEditorExit(dirty: Boolean, onLeave: () -> Unit): () -> Unit {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.unsaved_changes_title)) },
        confirmButton = { TextButton(onClick = { confirming = false; onLeave() }) {
            Text(stringResource(R.string.leave_without_saving), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.stay)) } }
    )
    return { if (dirty) confirming = true else onLeave() }
}
