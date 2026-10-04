package app.mymusclemap.ui.founder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.founder.BackendFounderSnapshot
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementComposer
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.FounderQualification
import app.mymusclemap.domain.entitlement.InactiveFounderLifetimeProvider
import app.mymusclemap.domain.entitlement.InactiveSubscriptionProvider
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrainingCompleteMilestoneTest {
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
    private val today = LocalDate.of(2026, 10, 4)
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val seenPro = FounderMilestoneAcknowledgements(temporaryProUnlocked = true)

    private lateinit var database: WeightDatabase
    private lateinit var programStore: FounderProgramStore
    private lateinit var acknowledgements: FounderMilestoneAcknowledgementStore
    private lateinit var coordinator: FounderProgramCoordinator
    private lateinit var composer: EntitlementComposer

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        programStore = FounderProgramStore(context)
        programStore.save(FounderProgramState())
        acknowledgements = FounderMilestoneAcknowledgementStore(context)
        acknowledgements.save(FounderMilestoneAcknowledgements())
        coordinator = FounderProgramCoordinator(
            store = programStore,
            sessions = database.workoutSessionDao(),
            dateProvider = object : DateProvider {
                override fun today(): LocalDate = today
                override fun now(): LocalDateTime = today.atStartOfDay()
                override fun observeToday() = flowOf(today)
            },
            rules = rules
        )
        composer = EntitlementComposer(
            subscriptionProvider = InactiveSubscriptionProvider,
            founderLifetimeProvider = InactiveFounderLifetimeProvider,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            founderProgram = coordinator::currentState,
            backendFounder = {
                BackendFounderEntitlement(
                    temporaryFounderPro = coordinator.currentState().status == FounderProgramStatus.ActivePro,
                    validUntil = now.plusSeconds(3600)
                )
            }
        )
        coordinator.refresh()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun backendFlagFalseDoesNotOfferTrainingComplete() {
        val journey = presented(trainingComplete = false, acknowledgements = seenPro)
        assertFalse(journey.trainingComplete)
        assertNull(journey.milestone)
        assertNull(overviewFounderMilestone(journey.milestone))
    }

    @Test
    fun backendFlagTrueOffersTrainingCompleteAfterTemporaryPro() {
        val journey = presented(trainingComplete = true, acknowledgements = seenPro)
        assertTrue(journey.trainingComplete)
        assertEquals(FounderMilestone.TrainingComplete, journey.milestone)
        assertEquals(FounderMilestone.TrainingComplete, overviewFounderMilestone(journey.milestone))
        assertTrue(journey.showFeedback)
    }

    @Test
    fun localWorkoutCountCannotOfferTrainingComplete() {
        val journey = founderJourney(
            status = FounderProgramStatus.ActivePro,
            qualification = qualification(workouts = 10, days = 6),
            acknowledgements = seenPro
        )
        assertTrue(journey.trainingComplete)
        assertNull(journey.milestone)
    }

    @Test
    fun localLegacyStatusCannotOfferTrainingComplete() {
        val journey = founderJourney(
            status = FounderProgramStatus.ActivePro,
            qualification = qualification(workouts = 2, days = 1),
            acknowledgements = seenPro,
            authoritative = false,
            trainingCompleteOverride = true
        )
        assertNull(journey.milestone)
        assertNull(overviewFounderMilestone(journey.milestone))
    }

    @Test
    fun temporaryProIsDeliveredBeforeTrainingComplete() {
        val both = presented(trainingComplete = true)
        assertEquals(FounderMilestone.TemporaryProUnlocked, both.milestone)
        assertEquals(FounderMilestone.TemporaryProUnlocked, overviewFounderMilestone(both.milestone))
        assertNull(overviewFounderMilestone(FounderMilestone.QualificationComplete))
        val next = presented(trainingComplete = true, acknowledgements = seenPro)
        assertEquals(FounderMilestone.TrainingComplete, next.milestone)
    }

    @Test
    fun pendingApprovalKeepsTheLaterMilestone() {
        val pending = founderJourney(
            status = FounderProgramStatus.PendingApproval,
            qualification = qualification(workouts = 2, days = 1, feedback = true, report = true),
            acknowledgements = FounderMilestoneAcknowledgements(),
            trainingCompleteOverride = true
        )
        assertEquals(FounderMilestone.QualificationComplete, pending.milestone)
        assertNull(overviewFounderMilestone(pending.milestone))
    }

    @Test
    fun subscriptionProDoesNotOfferTrainingComplete() {
        val enrolled = presented(trainingComplete = false, acknowledgements = seenPro)
        assertNull(enrolled.milestone)
        val resolved = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(60))
            ),
            now
        )
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertFalse(resolved.temporaryTesterPro)
        assertFalse(resolved.founderLifetime)
    }

    @Test
    fun acknowledgementHidesTheMilestoneUntilTheFlagIsCleared() = runTest {
        coordinator.applyBackendEnrollment(snapshot(trainingComplete = true, workouts = 2))
        acknowledgements.save(seenPro)
        val viewModel = viewModel()
        val shown = ready(viewModel)
        assertEquals(FounderMilestone.TrainingComplete, shown.journey.milestone)
        assertEquals(FounderProgramStatus.ActivePro, shown.status)
        val before = composer.resolve()
        val beforeState = coordinator.currentState()
        viewModel.acknowledgeMilestone()
        val hidden = viewModel.uiState.first { it.journey.milestone == null }
        assertEquals(before, composer.resolve())
        assertEquals(beforeState, coordinator.currentState())
        assertTrue(hidden.journey.trainingComplete)
        assertEquals(EntitlementTier.Pro, composer.resolve().tier)
        assertFalse(composer.resolve().founderLifetime)
        assertNull(ready(viewModel()).journey.milestone)
        assertNull(ready(viewModel()).journey.milestone)
        assertNull(ready(FounderProgramViewModel(coordinator, rules, "0.1.0-debug", acknowledgements)).journey.milestone)
        val restarted = FounderMilestoneAcknowledgementStore(ApplicationProvider.getApplicationContext())
        assertTrue(restarted.load().trainingComplete)
        restarted.save(restarted.load().copy(trainingComplete = false))
        val again = ready(FounderProgramViewModel(coordinator, rules, "0.1.0-debug", restarted))
        assertEquals(FounderMilestone.TrainingComplete, again.journey.milestone)
    }

    @Test
    fun secondWorkoutSnapshotOffersTheMilestoneAndARetryDoesNotRepeatIt() = runTest {
        coordinator.applyBackendEnrollment(snapshot(trainingComplete = false, workouts = 1))
        val first = ready(viewModel())
        assertEquals(FounderMilestone.TemporaryProUnlocked, first.journey.milestone)
        acknowledgements.save(seenPro)
        coordinator.applyBackendEnrollment(snapshot(trainingComplete = true, workouts = 2))
        val second = ready(viewModel())
        assertEquals(FounderMilestone.TrainingComplete, second.journey.milestone)
        assertTrue(second.journey.trainingComplete)
        val viewModel = viewModel()
        ready(viewModel)
        viewModel.acknowledgeMilestone()
        viewModel.uiState.first { it.journey.milestone == null }
        coordinator.applyBackendEnrollment(snapshot(trainingComplete = true, workouts = 2))
        assertNull(ready(viewModel()).journey.milestone)
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
        assertTrue(coordinator.currentState().serverTrainingRequirementsComplete)
    }

    private fun presented(
        trainingComplete: Boolean,
        acknowledgements: FounderMilestoneAcknowledgements = FounderMilestoneAcknowledgements()
    ): FounderJourney {
        return founderJourney(
            status = FounderProgramStatus.ActivePro,
            qualification = qualification(workouts = if (trainingComplete) 2 else 1, days = 1),
            acknowledgements = acknowledgements,
            authoritative = true,
            trainingCompleteOverride = trainingComplete
        )
    }

    private fun qualification(
        workouts: Int,
        days: Int,
        feedback: Boolean = false,
        report: Boolean = false
    ): FounderQualification {
        return FounderQualification(
            nativeCompletedWorkouts = workouts,
            distinctNativeWorkoutDays = days,
            feedbackRecorded = feedback,
            testerAnalyticsReportSubmitted = report,
            rules = rules
        )
    }

    private fun viewModel(): FounderProgramViewModel {
        return FounderProgramViewModel(coordinator, rules, "0.1.0-debug", acknowledgements)
    }

    private suspend fun ready(viewModel: FounderProgramViewModel): FounderProgramUiState {
        return viewModel.uiState.first { !it.loading }
    }

    private fun snapshot(trainingComplete: Boolean, workouts: Int): BackendFounderSnapshot {
        return BackendFounderSnapshot(
            status = FounderProgramStatus.ActivePro,
            enrolledOn = today,
            deadline = today.plusDays(45),
            qualifyingWorkouts = workouts,
            distinctWorkoutDays = 1,
            feedbackSubmitted = false,
            reportSubmitted = false,
            requiredWorkouts = 2,
            requiredDistinctDays = 1,
            temporaryProRequiredWorkouts = 1,
            temporaryProActive = true,
            trainingRequirementsComplete = trainingComplete
        )
    }
}
