package app.mymusclemap.ui.templates

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.domain.workout.toSetDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class TemplateEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun successfulAddEmitsStableLocalIdOnce() = runTest {
        val pull = savePull()
        val viewModel = editor()
        val catalog = catalogExercise(pull)
        viewModel.selectExercise(catalog)
        val state = awaitEditor {
            viewModel.uiState.first { it.scrollEvent != null && it.draft.exercises.isNotEmpty() }
        }
        val added = state.draft.exercises.single()
        assertEquals(added.localId, state.scrollEvent!!.exerciseLocalId)
        assertEquals(TemplateEditorPane.Form, state.pane)
        val generation = state.scrollEvent!!.generation
        viewModel.consumeScrollEvent()
        assertNull(viewModel.uiState.value.scrollEvent)
        viewModel.consumeScrollEvent()
        assertNull(viewModel.uiState.value.scrollEvent)
        assertEquals(generation, state.scrollEvent!!.generation)
    }

    @Test
    fun addingAnotherExerciseProducesANewTarget() = runTest {
        val pull = savePull()
        val dip = saveDip()
        val viewModel = editor()
        viewModel.selectExercise(catalogExercise(pull))
        val first = awaitEditor { viewModel.uiState.first { it.scrollEvent != null } }
        val firstId = first.scrollEvent!!.exerciseLocalId
        val firstGeneration = first.scrollEvent!!.generation
        viewModel.consumeScrollEvent()
        viewModel.selectExercise(catalogExercise(dip))
        val second = awaitEditor {
            viewModel.uiState.first { it.scrollEvent != null && it.draft.exercises.size == 2 }
        }
        assertNotEquals(firstId, second.scrollEvent!!.exerciseLocalId)
        assertNotEquals(firstGeneration, second.scrollEvent!!.generation)
        assertEquals(second.draft.exercises.last().localId, second.scrollEvent!!.exerciseLocalId)
    }

    @Test
    fun rejectedDuplicateDoesNotEmitAFalseTarget() = runTest {
        val pull = savePull()
        val viewModel = editor()
        val catalog = catalogExercise(pull)
        viewModel.selectExercise(catalog)
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor {
                viewModel.uiState.first { it.scrollEvent != null && it.draft.exercises.isNotEmpty() }
            }
        }
        viewModel.consumeScrollEvent()
        viewModel.selectExercise(catalog)
        val state = awaitEditor { viewModel.uiState.first { it.pendingDuplicate != null } }
        assertNull(state.scrollEvent)
        assertEquals(1, state.draft.exercises.size)
    }

    @Test
    fun unrelatedEditDoesNotReplayConsumedScroll() = runTest {
        val pull = savePull()
        val viewModel = editor()
        viewModel.selectExercise(catalogExercise(pull))
        awaitEditor { viewModel.uiState.first { it.scrollEvent != null } }
        viewModel.consumeScrollEvent()
        viewModel.onNameChange("Push B")
        assertNull(viewModel.uiState.value.scrollEvent)
    }

    @Test
    fun confirmedDuplicateEmitsTheNewLocalId() = runTest {
        val pull = savePull()
        val viewModel = editor()
        val catalog = catalogExercise(pull)
        viewModel.selectExercise(catalog)
        awaitEditor { viewModel.uiState.first { it.scrollEvent != null } }
        viewModel.consumeScrollEvent()
        viewModel.selectExercise(catalog)
        awaitEditor { viewModel.uiState.first { it.pendingDuplicate != null } }
        viewModel.confirmDuplicate()
        val state = awaitEditor {
            viewModel.uiState.first { it.draft.exercises.size == 2 && it.scrollEvent != null }
        }
        assertEquals(state.draft.exercises.last().localId, state.scrollEvent!!.exerciseLocalId)
        assertTrue(state.draft.exercises.map { it.localId }.distinct().size == 2)
    }

    @Test
    fun aRejectedStaleSaveShowsAnErrorAndLeavesThePlanUnchanged() = runTest {
        val pull = savePull()
        val created = templates.save(
            TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1,
                        exerciseId = pull,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -2,
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        )
                    )
                )
            )
        ) as TemplateSaveResult.Created
        val viewModel = editor(created.id)
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor {
                viewModel.uiState.first { !it.loading && it.draft.exercises.isNotEmpty() }
            }
        }
        val later = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            Clock.fixed(Instant.ofEpochMilli(9_000L), ZoneOffset.UTC),
            database.workoutSessionDao()
        )
        val current = templates.getAggregate(created.id)!!
        assertTrue(
            later.save(
                TemplateDraft(
                    id = created.id,
                    name = current.template.name,
                    createdAt = current.template.createdAt,
                    baselineUpdatedAt = current.template.updatedAt,
                    exercises = current.exercises.map { item ->
                        TemplateExerciseDraft(
                            localId = item.relation.id,
                            exerciseId = item.exercise.id,
                            sets = item.sets.map { set ->
                                set.toSetDraft(set.id).copy(minRepsText = "12")
                            }
                        )
                    }
                )
            ) is TemplateSaveResult.Updated
        )
        viewModel.onNameChange("Push B")
        viewModel.save()
        val rejected = withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor { viewModel.uiState.first { it.stalePlan } }
        }
        assertFalse(rejected.finished)
        assertFalse(rejected.saved)
        val stored = templates.getAggregate(created.id)!!
        assertEquals("Push A", stored.template.name)
        assertEquals(12, stored.exercises.single().sets.single().minReps)
        viewModel.reload()
        val reloaded = withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor {
                viewModel.uiState.first { !it.loading && !it.stalePlan && it.draft.name == "Push A" }
            }
        }
        assertEquals(stored.exercises.single().relation.id, reloaded.draft.exercises.single().localId)
    }

    @Test
    fun savedTimedModeReopensAsTimedAndSwitchingKeepsDuration() = runTest {
        val pull = savePull()
        val created = templates.save(
            TemplateDraft(
                name = "Plank",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = pull,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -2L,
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            ),
                            PlannedSetDraft(
                                localId = -3L,
                                minRepsText = "10",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        )
                    )
                )
            )
        ) as TemplateSaveResult.Created
        val viewModel = editor(created.id)
        val loaded = withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor {
                viewModel.uiState.first {
                    !it.loading && it.draft.exercises.isNotEmpty() && it.catalog.containsKey(pull)
                }
            }
        }
        val exercise = loaded.draft.exercises.single()
        assertTrue(exercise.sets.none { it.timedWork })
        viewModel.onTimedMode(exercise.localId, true)
        val timed = viewModel.uiState.value.draft.exercises.single()
        assertTrue(timed.sets.all { it.timedWork })
        val first = timed.sets[0]
        val second = timed.sets[1]
        viewModel.onMinutes(exercise.localId, first.localId, "1")
        viewModel.onSeconds(exercise.localId, first.localId, "0")
        viewModel.onMinutes(exercise.localId, second.localId, "0")
        viewModel.onSeconds(exercise.localId, second.localId, "45")
        viewModel.onSetRest(exercise.localId, first.localId, 20)
        viewModel.onExerciseRest(exercise.localId, 45)
        viewModel.save()
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor { viewModel.uiState.first { it.saved } }
        }
        val reopened = editor(created.id)
        val again = withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor {
                reopened.uiState.first { !it.loading && it.draft.exercises.single().sets.all { set -> set.timedWork } }
            }
        }
        val restored = again.draft.exercises.single()
        assertEquals(45, restored.restSeconds)
        assertEquals("1", restored.sets[0].minutesText)
        assertEquals("0", restored.sets[0].secondsText)
        assertEquals(20, restored.sets[0].restSeconds)
        assertEquals("0", restored.sets[1].minutesText)
        assertEquals("45", restored.sets[1].secondsText)
        assertTrue(restored.sets.all { it.timedWork })
        reopened.onTimedMode(restored.localId, false)
        val asReps = reopened.uiState.value.draft.exercises.single()
        assertTrue(asReps.sets.none { it.timedWork })
        assertEquals("8", asReps.sets[0].minRepsText)
        reopened.onTimedMode(restored.localId, true)
        val back = reopened.uiState.value.draft.exercises.single()
        assertTrue(back.sets.all { it.timedWork })
        assertEquals("1", back.sets[0].minutesText)
        assertEquals("45", back.sets[1].secondsText)
        reopened.save()
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitEditor { reopened.uiState.first { it.saved } }
        }
        val stored = templates.getAggregate(created.id)!!
        assertTrue(stored.exercises.single().sets.all { it.timedWork })
        assertEquals(60, stored.exercises.single().sets[0].durationSeconds)
        assertEquals(45, stored.exercises.single().sets[1].durationSeconds)
        assertEquals(20, stored.exercises.single().sets[0].restSeconds)
        assertEquals(45, stored.exercises.single().relation.restSeconds)
    }

    private suspend fun <T> awaitEditor(block: suspend () -> T): T {
        return withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { block() }
        }
    }

    private fun editor(templateId: Long? = null): TemplateEditorViewModel {
        val handle = if (templateId == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(mapOf(TemplateEditorViewModel.TEMPLATE_ID_KEY to templateId))
        }
        return TemplateEditorViewModel(handle, templates, exercises)
    }

    private suspend fun catalogExercise(id: Long): Exercise {
        return exercises.getById(id)!!
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
}
