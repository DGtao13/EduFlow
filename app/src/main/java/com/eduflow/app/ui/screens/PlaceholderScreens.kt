@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.eduflow.app.R
import com.eduflow.app.ui.EduFlowRootTopAppBar
import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.data.AcademicYearSettingsRepository
import com.eduflow.app.ui.FeedbackChooser

@Composable fun MoreScreen(navController: NavController) {
    val context = LocalContext.current
    val academicYear by remember { AcademicYearSettingsRepository(context) }.settings.collectAsState(AcademicYearSettings())
    var feedbackOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Scaffold(topBar = { EduFlowRootTopAppBar(stringResource(R.string.nav_more)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SettingsSection(stringResource(R.string.organization)) {
                Column {
                    SettingsRow(stringResource(R.string.nav_subjects), stringResource(R.string.subjects_supporting), Icons.Default.AccountBox, onClick = { navController.navigate("subjects") })
                    SettingsRow(stringResource(R.string.private_lessons), stringResource(R.string.private_lessons_supporting), Icons.Default.Person, onClick = { navController.navigate("private_lessons") })
                    SettingsRow(stringResource(R.string.academic_year), "${academicYear.startDate} – ${academicYear.endDate}", Icons.Default.CheckCircle, onClick = { navController.navigate("academic_year") })
                }
            }
            SettingsSection(stringResource(R.string.settings)) {
                SettingsRow(stringResource(R.string.notifications), stringResource(R.string.notifications_supporting), Icons.Default.Notifications, onClick = { navController.navigate("notification_settings") }, accent = com.eduflow.app.ui.theme.EduFlowColorRole.ATTENTION)
            }
            SettingsSection(stringResource(R.string.data_section)) {
                SettingsRow(stringResource(R.string.backup_restore), stringResource(R.string.backup_supporting), Icons.Default.MoreVert, onClick = { navController.navigate("backup") })
            }
            SettingsSection(stringResource(R.string.about_section)) {
                SettingsRow(stringResource(R.string.about_eduflow), stringResource(R.string.about_supporting), Icons.Default.Info, onClick = { navController.navigate("about") }, accent = com.eduflow.app.ui.theme.EduFlowColorRole.BRAND)
                SettingsRow(stringResource(R.string.setup_guide), stringResource(R.string.setup_guide_supporting), Icons.AutoMirrored.Filled.List, onClick = { navController.navigate("setup_guide") }, accent = com.eduflow.app.ui.theme.EduFlowColorRole.ACTIVE)
                SettingsRow(stringResource(R.string.feedback_action), stringResource(R.string.feedback_supporting), Icons.Default.Edit, onClick = { feedbackOpen = true })
            }
        }
    }
    if (feedbackOpen) FeedbackChooser { feedbackOpen = false }
}
