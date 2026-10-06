package app.mymusclemap.ui.achievements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.PendingCelebration

internal const val CELEBRATION_DIALOG = "celebration-dialog"
internal const val CELEBRATION_CONFIRM = "celebration-confirm"

@Composable
fun CelebrationDialog(
    celebrations: List<PendingCelebration>,
    onDismiss: () -> Unit
) {
    if (celebrations.isEmpty()) return
    val title = if (celebrations.size == 1) {
        celebrationTitle(celebrations.first())
    } else {
        stringResource(R.string.achievements_title)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(CELEBRATION_CONFIRM)
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CELEBRATION_DIALOG)
            ) {
                celebrations.forEachIndexed { index, celebration ->
                    if (celebrations.size > 1) {
                        Text(
                            text = celebrationTitle(celebration),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(text = celebrationBody(celebration))
                    if (index != celebrations.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    )
}

@Composable
fun celebrationTitle(celebration: PendingCelebration): String {
    return when (celebration) {
        is PendingCelebration.HistoryRecognized -> stringResource(R.string.celebration_history_title)
        is PendingCelebration.WeeklyGoalCompleted -> stringResource(R.string.celebration_weekly_title)
        is PendingCelebration.WorkoutCountUnlocked -> stringResource(R.string.celebration_workout_title)
        is PendingCelebration.TargetWeightMilestone -> when {
            celebration.includesLifetimeUnlock -> stringResource(R.string.achievement_on_target_name)
            celebration.milestone == app.mymusclemap.domain.achievements.WeightMilestone.HALFWAY ->
                stringResource(R.string.celebration_weight_halfway_title)
            celebration.milestone == app.mymusclemap.domain.achievements.WeightMilestone.REMAINING_5 ->
                stringResource(R.string.celebration_weight_5_title)
            celebration.milestone == app.mymusclemap.domain.achievements.WeightMilestone.REMAINING_2 ->
                stringResource(R.string.celebration_weight_2_title)
            celebration.milestone == app.mymusclemap.domain.achievements.WeightMilestone.REMAINING_1 ->
                stringResource(R.string.celebration_weight_1_title)
            else -> stringResource(R.string.celebration_weight_reached_title)
        }
    }
}

@Composable
fun celebrationBody(celebration: PendingCelebration): String {
    return when (celebration) {
        is PendingCelebration.HistoryRecognized -> pluralStringResource(
            R.plurals.celebration_history_body,
            celebration.badgeCount,
            celebration.badgeCount
        )
        is PendingCelebration.WeeklyGoalCompleted -> stringResource(
            R.string.celebration_weekly_body,
            celebration.completed
        )
        is PendingCelebration.WorkoutCountUnlocked -> stringResource(
            R.string.celebration_workout_body,
            celebration.threshold
        )
        is PendingCelebration.TargetWeightMilestone -> when (celebration.milestone) {
            app.mymusclemap.domain.achievements.WeightMilestone.HALFWAY ->
                stringResource(R.string.celebration_weight_halfway_body)
            app.mymusclemap.domain.achievements.WeightMilestone.REMAINING_5 ->
                stringResource(R.string.celebration_weight_5_body)
            app.mymusclemap.domain.achievements.WeightMilestone.REMAINING_2 ->
                stringResource(R.string.celebration_weight_2_body)
            app.mymusclemap.domain.achievements.WeightMilestone.REMAINING_1 ->
                stringResource(R.string.celebration_weight_1_body)
            app.mymusclemap.domain.achievements.WeightMilestone.REACHED ->
                stringResource(R.string.celebration_weight_reached_body)
        }
    }
}
