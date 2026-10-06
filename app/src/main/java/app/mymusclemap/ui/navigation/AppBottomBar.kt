package app.mymusclemap.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.mymusclemap.domain.theme.StrictBrandTokens
import app.mymusclemap.domain.theme.StrictNavigationSelection
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.StrictBrand

internal const val BOTTOM_BAR = "app-bottom-bar"
internal const val BOTTOM_OVERVIEW = "app-bottom-overview"
internal const val BOTTOM_WORKOUT_ACTION = "app-bottom-workout-action"
internal const val BOTTOM_STATISTICS = "app-bottom-statistics"
internal const val BOTTOM_JOURNAL = "app-bottom-journal"

@Composable
fun AppBottomBar(
    selectedRoute: String?,
    hasActiveSession: Boolean,
    onOverview: () -> Unit,
    onStatistics: () -> Unit,
    onJournal: () -> Unit,
    onWorkoutAction: () -> Unit,
    modifier: Modifier = Modifier,
    highlightWorkoutAction: Boolean = false,
    onWorkoutActionBounds: (Rect) -> Unit = {}
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
                highlight = highlightWorkoutAction,
                onBoundsInRoot = onWorkoutActionBounds,
                modifier = Modifier
                    .weight(1f)
                    .testTag(BOTTOM_WORKOUT_ACTION)
            )
            BottomTab(
                label = stringResource(R.string.statistics_title),
                icon = Icons.Outlined.BarChart,
                selected = selectedRoute == AppRoutes.STATISTICS,
                onClick = onStatistics,
                modifier = Modifier
                    .weight(1f)
                    .testTag(BOTTOM_STATISTICS)
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
    highlight: Boolean,
    onBoundsInRoot: (Rect) -> Unit,
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
        val actionContainer = StrictBrand.actionContainer()
        val actionContent = StrictBrand.onAction()
        val highlightColor = if (actionContainer == StrictBrand.lime) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.primary
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .onGloballyPositioned { coordinates ->
                    onBoundsInRoot(coordinates.boundsInRoot())
                }
                .then(
                    if (highlight) {
                        Modifier.border(2.dp, highlightColor, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(actionContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = actionContent,
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
    val scheme = MaterialTheme.colorScheme
    val selection = StrictBrandTokens.navigationSelection(scheme.background.toArgb(), scheme.primary.toArgb())
    val unselected = scheme.onSurfaceVariant
    val labelColor = when {
        !selected -> unselected
        selection == StrictNavigationSelection.FieldBehindIcon -> StrictBrand.blue
        selection == StrictNavigationSelection.Ink -> StrictBrand.lime
        else -> scheme.onSurface
    }
    val iconColor = when {
        !selected -> unselected
        selection == StrictNavigationSelection.FieldBehindIcon -> StrictBrand.dark
        selection == StrictNavigationSelection.Ink -> StrictBrand.lime
        else -> scheme.onSurface
    }
    val indicator = if (selection == StrictNavigationSelection.Ink || selection == StrictNavigationSelection.Scheme) {
        scheme.primary
    } else {
        Color.Transparent
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
        if (selected && indicator != Color.Transparent) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(AppDimens.navIndicatorWidth)
                    .height(AppDimens.navIndicatorThickness)
                    .background(indicator)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (selected && selection == StrictNavigationSelection.FieldBehindIcon) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .defaultMinSize(minWidth = 40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(StrictBrand.lime)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
