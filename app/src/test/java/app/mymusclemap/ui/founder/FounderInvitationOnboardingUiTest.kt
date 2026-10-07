package app.mymusclemap.ui.founder

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.testString
import app.mymusclemap.ui.onboarding.ONBOARDING_PRIMARY
import app.mymusclemap.ui.onboarding.ONBOARDING_SKIP
import app.mymusclemap.ui.onboarding.OnboardingScreen
import app.mymusclemap.ui.onboarding.OnboardingViewModel
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
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
    private val today = LocalDate.of(2026, 10, 3)
    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var acknowledgements: FounderMilestoneAcknowledgementStore
    private lateinit var programStore: FounderProgramStore
    private lateinit var coordinator: FirstRunCoordinator
    private lateinit var founder: FounderProgramCoordinator

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
        programStore = FounderProgramStore(context)
        programStore.save(FounderProgramState())
        val dates = object : DateProvider {
            override fun today(): LocalDate = today
            override fun now(): LocalDateTime = today.atStartOfDay()
            override fun observeToday(): Flow<LocalDate> = flowOf(today)
        }
        founder = FounderProgramCoordinator(
            store = programStore,
            sessions = database.workoutSessionDao(),
            dateProvider = dates,
            rules = rules
        )
        founder.refresh()
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
        programStore.save(FounderProgramState())
        database.close()
    }

    @Test
    fun openOnboardingShowsStrictThenTheInvitationThenSetup() {
        val viewModel = OnboardingViewModel(
            firstRunCoordinator = coordinator,
            founderInvitations = acknowledgements,
            founderProgram = founder,
            founderAvailability = FounderProgramAvailability.Open
        )
        show(viewModel)
        composeRule.onNodeWithText(testString(R.string.brand_tagline)).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_INVITATION).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(FOUNDER_INVITATION).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText(testString(R.string.weekly_goal_onboarding_title)).assertDoesNotExist()
        composeRule.onNodeWithTag(FOUNDER_INVITATION_NOT_NOW).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(testString(R.string.weekly_goal_onboarding_title)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(FOUNDER_INVITATION).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_SKIP).performClick()
        composeRule.onNodeWithText(testString(R.string.onboarding_setup_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_INVITATION).assertDoesNotExist()
    }

    @Test
    fun closedOnboardingGoesFromTrainingToTheWeeklyGoal() {
        val viewModel = OnboardingViewModel(
            firstRunCoordinator = coordinator,
            founderInvitations = acknowledgements,
            founderProgram = founder,
            founderAvailability = FounderProgramAvailability.Closed
        )
        show(viewModel)
        composeRule.onNodeWithText(testString(R.string.brand_tagline)).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.onNodeWithText(testString(R.string.weekly_goal_onboarding_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_INVITATION).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_title)).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_SKIP).performClick()
        composeRule.onNodeWithText(testString(R.string.onboarding_setup_title)).assertIsDisplayed()
    }

    private fun show(viewModel: OnboardingViewModel) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                val step by viewModel.step.collectAsStateWithLifecycle()
                OnboardingScreen(
                    step = step,
                    onContinue = viewModel::onContinue,
                    onCreatePlan = viewModel::onCreatePlan,
                    onSkip = viewModel::onSkip,
                    onSkipWeeklyGoal = viewModel::onWeeklyGoalSkipped,
                    founderRules = rules,
                    onJoinFounder = viewModel::onJoinFounder,
                    onDeclineFounder = viewModel::onDeclineFounder
                )
            }
        }
    }
}
