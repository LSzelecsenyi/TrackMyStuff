package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.AbandonWorkoutResult
import hu.laca.weighttracker.domain.workout.ActualSetDraft
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.FinishWorkoutResult
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.SessionMutationResult
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutSessionRepositoryRoomTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var weights: WeightRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val today = LocalDate.parse("2026-09-15")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock,
            database.workoutSessionDao()
        )
        weights = WeightRepository(database.weightMeasurementDao(), clock)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            weights,
            clock,
            FixedDateProvider(today)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startCreatesCompleteSnapshotAndInitializesActuals() = runTest {
        val pull = savePull()
        val dip = saveDip()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets(), dip to fourSets()))
        val started = sessions.start(templateId, "88,3", sessions.proposeBodyWeight(), false)
            as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        assertEquals("Push A", aggregate.session.templateName)
        assertEquals(listOf("Húzódzkodás", "Tolódzkodás"), aggregate.exercises.map { it.exercise.name })
        assertEquals(listOf(0, 1), aggregate.exercises.map { it.exercise.position })
        assertEquals(listOf(0, 1, 2, 3), aggregate.exercises[0].sets.map { it.position })
        assertEquals(8, aggregate.exercises[0].sets[0].actualReps)
        assertEquals(PlannedLoadKind.BODYWEIGHT_ONLY, aggregate.exercises[0].sets[0].actualLoadKind)
        assertTrue(aggregate.exercises[0].sets.all { it.status == SessionSetStatus.PENDING })
        assertEquals(MuscleGroup.BICEPS, aggregate.exercises[0].exercise.secondaryMuscles.single())
    }

    @Test
    fun templateAndExerciseEditsDoNotChangeStartedSession() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId, "", sessions.proposeBodyWeight(), false)
            as StartWorkoutResult.Started
        templates.save(
            TemplateDraft(
                id = templateId,
                name = "Push B",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1,
                        exerciseId = pull,
                        sets = listOf(PlannedSetDraft(-1, "6", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "10"))
                    )
                )
            )
        )
        exercises.save(
            ExerciseDraft(
                id = pull,
                name = "Húzódzkodás plusz",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = listOf(MuscleGroup.TRICEPS)
            )
        )
        val stored = sessions.getAggregate(started.sessionId)!!
        assertEquals("Push A", stored.session.templateName)
        assertEquals("Húzódzkodás", stored.exercises.single().exercise.name)
        assertEquals(MuscleGroup.LATS, stored.exercises.single().exercise.primaryMuscle)
        assertEquals(listOf(MuscleGroup.BICEPS), stored.exercises.single().exercise.secondaryMuscles)
        assertEquals(4, stored.exercises.single().sets.size)
        assertEquals(8, stored.exercises.single().sets[0].plannedMinReps)
    }

    @Test
    fun archivedTemplateCannotStartAndEmptyTemplateIsRejected() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        templates.archive(templateId)
        assertEquals(
            StartWorkoutResult.TemplateArchived,
            sessions.start(templateId, "", sessions.proposeBodyWeight(), false)
        )
        val emptyId = (templates.save(TemplateDraft(name = "Üres")) as TemplateSaveResult.Created).id
        assertEquals(
            StartWorkoutResult.TemplateEmpty,
            sessions.start(emptyId, "", sessions.proposeBodyWeight(), false)
        )
    }

    @Test
    fun onlyOneActiveSessionIsAllowed() = runTest {
        val pull = savePull()
        val first = saveTemplate("Push A", listOf(pull to fourSets()))
        val second = saveTemplate("Pull A", listOf(pull to fourSets()))
        val started = sessions.start(first, "", sessions.proposeBodyWeight(), false) as StartWorkoutResult.Started
        assertEquals(
            StartWorkoutResult.AlreadyActive,
            sessions.start(second, "", sessions.proposeBodyWeight(), false)
        )
        assertEquals(started.sessionId, sessions.observeInProgress().first()!!.session.id)
    }

    @Test
    fun bodyWeightSnapshotStaysImmutableAfterDailyEdit() = runTest {
        weights.save(today, 88.3)
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val proposal = sessions.proposeBodyWeight()
        assertEquals(BodyWeightSource.MEASURED_SAME_DAY, proposal.source)
        val started = sessions.start(templateId, "87,9", proposal, true) as StartWorkoutResult.Started
        weights.save(today, 90.0)
        val stored = sessions.getAggregate(started.sessionId)!!
        assertEquals(87.9, stored.session.bodyWeightKg!!, 0.0)
        assertEquals(BodyWeightSource.MANUAL, stored.session.bodyWeightSource)
    }

    @Test
    fun completeSkipExtraFinishAndAbandon() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId, "", sessions.proposeBodyWeight(), false)
            as StartWorkoutResult.Started
        val first = sessions.getAggregate(started.sessionId)!!.exercises.single().sets
        val completeDraft = ActualSetDraft(repsText = "7", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "10")
        assertEquals(SessionMutationResult.Updated, sessions.completeSet(first[0].id, completeDraft))
        val afterComplete = sessions.getAggregate(started.sessionId)!!.exercises.single().sets[0]
        assertEquals(SessionSetStatus.COMPLETED, afterComplete.status)
        assertEquals(7, afterComplete.actualReps)
        assertEquals(PlannedLoadKind.ADDED_WEIGHT, afterComplete.actualLoadKind)
        assertEquals(SessionMutationResult.Updated, sessions.completeSet(first[0].id, completeDraft.copy(repsText = "6")))
        assertEquals(SessionSetStatus.COMPLETED, sessions.getAggregate(started.sessionId)!!.exercises.single().sets[0].status)
        assertEquals(6, sessions.getAggregate(started.sessionId)!!.exercises.single().sets[0].actualReps)
        assertEquals(SessionMutationResult.Updated, sessions.skipSet(first[1].id))
        assertEquals(SessionSetStatus.SKIPPED, sessions.getAggregate(started.sessionId)!!.exercises.single().sets[1].status)
        assertNull(sessions.getAggregate(started.sessionId)!!.exercises.single().sets[1].actualReps)
        assertEquals(SessionMutationResult.Updated, sessions.undoSkip(first[1].id))
        assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(started.sessionId)!!.exercises.single().sets[1].status)
        val exerciseId = sessions.getAggregate(started.sessionId)!!.exercises.single().exercise.id
        assertEquals(SessionMutationResult.Updated, sessions.addExtraSet(exerciseId))
        var sets = sessions.getAggregate(started.sessionId)!!.exercises.single().sets
        assertEquals(5, sets.size)
        assertTrue(sets.last().addedDuringWorkout)
        assertEquals(SessionMutationResult.Updated, sessions.removeExtraSet(sets.last().id))
        sets = sessions.getAggregate(started.sessionId)!!.exercises.single().sets
        assertEquals(listOf(0, 1, 2, 3), sets.map { it.position })
        assertEquals(SessionMutationResult.OriginalSetProtected, sessions.removeExtraSet(sets.first().id))
        assertEquals(SessionMutationResult.Updated, sessions.addExtraSet(exerciseId))
        val extra = sessions.getAggregate(started.sessionId)!!.exercises.single().sets.last()
        sessions.completeSet(extra.id, ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        val pending = sessions.finish(started.sessionId, skipRemaining = false) as FinishWorkoutResult.PendingRemaining
        assertTrue(pending.count > 0)
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        assertEquals(FinishWorkoutResult.AlreadyTerminal, sessions.finish(started.sessionId, skipRemaining = true))
        assertNull(sessions.observeInProgress().first())
        val completed = sessions.getAggregate(started.sessionId)!!
        assertEquals(SessionStatus.COMPLETED, completed.session.status)
        assertTrue(completed.exercises.single().sets.none { it.status == SessionSetStatus.PENDING })

        val other = saveTemplate("Pull A", listOf(pull to fourSets()))
        val second = sessions.start(other, "", sessions.proposeBodyWeight(), false) as StartWorkoutResult.Started
        assertEquals(AbandonWorkoutResult.Abandoned, sessions.abandon(second.sessionId))
        assertNull(sessions.observeInProgress().first())
        assertEquals(SessionStatus.ABANDONED, sessions.getAggregate(second.sessionId)!!.session.status)
    }

    @Test
    fun referencedTemplateAndExerciseCannotBeDeletedWhileSessionExists() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId, "", sessions.proposeBodyWeight(), false)
            as StartWorkoutResult.Started
        assertFalse(templates.canDeletePermanently(templateId))
        assertFalse(exercises.canDeletePermanently(pull))
        assertTrue(templates.archive(templateId))
        val stored = sessions.getAggregate(started.sessionId)!!
        assertEquals("Push A", stored.session.templateName)
        assertTrue(exercises.archive(pull))
        assertEquals("Húzódzkodás", sessions.getAggregate(started.sessionId)!!.exercises.single().exercise.name)
        sessions.finish(started.sessionId, skipRemaining = true)
        assertFalse(templates.canDeletePermanently(templateId))
        assertFalse(exercises.canDeletePermanently(pull))
    }

    @Test
    fun setTableHasNoRirOrRpe() = runTest {
        val cursor = database.openHelper.readableDatabase.query("PRAGMA table_info(workout_session_sets)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns += cursor.getString(1).lowercase()
        }
        cursor.close()
        assertFalse(columns.any { it.contains("rir") || it.contains("rpe") })
    }

    private suspend fun savePull(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Húzódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS,
                secondaryMuscles = listOf(MuscleGroup.BICEPS)
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun saveDip(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Tolódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.TRICEPS
            )
        ) as ExerciseSaveResult.Created).id
    }

    private fun fourSets(): List<PlannedSetDraft> {
        return List(4) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
            )
        }
    }

    private suspend fun saveTemplate(
        name: String,
        items: List<Pair<Long, List<PlannedSetDraft>>>
    ): Long {
        val draft = TemplateDraft(
            name = name,
            exercises = items.mapIndexed { index, item ->
                TemplateExerciseDraft(
                    localId = -(index + 1L),
                    exerciseId = item.first,
                    sets = item.second
                )
            }
        )
        return (templates.save(draft) as TemplateSaveResult.Created).id
    }
}
