package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.local.WorkoutTemplateExerciseEntity
import app.mymusclemap.data.local.WorkoutTemplateSetEntity
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeExerciseReleaseResult
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.exercises.CatalogMessage
import app.mymusclemap.ui.exercises.ExerciseListViewModel
import app.mymusclemap.ui.exercises.summarizeFreeReleasePlans
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
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
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
class FreeAccessSlotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private val pro = AtomicBoolean(false)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun normalFreeCreationStopsAtFiveAndStartersDoNotCount() = runTest {
        val (exercises, access) = wired()
        assertTrue(exercises.save(draft("Starter"), asStarter = true) is ExerciseSaveResult.Created)
        repeat(5) { index ->
            assertTrue(exercises.save(draft("Custom $index")) is ExerciseSaveResult.Created)
        }
        assertEquals(ExerciseSaveResult.CreationLimited, exercises.save(draft("Custom 5")))
        assertEquals(5, exercises.activeCustomCount())
        access.refresh()
        assertEquals(5, access.current().accessibleCustomExerciseIds.size)
        assertEquals(FreeAccessPhase.NotRequired, access.current().state.phase)
    }

    @Test
    fun releaseFreesOneSlotForANewExerciseAndRejectsTheNext() = runTest {
        val (exercises, access) = wired()
        pro.set(true)
        val ids = (1..12).map { index ->
            (exercises.save(draft("Lift $index")) as ExerciseSaveResult.Created).id
        }
        pro.set(false)
        access.refresh()
        val confirmed = access.confirm(emptySet(), ids.take(5).toSet())
        assertTrue(confirmed)
        assertEquals(5, access.current().accessibleCustomExerciseIds.size)
        val released = ids[0]
        assertEquals(FreeExerciseReleaseResult.Released, access.releaseCustomExercise(released))
        assertEquals(FreeExerciseReleaseResult.AlreadyReleased, access.releaseCustomExercise(released))
        val afterRelease = access.current()
        assertEquals(4, afterRelease.accessibleCustomExerciseIds.size)
        assertFalse(afterRelease.customExerciseAccessible(released))
        assertTrue(ids.drop(5).all { !afterRelease.customExerciseAccessible(it) })
        val created = exercises.save(draft("Replacement")) as ExerciseSaveResult.Created
        access.refresh()
        assertTrue(access.current().customExerciseAccessible(created.id))
        assertEquals(5, access.current().accessibleCustomExerciseIds.size)
        assertFalse(access.current().customExerciseAccessible(released))
        assertFalse(access.allocateCreatedCustomExercise(ids[5]))
        assertFalse(access.current().customExerciseAccessible(ids[5]))
        assertEquals(ExerciseSaveResult.CreationLimited, exercises.save(draft("One too many")))
        assertEquals(5, access.current().accessibleCustomExerciseIds.size)
        assertFalse(access.current().customExerciseAccessible(released))
    }

    @Test
    fun releasedExerciseCannotBeRenamedArchivedOrReactivated() = runTest {
        val (exercises, access) = wired()
        val kept = (exercises.save(draft("Kept")) as ExerciseSaveResult.Created).id
        val released = (exercises.save(draft("Ring Pushup")) as ExerciseSaveResult.Created).id
        access.refresh()
        assertEquals(FreeExerciseReleaseResult.Released, access.releaseCustomExercise(released))
        val stored = exercises.getById(released)!!
        assertEquals("Ring Pushup", stored.name)
        assertFalse(stored.archived)
        val renamed = exercises.save(
            draft("Renamed").copy(id = released, primaryMuscle = stored.primaryMuscle)
        )
        assertTrue(renamed is ExerciseSaveResult.Locked)
        assertEquals("Ring Pushup", exercises.getById(released)!!.name)
        assertFalse(exercises.archive(released))
        assertFalse(exercises.getById(released)!!.archived)
        assertFalse(exercises.restore(released))
        assertFalse(access.current().customExerciseAccessible(released))
        assertTrue(access.current().customExerciseAccessible(kept))
        assertEquals(FreeExerciseReleaseResult.AlreadyReleased, access.releaseCustomExercise(released))
        assertTrue(access.current().customExerciseAccessible(kept))
    }

    @Test
    fun deletingASelectedExerciseDoesNotPromoteALockedOne() = runTest {
        val (exercises, access) = wired()
        pro.set(true)
        val ids = (1..7).map { index ->
            (exercises.save(draft("Item $index")) as ExerciseSaveResult.Created).id
        }
        pro.set(false)
        access.refresh()
        assertTrue(access.confirm(emptySet(), ids.take(5).toSet()))
        val selected = ids[1]
        val locked = ids[6]
        assertEquals(
            app.mymusclemap.domain.exercise.ExerciseDeleteResult.Deleted,
            exercises.deletePermanently(selected)
        )
        access.refresh()
        assertFalse(access.current().customExerciseAccessible(locked))
        assertFalse(access.allocateCreatedCustomExercise(locked))
        assertFalse(access.current().customExerciseAccessible(locked))
        val before = access.current().accessibleCustomExerciseIds.size
        val released = ids[0]
        assertEquals(FreeExerciseReleaseResult.Released, access.releaseCustomExercise(released))
        assertEquals(
            app.mymusclemap.domain.exercise.ExerciseDeleteResult.Deleted,
            exercises.deletePermanently(released)
        )
        access.refresh()
        assertEquals(before - 1, access.current().accessibleCustomExerciseIds.size)
        assertTrue(released in access.current().state.releasedCustomExerciseIds)
    }

    @Test
    fun proRestoresAccessAndTheNextDowngradeStartsACleanPeriod() = runTest {
        val (exercises, access) = wired()
        val id = (exercises.save(draft("Ring Pushup")) as ExerciseSaveResult.Created).id
        access.refresh()
        assertEquals(FreeExerciseReleaseResult.Released, access.releaseCustomExercise(id))
        val period = access.current().state.periodId
        pro.set(true)
        access.refresh()
        assertTrue(access.current().customExerciseAccessible(id))
        assertEquals(setOf(id), database.freeAccessDao().read()?.releasedCustomExerciseIds)
        pro.set(false)
        access.refresh()
        val next = access.current()
        assertEquals(period + 1L, next.state.periodId)
        assertTrue(next.state.releasedCustomExerciseIds.isEmpty())
        assertTrue(next.customExerciseAccessible(id))
    }

    @Test
    fun restartAndBackupKeepTheReleaseAndReconciliationDoesNotRefillIt() = runTest {
        val (exercises, access) = wired()
        val id = (exercises.save(draft("Ring Pushup")) as ExerciseSaveResult.Created).id
        access.refresh()
        assertEquals(FreeExerciseReleaseResult.Released, access.releaseCustomExercise(id))
        val restarted = FreeAccessRepository(
            dao = database.freeAccessDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            grantsPro = { false },
            sessionDao = database.workoutSessionDao()
        )
        restarted.refresh()
        assertEquals(setOf(id), restarted.current().state.releasedCustomExerciseIds)
        assertFalse(restarted.current().customExerciseAccessible(id))
        val tables = database.appBackupDao().loadTables()
        database.appBackupDao().replaceAll(tables)
        val restored = database.freeAccessDao().read()!!
        assertEquals(setOf(id), restored.releasedCustomExerciseIds)
        restarted.refresh()
        assertFalse(restarted.current().customExerciseAccessible(id))
        assertEquals(setOf(id), restarted.current().state.releasedCustomExerciseIds)
    }

    @Test
    fun concurrentCreatesCannotPassTheLimitAndAFailedSaveConsumesNothing() = runTest {
        val (exercises, access) = wired()
        repeat(4) { index -> exercises.save(draft("Ready $index")) }
        access.refresh()
        assertEquals(4, access.current().accessibleCustomExerciseIds.size)
        val invalid = exercises.save(draft("   "))
        assertTrue(invalid is ExerciseSaveResult.Invalid)
        assertEquals(4, exercises.activeCustomCount())
        val results = listOf(
            async { exercises.save(draft("Race A")) },
            async { exercises.save(draft("Race B")) }
        ).awaitAll()
        access.refresh()
        assertEquals(5, access.current().accessibleCustomExerciseIds.size)
        assertEquals(1, results.count { it is ExerciseSaveResult.Created })
        assertEquals(ExerciseSaveResult.CreationLimited, exercises.save(draft("Race C")))
        assertEquals(5, exercises.activeCustomCount())
    }

    @Test
    fun releaseKeepsPlanRowsHistoryAndLocksOnlyThatExercise() = runTest {
        val (exercises, access) = wired()
        val released = (exercises.save(draft("Ring Pushup")) as ExerciseSaveResult.Created).id
        val other = (exercises.save(draft("Kept Press")) as ExerciseSaveResult.Created).id
        val planId = database.workoutTemplateDao().insertTemplate(
            WorkoutTemplateEntity(
                name = "Push Day",
                normalizedName = "push day",
                notes = null,
                archived = false,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val templateExerciseId = database.workoutTemplateDao().insertExercise(
            WorkoutTemplateExerciseEntity(
                templateId = planId,
                exerciseId = released,
                position = 2,
                notes = "keep",
                createdAt = 1L
            )
        )
        database.workoutTemplateDao().insertSet(
            WorkoutTemplateSetEntity(
                templateExerciseId = templateExerciseId,
                position = 0,
                minReps = 6,
                maxReps = 8,
                loadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                weightKg = 42.5,
                durationSeconds = null,
                distanceMeters = null
            )
        )
        val sessionId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = planId,
                templateName = "Push Day",
                status = SessionStatus.COMPLETED.name,
                workoutDate = "2026-10-01",
                startedAt = 10L,
                finishedAt = 20L,
                abandonedAt = null,
                notes = "history",
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN.name,
                bodyWeightSourceDate = null,
                createdAt = 10L,
                updatedAt = 20L,
                activeLock = null
            )
        )
        val sessionExerciseId = database.workoutSessionDao().insertExercise(
            sessionExercise(sessionId, released)
        )
        database.workoutSessionDao().insertSet(completedSet(sessionExerciseId))
        access.refresh()
        assertEquals(FreeExerciseReleaseResult.Released, access.releaseCustomExercise(released))
        val templateExercise = database.workoutTemplateDao().getExercises(planId).single()
        assertEquals(released, templateExercise.exerciseId)
        assertEquals(2, templateExercise.position)
        assertEquals("keep", templateExercise.notes)
        val set = database.workoutTemplateDao().getSets(templateExercise.id).single()
        assertEquals(6, set.minReps)
        assertEquals(42.5, set.weightKg!!, 0.0)
        val history = database.workoutSessionDao().getSets(sessionExerciseId).single()
        assertEquals(SessionSetStatus.COMPLETED.name, history.status)
        assertEquals(8, history.actualReps)
        assertFalse(access.current().customExerciseAccessible(released))
        assertTrue(access.current().customExerciseAccessible(other))
        assertTrue(access.current().planAccessible(planId))
        assertFalse(exercises.getById(released)!!.archived)
    }

    @Test
    fun activePendingWorkBlocksReleaseWithoutChangingTheWorkout() = runTest {
        val (exercises, access) = wired()
        val id = (exercises.save(draft("Ring Pushup")) as ExerciseSaveResult.Created).id
        val sessionId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Now",
                status = SessionStatus.IN_PROGRESS.name,
                workoutDate = "2026-10-23",
                startedAt = 10L,
                finishedAt = null,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN.name,
                bodyWeightSourceDate = null,
                createdAt = 10L,
                updatedAt = 10L,
                activeLock = 1
            )
        )
        val sessionExerciseId = database.workoutSessionDao().insertExercise(sessionExercise(sessionId, id))
        val setId = database.workoutSessionDao().insertSet(
            completedSet(sessionExerciseId).copy(
                status = SessionSetStatus.PENDING.name,
                actualReps = null,
                completedAt = null
            )
        )
        access.refresh()
        assertEquals(FreeExerciseReleaseResult.BlockedByActiveWorkout, access.releaseCustomExercise(id))
        assertTrue(access.current().customExerciseAccessible(id))
        assertEquals(SessionSetStatus.PENDING.name, database.workoutSessionDao().getSets(sessionExerciseId).single().status)
        assertEquals(setId, database.workoutSessionDao().getSets(sessionExerciseId).single().id)
    }

    @Test
    fun confirmationDialogDescribesAccessibleLockedAndMissingPlans() = runTest {
        val (shown, extra) = summarizeFreeReleasePlans(listOf("A", "B", "C", "D"))
        assertEquals("A, B, C", shown)
        assertEquals(1, extra)
        val (exercises, access) = wired()
        pro.set(true)
        val exerciseId = (exercises.save(draft("Ring Pushup")) as ExerciseSaveResult.Created).id
        val unused = (exercises.save(draft("Unused")) as ExerciseSaveResult.Created).id
        val onlyLockedId = (exercises.save(draft("Saved Elsewhere")) as ExerciseSaveResult.Created).id
        val push = insertPlan("Push Day", exerciseId, archived = false)
        val pull = insertPlan("Pull Day", exerciseId, archived = false)
        val legs = insertPlan("Leg Day", exerciseId, archived = false)
        val parked = insertPlan("Extra Day", onlyLockedId, archived = false)
        pro.set(false)
        access.refresh()
        assertTrue(access.confirm(setOf(push, pull, legs), emptySet()))
        val viewModel = ExerciseListViewModel(
            exercises,
            SelectiveFeatureEntitlements(emptySet()),
            access = access.decisions(),
            planReferences = { id -> database.workoutTemplateDao().plansReferencingExercise(id) },
            exerciseHasPendingWork = { false },
            releaseExercise = { id -> access.releaseCustomExercise(id) }
        )
        viewModel.uiState.first { exerciseId in it.freeReleaseExerciseIds && push in it.accessiblePlanIds }
        viewModel.requestRelease(exercises.getById(exerciseId)!!)
        val prompt = viewModel.uiState.first { it.pendingRelease?.exercise?.id == exerciseId }.pendingRelease!!
        assertEquals(listOf("Leg Day", "Pull Day", "Push Day"), prompt.accessiblePlanNames.sorted())
        assertTrue(prompt.lockedPlanNames.isEmpty())
        viewModel.dismissRelease()
        viewModel.requestRelease(exercises.getById(unused)!!)
        val empty = viewModel.uiState.first { it.pendingRelease?.exercise?.id == unused }.pendingRelease!!
        assertTrue(empty.accessiblePlanNames.isEmpty())
        assertTrue(empty.lockedPlanNames.isEmpty())
        viewModel.dismissRelease()
        viewModel.requestRelease(exercises.getById(onlyLockedId)!!)
        val lockedOnly = viewModel.uiState.first { it.pendingRelease?.exercise?.id == onlyLockedId }.pendingRelease!!
        assertTrue(lockedOnly.accessiblePlanNames.isEmpty())
        assertEquals(listOf("Extra Day"), lockedOnly.lockedPlanNames)
        assertTrue(access.current().planAccessible(push))
        assertFalse(access.current().planAccessible(parked))
    }

    @Test
    fun anActiveWorkoutPreventsTheDialogAndLeavesTheAllocationAlone() = runTest {
        val (exercises, access) = wired()
        val id = (exercises.save(draft("Ring Pushup")) as ExerciseSaveResult.Created).id
        access.refresh()
        val viewModel = ExerciseListViewModel(
            exercises,
            SelectiveFeatureEntitlements(emptySet()),
            access = access.decisions(),
            exerciseHasPendingWork = { true },
            releaseExercise = { exerciseId -> access.releaseCustomExercise(exerciseId) }
        )
        viewModel.uiState.first { id in it.freeReleaseExerciseIds }
        viewModel.requestRelease(exercises.getById(id)!!)
        val state = viewModel.uiState.first { it.message == CatalogMessage.ReleaseBlocked }
        assertEquals(null, state.pendingRelease)
        assertTrue(access.current().customExerciseAccessible(id))
    }

    private fun wired(): Pair<ExerciseRepository, FreeAccessRepository> {
        val access = FreeAccessRepository(
            dao = database.freeAccessDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            grantsPro = { pro.get() },
            sessionDao = database.workoutSessionDao()
        )
        val exercises = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC),
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao(),
            allowCustomCreate = {
                access.refresh()
                FreeAccessLogic.creationAllowed(
                    access.current(),
                    pro.get(),
                    database.exerciseDao().countActiveCustom()
                )
            },
            mayUpdate = { id ->
                val row = database.exerciseDao().getById(id)
                row == null || !row.custom || access.current().customExerciseAccessible(id)
            },
            onCustomCreated = { id -> access.allocateCreatedCustomExercise(id) }
        )
        return exercises to access
    }

    private fun draft(name: String): ExerciseDraft {
        return ExerciseDraft(name = name, primaryMuscle = MuscleGroup.BICEPS)
    }

    private suspend fun insertPlan(name: String, exerciseId: Long, archived: Boolean): Long {
        val id = database.workoutTemplateDao().insertTemplate(
            WorkoutTemplateEntity(
                name = name,
                normalizedName = name.lowercase(),
                notes = null,
                archived = archived,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        database.workoutTemplateDao().insertExercise(
            WorkoutTemplateExerciseEntity(
                templateId = id,
                exerciseId = exerciseId,
                position = 0,
                notes = null,
                createdAt = 1L
            )
        )
        return id
    }

    private fun sessionExercise(sessionId: Long, exerciseId: Long): WorkoutSessionExerciseEntity {
        return WorkoutSessionExerciseEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            position = 0,
            name = "Ring Pushup",
            category = ExerciseCategory.STRENGTH.name,
            movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
            measurementType = MeasurementType.REPETITIONS.name,
            resistanceBasis = ResistanceBasis.BODYWEIGHT.name,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE.name,
            primaryMuscle = MuscleGroup.CHEST.name,
            notes = null
        )
    }

    private fun completedSet(sessionExerciseId: Long): WorkoutSessionSetEntity {
        return WorkoutSessionSetEntity(
            sessionExerciseId = sessionExerciseId,
            position = 0,
            plannedMinReps = 8,
            plannedMaxReps = 8,
            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY.name,
            plannedWeightKg = null,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = 8,
            actualLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY.name,
            actualWeightKg = null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = SessionSetStatus.COMPLETED.name,
            completedAt = 20L,
            addedDuringWorkout = false
        )
    }
}
