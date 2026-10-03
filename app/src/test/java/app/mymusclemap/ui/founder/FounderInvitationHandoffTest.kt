package app.mymusclemap.ui.founder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
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
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
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
    fun onboardingCompletesBeforeTheInvitationAndOnlyForNotEnrolled() = runTest {
        val pending = FounderMilestoneAcknowledgements(invitationPending = true)
        assertEquals(
            AppLaunchStage.Onboarding,
            appLaunchStage(FirstRunDecision.ShowOnboarding, FounderProgramStatus.NotEnrolled, pending)
        )
        assertEquals(
            AppLaunchStage.FounderInvitation,
            appLaunchStage(FirstRunDecision.Ready, FounderProgramStatus.NotEnrolled, pending)
        )
        assertEquals(
            AppLaunchStage.FounderInvitation,
            appLaunchStage(FirstRunDecision.Ready, FounderProgramStatus.NotEnrolled, pending)
        )
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(
                FirstRunDecision.Ready,
                FounderProgramStatus.NotEnrolled,
                FounderMilestoneAcknowledgements()
            )
        )
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(
                FirstRunDecision.Ready,
                FounderProgramStatus.NotEnrolled,
                pending.copy(invitationHandled = true)
            )
        )
        FounderProgramStatus.values().filter { it != FounderProgramStatus.NotEnrolled }.forEach { status ->
            assertEquals(
                status.name,
                AppLaunchStage.App,
                appLaunchStage(FirstRunDecision.Ready, status, pending)
            )
        }
        assertEquals(5, FounderProgramRules.Production.temporaryProWorkoutCount)
        assertEquals(10, FounderProgramRules.Production.founderWorkoutCount)
        assertEquals(6, FounderProgramRules.Production.requiredDistinctWorkoutDays)
        assertEquals(45, FounderProgramRules.Production.qualificationWindowDays)
        assertTrue(FounderProgramRules.Production.feedbackRequired)
        assertTrue(FounderProgramRules.Production.testerAnalyticsReportRequired)
    }

    @Test
    fun cleanInstallShowsOnboardingThenTheInvitation() = runTest {
        val before = firstRun.prepare()
        assertEquals(FirstRunDecision.ShowOnboarding, before)
        assertEquals(
            AppLaunchStage.Onboarding,
            appLaunchStage(before, founder.currentState().status, acknowledgements.load())
        )
        val viewModel = OnboardingViewModel(firstRun, founderInvitations = acknowledgements)
        viewModel.onContinue()
        viewModel.onWeeklyGoalSkipped()
        viewModel.onCreatePlan()
        viewModel.exit.first { it != null }
        val armed = acknowledgements.load()
        assertTrue(armed.invitationPending)
        assertFalse(armed.invitationHandled)
        val after = firstRun.prepare()
        assertEquals(FirstRunDecision.Ready, after)
        assertEquals(FounderProgramStatus.NotEnrolled, founder.currentState().status)
        assertEquals(AppLaunchStage.FounderInvitation, appLaunchStage(after, founder.currentState().status, armed))
    }

    @Test
    fun joinUsesEnrollmentOnceAndLandsActiveFree() = runTest {
        finishOnboarding()
        var joins = 0
        suspend fun join() {
            if (joins > 0) return
            joins += 1
            founder.enroll()
            acknowledgements.markInvitationHandled()
        }
        join()
        join()
        assertEquals(1, joins)
        val enrolled = founder.currentState()
        assertEquals(FounderProgramStatus.ActiveFree, enrolled.status)
        assertEquals(today, enrolled.enrolledOn)
        assertEquals(today.plusDays(rules.qualificationWindowDays.toLong()), enrolled.deadline)
        founder.enroll()
        assertEquals(enrolled, founder.currentState())
        val restarted = appLaunchStage(firstRun.prepare(), founder.currentState().status, acknowledgements.load())
        assertEquals(AppLaunchStage.App, restarted)
    }

    @Test
    fun notNowLeavesTheTesterUnenrolledAndSettingsCanStillEnroll() = runTest {
        finishOnboarding()
        acknowledgements.markInvitationHandled()
        assertEquals(FounderProgramStatus.NotEnrolled, founder.currentState().status)
        assertFalse(acknowledgements.load().invitationPending)
        val restarted = appLaunchStage(firstRun.prepare(), founder.currentState().status, acknowledgements.load())
        assertEquals(AppLaunchStage.App, restarted)
        founder.enroll()
        assertEquals(FounderProgramStatus.ActiveFree, founder.currentState().status)
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(firstRun.prepare(), founder.currentState().status, acknowledgements.load())
        )
    }

    @Test
    fun alreadyEnrolledPendingAndApprovedTestersSkipTheInvitation() = runTest {
        finishOnboarding()
        founder.enroll()
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(FirstRunDecision.Ready, founder.currentState().status, acknowledgements.load())
        )
        programStore.save(
            FounderProgramState(
                status = FounderProgramStatus.PendingApproval,
                enrolledOn = today,
                deadline = today.plusDays(45)
            )
        )
        founder.refresh()
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(FirstRunDecision.Ready, founder.currentState().status, acknowledgements.load())
        )
        programStore.save(
            FounderProgramState(
                status = FounderProgramStatus.Approved,
                enrolledOn = today,
                deadline = today.plusDays(45)
            )
        )
        founder.refresh()
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(FirstRunDecision.Ready, founder.currentState().status, acknowledgements.load())
        )
    }

    @Test
    fun anInstallationThatAlreadyFinishedOnboardingIsNotIntercepted() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, firstRun.prepare())
        themePreferences.clearOnboardingProgress()
        val decision = firstRun.prepare()
        assertEquals(FirstRunDecision.Ready, decision)
        assertTrue(themePreferences.isOnboardingCompleted())
        assertFalse(acknowledgements.load().invitationPending)
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(decision, founder.currentState().status, acknowledgements.load())
        )
        themePreferences.clearOnboardingProgress()
        themePreferences.markOnboardingStarted()
        acknowledgements.save(FounderMilestoneAcknowledgements())
        val started = firstRun.prepare()
        assertEquals(FirstRunDecision.Ready, started)
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(started, FounderProgramStatus.NotEnrolled, acknowledgements.load())
        )
    }

    @Test
    fun onboardingWithoutTheInvitationStoreDoesNotArmTheHandoff() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, firstRun.prepare())
        val viewModel = OnboardingViewModel(firstRun)
        viewModel.onContinue()
        viewModel.onWeeklyGoalSkipped()
        viewModel.onSkip()
        viewModel.exit.first { it != null }
        assertFalse(acknowledgements.load().invitationPending)
        assertEquals(
            AppLaunchStage.App,
            appLaunchStage(firstRun.prepare(), founder.currentState().status, acknowledgements.load())
        )
    }

    private suspend fun finishOnboarding() {
        assertEquals(FirstRunDecision.ShowOnboarding, firstRun.prepare())
        val viewModel = OnboardingViewModel(firstRun, founderInvitations = acknowledgements)
        viewModel.onContinue()
        viewModel.onWeeklyGoalSkipped()
        viewModel.onSkip()
        viewModel.exit.first { it != null }
        assertTrue(acknowledgements.load().invitationPending)
    }
}
