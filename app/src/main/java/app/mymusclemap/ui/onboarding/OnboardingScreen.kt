package app.mymusclemap.ui.onboarding

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
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val ONBOARDING_SCREEN = "onboarding-screen"
internal const val ONBOARDING_TITLE = "onboarding-title"
internal const val ONBOARDING_BODY = "onboarding-body"
internal const val ONBOARDING_PRIMARY = "onboarding-primary"
internal const val ONBOARDING_SKIP = "onboarding-skip"

@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    onContinue: () -> Unit,
    onCreatePlan: () -> Unit,
    onSkip: () -> Unit
) {
    val title = when (step) {
        OnboardingStep.Welcome -> stringResource(R.string.onboarding_welcome_title)
        OnboardingStep.CreatePlan -> stringResource(R.string.onboarding_setup_title)
    }
    val body = when (step) {
        OnboardingStep.Welcome -> stringResource(R.string.onboarding_welcome_body)
        OnboardingStep.CreatePlan -> stringResource(R.string.onboarding_setup_body)
    }
    val primary = when (step) {
        OnboardingStep.Welcome -> stringResource(R.string.onboarding_continue)
        OnboardingStep.CreatePlan -> stringResource(R.string.onboarding_create_plan)
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ONBOARDING_SCREEN),
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
                Text(
                    text = stringResource(R.string.onboarding_kicker).uppercase(),
                    style = AppTypeTokens.sectionKicker,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag(ONBOARDING_TITLE)
                        .semantics { heading() }
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                Text(
                    text = body,
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ONBOARDING_BODY)
                )
            }
            Button(
                onClick = {
                    if (step == OnboardingStep.Welcome) {
                        onContinue()
                    } else {
                        onCreatePlan()
                    }
                },
                shape = AppShapeTokens.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(ONBOARDING_PRIMARY)
            ) {
                Text(primary)
            }
            if (step == OnboardingStep.CreatePlan) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(ONBOARDING_SKIP)
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
    }
}
