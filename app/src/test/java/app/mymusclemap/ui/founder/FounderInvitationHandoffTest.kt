package app.mymusclemap.ui.founder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.founder.BackendFounderSnapshot
import app.mymusclemap.data.founder.FounderJoinResult
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.ui.onboarding.OnboardingStep
import app.mymusclemap.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class FounderInvitationHandoffTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rules = FounderProgramRules(
        temporaryProWorkoutCount = 1,
        founderWorkoutCount = 2,
        requiredDistinctWorkoutDays = 1,
        qualificationWindowDays = 45,
        feedbackRequired = true,
        testerAnalyticsReportRequired = true
    )
    private val today = LocalDate.of(2026, 10, 3)
    private val dates = object : DateProvider {
        override fun today(): LocalDate = today
        override fun now(): LocalDateTime = today.atStartOfDay()
        override fun observeToday(): Flow<LocalDate> = flowOf(today)
    }
    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var firstRun: FirstRunCoordinator
    private lateinit var programStore: FounderProgramStore
    private lateinit var acknowledgements: FounderMilestoneAcknowledgementStore
    private lateinit var founder: FounderProgramCoordinator

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        themePreferences = ThemePreferences(context)
        themePreferences.clearOnboardingProgress()
        firstRun = FirstRunCoordinator(
            database,
            ExerciseRepository(
                dao = database.exerciseDao(),
                clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)
            ),
            themePreferences
        )
        programStore = FounderProgramStore(context)
        programStore.save(FounderProgramState())
        acknowledgements = FounderMilestoneAcknowledgementStore(context)
        acknowledgements.save(FounderMilestoneAcknowledgements())
        founder = FounderProgramCoordinator(
            store = programStore,
            sessions = database.workoutSessionDao(),
            dateProvider = dates,
            rules = rules
        )
        founder.refresh()
    }

    @After
    fun tearDown() = runTest {
        themePreferences.clearOnboardingProgress()
        programStore.save(FounderProgramState())
        acknowledgements.save(FounderMilestoneAcknowledgements())
        database.close()
    }

    @Test
    fun closedOnboardingSkipsTheFounderStepAndStillFinishes() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, firstRun.prepare())
        assertEquals(AppLaunchStage.Onboarding, appLaunchStage(FirstRunDecision.ShowOnboarding))
        val viewModel = onboardingViewModel(FounderProgramAvailability.Closed)
        assertEquals(OnboardingStep.Welcome, viewModel.step.value)
        viewModel.onContinue()
        assertEquals(OnboardingStep.WeeklyGoal, viewModel.step.value)
        viewModel.onWeeklyGoalSkipped()
        assertEquals(OnboardingStep.CreatePlan, viewModel.step.value)
        viewModel.onSkip()
        viewModel.exit.first { it != null }
        assertEquals(FounderProgramStatus.NotEnrolled, founder.currentState().status)
        assertFalse(acknowledgements.load().invitationHandled)
        assertEquals(AppLaunchStage.App, appLaunchStage(firstRun.prepare()))
    }

    @Test
    fun openOnboardingOffersTheInvitationBeforeTheWeeklyGoal() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, firstRun.prepare())
        val viewModel = onboardingViewModel(FounderProgramAvailability.Open)
        viewModel.onContinue()
        assertEquals(OnboardingStep.FounderInvitation, viewModel.step.first { it != OnboardingStep.Welcome })
        assertEquals(FounderProgramStatus.NotEnrolled, founder.currentState().status)
        viewModel.onDeclineFounder()
        assertEquals(OnboardingStep.WeeklyGoal, viewModel.step.first { it == OnboardingStep.WeeklyGoal })
        viewModel.onWeeklyGoalSkipped()
        assertEquals(OnboardingStep.CreatePlan, viewModel.step.value)
    }

    @Test
    fun joinEnrollsOnceAndContinuesOnboarding() = runTest {
        firstRun.prepare()
        val viewModel = onboardingViewModel(FounderProgramAvailability.Open)
        viewModel.onContinue()
        viewModel.step.first { it == OnboardingStep.FounderInvitation }
        viewModel.onJoinFounder()
        viewModel.onJoinFounder()
        assertEquals(OnboardingStep.WeeklyGoal, viewModel.step.first { it == OnboardingStep.WeeklyGoal })
        val enrolled = founder.currentState()
        assertEquals(FounderProgramStatus.ActiveFree, enrolled.status)
        assertEquals(today, enrolled.enrolledOn)
        assertEquals(today.plusDays(45), enrolled.deadline)
        assertTrue(enrolled.backendOwned)
        founder.enroll()
        assertEquals(enrolled, founder.currentState())
        assertTrue(acknowledgements.load().invitationHandled)
        assertEquals(AppLaunchStage.App, appLaunchStage(FirstRunDecision.Ready))
    }

    @Test
    fun notNowLeavesEnrollmentAvailableFromSettingsWhileOpen() = runTest {
        firstRun.prepare()
        val viewModel = onboardingViewModel(FounderProgramAvailability.Open)
        viewModel.onContinue()
        viewModel.step.first { it == OnboardingStep.FounderInvitation }
        viewModel.onDeclineFounder()
        viewModel.step.first { it == OnboardingStep.WeeklyGoal }
        assertEquals(FounderProgramStatus.NotEnrolled, founder.currentState().status)
        assertTrue(acknowledgements.load().invitationHandled)
        val restarted = onboardingViewModel(FounderProgramAvailability.Open)
        restarted.onContinue()
        assertEquals(OnboardingStep.WeeklyGoal, restarted.step.first { it != OnboardingStep.Welcome })
        val settings = FounderProgramViewModel(
            founder,
            rules,
            "0.1.0-debug",
            acknowledgements,
            FounderProgramAvailability.Open,
            joinFounder = { applyServerEnrollment() }
        )
        settings.enroll()
        assertEquals(
            FounderProgramStatus.ActiveFree,
            founder.view.first { it.state.status == FounderProgramStatus.ActiveFree }.state.status
        )
    }

    @Test
    fun closedSettingsCannotEnrollAndExistingParticipantsKeepTheirStatus() = runTest {
        var joinCalls = 0
        val blocked = FounderProgramViewModel(
            founder,
            rules,
            "0.1.0-debug",
            acknowledgements,
            FounderProgramAvailability.Closed,
            joinFounder = {
                joinCalls += 1
                FounderJoinResult.Enrolled
            }
        )
        blocked.enroll()
        assertEquals(0, joinCalls)
        assertEquals(FounderProgramStatus.NotEnrolled, founder.currentState().status)
        programStore.save(
            FounderProgramState(
                status = FounderProgramStatus.ActivePro,
                enrolledOn = today,
                deadline = today.plusDays(45)
            )
        )
        founder.refresh()
        blocked.enroll()
        assertEquals(0, joinCalls)
        assertEquals(FounderProgramStatus.ActivePro, founder.currentState().status)
        val resolved = EntitlementResolver.resolve(
            EntitlementSources.of(program = founder.currentState()),
            Instant.parse("2026-10-03T12:00:00Z")
        )
        assertTrue(resolved.temporaryTesterPro)
        assertEquals(EntitlementTier.Pro, resolved.tier)
        programStore.save(
            founder.currentState().copy(status = FounderProgramStatus.Approved)
        )
        founder.refresh()
        val lifetime = EntitlementResolver.resolve(
            EntitlementSources.of(program = founder.currentState()),
            Instant.parse("2026-10-03T12:00:00Z")
        )
        assertTrue(lifetime.founderLifetime)
        assertEquals(EntitlementTier.Pro, lifetime.tier)
    }

    @Test
    fun anInstallationThatAlreadyFinishedOnboardingIsNotIntercepted() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, firstRun.prepare())
        themePreferences.clearOnboardingProgress()
        val decision = firstRun.prepare()
        assertEquals(FirstRunDecision.Ready, decision)
        assertEquals(AppLaunchStage.App, appLaunchStage(decision))
        assertFalse(acknowledgements.load().invitationPending)
        assertFalse(acknowledgements.load().invitationHandled)
    }

    private fun onboardingViewModel(availability: FounderProgramAvailability): OnboardingViewModel {
        return OnboardingViewModel(
            firstRunCoordinator = firstRun,
            founderInvitations = acknowledgements,
            founderProgram = founder,
            founderAvailability = availability,
            founderJoin = { applyServerEnrollment() }
        )
    }

    private suspend fun applyServerEnrollment(): FounderJoinResult {
        founder.applyBackendEnrollment(
            BackendFounderSnapshot(
                status = FounderProgramStatus.ActiveFree,
                enrolledOn = today,
                deadline = today.plusDays(45),
                qualifyingWorkouts = 0,
                distinctWorkoutDays = 0,
                feedbackSubmitted = false,
                reportSubmitted = false
            )
        )
        return FounderJoinResult.Enrolled
    }
}
