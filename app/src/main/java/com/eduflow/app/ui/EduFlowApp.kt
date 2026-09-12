package com.eduflow.app.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eduflow.app.ui.screens.MoreScreen
import com.eduflow.app.ui.screens.ScheduleScreen
import com.eduflow.app.ui.screens.TasksScreen
import com.eduflow.app.ui.screens.LessonDetailsScreen
import com.eduflow.app.ui.screens.TaskEditorScreen
import com.eduflow.app.ui.screens.SubjectsManagementScreen
import com.eduflow.app.ui.screens.SlotEditorScreen
import com.eduflow.app.ui.screens.TimetableSetupScreen
import com.eduflow.app.ui.screens.PrivateLessonsScreen
import com.eduflow.app.ui.screens.PrivateLessonEditorScreen
import com.eduflow.app.ui.screens.SubjectDetailsScreen
import com.eduflow.app.ui.screens.NotificationSettingsScreen
import com.eduflow.app.ui.screens.BackupScreen
import com.eduflow.app.ui.screens.AboutScreen
import com.eduflow.app.ui.screens.AcademicYearScreen
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.R
import androidx.compose.ui.res.stringResource

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    SCHEDULE("schedule", R.string.nav_schedule, Icons.AutoMirrored.Filled.List),
    TASKS("tasks", R.string.nav_tasks, Icons.Default.CheckCircle),
    SUBJECTS("subjects", R.string.nav_subjects, Icons.Default.AccountBox),
    MORE("more", R.string.nav_more, Icons.Default.MoreVert)
}

@Composable
fun EduFlowApp(externalRoute: ExternalRouteEvent? = null, onExternalRouteConsumed: (Long) -> Unit = {}) {
    val generation by com.eduflow.app.data.AppDataSession.generation.collectAsState()
    androidx.compose.runtime.key(generation) { EduFlowSessionApp(externalRoute, onExternalRouteConsumed) }
}

@Composable
private fun EduFlowSessionApp(externalRoute: ExternalRouteEvent?, onExternalRouteConsumed: (Long) -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val database = remember(context) { EduFlowDatabase.getInstance(context) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val rootRoutes = remember { TopLevelDestination.entries.map { it.route }.toSet() }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = currentRoute !in rootRoutes
        controller.isAppearanceLightNavigationBars = true
    }
    LaunchedEffect(externalRoute?.id) { externalRoute?.let { event -> onExternalRouteConsumed(event.id); navController.navigate(event.route) { launchSingleTop = true } } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentRoute in TopLevelDestination.entries.map { it.route })
            NavigationBar(
                tonalElevation = 0.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (currentRoute != destination.route) navController.navigate(destination.route) {
                                popUpTo(TopLevelDestination.SCHEDULE.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.SCHEDULE.route,
            modifier = androidx.compose.ui.Modifier.padding(contentPadding),
            enterTransition = { fadeIn(animationSpec = tween(150)) },
            exitTransition = { fadeOut(animationSpec = tween(120)) },
            popEnterTransition = { fadeIn(animationSpec = tween(150)) },
            popExitTransition = { fadeOut(animationSpec = tween(120)) }
        ) {
            composable(TopLevelDestination.SCHEDULE.route) { ScheduleScreen(database, navController) }
            composable(TopLevelDestination.TASKS.route) { TasksScreen(database, navController) }
            composable(TopLevelDestination.SUBJECTS.route) { SubjectsManagementScreen(database, navController) }
            composable(TopLevelDestination.MORE.route) { MoreScreen(navController) }
            composable("timetable_setup") { TimetableSetupScreen(database, navController) }
            composable("timetable_setup/{templateId}") { entry ->
                entry.arguments?.getString("templateId")?.toLongOrNull()?.let { id -> SlotEditorScreen(database, id, navController) }
            }
            composable("lesson/{lessonId}?focus={focus}", arguments = listOf(androidx.navigation.navArgument("focus") { type = androidx.navigation.NavType.StringType; defaultValue = "" })) { entry -> entry.arguments?.getString("lessonId")?.toLongOrNull()?.let { LessonDetailsScreen(database, it, navController, entry.arguments?.getString("focus").orEmpty()) } }
            composable("private_lessons") { PrivateLessonsScreen(database, navController) }
            composable("notification_settings") { NotificationSettingsScreen(navController) }
            composable("backup") { BackupScreen(navController) }
            composable("about") { AboutScreen(navController) }
            composable("academic_year") { AcademicYearScreen(navController) }
            composable("private/new") { PrivateLessonEditorScreen(database, null, false, navController) }
            composable("private/oneoff") { PrivateLessonEditorScreen(database, null, true, navController) }
            composable("private/{privateId}") { entry -> entry.arguments?.getString("privateId")?.toLongOrNull()?.let { PrivateLessonEditorScreen(database, it, false, navController) } }
            composable("subject/{subjectId}") { entry -> entry.arguments?.getString("subjectId")?.toLongOrNull()?.let { SubjectDetailsScreen(database, it, navController) } }
            composable("task/{taskId}/{originId}") { entry ->
                val taskId = entry.arguments?.getString("taskId")?.takeUnless { it == "new" }?.toLongOrNull()
                val token = entry.arguments?.getString("originId")
                val originId = token?.takeUnless { it == "none" || it.startsWith("subject_") }?.toLongOrNull()
                val subjectId = token?.removePrefix("subject_")?.takeIf { token.startsWith("subject_") }?.toLongOrNull()
                TaskEditorScreen(database, taskId, originId, navController, subjectId)
            }
        }
    }
}
