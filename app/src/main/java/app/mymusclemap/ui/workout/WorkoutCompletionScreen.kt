package app.mymusclemap.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.mymusclemap.R
import app.mymusclemap.domain.workout.WorkoutCompletionSummary
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val WORKOUT_COMPLETE_SCREEN = "workout-complete-screen"
internal const val WORKOUT_COMPLETE_TITLE = "workout-complete-title"
internal const val WORKOUT_COMPLETE_SUMMARY = "workout-complete-summary"
internal const val WORKOUT_COMPLETE_BACK = "workout-complete-back"
internal const val WORKOUT_COMPLETE_HEATMAP = "workout-complete-heatmap"

@Composable
fun WorkoutCompletionScreen(
    summary: WorkoutCompletionSummary,
    onBackToOverview: () -> Unit,
    playAnimation: Boolean = true,
    showHeatmapCta: Boolean = false,
    onSeeWhatYouTrained: () -> Unit = onBackToOverview
) {
    BackHandler(onBack = onBackToOverview)
    val stats = stringResource(
        R.string.workout_complete_summary,
        summary.exerciseCount,
        summary.completedSetCount,
        summary.durationLabel
    )
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WORKOUT_COMPLETE_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding, vertical = AppDimens.sectionGap)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WorkoutCompletionMark(
                    contentDescription = stringResource(R.string.workout_complete_mark_a11y),
                    playAnimation = playAnimation
                )
                Spacer(Modifier.height(AppDimens.sectionGap))
                Text(
                    text = stringResource(R.string.workout_complete_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag(WORKOUT_COMPLETE_TITLE)
                        .semantics { heading() }
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = stats,
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(WORKOUT_COMPLETE_SUMMARY)
                )
            }
            if (showHeatmapCta) {
                Button(
                    onClick = onSeeWhatYouTrained,
                    shape = AppShapeTokens.button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(WORKOUT_COMPLETE_HEATMAP)
                ) {
                    Text(stringResource(R.string.workout_complete_see_trained))
                }
                TextButton(
                    onClick = onBackToOverview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(WORKOUT_COMPLETE_BACK)
                ) {
                    Text(stringResource(R.string.workout_complete_back))
                }
            } else {
                Button(
                    onClick = onBackToOverview,
                    shape = AppShapeTokens.button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(WORKOUT_COMPLETE_BACK)
                ) {
                    Text(stringResource(R.string.workout_complete_back))
                }
            }
        }
    }
}
