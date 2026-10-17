package app.mymusclemap.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.AddExtraExerciseResult
import app.mymusclemap.domain.workout.ApplyPlanProgressionResult
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.domain.workout.toSetDraft
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
class PlanProgressionRoomTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-10-16T08:00:00Z"), ZoneOffset.UTC)
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
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(LocalDate.parse("2026-10-16"))
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun approvedUpdateChangesOnlyImprovedPlanSetsAndLeavesHistoryAlone() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate(
            "Arms",
            listOf(curl to listOf(set(8, "15"), set(8, "15"), set(8, "15")))
        )
        val planBefore = database.workoutTemplateDao().getExercises(templateId)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val active = sessions.getAggregate(started.sessionId)!!
        val exercise = active.exercises.single()
        assertEquals(planBefore.single().id, exercise.exercise.sourceTemplateExerciseId)
        assertFalse(exercise.exercise.addedDuringWorkout)
        assertEquals(
            database.workoutTemplateDao().getSets(planBefore.single().id).map { it.id },
            exercise.sets.map { it.sourceTemplateSetId }
        )
        complete(exercise.sets[0].id, "10", "15")
        complete(exercise.sets[1].id, "8", "17.5")
        complete(exercise.sets[2].id, "7", "15")
        val extra = sessions.addExtraSet(exercise.exercise.id)
        assertEquals(app.mymusclemap.domain.workout.SessionMutationResult.Updated, extra)
        val withExtra = sessions.getAggregate(started.sessionId)!!
        val extraSet = withExtra.exercises.single().sets.last()
        assertTrue(extraSet.addedDuringWorkout)
        assertNull(extraSet.sourceTemplateSetId)
        complete(extraSet.id, "6", "15")
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = false))
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = sessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }.toSet()
        assertEquals(2, shown.size)
        assertEquals(
            database.workoutTemplateDao().getSets(planBefore.single().id).map { it.minReps to it.weightKg },
            listOf(8 to 15.0, 8 to 15.0, 8 to 15.0)
        )
        val applied = sessions.applyPlanProgression(clientId, shown) as ApplyPlanProgressionResult.Applied
        assertEquals(2, applied.updated)
        assertEquals(0, applied.skipped)
        val planSets = database.workoutTemplateDao().getSets(planBefore.single().id)
        assertEquals(listOf(10 to 15.0, 8 to 17.5, 8 to 15.0), planSets.map { it.minReps to it.weightKg })
        val history = sessions.getAggregate(started.sessionId)!!
        assertEquals(listOf(10, 8, 7, 6), history.exercises.single().sets.map { it.actualReps })
        assertEquals(listOf(15.0, 15.0, 15.0), history.exercises.single().sets.take(3).map { it.plannedWeightKg })
        assertTrue(history.session.planProgressionApplied)
        assertEquals(1, planBefore.size)
        val again = sessions.applyPlanProgression(clientId, shown)
        assertEquals(ApplyPlanProgressionResult.AlreadyApplied, again)
        assertEquals(
            listOf(10 to 15.0, 8 to 17.5, 8 to 15.0),
            database.workoutTemplateDao().getSets(planBefore.single().id).map { it.minReps to it.weightKg }
        )
    }

    @Test
    fun anEditedOrMissingPlanTargetIsSkippedAndAFailedWriteRollsBack() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate("Arms", listOf(curl to listOf(set(8, "15"), set(8, "15"))))
        val planExercise = database.workoutTemplateDao().getExercises(templateId).single()
        val planSets = database.workoutTemplateDao().getSets(planExercise.id)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises.single().sets[0].id, "10", "15")
        complete(active.exercises.single().sets[1].id, "8", "17.5")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = sessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }.toSet()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_template_sets SET weightKg = 20 WHERE id = ${planSets[0].id}"
        )
        val partial = sessions.applyPlanProgression(clientId, shown) as ApplyPlanProgressionResult.Applied
        assertEquals(1, partial.updated)
        assertEquals(1, partial.skipped)
        val afterPartial = database.workoutTemplateDao().getSets(planExercise.id)
        assertEquals(20.0, afterPartial[0].weightKg!!, 0.0)
        assertEquals(8, afterPartial[0].minReps)
        assertEquals(17.5, afterPartial[1].weightKg!!, 0.0)
        assertTrue(sessions.getAggregate(started.sessionId)!!.session.planProgressionApplied)

        val secondTemplate = saveTemplate("Arms 2", listOf(curl to listOf(set(8, "15"), set(8, "15"))))
        val secondExercise = database.workoutTemplateDao().getExercises(secondTemplate).single()
        val secondSets = database.workoutTemplateDao().getSets(secondExercise.id)
        val secondStart = sessions.start(secondTemplate) as StartWorkoutResult.Started
        val second = sessions.getAggregate(secondStart.sessionId)!!
        complete(second.exercises.single().sets[0].id, "10", "15")
        complete(second.exercises.single().sets[1].id, "10", "15")
        sessions.finish(secondStart.sessionId, skipRemaining = false)
        val secondClient = sessions.getAggregate(secondStart.sessionId)!!.session.clientWorkoutId
        val secondShown = sessions.planProgression(secondClient).updates.map { it.sourceTemplateSetId }.toSet()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_plan_update
            BEFORE UPDATE ON workout_template_sets
            WHEN NEW.id = ${secondSets[1].id}
            BEGIN
                SELECT RAISE(ABORT, 'fail');
            END
            """.trimIndent()
        )
        assertEquals(ApplyPlanProgressionResult.Failed, sessions.applyPlanProgression(secondClient, secondShown))
        assertEquals(
            listOf(8 to 15.0, 8 to 15.0),
            database.workoutTemplateDao().getSets(secondExercise.id).map { it.minReps to it.weightKg }
        )
        assertFalse(sessions.getAggregate(secondStart.sessionId)!!.session.planProgressionApplied)
    }

    @Test
    fun duplicatePlanOccurrencesStayIndependentAndUnmappedHistoryIsLeftAlone() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate(
            "Twice",
            listOf(curl to listOf(set(8, "15")), curl to listOf(set(8, "15")))
        )
        val planExercises = database.workoutTemplateDao().getExercises(templateId)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises[0].sets[0].id, "8", "15")
        complete(active.exercises[1].sets[0].id, "10", "15")
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_session_exercises SET position = 2 WHERE id = ${active.exercises[0].exercise.id}"
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_session_exercises SET position = 0 WHERE id = ${active.exercises[1].exercise.id}"
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_session_exercises SET position = 1 WHERE id = ${active.exercises[0].exercise.id}"
        )
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val proposal = sessions.planProgression(clientId)
        assertEquals(planExercises[1].id, proposal.updates.single().sourceTemplateExerciseId)
        val applied = sessions.applyPlanProgression(
            clientId,
            proposal.updates.map { it.sourceTemplateSetId }.toSet()
        ) as ApplyPlanProgressionResult.Applied
        assertEquals(1, applied.updated)
        assertEquals(8, database.workoutTemplateDao().getSets(planExercises[0].id).single().minReps)
        assertEquals(10, database.workoutTemplateDao().getSets(planExercises[1].id).single().minReps)

        val plain = saveTemplate("Plain", listOf(curl to listOf(set(8, "15"))))
        val plainStart = sessions.start(plain) as StartWorkoutResult.Started
        val plainAggregate = sessions.getAggregate(plainStart.sessionId)!!
        complete(plainAggregate.exercises.single().sets.single().id, "12", "15")
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_session_exercises SET sourceTemplateExerciseId = NULL"
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_session_sets SET sourceTemplateSetId = NULL"
        )
        sessions.finish(plainStart.sessionId, skipRemaining = false)
        val plainClient = sessions.getAggregate(plainStart.sessionId)!!.session.clientWorkoutId
        assertTrue(sessions.planProgression(plainClient).updates.isEmpty())
        assertEquals(8, database.workoutTemplateDao().getSets(
            database.workoutTemplateDao().getExercises(plain).single().id
        ).single().minReps)
    }

    @Test
    fun anExtraExerciseDoesNotChangeThePlan() = runTest {
        val curl = saveCurl()
        val raise = saveCurl("Lateral Raise")
        val templateId = saveTemplate("Arms", listOf(curl to listOf(set(8, "15"))))
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises.single().sets.single().id, "8", "15")
        val added = sessions.addExtraExercise(started.sessionId, raise) as AddExtraExerciseResult.Added
        complete(added.setId, "12", "10")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        assertTrue(sessions.planProgression(clientId).updates.isEmpty())
        assertEquals(1, database.workoutTemplateDao().getExercises(templateId).size)
    }

    @Test
    fun anUnrelatedPlanEditKeepsProgressionForTheUntouchedSet() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate("Arms", listOf(curl to listOf(set(8, "15"), set(8, "15"))))
        val before = database.workoutTemplateDao().getExercises(templateId).single()
        val beforeSets = database.workoutTemplateDao().getSets(before.id)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        editPreserving(templateId) { draft ->
            val exercise = draft.exercises.single()
            val sets = exercise.sets.toMutableList()
            sets[1] = sets[1].copy(minRepsText = "10")
            draft.copy(exercises = listOf(exercise.copy(sets = sets)))
        }
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises.single().sets[0].id, "12", "15")
        complete(active.exercises.single().sets[1].id, "8", "15")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = sessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }
        assertEquals(listOf(beforeSets[0].id), shown)
        val applied = sessions.applyPlanProgression(clientId, shown.toSet()) as ApplyPlanProgressionResult.Applied
        assertEquals(1, applied.updated)
        val after = database.workoutTemplateDao().getSets(before.id)
        assertEquals(beforeSets.map { it.id }, after.map { it.id })
        assertEquals(12, after[0].minReps)
        assertEquals(10, after[1].minReps)
    }

    @Test
    fun aTargetChangedAfterStartConflictsAndIsNotOverwritten() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate("Arms", listOf(curl to listOf(set(8, "15"))))
        val planSet = database.workoutTemplateDao().getSets(
            database.workoutTemplateDao().getExercises(templateId).single().id
        ).single()
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        editPreserving(templateId) { draft ->
            val exercise = draft.exercises.single()
            draft.copy(
                exercises = listOf(
                    exercise.copy(sets = listOf(exercise.sets.single().copy(minRepsText = "10")))
                )
            )
        }
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises.single().sets.single().id, "12", "15")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        assertTrue(sessions.planProgression(clientId).updates.isEmpty())
        val stored = database.workoutTemplateDao().getSets(planSet.templateExerciseId).single()
        assertEquals(planSet.id, stored.id)
        assertEquals(10, stored.minReps)
        assertEquals(15.0, stored.weightKg!!, 0.0)
    }

    @Test
    fun reorderingExercisesOrSetsDoesNotBreakSourceMapping() = runTest {
        val curl = saveCurl()
        val raise = saveCurl("Lateral Raise")
        val templateId = saveTemplate(
            "Arms",
            listOf(
                curl to listOf(set(8, "15"), set(8, "15")),
                raise to listOf(set(10, "8"))
            )
        )
        val beforeExercises = database.workoutTemplateDao().getExercises(templateId)
        val curlSets = database.workoutTemplateDao().getSets(beforeExercises[0].id)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        editPreserving(templateId) { draft ->
            val first = draft.exercises[0]
            val swappedSets = listOf(first.sets[1], first.sets[0])
            draft.copy(exercises = listOf(draft.exercises[1], first.copy(sets = swappedSets)))
        }
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises[0].sets[0].id, "10", "15")
        complete(active.exercises[0].sets[1].id, "8", "15")
        complete(active.exercises[1].sets.single().id, "10", "8")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = sessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }
        assertEquals(listOf(curlSets[0].id), shown)
        val applied = sessions.applyPlanProgression(clientId, shown.toSet()) as ApplyPlanProgressionResult.Applied
        assertEquals(1, applied.updated)
        val afterExercises = database.workoutTemplateDao().getExercises(templateId)
        assertEquals(beforeExercises[1].id, afterExercises[0].id)
        assertEquals(beforeExercises[0].id, afterExercises[1].id)
        val afterCurlSets = database.workoutTemplateDao().getSets(beforeExercises[0].id)
        assertEquals(listOf(curlSets[1].id, curlSets[0].id), afterCurlSets.map { it.id })
        assertEquals(10, afterCurlSets[1].minReps)
        assertEquals(8, afterCurlSets[0].minReps)
    }

    @Test
    fun removedSourceSetAndExerciseAreSkippedAndNotRecreated() = runTest {
        val curl = saveCurl()
        val raise = saveCurl("Lateral Raise")
        val templateId = saveTemplate(
            "Arms",
            listOf(
                curl to listOf(set(8, "15"), set(8, "15")),
                raise to listOf(set(10, "8"))
            )
        )
        val before = database.workoutTemplateDao().getExercises(templateId)
        val removedSet = database.workoutTemplateDao().getSets(before[0].id)[0]
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        editPreserving(templateId) { draft ->
            val curlDraft = draft.exercises[0]
            draft.copy(exercises = listOf(curlDraft.copy(sets = curlDraft.sets.drop(1))))
        }
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises[0].sets[0].id, "12", "15")
        complete(active.exercises[0].sets[1].id, "12", "15")
        complete(active.exercises[1].sets.single().id, "12", "8")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = sessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }
        val keptId = database.workoutTemplateDao().getSets(before[0].id).single().id
        assertEquals(listOf(keptId), shown)
        assertFalse(removedSet.id in shown)
        sessions.applyPlanProgression(clientId, shown.toSet())
        val exercises = database.workoutTemplateDao().getExercises(templateId)
        assertEquals(listOf(before[0].id), exercises.map { it.id })
        val sets = database.workoutTemplateDao().getSets(before[0].id)
        assertEquals(1, sets.size)
        assertFalse(sets.any { it.id == removedSet.id })
    }

    @Test
    fun duplicateCatalogOccurrencesStayIndependentlyEditable() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate(
            "Arms",
            listOf(
                curl to listOf(set(8, "15")),
                curl to listOf(set(8, "15"))
            )
        )
        val before = database.workoutTemplateDao().getExercises(templateId)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        editPreserving(templateId) { draft ->
            val second = draft.exercises[1]
            draft.copy(
                exercises = listOf(
                    draft.exercises[0],
                    second.copy(sets = listOf(second.sets.single().copy(weightText = "20")))
                )
            )
        }
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises[0].sets.single().id, "10", "15")
        complete(active.exercises[1].sets.single().id, "8", "15")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = sessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }
        val firstSet = database.workoutTemplateDao().getSets(before[0].id).single().id
        assertEquals(listOf(firstSet), shown)
        sessions.applyPlanProgression(clientId, shown.toSet())
        val first = database.workoutTemplateDao().getSets(before[0].id).single()
        val second = database.workoutTemplateDao().getSets(before[1].id).single()
        assertEquals(10, first.minReps)
        assertEquals(15.0, first.weightKg!!, 0.0)
        assertEquals(8, second.minReps)
        assertEquals(20.0, second.weightKg!!, 0.0)
        assertNotEquals(before[0].id, before[1].id)
    }

    @Test
    fun planDuplicationDoesNotRedirectProgression() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate("Arms", listOf(curl to listOf(set(8, "15"))))
        val originalExercise = database.workoutTemplateDao().getExercises(templateId).single()
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val loaded = editableDraft(templateId)
        val duplicate = templates.save(
            loaded.copy(id = null, name = "Arms copy", baselineUpdatedAt = null)
        ) as TemplateSaveResult.Created
        val active = sessions.getAggregate(started.sessionId)!!
        complete(active.exercises.single().sets.single().id, "12", "15")
        sessions.finish(started.sessionId, skipRemaining = false)
        val clientId = sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = sessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }.toSet()
        val applied = sessions.applyPlanProgression(clientId, shown) as ApplyPlanProgressionResult.Applied
        assertEquals(1, applied.updated)
        val originalSet = database.workoutTemplateDao().getSets(originalExercise.id).single()
        val copyExercise = database.workoutTemplateDao().getExercises(duplicate.id).single()
        val copySet = database.workoutTemplateDao().getSets(copyExercise.id).single()
        assertEquals(12, originalSet.minReps)
        assertEquals(8, copySet.minReps)
        assertNotEquals(originalExercise.id, copyExercise.id)
        assertNotEquals(originalSet.id, copySet.id)
    }

    @Test
    fun aStaleEditorDoesNotOverwriteANewerProgressionUpdate() = runTest {
        val curl = saveCurl()
        val templateId = saveTemplate("Arms", listOf(curl to listOf(set(8, "15"))))
        val loaded = editableDraft(templateId)
        val laterClock = Clock.fixed(Instant.parse("2026-10-16T09:00:00Z"), ZoneOffset.UTC)
        val laterSessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), laterClock),
            laterClock,
            FixedDateProvider(LocalDate.parse("2026-10-16"))
        )
        val started = laterSessions.start(templateId) as StartWorkoutResult.Started
        val active = laterSessions.getAggregate(started.sessionId)!!
        laterSessions.completeSet(
            active.exercises.single().sets.single().id,
            ActualSetDraft(repsText = "12", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "15")
        )
        laterSessions.finish(started.sessionId, skipRemaining = false)
        val clientId = laterSessions.getAggregate(started.sessionId)!!.session.clientWorkoutId
        val shown = laterSessions.planProgression(clientId).updates.map { it.sourceTemplateSetId }.toSet()
        assertEquals(
            ApplyPlanProgressionResult.Applied(updated = 1, skipped = 0),
            laterSessions.applyPlanProgression(clientId, shown)
        )
        val stale = templates.save(loaded.copy(name = "Arms renamed"))
        val stored = database.workoutTemplateDao().getById(templateId)!!
        val storedSet = database.workoutTemplateDao().getSets(
            database.workoutTemplateDao().getExercises(templateId).single().id
        ).single()
        assertEquals(TemplateSaveResult.Stale, stale)
        assertEquals("Arms", stored.name)
        assertEquals(12, storedSet.minReps)
        assertEquals(loaded.exercises.single().sets.single().localId, storedSet.id)
    }

    private suspend fun complete(setId: Long, reps: String, weight: String) {
        sessions.completeSet(
            setId,
            ActualSetDraft(repsText = reps, loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = weight)
        )
    }

    private var nextLocalId = -1L

    private fun set(reps: Int, weight: String): PlannedSetDraft {
        nextLocalId -= 1
        return PlannedSetDraft(
            localId = nextLocalId,
            minRepsText = reps.toString(),
            loadKind = PlannedLoadKind.ADDED_WEIGHT,
            weightText = weight
        )
    }

    private suspend fun saveCurl(name: String = "Curl"): Long {
        return (exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.ISOLATION,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.BICEPS,
                secondaryMuscles = emptyList()
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun editableDraft(templateId: Long): TemplateDraft {
        val aggregate = templates.getAggregate(templateId)!!
        return TemplateDraft(
            id = aggregate.template.id,
            name = aggregate.template.name,
            notes = aggregate.template.notes.orEmpty(),
            createdAt = aggregate.template.createdAt,
            baselineUpdatedAt = aggregate.template.updatedAt,
            exercises = aggregate.exercises.map { item ->
                TemplateExerciseDraft(
                    localId = item.relation.id,
                    exerciseId = item.exercise.id,
                    notes = item.relation.notes.orEmpty(),
                    sets = item.sets.map { set -> set.toSetDraft(set.id) }
                )
            }
        )
    }

    private suspend fun editPreserving(
        templateId: Long,
        transform: (TemplateDraft) -> TemplateDraft
    ): TemplateSaveResult {
        val result = templates.save(transform(editableDraft(templateId)))
        assertTrue(result is TemplateSaveResult.Updated)
        return result
    }

    private suspend fun saveTemplate(name: String, items: List<Pair<Long, List<PlannedSetDraft>>>): Long {
        return (templates.save(
            TemplateDraft(
                name = name,
                exercises = items.mapIndexed { index, item ->
                    TemplateExerciseDraft(
                        localId = -(index + 1L),
                        exerciseId = item.first,
                        sets = item.second
                    )
                }
            )
        ) as TemplateSaveResult.Created).id
    }
}
