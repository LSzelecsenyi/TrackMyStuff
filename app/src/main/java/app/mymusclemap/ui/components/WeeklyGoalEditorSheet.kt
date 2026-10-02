package app.mymusclemap.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.workout.PendingWeeklyGoal
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalStatus
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val WEEKLY_GOAL_SHEET = "weekly-goal-sheet"
internal const val WEEKLY_GOAL_VALUE = "weekly-goal-value"
internal const val WEEKLY_GOAL_DECREASE = "weekly-goal-decrease"
internal const val WEEKLY_GOAL_INCREASE = "weekly-goal-increase"
internal const val WEEKLY_GOAL_SAVE = "weekly-goal-save"
internal const val WEEKLY_GOAL_DISABLE = "weekly-goal-disable"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyGoalEditorSheet(
    status: WeeklyGoalStatus,
    onSave: (Int) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    val seed = when (val pending = status.pending) {
        is PendingWeeklyGoal.Update -> pending.workoutsPerWeek
        else -> status.current.goal ?: 3
    }
    var value by rememberSaveable(status.current.goal, status.pending) { mutableIntStateOf(seed) }
    val notice = when {
        status.pending is PendingWeeklyGoal.Disable ->
            stringResource(R.string.weekly_goal_turns_off_next_monday)
        status.current.goal != null -> stringResource(R.string.weekly_goal_next_monday)
        status.today != status.currentWeekStart -> stringResource(R.string.weekly_goal_grace)
        else -> null
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.sectionGap)
                .testTag(WEEKLY_GOAL_SHEET),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.weekly_goal_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (notice != null) {
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = notice,
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(AppDimens.sectionGap))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (value > WeeklyGoalLogic.MIN_GOAL) value -= 1 },
                    enabled = value > WeeklyGoalLogic.MIN_GOAL,
                    modifier = Modifier.testTag(WEEKLY_GOAL_DECREASE)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.weekly_goal_decrease)
                    )
                }
                Text(
                    text = value.toString(),
                    style = AppTypeTokens.statValue,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag(WEEKLY_GOAL_VALUE)
                )
                IconButton(
                    onClick = { if (value < WeeklyGoalLogic.MAX_GOAL) value += 1 },
                    enabled = value < WeeklyGoalLogic.MAX_GOAL,
                    modifier = Modifier.testTag(WEEKLY_GOAL_INCREASE)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.weekly_goal_increase)
                    )
                }
            }
            Text(
                text = stringResource(R.string.weekly_goal_per_week),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            Button(
                onClick = {
                    onSave(value)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WEEKLY_GOAL_SAVE)
            ) {
                Text(
                    text = stringResource(
                        if (status.current.goal == null) R.string.weekly_goal_set else R.string.action_save
                    )
                )
            }
            if (status.current.goal != null && status.pending !is PendingWeeklyGoal.Disable) {
                TextButton(
                    onClick = {
                        onDisable()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WEEKLY_GOAL_DISABLE)
                ) {
                    Text(stringResource(R.string.weekly_goal_turn_off))
                }
            }
        }
    }
}
