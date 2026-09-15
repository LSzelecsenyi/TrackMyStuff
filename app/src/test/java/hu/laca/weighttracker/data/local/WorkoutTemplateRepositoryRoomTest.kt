package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDeleteResult
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutTemplateRepositoryRoomTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        exercises = ExerciseRepository(database.exerciseDao(), clock, database.workoutTemplateDao())
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duplicateNamesAreCaseInsensitiveIncludingArchived() = runTest {
        val pull = savePull()
        templates.save(draft("Push A", pull))
        assertEquals(TemplateSaveResult.DuplicateName, templates.save(draft("  push a ", pull)))
        val id = (templates.save(draft("Pull A", pull)) as TemplateSaveResult.Created).id
        assertTrue(templates.archive(id))
        assertEquals(TemplateSaveResult.DuplicateName, templates.save(draft("PULL A", pull)))
    }

    @Test
    fun saveIsTransactionalAndPreservesStableTemplateIdentity() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        val original = templates.getAggregate(created.id)!!
        val later = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)
        )
        val updated = later.save(
            TemplateDraft(
                id = created.id,
                name = "Push A",
                notes = "Másolat",
                createdAt = original.template.createdAt,
                exercises = original.exercises.map { item ->
                    TemplateExerciseDraft(
                        localId = item.relation.id,
                        exerciseId = item.exercise.id,
                        sets = item.sets.mapIndexed { index, set ->
                            PlannedSetDraft(
                                localId = set.id,
                                minRepsText = if (index == 3) "6" else "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        }
                    )
                }
            )
        )
        val stored = later.getAggregate(created.id)!!
        assertTrue(updated is TemplateSaveResult.Updated)
        assertEquals(original.template.id, stored.template.id)
        assertEquals(1_000L, stored.template.createdAt)
        assertEquals(5_000L, stored.template.updatedAt)
        assertEquals("6", stored.exercises.single().sets[3].minReps.toString())
        assertEquals(8, stored.exercises.single().sets[0].minReps)
        assertNotEquals(stored.template.createdAt, stored.template.updatedAt)
    }

    @Test
    fun exerciseAndSetOrderAreContiguousAfterReorderAndRemoval() = runTest {
        val first = savePull("Húzódzkodás")
        val second = saveDip()
        val created = templates.save(
            TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(-1, first, sets = fourSets()),
                    TemplateExerciseDraft(-2, second, sets = fourSets())
                )
            )
        ) as TemplateSaveResult.Created
        val loaded = templates.getAggregate(created.id)!!
        val reordered = TemplateDraft(
            id = created.id,
            name = "Push A",
            exercises = listOf(
                TemplateExerciseDraft(-10, second, sets = fourSets().dropLast(1)),
                TemplateExerciseDraft(-11, first, sets = fourSets())
            )
        )
        templates.save(reordered)
        val stored = templates.getAggregate(created.id)!!
        assertEquals(listOf(0, 1), stored.exercises.map { it.relation.position })
        assertEquals(second, stored.exercises[0].exercise.id)
        assertEquals(listOf(0, 1, 2), stored.exercises[0].sets.map { it.position })
        assertEquals(3, stored.exercises[0].sets.size)
    }

    @Test
    fun referencedExerciseCannotBeDeletedEvenIfTemplateIsArchived() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        assertFalse(exercises.canDeletePermanently(pull))
        assertEquals(ExerciseDeleteResult.BlockedByReferences, exercises.deletePermanently(pull))
        assertTrue(templates.archive(created.id))
        assertFalse(exercises.canDeletePermanently(pull))
        templates.deletePermanently(created.id)
        assertTrue(exercises.canDeletePermanently(pull))
        assertEquals(ExerciseDeleteResult.Deleted, exercises.deletePermanently(pull))
    }

    @Test
    fun deletingTemplateCascadesChildrenButNotCatalog() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        assertTrue(templates.canDeletePermanently(created.id))
        templates.deletePermanently(created.id)
        assertTrue(templates.observeActive().first().isEmpty())
        assertTrue(database.workoutTemplateDao().observeExercises().first().isEmpty())
        assertTrue(database.workoutTemplateDao().observeSets().first().isEmpty())
        assertEquals("Húzódzkodás", exercises.getById(pull)!!.name)
    }

    @Test
    fun archivedExerciseRemainsVisibleInExistingTemplate() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        assertTrue(exercises.archive(pull))
        val stored = templates.getAggregate(created.id)!!
        assertTrue(stored.exercises.single().exercise.archived)
        assertEquals("Húzódzkodás", stored.exercises.single().exercise.name)
    }

    @Test
    fun setTableHasNoPerformedWorkoutColumns() = runTest {
        val cursor = database.openHelper.readableDatabase.query("PRAGMA table_info(workout_template_sets)")
        val columns = mutableListOf<String>()
        while (cursor.moveToNext()) {
            columns += cursor.getString(1).lowercase()
        }
        cursor.close()
        assertFalse(columns.any { it.contains("rir") || it.contains("rpe") || it.contains("actual") || it.contains("completed") })
    }

    private suspend fun savePull(name: String = "Húzódzkodás"): Long {
        val result = exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS,
                secondaryMuscles = listOf(MuscleGroup.BICEPS)
            )
        ) as ExerciseSaveResult.Created
        return result.id
    }

    private suspend fun saveDip(): Long {
        val result = exercises.save(
            ExerciseDraft(
                name = "Tolódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.TRICEPS
            )
        ) as ExerciseSaveResult.Created
        return result.id
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

    private fun draft(name: String, exerciseId: Long): TemplateDraft {
        return TemplateDraft(
            name = name,
            exercises = listOf(TemplateExerciseDraft(-1, exerciseId, sets = fourSets()))
        )
    }
}
