package com.eduflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Content meaning, not a color assigned to a screen or category. Subject colors stay separate. */
enum class EduFlowColorRole { BRAND, ACTIVE, ATTENTION, SUPPORTING_BRAND, NEUTRAL }

@Composable
fun eduFlowColor(role: EduFlowColorRole): Color = when (role) {
    EduFlowColorRole.BRAND -> MaterialTheme.colorScheme.primary
    EduFlowColorRole.ACTIVE -> MaterialTheme.colorScheme.secondary
    EduFlowColorRole.ATTENTION -> MaterialTheme.colorScheme.tertiary
    EduFlowColorRole.SUPPORTING_BRAND -> MaterialTheme.colorScheme.primaryContainer
    EduFlowColorRole.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}
