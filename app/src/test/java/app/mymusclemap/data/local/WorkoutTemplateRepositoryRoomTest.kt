package app.mymusclemap.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDeleteResult
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.domain.workout.toSetDraft
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
        assertEquals(original.exercises.single().relation.id, stored.exercises.single().relation.id)
        assertEquals(original.exercises.single().sets.map { it.id }, stored.exercises.single().sets.map { it.id })
        assertEquals(original.exercises.single().relation.createdAt, stored.exercises.single().relation.createdAt)
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
    fun savingAnUnchangedPlanPreservesEveryExerciseAndSetId() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        val before = templates.getAggregate(created.id)!!
        val saved = templates.save(reopen(created.id))
        val after = templates.getAggregate(created.id)!!
        assertTrue(saved is TemplateSaveResult.Updated)
        assertEquals(before.exercises.map { it.relation.id }, after.exercises.map { it.relation.id })
        assertEquals(before.exercises.single().sets.map { it.id }, after.exercises.single().sets.map { it.id })
        assertEquals(before.exercises.single().sets.map { it.minReps }, after.exercises.single().sets.map { it.minReps })
    }

    @Test
    fun editingNameRepsAndWeightPreservesRetainedIds() = runTest {
        val curl = saveCurl()
        val created = templates.save(
            TemplateDraft(
                name = "Arms",
                exercises = listOf(
                    TemplateExerciseDraft(-1, curl, sets = listOf(weightedSet(-2, 8, "15"), weightedSet(-3, 8, "15")))
                )
            )
        ) as TemplateSaveResult.Created
        val before = templates.getAggregate(created.id)!!
        val exercise = reopen(created.id).exercises.single()
        val sets = exercise.sets.toMutableList()
        sets[0] = sets[0].copy(minRepsText = "10")
        sets[1] = sets[1].copy(weightText = "17.5")
        val saved = templates.save(
            reopen(created.id).copy(
                name = "Arms B",
                exercises = listOf(exercise.copy(sets = sets))
            )
        )
        val after = templates.getAggregate(created.id)!!
        assertTrue(saved is TemplateSaveResult.Updated)
        assertEquals("Arms B", after.template.name)
        assertEquals(before.exercises.single().relation.id, after.exercises.single().relation.id)
        assertEquals(before.exercises.single().sets.map { it.id }, after.exercises.single().sets.map { it.id })
        assertEquals(10, after.exercises.single().sets[0].minReps)
        assertEquals(17.5, after.exercises.single().sets[1].weightKg!!, 0.0)
        val reopened = templates.getAggregate(created.id)!!
        assertEquals(after.exercises.single().sets.map { it.id to it.minReps }, reopened.exercises.single().sets.map { it.id to it.minReps })
    }

    @Test
    fun reorderAddAndRemovePreserveOnlyRetainedIds() = runTest {
        val pull = savePull()
        val dip = saveDip()
        val curl = saveCurl()
        val created = templates.save(
            TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(-1, pull, sets = fourSets()),
                    TemplateExerciseDraft(-2, dip, sets = fourSets())
                )
            )
        ) as TemplateSaveResult.Created
        val before = templates.getAggregate(created.id)!!
        val pullItem = before.exercises[0]
        val dipItem = before.exercises[1]
        val removedSetId = dipItem.sets.last().id
        val loaded = reopen(created.id)
        val pullDraft = loaded.exercises[0]
        val dipDraft = loaded.exercises[1]
        val reorderedSets = listOf(pullDraft.sets[2], pullDraft.sets[0], pullDraft.sets[1], pullDraft.sets[3])
        val saved = templates.save(
            loaded.copy(
                name = "Push B",
                exercises = listOf(
                    dipDraft.copy(sets = dipDraft.sets.dropLast(1)),
                    pullDraft.copy(
                        sets = reorderedSets + PlannedSetDraft(
                            localId = -50,
                            minRepsText = "5",
                            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                        )
                    ),
                    TemplateExerciseDraft(
                        localId = -60,
                        exerciseId = curl,
                        sets = listOf(weightedSet(-61, 12, "10"))
                    ),
                    TemplateExerciseDraft(
                        localId = -70,
                        exerciseId = pull,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -71,
                                minRepsText = "6",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        )
                    )
                )
            )
        )
        val after = templates.getAggregate(created.id)!!
        assertTrue(saved is TemplateSaveResult.Updated)
        assertEquals(dipItem.relation.id, after.exercises[0].relation.id)
        assertEquals(pullItem.relation.id, after.exercises[1].relation.id)
        assertEquals(listOf(0, 1, 2), after.exercises[0].sets.map { it.position })
        assertEquals(dipItem.sets.dropLast(1).map { it.id }, after.exercises[0].sets.map { it.id })
        assertEquals(
            listOf(pullItem.sets[2].id, pullItem.sets[0].id, pullItem.sets[1].id, pullItem.sets[3].id),
            after.exercises[1].sets.take(4).map { it.id }
        )
        assertNotEquals(removedSetId, after.exercises[1].sets.last().id)
        assertFalse(setExists(removedSetId))
        assertEquals(2, after.exercises.size - before.exercises.size)
        assertNotEquals(pullItem.relation.id, after.exercises[3].relation.id)
        assertEquals(pull, after.exercises[1].exercise.id)
        assertEquals(pull, after.exercises[3].exercise.id)
        assertEquals(5, after.exercises[1].sets.last().minReps)
    }

    @Test
    fun duplicatingAPlanCreatesIndependentChildIds() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        val loaded = reopen(created.id)
        val duplicate = templates.save(
            loaded.copy(id = null, name = "Push copy", baselineUpdatedAt = null)
        ) as TemplateSaveResult.Created
        val original = templates.getAggregate(created.id)!!
        val copied = templates.getAggregate(duplicate.id)!!
        assertEquals(loaded.exercises.map { it.localId }, original.exercises.map { it.relation.id })
        assertNotEquals(original.template.id, copied.template.id)
        assertNotEquals(original.exercises.map { it.relation.id }, copied.exercises.map { it.relation.id })
        assertNotEquals(original.exercises.single().sets.map { it.id }, copied.exercises.single().sets.map { it.id })
    }

    @Test
    fun aFailedSaveLeavesTheOriginalHierarchyUnchanged() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        val before = templates.getAggregate(created.id)!!
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER abort_plan_set_update
            BEFORE UPDATE OF minReps ON workout_template_sets
            BEGIN
                SELECT RAISE(ABORT, 'fail');
            END
            """.trimIndent()
        )
        val exercise = reopen(created.id).exercises.single()
        val sets = exercise.sets.toMutableList()
        sets[0] = sets[0].copy(minRepsText = "3")
        val result = runCatching {
            templates.save(
                reopen(created.id).copy(exercises = listOf(exercise.copy(sets = sets)))
            )
        }
        val after = templates.getAggregate(created.id)!!
        if (result.isSuccess) {
            assertFalse(result.getOrThrow() is TemplateSaveResult.Updated)
        }
        assertEquals(before.template.updatedAt, after.template.updatedAt)
        assertEquals(before.exercises.map { it.relation.id }, after.exercises.map { it.relation.id })
        assertEquals(before.exercises.single().sets.map { it.id to it.minReps }, after.exercises.single().sets.map { it.id to it.minReps })
    }

    @Test
    fun staleEditorSaveDoesNotOverwriteANewerPlan() = runTest {
        val pull = savePull()
        val created = templates.save(draft("Push A", pull)) as TemplateSaveResult.Created
        val loaded = reopen(created.id)
        val later = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            Clock.fixed(Instant.ofEpochMilli(9_000L), ZoneOffset.UTC)
        )
        val newerExercise = reopen(created.id).exercises.single()
        val newerSets = newerExercise.sets.toMutableList()
        newerSets[0] = newerSets[0].copy(minRepsText = "12")
        assertTrue(
            later.save(
                reopen(created.id).copy(
                    exercises = listOf(newerExercise.copy(sets = newerSets))
                )
            ) is TemplateSaveResult.Updated
        )
        val stale = templates.save(loaded.copy(name = "Push B"))
        val stored = templates.getAggregate(created.id)!!
        assertEquals(TemplateSaveResult.Stale, stale)
        assertEquals("Push A", stored.template.name)
        assertEquals(12, stored.exercises.single().sets[0].minReps)
        assertEquals(loaded.exercises.single().localId, stored.exercises.single().relation.id)
        assertEquals(loaded.exercises.single().sets.map { it.localId }, stored.exercises.single().sets.map { it.id })
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

    private suspend fun saveCurl(): Long {
        val result = exercises.save(
            ExerciseDraft(
                name = "Bicepsz",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.ISOLATION,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.BICEPS
            )
        ) as ExerciseSaveResult.Created
        return result.id
    }

    private fun weightedSet(localId: Long, reps: Int, weight: String): PlannedSetDraft {
        return PlannedSetDraft(
            localId = localId,
            minRepsText = reps.toString(),
            loadKind = PlannedLoadKind.ADDED_WEIGHT,
            weightText = weight
        )
    }

    private suspend fun reopen(id: Long): TemplateDraft {
        val aggregate = templates.getAggregate(id)!!
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

    private fun setExists(id: Long): Boolean {
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT id FROM workout_template_sets WHERE id = $id"
        )
        val found = cursor.moveToFirst()
        cursor.close()
        return found
    }

    private fun draft(name: String, exerciseId: Long): TemplateDraft {
        return TemplateDraft(
            name = name,
            exercises = listOf(TemplateExerciseDraft(-1, exerciseId, sets = fourSets()))
        )
    }
}
