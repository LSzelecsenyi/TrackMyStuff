package app.mymusclemap.data.founder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.auth.FounderReportSubmission
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementComposer
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.InactiveFounderLifetimeProvider
import app.mymusclemap.domain.entitlement.PolicyBackedEntitlements
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import app.mymusclemap.domain.entitlement.SubscriptionEntitlementProvider
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.founder.FounderJourneyPhase
import app.mymusclemap.ui.founder.FounderMilestone
import app.mymusclemap.ui.founder.FounderNextAction
import app.mymusclemap.ui.founder.FounderProgramUiState
import app.mymusclemap.ui.founder.FounderProgramViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class FounderProgramRuntimeTest {
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
    private var today = LocalDate.of(2026, 10, 3)
    private val dates = object : DateProvider {
        override fun today(): LocalDate = today
        override fun now(): LocalDateTime = today.atStartOfDay()
        override fun observeToday(): Flow<LocalDate> = flowOf(today)
    }
    private lateinit var database: WeightDatabase
    private lateinit var store: FounderProgramStore
    private lateinit var acknowledgements: FounderMilestoneAcknowledgementStore
    private lateinit var revisions: MutableStateFlow<Int>
    private lateinit var coordinator: FounderProgramCoordinator
    private lateinit var subscription: MutableSubscription
    private lateinit var composer: EntitlementComposer
    private var backendEntitlement = BackendFounderEntitlement()
    private lateinit var entitlements: PolicyBackedEntitlements

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = FounderProgramStore(context)
        store.save(app.mymusclemap.domain.entitlement.FounderProgramState())
        acknowledgements = FounderMilestoneAcknowledgementStore(context)
        acknowledgements.save(FounderMilestoneAcknowledgements())
        revisions = MutableStateFlow(0)
        coordinator = FounderProgramCoordinator(
            store = store,
            sessions = database.workoutSessionDao(),
            dateProvider = dates,
            rules = rules,
            onEntitlementChanged = { revisions.value = revisions.value + 1 }
        )
        subscription = MutableSubscription()
        backendEntitlement = BackendFounderEntitlement()
        composer = EntitlementComposer(
            subscriptionProvider = subscription,
            founderLifetimeProvider = InactiveFounderLifetimeProvider,
            clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC),
            founderProgram = coordinator::currentState,
            backendFounder = { backendEntitlement }
        )
        entitlements = PolicyBackedEntitlements(
            policySource = { composer.policy() },
            revisions = revisions
        )
        coordinator.refresh()
    }

    @After
    fun tearDown() = runTest {
        store.save(app.mymusclemap.domain.entitlement.FounderProgramState())
        database.close()
    }

    @Test
    fun enrollmentStartsActiveFreeAndASecondEnrollmentDoesNotResetIt() = runTest {
        coordinator.enroll()
        val enrolled = coordinator.currentState()
        assertEquals(FounderProgramStatus.ActiveFree, enrolled.status)
        assertEquals(today, enrolled.enrolledOn)
        assertEquals(today.plusDays(45), enrolled.deadline)
        assertFalse(enrolled.status.grantsTemporaryPro())
        assertEquals(EntitlementTier.Free, composer.resolve().tier)
        coordinator.enroll()
        assertEquals(enrolled, coordinator.currentState())
    }

    @Test
    fun nativeCompletionUnlocksTemporaryProAndCountsASessionOnce() = runTest {
        coordinator.enroll()
        assertFalse(entitlements.hasAccess(AppFeature.ProgressPhotos))
        insertWorkout(day = today, status = SessionStatus.COMPLETED)
        coordinator.onNativeWorkoutCompleted()
        coordinator.onNativeWorkoutCompleted()
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
        assertEquals(1, coordinator.view.value.qualification.nativeCompletedWorkouts)
        assertFalse(composer.resolve().temporaryTesterPro)
        assertEquals(EntitlementTier.Free, composer.resolve().tier)
        assertFalse(entitlements.hasAccess(AppFeature.ProgressPhotos))
    }

    @Test
    fun abandonedIncompleteImportedAndHealthConnectSessionsDoNotQualify() = runTest {
        coordinator.enroll()
        insertWorkout(day = today, status = SessionStatus.ABANDONED)
        insertWorkout(day = today, status = SessionStatus.IN_PROGRESS, activeLock = 1)
        insertWorkout(day = today, status = SessionStatus.COMPLETED, fingerprint = "imported-file")
        coordinator.refresh()
        assertEquals(FounderProgramStatus.ActiveFree, coordinator.currentState().status)
        assertEquals(0, coordinator.view.value.qualification.nativeCompletedWorkouts)
        assertTrue(coordinator.view.value.workouts.none { it.asCompletedWorkout().origin.name != "NativeStrict" })
    }

    @Test
    fun workoutsBeforeTheEnrollmentDateDoNotCountAndTheEnrollmentDateDoes() = runTest {
        insertWorkout(day = today.minusDays(1), status = SessionStatus.COMPLETED)
        coordinator.enroll()
        assertEquals(0, coordinator.view.value.qualification.nativeCompletedWorkouts)
        assertEquals(FounderProgramStatus.ActiveFree, coordinator.currentState().status)
        insertWorkout(day = today, status = SessionStatus.COMPLETED, name = "Same day")
        coordinator.refresh()
        assertEquals(1, coordinator.view.value.qualification.nativeCompletedWorkouts)
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
    }

    @Test
    fun feedbackAndReportOrderDoesNotMatter() = runTest {
        assertPending("workout", "workout", "feedback", "report")
        resetProgram()
        assertPending("feedback", "report", "workout", "workout")
        resetProgram()
        assertPending("report", "workout", "feedback", "workout")
    }

    @Test
    fun blankFeedbackIsRejectedAndOpeningTheReportDoesNotSubmitIt() = runTest {
        coordinator.enroll()
        assertFalse(coordinator.recordFeedback("   "))
        assertFalse(coordinator.currentState().feedbackRecorded)
        assertTrue(coordinator.recordFeedback("The rest timer should stay visible."))
        assertTrue(coordinator.currentState().feedbackRecorded)
        assertEquals("The rest timer should stay visible.", store.loadFeedbackText())
        val viewModel = founderViewModel()
        val ready = ready(viewModel)
        viewModel.onFeedbackChange("   ")
        viewModel.submitTesterReport()
        val blank = viewModel.uiState.first { it.feedbackBlank }
        assertEquals("   ", blank.feedbackDraft)
        assertFalse(blank.reportSubmitFailed)
        assertFalse(coordinator.currentState().testerAnalyticsReportSubmitted)
        assertEquals(ready.status, coordinator.currentState().status)
    }

    @Test
    fun pendingApprovalStaysProAndDoesNotExpire() = runTest {
        completeQualification()
        assertEquals(FounderProgramStatus.PendingApproval, coordinator.currentState().status)
        assertFalse(composer.resolve().temporaryTesterPro)
        today = today.plusDays(90)
        coordinator.refresh()
        assertEquals(FounderProgramStatus.PendingApproval, coordinator.currentState().status)
        assertEquals(EntitlementTier.Free, composer.resolve().tier)
    }

    @Test
    fun incompleteProgramsExpireAndRejectionFollowsTheOtherProSource() = runTest {
        coordinator.enroll()
        today = today.plusDays(46)
        coordinator.refresh()
        assertEquals(FounderProgramStatus.Expired, coordinator.currentState().status)
        assertEquals(EntitlementTier.Free, composer.resolve().tier)

        resetProgram()
        insertWorkout(day = today, status = SessionStatus.COMPLETED)
        coordinator.enroll()
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
        today = today.plusDays(46)
        coordinator.refresh()
        assertEquals(FounderProgramStatus.Expired, coordinator.currentState().status)

        resetProgram()
        completeQualification()
        coordinator.reject("   ")
        assertEquals(FounderProgramStatus.PendingApproval, coordinator.currentState().status)
        coordinator.reject("Spam feedback")
        assertEquals(FounderProgramStatus.Rejected, coordinator.currentState().status)
        assertFalse(composer.resolve().temporaryTesterPro)
        assertEquals(EntitlementTier.Free, composer.resolve().tier)
        subscription.current = SubscriptionEntitlement(paidUntilInclusive = Instant.parse("2027-01-01T00:00:00Z"))
        assertEquals(EntitlementTier.Pro, composer.resolve().tier)
        assertTrue(composer.resolve().subscriptionValid)
        assertFalse(composer.resolve().founderLifetime)
    }

    @Test
    fun approvedStateReloadsAndBackupTablesDoNotCarryIt() = runTest {
        completeQualification()
        coordinator.approve()
        assertEquals(FounderProgramStatus.Approved, coordinator.currentState().status)
        assertFalse(composer.resolve().founderLifetime)
        assertEquals(EntitlementTier.Free, composer.resolve().tier)
        val reloaded = FounderProgramCoordinator(
            store = FounderProgramStore(ApplicationProvider.getApplicationContext()),
            sessions = database.workoutSessionDao(),
            dateProvider = dates,
            rules = rules
        )
        reloaded.refresh()
        assertEquals(FounderProgramStatus.Approved, reloaded.currentState().status)
        assertEquals("The rest timer should stay visible.", reloaded.view.value.feedbackText)
        val tables = listOf(
            AppBackupFormat.TABLE_WEIGHT_MEASUREMENTS,
            AppBackupFormat.TABLE_BODY_MEASUREMENTS,
            AppBackupFormat.TABLE_EXERCISES,
            AppBackupFormat.TABLE_WORKOUT_SESSIONS,
            AppBackupFormat.TABLE_WORKOUT_TEMPLATES,
            AppBackupFormat.TABLE_PROGRESS_PHOTOS,
            AppBackupFormat.TABLE_WEEKLY_WORKOUT_GOALS
        )
        assertTrue(tables.none { name ->
            name.contains("founder") || name.contains("entitlement") ||
                name == FounderProgramStore.PREFERENCES_NAME ||
                name == FounderMilestoneAcknowledgementStore.PREFERENCES_NAME
        })
        assertEquals(FounderProgramStatus.Approved, store.load().status)
    }

    @Test
    fun activeFreeJourneyPointsAtTemporaryProFromTheActiveRules() = runTest {
        coordinator.enroll()
        val shown = ready(founderViewModel())
        assertEquals(FounderJourneyPhase.ActiveFree, shown.journey.phase)
        assertEquals(FounderNextAction.UnlockTemporaryPro, shown.journey.nextAction)
        assertEquals(rules.temporaryProWorkoutCount, shown.journey.nextRemaining)
        assertEquals(rules.founderWorkoutCount, shown.journey.rules.founderWorkoutCount)
        assertNull(shown.journey.milestone)
        assertFalse(shown.temporaryProActive)
    }

    @Test
    fun temporaryProMilestoneShowsOnceAndDoesNotChangeEntitlement() = runTest {
        coordinator.enroll()
        insertWorkout(day = today, status = SessionStatus.COMPLETED)
        coordinator.onNativeWorkoutCompleted()
        val before = composer.resolve()
        val viewModel = founderViewModel()
        val shown = ready(viewModel)
        assertEquals(FounderProgramStatus.ActivePro, shown.status)
        assertNull(shown.journey.milestone)
        assertEquals(1, shown.journey.nextRemaining)
        assertEquals(FounderNextAction.QualifyingWorkout, shown.journey.nextAction)
        viewModel.acknowledgeMilestone()
        val dismissed = viewModel.uiState.first { !it.loading && it.journey.milestone == null }
        assertEquals(before, composer.resolve())
        assertEquals(FounderProgramStatus.ActivePro, store.load().status)
        assertNull(ready(founderViewModel()).journey.milestone)
        acknowledgements.save(FounderMilestoneAcknowledgements())
        val again = ready(founderViewModel())
        assertNull(again.journey.milestone)
        assertEquals(before, composer.resolve())
        assertFalse(before.temporaryTesterPro)
    }

    @Test
    fun backendTemporaryProShowsTheMilestoneWithoutUsingLocalStatusAsAGrant() = runTest {
        coordinator.applyBackendEnrollment(
            BackendFounderSnapshot(
                status = FounderProgramStatus.ActivePro,
                enrolledOn = today,
                deadline = today.plusDays(45),
                qualifyingWorkouts = 1,
                distinctWorkoutDays = 1,
                feedbackSubmitted = false,
                reportSubmitted = false,
                requiredWorkouts = 2,
                requiredDistinctDays = 1,
                temporaryProRequiredWorkouts = 1,
                temporaryProActive = true
            )
        )
        backendEntitlement = BackendFounderEntitlement(
            temporaryFounderPro = true,
            validUntil = Instant.parse("2026-10-04T12:00:00Z")
        )
        val before = composer.resolve()
        assertTrue(before.temporaryTesterPro)
        assertFalse(before.founderLifetime)
        val viewModel = founderViewModel()
        val shown = ready(viewModel)
        assertEquals(FounderMilestone.TemporaryProUnlocked, shown.journey.milestone)
        viewModel.acknowledgeMilestone()
        viewModel.uiState.first { !it.loading && it.journey.milestone == null }
        assertEquals(before, composer.resolve())
        acknowledgements.save(FounderMilestoneAcknowledgements())
        assertEquals(FounderMilestone.TemporaryProUnlocked, ready(founderViewModel()).journey.milestone)
        assertEquals(before, composer.resolve())
    }

    @Test
    fun qualificationMilestoneShowsOnceWhileTemporaryProStaysActive() = runTest {
        completeQualification()
        assertNull(ready(founderViewModel()).journey.milestone)
        coordinator.applyBackendEnrollment(
            BackendFounderSnapshot(
                status = FounderProgramStatus.PendingApproval,
                enrolledOn = today,
                deadline = today.plusDays(45),
                qualifyingWorkouts = 2,
                distinctWorkoutDays = 1,
                feedbackSubmitted = true,
                reportSubmitted = true,
                requiredWorkouts = 2,
                requiredDistinctDays = 1,
                temporaryProRequiredWorkouts = 1,
                temporaryProActive = true,
                trainingRequirementsComplete = true
            )
        )
        backendEntitlement = BackendFounderEntitlement(
            temporaryFounderPro = true,
            validUntil = Instant.parse("2026-10-04T12:00:00Z")
        )
        val before = composer.resolve()
        assertTrue(before.temporaryTesterPro)
        assertFalse(before.founderLifetime)
        val viewModel = founderViewModel()
        val shown = ready(viewModel)
        assertEquals(FounderJourneyPhase.PendingReview, shown.journey.phase)
        assertEquals(FounderMilestone.QualificationComplete, shown.journey.milestone)
        assertTrue(shown.journey.temporaryProActive)
        assertFalse(shown.journey.showDeadline)
        assertNull(shown.journey.nextAction)
        assertFalse(shown.journey.acceptsContribution)
        viewModel.acknowledgeMilestone()
        viewModel.uiState.first { it.journey.milestone == null }
        assertNull(ready(founderViewModel()).journey.milestone)
        assertEquals(before, composer.resolve())
        assertEquals(FounderProgramStatus.PendingApproval, store.load().status)
        acknowledgements.save(FounderMilestoneAcknowledgements())
        assertEquals(FounderMilestone.QualificationComplete, ready(founderViewModel()).journey.milestone)
        assertEquals(before, composer.resolve())
        assertFalse(composer.resolve().founderLifetime)
    }

    @Test
    fun approvedMilestoneShowsOnceAndLosingItCannotRemoveLifetimePro() = runTest {
        completeQualification()
        coordinator.approve()
        val before = composer.resolve()
        val viewModel = founderViewModel()
        val shown = ready(viewModel)
        assertEquals(FounderJourneyPhase.FoundingMember, shown.journey.phase)
        assertEquals(FounderMilestone.FounderApproved, shown.journey.milestone)
        assertFalse(shown.journey.phase.name == "Approved")
        assertTrue(shown.founderBadge)
        viewModel.acknowledgeMilestone()
        viewModel.uiState.first { it.journey.milestone == null }
        val steady = ready(founderViewModel())
        assertNull(steady.journey.milestone)
        assertEquals(FounderJourneyPhase.FoundingMember, steady.journey.phase)
        assertTrue(steady.founderBadge)
        acknowledgements.save(FounderMilestoneAcknowledgements())
        assertEquals(FounderMilestone.FounderApproved, ready(founderViewModel()).journey.milestone)
        assertEquals(before, composer.resolve())
        assertFalse(before.founderLifetime)
        assertEquals(FounderProgramStatus.Approved, store.load().status)
    }

    @Test
    fun feedbackAndReportCompletionAreRepresentedWithoutOpeningTheReport() = runTest {
        coordinator.enroll()
        insertWorkout(day = today, status = SessionStatus.COMPLETED)
        insertWorkout(day = today, status = SessionStatus.COMPLETED, name = "Second")
        coordinator.onNativeWorkoutCompleted()
        val viewModel = founderViewModel()
        val open = ready(viewModel)
        assertFalse(open.journey.feedbackSaved)
        assertFalse(open.journey.reportShared)
        assertEquals(FounderNextAction.Feedback, open.journey.nextAction)
        assertTrue(open.journey.showFeedback)
        viewModel.onFeedbackChange("   ")
        viewModel.submitTesterReport()
        val blank = viewModel.uiState.first { it.feedbackBlank }
        assertFalse(blank.reportSubmitted)
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
        viewModel.onFeedbackChange("Keep the rest timer visible.")
        viewModel.submitTesterReport()
        val failed = viewModel.uiState.first { it.reportSubmitFailed }
        assertFalse(failed.journey.reportShared)
        assertEquals(FounderJourneyPhase.ActivePro, failed.journey.phase)
    }

    @Test
    fun testerReportSubmissionUsesTheBackendSnapshotAndKeepsTheDraftUntilThen() = runTest {
        acknowledgements.save(
            FounderMilestoneAcknowledgements(
                temporaryProUnlocked = true,
                trainingComplete = true
            )
        )
        store.saveFeedbackText("Keep the rest timer visible.")
        coordinator.applyBackendEnrollment(trainingSnapshot())
        assertFalse(coordinator.recordFeedback("Local note"))
        coordinator.submitTesterAnalyticsReport()
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
        val ids = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        var attempts = 0
        val viewModel = founderViewModel { id, feedback, version ->
            attempts += 1
            ids += id
            bodies += feedback
            assertEquals("0.1.0-debug", version)
            assertFalse(feedback.contains("status"))
            if (attempts == 1) {
                FounderReportSubmission.Unavailable
            } else {
                coordinator.applyBackendEnrollment(pendingSnapshot())
                backendEntitlement = BackendFounderEntitlement(
                    temporaryFounderPro = true,
                    validUntil = Instant.parse("2026-10-04T12:00:00Z")
                )
                FounderReportSubmission.Accepted(pendingSnapshot())
            }
        }
        val open = viewModel.uiState.first { !it.loading && it.feedbackDraft == "Keep the rest timer visible." }
        assertTrue(open.journey.showFeedback)
        assertNull(open.journey.milestone)
        viewModel.submitTesterReport()
        val failed = viewModel.uiState.first { it.reportSubmitFailed && !it.submittingReport }
        assertEquals("Keep the rest timer visible.", failed.feedbackDraft)
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
        assertNull(failed.journey.milestone)
        viewModel.submitTesterReport()
        val submitted = viewModel.uiState.first {
            it.status == FounderProgramStatus.PendingApproval &&
                !it.submittingReport &&
                it.feedbackDraft.isEmpty() &&
                it.journey.milestone == FounderMilestone.QualificationComplete
        }
        assertEquals("", store.loadFeedbackText())
        assertEquals(1, ids.distinct().size)
        assertEquals(listOf("Keep the rest timer visible.", "Keep the rest timer visible."), bodies)
        assertTrue(composer.resolve().temporaryTesterPro)
        assertFalse(composer.resolve().founderLifetime)
        assertEquals(FounderJourneyPhase.PendingReview, submitted.journey.phase)
        val restarted = founderViewModel()
        val again = ready(restarted)
        assertEquals(FounderProgramStatus.PendingApproval, again.status)
        assertEquals(FounderMilestone.QualificationComplete, again.journey.milestone)
    }

    @Test
    fun testerReportDoubleTapAndAuthenticationFailureDoNotInventPendingApproval() = runTest {
        acknowledgements.save(FounderMilestoneAcknowledgements(temporaryProUnlocked = true, trainingComplete = true))
        coordinator.applyBackendEnrollment(trainingSnapshot())
        store.saveFeedbackText("Still here")
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gate = kotlinx.coroutines.CompletableDeferred<FounderReportSubmission>()
        var calls = 0
        val viewModel = founderViewModel { _, _, _ ->
            calls += 1
            started.complete(Unit)
            gate.await()
        }
        viewModel.uiState.first { !it.loading && it.feedbackDraft == "Still here" }
        viewModel.submitTesterReport()
        viewModel.submitTesterReport()
        started.await()
        assertEquals(1, calls)
        assertTrue(viewModel.uiState.value.submittingReport)
        gate.complete(FounderReportSubmission.Unavailable)
        val failed = viewModel.uiState.first { it.reportSubmitFailed && !it.submittingReport }
        assertEquals("Still here", failed.feedbackDraft)
        assertEquals(FounderProgramStatus.ActivePro, failed.status)

        var signedIn = true
        val revisions = MutableStateFlow(0)
        val signedOut = FounderProgramViewModel(
            coordinator = coordinator,
            rules = rules,
            versionName = "0.1.0-debug",
            milestoneAcknowledgements = acknowledgements,
            sessionRevision = revisions,
            sessionPresent = { signedIn },
            submitReport = { _, feedback, _ ->
                assertEquals("Still here", feedback)
                signedIn = false
                revisions.value = revisions.value + 1
                FounderReportSubmission.Unauthenticated
            }
        )
        signedOut.uiState.first { !it.loading && it.feedbackDraft == "Still here" }
        signedOut.submitTesterReport()
        val rejected = signedOut.uiState.first { it.reportSubmitFailed && it.sessionRequired }
        assertEquals("Still here", rejected.feedbackDraft)
        assertEquals("Still here", store.loadFeedbackText())
        assertEquals(FounderProgramStatus.ActivePro, coordinator.currentState().status)
        assertNull(rejected.journey.milestone)
    }

    private fun trainingSnapshot(): BackendFounderSnapshot {
        return BackendFounderSnapshot(
            status = FounderProgramStatus.ActivePro,
            enrolledOn = today,
            deadline = today.plusDays(45),
            qualifyingWorkouts = 2,
            distinctWorkoutDays = 1,
            feedbackSubmitted = false,
            reportSubmitted = false,
            requiredWorkouts = 2,
            requiredDistinctDays = 1,
            temporaryProRequiredWorkouts = 1,
            temporaryProActive = true,
            trainingRequirementsComplete = true
        )
    }

    private fun pendingSnapshot(): BackendFounderSnapshot {
        return trainingSnapshot().copy(
            status = FounderProgramStatus.PendingApproval,
            feedbackSubmitted = true,
            reportSubmitted = true
        )
    }

    private fun founderViewModel(
        submit: suspend (String, String, String) -> FounderReportSubmission = { _, _, _ ->
            FounderReportSubmission.Unavailable
        }
    ): FounderProgramViewModel {
        return FounderProgramViewModel(
            coordinator = coordinator,
            rules = rules,
            versionName = "0.1.0-debug",
            milestoneAcknowledgements = acknowledgements,
            submitReport = submit
        )
    }

    private suspend fun ready(viewModel: FounderProgramViewModel): FounderProgramUiState {
        return viewModel.uiState.first { !it.loading }
    }

    private suspend fun assertPending(vararg steps: String) {
        coordinator.enroll()
        var workout = 0
        steps.forEach { step ->
            when (step) {
                "workout" -> {
                    workout += 1
                    insertWorkout(
                        day = today,
                        status = SessionStatus.COMPLETED,
                        name = "Workout $workout"
                    )
                    coordinator.onNativeWorkoutCompleted()
                }
                "feedback" -> assertTrue(coordinator.recordFeedback("The rest timer should stay visible."))
                "report" -> coordinator.submitTesterAnalyticsReport()
            }
        }
        assertEquals(FounderProgramStatus.PendingApproval, coordinator.currentState().status)
        assertEquals(2, coordinator.view.value.qualification.nativeCompletedWorkouts)
        assertEquals(1, coordinator.view.value.qualification.distinctNativeWorkoutDays)
        assertFalse(composer.resolve().temporaryTesterPro)
    }

    @Test
    fun backendEnrollmentSurvivesLocalRefreshAndDebugApproval() = runTest {
        insertWorkout(day = today, status = SessionStatus.COMPLETED)
        coordinator.applyBackendEnrollment(
            BackendFounderSnapshot(
                status = FounderProgramStatus.ActiveFree,
                enrolledOn = LocalDate.of(2026, 6, 1),
                deadline = LocalDate.of(2026, 7, 16),
                qualifyingWorkouts = 0,
                distinctWorkoutDays = 0,
                feedbackSubmitted = false,
                reportSubmitted = false
            )
        )
        coordinator.refresh()
        val state = coordinator.currentState()
        assertEquals(FounderProgramStatus.ActiveFree, state.status)
        assertEquals(LocalDate.of(2026, 6, 1), state.enrolledOn)
        assertEquals(LocalDate.of(2026, 7, 16), state.deadline)
        assertTrue(state.backendOwned)
        assertEquals(0, coordinator.view.value.qualification.nativeCompletedWorkouts)
        assertFalse(coordinator.recordFeedback("This must stay on the server."))
        coordinator.submitTesterAnalyticsReport()
        coordinator.approve()
        coordinator.reject("no")
        assertEquals(FounderProgramStatus.ActiveFree, coordinator.currentState().status)
        assertFalse(coordinator.currentState().feedbackRecorded)
        var joins = 0
        val viewModel = FounderProgramViewModel(
            coordinator,
            rules,
            "0.1.0-debug",
            acknowledgements,
            joinFounder = {
                joins += 1
                FounderJoinResult.Rejected
            }
        )
        viewModel.uiState.first { !it.loading }
        assertEquals(0, joins)
    }

    @Test
    fun repeatedEnrollDoesNotStartASecondJoin() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<FounderJoinResult>()
        var calls = 0
        val viewModel = FounderProgramViewModel(
            coordinator,
            rules,
            "0.1.0-debug",
            acknowledgements,
            joinFounder = {
                calls += 1
                gate.await()
            }
        )
        viewModel.enroll()
        viewModel.enroll()
        assertEquals(1, calls)
        assertTrue(viewModel.uiState.value.joining)
        gate.complete(FounderJoinResult.Cancelled)
        assertFalse(viewModel.uiState.value.joining)
        assertEquals(FounderProgramStatus.NotEnrolled, coordinator.currentState().status)
    }

    private suspend fun completeQualification() {
        assertPending("workout", "workout", "feedback", "report")
    }

    private suspend fun resetProgram() {
        store.save(app.mymusclemap.domain.entitlement.FounderProgramState())
        database.clearAllTables()
        today = LocalDate.of(2026, 10, 3)
        subscription.current = SubscriptionEntitlement()
        coordinator.refresh()
    }

    private suspend fun insertWorkout(
        day: LocalDate,
        status: SessionStatus,
        name: String = "Push",
        fingerprint: String? = null,
        activeLock: Int? = null
    ) {
        database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = name,
                status = status.name,
                workoutDate = day.toString(),
                startedAt = 1L,
                finishedAt = if (status == SessionStatus.COMPLETED) 2L else null,
                abandonedAt = if (status == SessionStatus.ABANDONED) 2L else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = "MANUAL",
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 2L,
                activeLock = activeLock,
                importFingerprint = fingerprint
            )
        )
    }

    private class MutableSubscription : SubscriptionEntitlementProvider {
        var current: SubscriptionEntitlement = SubscriptionEntitlement()
        override fun current(): SubscriptionEntitlement = current
    }
}
