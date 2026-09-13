@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.data.OnboardingRepository
import kotlinx.coroutines.launch

/** A non-destructive guide: screens it opens write only through their existing flows. */
@Composable
fun SetupGuideScreen(navController: NavController, firstUse: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // The guide remains below nested setup routes in the back stack. Save its current
    // step with the destination so returning never restarts onboarding.
    var step by rememberSaveable(firstUse) { mutableIntStateOf(0) }
    val headings = listOf(R.string.setup_welcome, R.string.academic_year, R.string.nav_subjects, R.string.nav_schedule, R.string.private_lessons, R.string.notifications, R.string.setup_finish)
    fun finish() {
        scope.launch { OnboardingRepository(context).markComplete(); navController.navigate("schedule") { popUpTo("schedule") { inclusive = false }; launchSingleTop = true } }
    }
    BackHandler(enabled = step > 0) { step-- }
    Scaffold(topBar = {
        if (!firstUse || step > 0) androidx.compose.material3.TopAppBar(
            title = { Text(stringResource(headings[step])) },
            navigationIcon = { IconButton(onClick = { if (step > 0) step-- else navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            if (step == 0) Image(painterResource(R.drawable.eduflow_logo), stringResource(R.string.app_name), modifier = Modifier.fillMaxWidth().padding(horizontal = 44.dp))
            Text(stringResource(headings[step]), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(setupDescription(step)), textAlign = TextAlign.Center, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            when (step) {
                1 -> OutlinedButton(onClick = { navController.navigate("academic_year") }) { Text(stringResource(R.string.setup_open_academic_year)) }
                2 -> OutlinedButton(onClick = { navController.navigate("subjects") }) { Text(stringResource(R.string.setup_open_subjects)) }
                3 -> {
                    OutlinedButton(onClick = { navController.navigate("timetable_setup") }) { Text(stringResource(R.string.create_manually)) }
                    OutlinedButton(onClick = { navController.navigate("backup") }) { Text(stringResource(R.string.import_program)) }
                }
                4 -> OutlinedButton(onClick = { navController.navigate("private_lessons") }) { Text(stringResource(R.string.private_lessons)) }
                5 -> OutlinedButton(onClick = { navController.navigate("notification_settings") }) { Text(stringResource(R.string.notifications)) }
            }
            Button(onClick = { if (step == headings.lastIndex) finish() else step++ }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (step == headings.lastIndex) R.string.finish else R.string.next)) }
            if (step in 2..5) androidx.compose.material3.TextButton(onClick = { step++ }) { Text(stringResource(R.string.skip)) }
        }
    }
}

private fun setupDescription(step: Int) = when (step) {
    0 -> R.string.setup_welcome_description
    1 -> R.string.setup_academic_year_description
    2 -> R.string.setup_subjects_description
    3 -> R.string.setup_schedule_description
    4 -> R.string.setup_private_description
    5 -> R.string.setup_notifications_description
    else -> R.string.setup_finish_description
}
