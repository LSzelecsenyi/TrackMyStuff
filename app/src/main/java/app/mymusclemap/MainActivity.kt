package app.mymusclemap

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mymusclemap.data.health.HealthConnectGateway
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.ui.navigation.WeightTrackerNavHost
import app.mymusclemap.ui.onboarding.OnboardingExit
import app.mymusclemap.ui.onboarding.OnboardingScreen
import app.mymusclemap.ui.onboarding.OnboardingViewModel
import app.mymusclemap.ui.pro.LocalFeatureEntitlements
import app.mymusclemap.ui.theme.WeightTrackerTheme

class MainActivity : ComponentActivity() {
    private val openPrivacyPolicy = mutableStateOf(false)
    private val overviewRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openPrivacyPolicy.value = HealthConnectGateway.isPermissionUsage(intent)
        acceptOverviewRequest(intent)
        val container = (application as WeightTrackerApplication).container
        setContent {
            val appearance by container.themePreferences.appearance.collectAsStateWithLifecycle(
                initialValue = AppearanceSettings.Default
            )
            val openPrivacy by openPrivacyPolicy
            val openOverviewRequest by overviewRequest
            var decision by remember { mutableStateOf<FirstRunDecision?>(null) }
            var openNewTemplate by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                decision = container.firstRunCoordinator.prepare()
            }
            CompositionLocalProvider(
                LocalFeatureEntitlements provides container.featureEntitlements
            ) {
                WeightTrackerTheme(appearance = appearance) {
                    when (decision) {
                        null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            content = {}
                        )
                        FirstRunDecision.ShowOnboarding -> {
                            val viewModel: OnboardingViewModel = viewModel(factory = container.viewModelFactory)
                            val step by viewModel.step.collectAsStateWithLifecycle()
                            val exit by viewModel.exit.collectAsStateWithLifecycle()
                            LaunchedEffect(exit) {
                                when (exit) {
                                    OnboardingExit.OpenTemplateEditor -> {
                                        openNewTemplate = true
                                        decision = FirstRunDecision.Ready
                                    }
                                    OnboardingExit.Dismiss -> {
                                        decision = FirstRunDecision.Ready
                                    }
                                    null -> Unit
                                }
                            }
                            OnboardingScreen(
                                step = step,
                                onContinue = viewModel::onContinue,
                                onCreatePlan = viewModel::onCreatePlan,
                                onSkip = viewModel::onSkip,
                                onSetWeeklyGoal = viewModel::onWeeklyGoalSet,
                                onSkipWeeklyGoal = viewModel::onWeeklyGoalSkipped
                            )
                        }
                        FirstRunDecision.Ready -> WeightTrackerNavHost(
                            factory = container.viewModelFactory,
                            dateProvider = container.dateProvider,
                            openNewTemplate = openNewTemplate,
                            onOpenedNewTemplate = { openNewTemplate = false },
                            openPrivacyPolicy = openPrivacy,
                            onOpenedPrivacyPolicy = { openPrivacyPolicy.value = false },
                            openOverviewRequest = openOverviewRequest
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as WeightTrackerApplication).container
        lifecycleScope.launch {
            container.refreshFounderProgramFromStore()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (HealthConnectGateway.isPermissionUsage(intent)) {
            openPrivacyPolicy.value = true
        }
        acceptOverviewRequest(intent)
    }

    private fun acceptOverviewRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_OVERVIEW, false) == true) {
            overviewRequest.intValue += 1
        }
    }

    companion object {
        const val EXTRA_OPEN_OVERVIEW = "app.mymusclemap.OPEN_OVERVIEW"
    }
}
