package app.mymusclemap

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mymusclemap.data.health.HealthConnectGateway
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.founder.AppLaunchStage
import app.mymusclemap.ui.founder.FounderProgramViewModel
import app.mymusclemap.ui.navigation.WeightTrackerNavHost
import app.mymusclemap.ui.onboarding.OnboardingExit
import app.mymusclemap.ui.onboarding.OnboardingScreen
import app.mymusclemap.ui.onboarding.OnboardingViewModel
import app.mymusclemap.ui.pro.LocalFeatureEntitlements
import app.mymusclemap.ui.theme.WeightTrackerTheme

class MainActivity : ComponentActivity() {
    @Volatile
    private var splashReady = false
    private val openPrivacyPolicy = mutableStateOf(false)
    private val overviewRequest = mutableIntStateOf(0)
    private val activeWorkoutSessionId = mutableStateOf<Long?>(null)
    private val activeWorkoutGeneration = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !splashReady }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openPrivacyPolicy.value = HealthConnectGateway.isPermissionUsage(intent)
        acceptOverviewRequest(intent)
        if (savedInstanceState == null) {
            acceptActiveWorkoutRequest(intent)
        }
        val container = (application as WeightTrackerApplication).container
        setContent {
            val appearance by container.themePreferences.appearance.collectAsStateWithLifecycle(
                initialValue = AppearanceSettings.Default
            )
            val openPrivacy by openPrivacyPolicy
            val openOverviewRequest by overviewRequest
            val openActiveSessionId by activeWorkoutSessionId
            val openActiveGeneration by activeWorkoutGeneration
            var stage by remember { mutableStateOf<AppLaunchStage?>(null) }
            var openNewTemplate by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                try {
                    val firstRun = container.firstRunCoordinator.prepare()
                    stage = if (firstRun == FirstRunDecision.ShowOnboarding) {
                        AppLaunchStage.Onboarding
                    } else {
                        AppLaunchStage.App
                    }
                } catch (error: Throwable) {
                    stage = AppLaunchStage.App
                    throw error
                }
            }
            if (stage != null) {
                SideEffect { splashReady = true }
            }
            CompositionLocalProvider(
                LocalFeatureEntitlements provides container.featureEntitlements
            ) {
                WeightTrackerTheme(appearance = appearance) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                    when (stage) {
                        null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            content = {}
                        )
                        AppLaunchStage.Onboarding -> {
                            val viewModel: OnboardingViewModel = viewModel(factory = container.viewModelFactory)
                            val step by viewModel.step.collectAsStateWithLifecycle()
                            val exit by viewModel.exit.collectAsStateWithLifecycle()
                            val founderJoining by viewModel.founderJoining.collectAsStateWithLifecycle()
                            val founderJoinNotice by viewModel.founderJoinNotice.collectAsStateWithLifecycle()
                            LaunchedEffect(exit) {
                                val chosen = exit ?: return@LaunchedEffect
                                openNewTemplate = chosen == OnboardingExit.OpenTemplateEditor
                                stage = AppLaunchStage.App
                            }
                            OnboardingScreen(
                                step = step,
                                onContinue = viewModel::onContinue,
                                onCreatePlan = viewModel::onCreatePlan,
                                onSkip = viewModel::onSkip,
                                onSetWeeklyGoal = viewModel::onWeeklyGoalSet,
                                onSkipWeeklyGoal = viewModel::onWeeklyGoalSkipped,
                                founderRules = container.founderProgramRules,
                                onJoinFounder = viewModel::onJoinFounder,
                                onDeclineFounder = viewModel::onDeclineFounder,
                                founderJoining = founderJoining,
                                founderJoinNotice = founderJoinNotice
                            )
                        }
                        AppLaunchStage.App -> {
                            val founderViewModel: FounderProgramViewModel =
                                viewModel(factory = container.viewModelFactory)
                            WeightTrackerNavHost(
                                factory = container.viewModelFactory,
                                dateProvider = container.dateProvider,
                                openNewTemplate = openNewTemplate,
                                onOpenedNewTemplate = { openNewTemplate = false },
                                openPrivacyPolicy = openPrivacy,
                                onOpenedPrivacyPolicy = { openPrivacyPolicy.value = false },
                                openOverviewRequest = openOverviewRequest,
                                openActiveWorkoutSessionId = openActiveSessionId,
                                openActiveWorkoutGeneration = openActiveGeneration,
                                founderAvailability = container.founderProgramAvailability,
                                founderProgram = founderViewModel,
                                currentEntitlement = container.entitlementComposer::resolve,
                                entitlementChanges = container.featureEntitlements.changes()
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as WeightTrackerApplication).container
        container.bindStrictSignIn(this)
        container.activeWorkoutNotifications.refresh()
        lifecycleScope.launch {
            container.refreshFounderProgramFromStore()
            container.refreshFounderAuthority()
        }
    }

    override fun onStop() {
        (application as WeightTrackerApplication).container.unbindStrictSignIn(this)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (HealthConnectGateway.isPermissionUsage(intent)) {
            openPrivacyPolicy.value = true
        }
        acceptOverviewRequest(intent)
        acceptActiveWorkoutRequest(intent)
    }

    private fun acceptActiveWorkoutRequest(intent: Intent?) {
        if (intent == null || !intent.hasExtra(EXTRA_OPEN_ACTIVE_WORKOUT)) return
        val sessionId = intent.getLongExtra(EXTRA_OPEN_ACTIVE_WORKOUT, -1L)
        intent.removeExtra(EXTRA_OPEN_ACTIVE_WORKOUT)
        if (sessionId <= 0L) return
        val container = (application as WeightTrackerApplication).container
        lifecycleScope.launch {
            val status = container.workoutSessionRepository.getAggregate(sessionId)?.session?.status
            activeWorkoutSessionId.value = if (status == SessionStatus.IN_PROGRESS) sessionId else null
            activeWorkoutGeneration.intValue += 1
        }
    }

    private fun acceptOverviewRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_OVERVIEW, false) == true) {
            overviewRequest.intValue += 1
        }
    }

    companion object {
        const val EXTRA_OPEN_OVERVIEW = "app.mymusclemap.OPEN_OVERVIEW"
        const val EXTRA_OPEN_ACTIVE_WORKOUT = "app.mymusclemap.OPEN_ACTIVE_WORKOUT"
    }
}
