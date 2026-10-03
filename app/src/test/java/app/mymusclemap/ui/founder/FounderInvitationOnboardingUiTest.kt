package app.mymusclemap.ui.founder

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.ui.onboarding.ONBOARDING_PRIMARY
import app.mymusclemap.ui.onboarding.ONBOARDING_SCREEN
import app.mymusclemap.ui.onboarding.ONBOARDING_SKIP
import app.mymusclemap.ui.onboarding.OnboardingScreen
import app.mymusclemap.ui.onboarding.OnboardingViewModel
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class FounderInvitationOnboardingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val rules = FounderProgramRules(
        temporaryProWorkoutCount = 1,
        founderWorkoutCount = 2,
        requiredDistinctWorkoutDays = 1,
        qualificationWindowDays = 45,
        feedbackRequired = true,
        testerAnalyticsReportRequired = true
    )
    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var acknowledgements: FounderMilestoneAcknowledgementStore
    private lateinit var coordinator: FirstRunCoordinator

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        themePreferences = ThemePreferences(context)
        themePreferences.clearOnboardingProgress()
        acknowledgements = FounderMilestoneAcknowledgementStore(context)
        acknowledgements.save(FounderMilestoneAcknowledgements())
        coordinator = FirstRunCoordinator(
            database,
            ExerciseRepository(
                dao = database.exerciseDao(),
                clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)
            ),
            themePreferences
        )
    }

    @After
    fun tearDown() = runBlocking {
        themePreferences.clearOnboardingProgress()
        acknowledgements.save(FounderMilestoneAcknowledgements())
        database.close()
    }

    @Test
    fun onboardingStaysOnScreenUntilItFinishesThenShowsOneInvitation() {
        val decision = runBlocking { coordinator.prepare() }
        assertEquals(FirstRunDecision.ShowOnboarding, decision)
        val viewModel = OnboardingViewModel(coordinator, founderInvitations = acknowledgements)
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                var stage by remember { mutableStateOf(AppLaunchStage.Onboarding) }
                val step by viewModel.step.collectAsStateWithLifecycle()
                val exit by viewModel.exit.collectAsStateWithLifecycle()
                LaunchedEffect(exit) {
                    if (exit == null) return@LaunchedEffect
                    stage = appLaunchStage(
                        FirstRunDecision.Ready,
                        FounderProgramStatus.NotEnrolled,
                        acknowledgements.load()
                    )
                }
                when (stage) {
                    AppLaunchStage.Onboarding -> OnboardingScreen(
                        step = step,
                        onContinue = viewModel::onContinue,
                        onCreatePlan = viewModel::onCreatePlan,
                        onSkip = viewModel::onSkip,
                        onSetWeeklyGoal = {},
                        onSkipWeeklyGoal = viewModel::onWeeklyGoalSkipped
                    )
                    AppLaunchStage.FounderInvitation -> FounderInvitationScreen(
                        rules = rules,
                        onJoin = {},
                        onNotNow = {}
                    )
                    AppLaunchStage.App -> Box(Modifier.testTag("normal-app"))
                }
            }
        }
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_INVITATION).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.onNodeWithTag(ONBOARDING_SKIP).performClick()
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_INVITATION).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FOUNDER_INVITATION).fetchSemanticsNodes().size == 1
        }
        composeRule.onAllNodesWithTag(FOUNDER_INVITATION).assertCountEquals(1)
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertDoesNotExist()
        composeRule.onNodeWithTag("normal-app").assertDoesNotExist()
    }
}
