@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.eduflow.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Edit
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowChildTopAppBar
import com.eduflow.app.ui.FeedbackChooser

@Composable fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    var feedbackOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Scaffold(topBar = { EduFlowChildTopAppBar(title = stringResource(R.string.about_eduflow), navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Image(
                painter = painterResource(R.drawable.eduflow_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(112.dp)
            )
            Text(stringResource(R.string.app_version, version), Modifier.padding(top = 8.dp))
            Text(
                stringResource(R.string.creator_credit),
                Modifier.padding(top = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.about_description), Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.local_first_privacy), Modifier.padding(top = 8.dp))
            SettingsSection(stringResource(R.string.about_section)) {
                SettingsRow(stringResource(R.string.feedback_action), stringResource(R.string.feedback_supporting), Icons.Default.Edit, onClick = { feedbackOpen = true })
            }
        }
    }
    if (feedbackOpen) FeedbackChooser { feedbackOpen = false }
}
