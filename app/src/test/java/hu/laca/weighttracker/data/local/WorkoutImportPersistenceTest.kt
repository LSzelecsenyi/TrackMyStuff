package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportCsv
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportDatabaseFailure
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportParseResult
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportPersistenceResult
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutImportPersistenceTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var weights: WeightRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val today = LocalDate.parse("2026-09-16")
    private val clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
        sessions = repository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun historicalFixturePersistsFourCompletedSessions() = runTest {
        seedCatalogAndWeights()
        val plan = resolvedFixture()
        assertTrue(plan.canConfirm)
        val result = sessions.importCompletedWorkouts(plan) as WorkoutImportPersistenceResult.Imported
        assertEquals(4, result.workoutCount)
        assertEquals(11, result.exerciseCount)
        assertEquals(51, result.completedSetCount)
        assertEquals(0, result.skippedSetCount)
        assertEquals(4, result.sessionIds.size)
        val stored = result.sessionIds.map { sessions.getAggregate(it)!! }
        assertTrue(stored.all { it.session.templateId == null })
        assertTrue(stored.all { it.session.status == SessionStatus.COMPLETED })
        assertTrue(stored.all { it.session.importFingerprint != null })
        assertEquals(4, stored.map { it.session.importFingerprint }.distinct().size)
        assertTrue(stored.all { it.session.bodyWeightSource == BodyWeightSource.MEASURED_SAME_DAY })
        val pull = stored.single { it.session.templateName == "Pull" }
        assertEquals(WeightInterpretation.PER_SIDE, pull.exercises[2].exercise.weightInterpretation)
        assertEquals(17.5, pull.exercises[2].sets[0].actualWeightKg!!, 0.0)
        assertEquals(WeightInterpretation.PER_SIDE, pull.exercises[3].exercise.weightInterpretation)
        assertEquals(WeightInterpretation.TOTAL, pull.exercises[4].exercise.weightInterpretation)
        val run = stored.filter { it.session.templateName == "Futás" }
        assertEquals(2, run.size)
        run.forEach { session ->
            val set = session.exercises.single().sets.single()
            assertEquals(2000.0, set.actualDistanceMeters!!, 0.0)
            assertEquals(720, set.actualDurationSeconds)
            assertEquals(PlannedLoadKind.NONE, set.actualLoadKind)
        }
        assertEquals(4, sessions.observeSummaries().first().count { it.session.status == SessionStatus.COMPLETED })
        stored.forEach { aggregate ->
            val detail = sessions.getAggregate(aggregate.session.id)!!
            assertEquals(aggregate.exercises.map { it.exercise.name }, detail.exercises.map { it.exercise.name })
        }
        val counts = sessions.observeCompletedCounts(
            LocalDate.parse("2026-09-01"),
            LocalDate.parse("2026-09-30")
        ).first()
        assertEquals(1, counts[LocalDate.parse("2026-09-13")])
        assertEquals(2, counts[LocalDate.parse("2026-09-14")])
        assertEquals(1, counts[LocalDate.parse("2026-09-15")])
        val heatmap = sessions.observeHeatmapExercises().first()
        assertEquals(11, heatmap.size)
        assertTrue(heatmap.all { it.completedSetCount > 0 })
        val latest = sessions.observeLatestCompleted().first()
        assertEquals("Pull", latest?.session?.templateName)
        assertEquals(LocalDate.parse("2026-09-15"), latest?.session?.workoutDate)
    }

    @Test
    fun secondImportOfTheSamePlanIsFullyRejected() = runTest {
        seedCatalogAndWeights()
        val plan = resolvedFixture()
        val first = sessions.importCompletedWorkouts(plan) as WorkoutImportPersistenceResult.Imported
        val second = sessions.importCompletedWorkouts(plan)
        assertTrue(second is WorkoutImportPersistenceResult.DuplicateWorkouts)
        assertEquals(4, database.workoutSessionDao().observeAll().first().size)
        assertEquals(first.sessionIds.toSet(), database.workoutSessionDao().observeAll().first().map { it.id }.toSet())
        val recreated = repository()
        val third = recreated.importCompletedWorkouts(plan)
        assertTrue(third is WorkoutImportPersistenceResult.DuplicateWorkouts)
    }

    @Test
    fun duplicateFingerprintsInsideOnePlanWriteNothing() = runTest {
        seedCatalogAndWeights()
        val plan = resolvedFixture()
        val doubled = plan.copy(workouts = plan.workouts + plan.workouts.first().copy(workoutId = "duplicate-copy"))
        val result = sessions.importCompletedWorkouts(doubled)
        assertTrue(result is WorkoutImportPersistenceResult.DuplicateWorkouts)
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
        assertTrue(database.workoutSessionDao().observeAllExercises().first().isEmpty())
        assertTrue(database.workoutSessionDao().observeAllSets().first().isEmpty())
        assertTrue(database.workoutSessionDao().observeAllMuscles().first().isEmpty())
    }

    @Test
    fun sameDateAndNameWithDifferentContentCanBothBeImported() = runTest {
        seedCatalogAndWeights()
        val first = resolvedOneSet("Pullup", reps = "5")
        val second = resolvedOneSet("Pullup", reps = "4")
        assertTrue(sessions.importCompletedWorkouts(first) is WorkoutImportPersistenceResult.Imported)
        val imported = sessions.importCompletedWorkouts(second)
        assertTrue(imported.toString(), imported is WorkoutImportPersistenceResult.Imported)
        assertEquals(2, database.workoutSessionDao().observeAll().first().size)
    }

    @Test
    fun deletedImportedWorkoutCanBeImportedAgain() = runTest {
        seedCatalogAndWeights()
        val plan = resolvedOneSet("Pullup")
        val imported = sessions.importCompletedWorkouts(plan) as WorkoutImportPersistenceResult.Imported
        val sessionId = imported.sessionIds.single()
        val fingerprint = database.workoutSessionDao().getById(sessionId)!!.importFingerprint
        assertNotNull(fingerprint)
        assertEquals(
            hu.laca.weighttracker.domain.workout.DeleteWorkoutResult.Deleted,
            sessions.deleteWorkout(sessionId)
        )
        assertNull(database.workoutSessionDao().getById(sessionId))
        assertTrue(sessions.existingImportFingerprints().isEmpty())
        val second = sessions.importCompletedWorkouts(plan)
        assertTrue(second.toString(), second is WorkoutImportPersistenceResult.Imported)
        val reimported = (second as WorkoutImportPersistenceResult.Imported).sessionIds.single()
        assertEquals(fingerprint, database.workoutSessionDao().getById(reimported)!!.importFingerprint)
    }

    @Test
    fun uniqueIndexRejectsARacedDuplicateFingerprint() = runTest {
        seedCatalogAndWeights()
        val plan = resolvedOneSet("Pullup")
        val imported = sessions.importCompletedWorkouts(plan) as WorkoutImportPersistenceResult.Imported
        val original = database.workoutSessionDao().getById(imported.sessionIds.single())!!
        var failed = false
        try {
            database.workoutSessionDao().insertSession(
                original.copy(id = 0L, importFingerprint = original.importFingerprint)
            )
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(1, database.workoutSessionDao().observeAll().first().size)
    }

    @Test
    fun failedSecondWorkoutRollsBackTheEntireBatch() = runTest {
        seedCatalogAndWeights()
        val plan = resolvedFixture()
        val broken = plan.copy(
            workouts = plan.workouts.mapIndexed { index, workout ->
                if (index != 1) {
                    workout
                } else {
                    workout.copy(
                        exercises = workout.exercises.map { exercise ->
                            exercise.copy(snapshot = exercise.snapshot!!.copy(exerciseId = 999_999L))
                        }
                    )
                }
            }
        )
        val result = sessions.importCompletedWorkouts(broken)
        assertEquals(
            WorkoutImportPersistenceResult.DatabaseError(WorkoutImportDatabaseFailure.ForeignKey),
            result
        )
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
        assertTrue(database.workoutSessionDao().observeAllExercises().first().isEmpty())
        assertTrue(database.workoutSessionDao().observeAllSets().first().isEmpty())
        assertTrue(database.workoutSessionDao().observeAllMuscles().first().isEmpty())
    }

    @Test
    fun unconfirmablePlanIsRejectedBeforeWrites() = runTest {
        seedCatalogAndWeights()
        val plan = WorkoutImportResolver.resolve(
            parse(oneSetCsv("Missing")),
            exercises.observeAll().first()
        )
        assertFalse(plan.canConfirm)
        val result = sessions.importCompletedWorkouts(plan)
        assertTrue(result is WorkoutImportPersistenceResult.PlanNotConfirmable)
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
    }

    @Test
    fun importedSnapshotsDoNotReloadLiveCatalog() = runTest {
        seedCatalogAndWeights()
        val plan = resolvedOneSet("Pullup")
        val imported = sessions.importCompletedWorkouts(plan) as WorkoutImportPersistenceResult.Imported
        val pull = exercises.observeAll().first().single { it.name == "Pullup" }
        exercises.save(
            ExerciseDraft(
                id = pull.id,
                name = "Pullup renamed",
                category = pull.category,
                movementPattern = pull.movementPattern,
                measurementType = pull.measurementType,
                resistanceBasis = pull.resistanceBasis,
                weightInterpretation = pull.weightInterpretation,
                primaryMuscle = MuscleGroup.CHEST,
                secondaryMuscles = listOf(MuscleGroup.TRICEPS),
                notes = pull.notes.orEmpty(),
                createdAt = pull.createdAt
            )
        )
        val stored = sessions.getAggregate(imported.sessionIds.single())!!
        assertEquals("Pullup", stored.exercises.single().exercise.name)
        assertEquals(MuscleGroup.LATS, stored.exercises.single().exercise.primaryMuscle)
        assertEquals(listOf(MuscleGroup.ABS, MuscleGroup.BICEPS, MuscleGroup.FOREARMS), stored.exercises.single().exercise.secondaryMuscles.sortedBy { it.name })
    }

    @Test
    fun importedSessionsDoNotBlockUnrelatedTemplateDeletionAndDoBlockReferencedExercises() = runTest {
        seedCatalogAndWeights()
        val unused = (templates.save(TemplateDraft(name = "Unused")) as TemplateSaveResult.Created).id
        val plan = resolvedOneSet("Pullup")
        sessions.importCompletedWorkouts(plan) as WorkoutImportPersistenceResult.Imported
        assertTrue(templates.canDeletePermanently(unused))
        val pull = exercises.observeAll().first().single { it.name == "Pullup" }
        assertFalse(exercises.canDeletePermanently(pull.id))
    }

    @Test
    fun nullableTemplateInsertWorksAndInvalidTemplateIdStillFails() = runTest {
        seedCatalogAndWeights()
        val pull = exercises.observeAll().first().single { it.name == "Pullup" }
        val templateId = (templates.save(
            TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1,
                        exerciseId = pull.id,
                        sets = listOf(PlannedSetDraft(-1, "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
                    )
                )
            )
        ) as TemplateSaveResult.Created).id
        val started = sessions.start(templateId, "", sessions.proposeBodyWeight(), false)
        assertTrue(started is StartWorkoutResult.Started)
        val imported = sessions.importCompletedWorkouts(resolvedOneSet("Chinup"))
        assertTrue(imported is WorkoutImportPersistenceResult.Imported)
        val importedSession = sessions.getAggregate(
            (imported as WorkoutImportPersistenceResult.Imported).sessionIds.single()
        )!!
        assertNull(importedSession.session.templateId)
        var failed = false
        try {
            database.workoutSessionDao().insertSession(
                WorkoutSessionEntity(
                    templateId = 999_999L,
                    templateName = "Ghost",
                    status = SessionStatus.COMPLETED.name,
                    workoutDate = "2026-09-15",
                    startedAt = 1L,
                    finishedAt = 2L,
                    abandonedAt = null,
                    notes = null,
                    bodyWeightKg = null,
                    bodyWeightSource = BodyWeightSource.UNKNOWN.name,
                    bodyWeightSourceDate = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    activeLock = null
                )
            )
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    private fun repository(): WorkoutSessionRepository {
        return WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            weights,
            clock,
            FixedDateProvider(today)
        )
    }

    private suspend fun seedCatalogAndWeights() {
        saveBodyweight("Pullup", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.ABS))
        saveBodyweight("Chinup", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.ABS))
        saveExternal("Biceps curl", MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS), WeightInterpretation.PER_SIDE)
        saveExternal("Hammer curl", MuscleGroup.FOREARMS, listOf(MuscleGroup.BICEPS), WeightInterpretation.PER_SIDE)
        saveExternal("Wrist roll", MuscleGroup.FOREARMS, emptyList(), WeightInterpretation.TOTAL)
        saveBodyweight("Gyűrűn tolódzkodás", MovementPattern.VERTICAL_PUSH, MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS))
        saveBodyweight("Tolódzkodás", MovementPattern.VERTICAL_PUSH, MuscleGroup.TRICEPS, listOf(MuscleGroup.CHEST))
        saveBodyweight("Kézenállás kitolás", MovementPattern.VERTICAL_PUSH, MuscleGroup.FRONT_DELTOID, listOf(MuscleGroup.TRICEPS))
        saveBodyweight("Decline Pushup", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTOID, MuscleGroup.TRICEPS))
        exercises.save(
            ExerciseDraft(
                name = "Futás",
                category = ExerciseCategory.CARDIO,
                movementPattern = MovementPattern.CARDIO,
                measurementType = MeasurementType.DISTANCE_AND_DURATION,
                resistanceBasis = ResistanceBasis.NONE,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.QUADRICEPS,
                secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)
            )
        )
        listOf("2026-09-13", "2026-09-14", "2026-09-15").forEach { date ->
            weights.save(LocalDate.parse(date), 80.0)
        }
    }

    private suspend fun saveBodyweight(
        name: String,
        pattern: MovementPattern,
        primary: MuscleGroup,
        secondary: List<MuscleGroup>
    ) {
        exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = pattern,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = primary,
                secondaryMuscles = secondary
            )
        )
    }

    private suspend fun saveExternal(
        name: String,
        primary: MuscleGroup,
        secondary: List<MuscleGroup>,
        interpretation: WeightInterpretation
    ) {
        exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.ISOLATION,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = interpretation,
                primaryMuscle = primary,
                secondaryMuscles = secondary
            )
        )
    }

    private suspend fun resolvedFixture() = WorkoutImportResolver.resolve(
        parse(fixtureCsv()),
        catalog(),
        measurements = measurements()
    )

    private suspend fun resolvedOneSet(name: String, reps: String = "5") = WorkoutImportResolver.resolve(
        parse(oneSetCsv(name, reps)),
        catalog(),
        measurements = measurements()
    )

    private suspend fun catalog(): List<Exercise> = exercises.observeAll().first()

    private suspend fun measurements(): List<WeightMeasurement> = weights.observeAll().first()

    private fun parse(csv: String) = (WorkoutImportCsv.parse(csv, today) as WorkoutImportParseResult.Success).document

    private fun fixtureCsv(): String {
        return javaClass.getResource("/hu/laca/weighttracker/domain/workoutimport/history-v1-sample.csv")!!
            .readText(StandardCharsets.UTF_8)
    }

    private fun oneSetCsv(exerciseName: String, reps: String = "5"): String {
        return WorkoutImportCsv.HEADER + "\n" + listOf(
            "1", "w1", "Pull", "2026-09-15", "2026-09-15T12:00:00", "2026-09-15T13:00:00", "", "",
            "1", exerciseName, "1", "COMPLETED", reps, "", "", "", "BODYWEIGHT_ONLY", ""
        ).joinToString(",") + "\n"
    }
}
