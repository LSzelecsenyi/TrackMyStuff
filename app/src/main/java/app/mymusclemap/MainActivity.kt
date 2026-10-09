package app.mymusclemap

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.mymusclemap.data.account.VerifiedAccountProfile
import app.mymusclemap.data.auth.StrictSignInResult
import app.mymusclemap.ui.auth.WelcomeSignInScreen
import app.mymusclemap.ui.theme.StrictBrand
import app.mymusclemap.domain.billing.manageSubscriptionUrl
import app.mymusclemap.domain.locale.AppLanguageApplicator
import app.mymusclemap.domain.locale.AppLanguagePolicy
import app.mymusclemap.domain.locale.SystemLanguage
import app.mymusclemap.ui.widget.refreshHeatmapWidgets
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

class MainActivity : AppCompatActivity() {
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
        val app = application as WeightTrackerApplication
        val container = app.container
        setContent {
            if (container == null) {
                SideEffect { splashReady = true }
                WelcomeGate(app)
                return@setContent
            }
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
                            val billingState by container.billing.screen.collectAsStateWithLifecycle()
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
                                entitlementChanges = container.featureEntitlements.changes(),
                                founderApprovalCelebration = container::pendingFounderApprovalCelebration,
                                onAcknowledgeFounderApproval = container::acknowledgeFounderApprovalCelebration,
                                proBenefitsStatus = container::proBenefitsStatus,
                                proDiscovery = container::proDiscoverySnapshot,
                                promotionNow = container::promotionNow,
                                onActivateProDiscovery = container::activateProDiscovery,
                                onDismissProDiscoveryOffer = container::dismissProDiscoveryOffer,
                                onDismissProDiscoveryWarning = container::dismissProDiscoveryWarning,
                                onPromotionClock = container::notePromotionClock,
                                billingScreen = billingState,
                                paidSubscription = container::paidSubscriptionSnapshot,
                                onSelectBillingOffer = container::selectBillingOffer,
                                onSubscribe = {
                                    lifecycleScope.launch { container.launchBilling(this@MainActivity) }
                                },
                                onManageSubscription = { productId ->
                                    val url = manageSubscriptionUrl(
                                        packageName,
                                        productId
                                    )
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    )
                                },
                                accountName = app.accounts.profile()?.displayName,
                                accountEmail = app.accounts.profile()?.email,
                                showLanguageSetting = AppLanguagePolicy.systemAllowsChoice(
                                    SystemLanguage.primary(this@MainActivity)
                                ),
                                selectedLanguage = app.resolvedLanguage,
                                onLanguageSelected = { language ->
                                    lifecycleScope.launch {
                                        app.languages.write(language)
                                        app.rememberLanguage(language)
                                        AppLanguageApplicator.apply(language)
                                        refreshHeatmapWidgets(this@MainActivity)
                                    }
                                },
                                onSignOut = {
                                    lifecycleScope.launch {
                                        app.signIn.auth.logout()
                                        app.accounts.clearActive()
                                        app.restartProcess()
                                    }
                                }
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
        val app = application as WeightTrackerApplication
        app.signIn.bind(this)
        val container = app.container ?: return
        container.bindStrictSignIn(this)
        container.bindBilling(this)
        container.activeWorkoutNotifications.refresh()
        lifecycleScope.launch {
            container.refreshFounderProgramFromStore()
            container.refreshFounderAuthority()
            container.refreshPromotionAvailability()
            container.refreshBilling()
        }
    }

    override fun onStop() {
        (application as WeightTrackerApplication).signIn.unbind(this)
        (application as WeightTrackerApplication).container?.unbindStrictSignIn(this)
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
        val container = (application as WeightTrackerApplication).container ?: return
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

@Composable
private fun WelcomeGate(app: WeightTrackerApplication) {
    var busy by remember { androidx.compose.runtime.mutableStateOf(false) }
    val offline = app.startupDecision().offline
    var message by remember { mutableStateOf<String?>(null) }
    var claim by remember { androidx.compose.runtime.mutableStateOf<VerifiedAccountProfile?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StrictBrand.dark)
    ) {
        WelcomeSignInScreen(
            busy = busy,
            message = message ?: if (offline) stringResource(R.string.welcome_offline_first) else null,
            claimOpen = claim != null,
            onContinue = {
                if (busy) return@WelcomeSignInScreen
                if (app.startupDecision().offline) {
                    message = context.getString(R.string.welcome_offline_first)
                    return@WelcomeSignInScreen
                }
                busy = true
                message = null
                scope.launch {
                    when (val result = app.signIn.continueWithGoogle()) {
                        is StrictSignInResult.SignedIn -> {
                            val profile = VerifiedAccountProfile(
                                userId = result.userId,
                                email = result.email,
                                displayName = result.displayName
                            )
                            if (app.accounts.legacyUnclaimed()) {
                                claim = profile
                            } else {
                                app.accounts.activate(profile)
                                app.restartProcess()
                            }
                        }
                        StrictSignInResult.Cancelled ->
                            message = context.getString(R.string.welcome_canceled)
                        StrictSignInResult.Unavailable ->
                            message = context.getString(R.string.welcome_unavailable)
                        StrictSignInResult.InvalidGoogleToken ->
                            message = context.getString(R.string.welcome_invalid)
                        StrictSignInResult.GoogleNotConfigured ->
                            message = context.getString(R.string.welcome_not_configured)
                        StrictSignInResult.GoogleFailed,
                        StrictSignInResult.BackendRejected ->
                            message = context.getString(R.string.welcome_failed)
                    }
                    busy = false
                }
            },
            onConfirmClaim = {
                val profile = claim ?: return@WelcomeSignInScreen
                app.accounts.claimLegacy(profile.userId)
                app.accounts.activate(profile)
                app.restartProcess()
            },
            onCancelClaim = {
                claim = null
                scope.launch { app.signIn.auth.logout() }
            }
        )
    }
}

