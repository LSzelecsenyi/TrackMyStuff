package app.mymusclemap.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.theme.AppDimens

internal const val BOTTOM_BAR = "app-bottom-bar"
internal const val BOTTOM_OVERVIEW = "app-bottom-overview"
internal const val BOTTOM_WORKOUT_ACTION = "app-bottom-workout-action"
internal const val BOTTOM_JOURNAL = "app-bottom-journal"

@Composable
fun AppBottomBar(
    selectedRoute: String?,
    hasActiveSession: Boolean,
    onOverview: () -> Unit,
    onJournal: () -> Unit,
    onWorkoutAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val workoutLabel = stringResource(
        if (hasActiveSession) {
            R.string.action_resume_workout
        } else {
            R.string.action_start_workout
        }
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag(BOTTOM_BAR)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.strokeThin)
                .background(outline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.navBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab(
                label = stringResource(R.string.nav_dashboard),
                icon = Icons.Outlined.Home,
                selected = selectedRoute == AppRoutes.OVERVIEW,
                onClick = onOverview,
                modifier = Modifier
                    .weight(1f)
                    .testTag(BOTTOM_OVERVIEW)
            )
            WorkoutActionButton(
                label = workoutLabel,
                onClick = onWorkoutAction,
                modifier = Modifier
                    .weight(1f)
                    .testTag(BOTTOM_WORKOUT_ACTION)
            )
            BottomTab(
                label = stringResource(R.string.nav_journal),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                selected = selectedRoute == AppRoutes.JOURNAL,
                onClick = onJournal,
                modifier = Modifier
                    .weight(1f)
                    .testTag(BOTTOM_JOURNAL)
            )
        }
    }
}

@Composable
private fun WorkoutActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun BottomTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = label
            }
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(AppDimens.navIndicatorWidth)
                    .height(AppDimens.navIndicatorThickness)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
