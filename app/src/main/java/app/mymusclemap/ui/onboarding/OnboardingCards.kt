package app.mymusclemap.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.onboarding.OnboardingChecklist
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

@Composable
fun OnboardingReminderCard(
    checklist: OnboardingChecklist,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ONBOARDING_REMINDER),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(AppDimens.heroPadding)) {
            Text(
                text = stringResource(R.string.onboarding_reminder_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            OnboardingChecklistRow(
                done = checklist.planCreated,
                label = stringResource(R.string.onboarding_step_plan)
            )
            OnboardingChecklistRow(
                done = checklist.firstWorkoutDone,
                label = stringResource(R.string.onboarding_step_workout)
            )
            OnboardingChecklistRow(
                done = checklist.heatmapDone,
                label = stringResource(R.string.onboarding_step_heatmap)
            )
            OnboardingChecklistRow(
                done = checklist.weightDone,
                label = stringResource(R.string.onboarding_step_weight)
            )
            OnboardingChecklistRow(
                done = checklist.historyDone,
                label = stringResource(R.string.onboarding_step_history)
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onContinue,
                    shape = AppShapeTokens.button,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(ONBOARDING_REMINDER_CONTINUE)
                ) {
                    Text(stringResource(R.string.onboarding_reminder_continue))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(ONBOARDING_REMINDER_DISMISS)
                ) {
                    Text(stringResource(R.string.onboarding_reminder_dismiss))
                }
            }
        }
    }
}

@Composable
fun OnboardingTipCard(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(AppDimens.heroPadding)) {
            Text(
                text = title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = body,
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = onConfirm,
                shape = AppShapeTokens.button,
                modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
            ) {
                Text(stringResource(R.string.onboarding_got_it))
            }
        }
    }
}

@Composable
private fun OnboardingChecklistRow(
    done: Boolean,
    label: String
) {
    val mark = if (done) "●" else "○"
    Text(
        text = "$mark  $label",
        style = AppTypeTokens.statSecondary,
        color = if (done) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}
