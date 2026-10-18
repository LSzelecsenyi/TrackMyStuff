package app.mymusclemap.ui.templates

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.EffectiveEntitlement
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FeatureAccessPolicy
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.entitlement.PolicyBackedEntitlements
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun freePreviewDoesNotConvertRepsOrAcceptTimedEdits() = runTest {
        val pull = savePull()
        val created = saveRepsPlan(pull, "Free plan")
        val viewModel = editor(created, SelectiveFeatureEntitlements(emptySet()))
        val loaded = awaitLoaded(viewModel, pull)
        val exercise = loaded.draft.exercises.single()
        val set = exercise.sets.single()
        assertFalse(loaded.timersEnabled)
        assertTrue(loaded.timedTabPreview.isEmpty())
        assertNull(loaded.lockedFeature)
        viewModel.onTimedMode(exercise.localId, true)
        val preview = viewModel.uiState.value
        val previewSet = preview.draft.exercises.single().sets.single()
        assertEquals(true, preview.timedTabPreview[exercise.localId])
        assertFalse(previewSet.timedWork)
        assertEquals("8", previewSet.minRepsText)
        assertEquals("0", previewSet.minutesText)
        assertEquals("0", previewSet.secondsText)
        assertNull(preview.lockedFeature)
        viewModel.onMinutes(exercise.localId, set.localId, "2")
        viewModel.onSeconds(exercise.localId, set.localId, "15")
        viewModel.onWorkDuration(exercise.localId, set.localId, 45)
        val rejected = viewModel.uiState.value.draft.exercises.single().sets.single()
        assertEquals("0", rejected.minutesText)
        assertEquals("0", rejected.secondsText)
        assertFalse(rejected.timedWork)
        viewModel.onTimedMode(exercise.localId, false)
        val reps = viewModel.uiState.value
        assertEquals(false, reps.timedTabPreview[exercise.localId])
        assertEquals("8", reps.draft.exercises.single().sets.single().minRepsText)
        assertFalse(reps.draft.exercises.single().sets.single().timedWork)
        viewModel.onTimedMode(exercise.localId, true)
        viewModel.save()
        awaitEditor { viewModel.uiState.first { it.saved } }
        val stored = templates.getAggregate(created)!!
        assertFalse(stored.exercises.single().sets.single().timedWork)
        assertEquals(8, stored.exercises.single().sets.single().minReps)
        assertNull(stored.exercises.single().sets.single().durationSeconds)
    }

    @Test
    fun existingTimedSetStaysIntactAndRejectsFreeEdits() = runTest {
        val pull = savePull()
        val created = saveTimedPlan(pull, minutes = "1", seconds = "15")
        val viewModel = editor(created, SelectiveFeatureEntitlements(emptySet()))
        val loaded = awaitLoaded(viewModel, pull)
        val exercise = loaded.draft.exercises.single()
        val set = exercise.sets.single()
        assertTrue(set.timedWork)
        assertEquals("1", set.minutesText)
        assertEquals("15", set.secondsText)
        assertFalse(loaded.timersEnabled)
        viewModel.onMinutes(exercise.localId, set.localId, "9")
        viewModel.onWorkDuration(exercise.localId, set.localId, 45)
        viewModel.onTimedMode(exercise.localId, false)
        viewModel.addSet(exercise.localId)
        val after = viewModel.uiState.value.draft.exercises.single()
        assertEquals(1, after.sets.size)
        assertTrue(after.sets.single().timedWork)
        assertEquals("1", after.sets.single().minutesText)
        assertEquals("15", after.sets.single().secondsText)
        assertEquals(false, viewModel.uiState.value.timedTabPreview[exercise.localId])
        viewModel.save()
        awaitEditor { viewModel.uiState.first { it.saved } }
        val stored = templates.getAggregate(created)!!.exercises.single().sets.single()
        assertTrue(stored.timedWork)
        assertEquals(75, stored.durationSeconds)
    }

    @Test
    fun regainingProTurnsTheOpenPreviewIntoAnEditableTimedSet() = runTest {
        val pull = savePull()
        val created = saveRepsPlan(pull, "Return")
        val access = FlipEntitlements(pro = false)
        val viewModel = editor(created, access)
        val loaded = awaitLoaded(viewModel, pull)
        val exerciseId = loaded.draft.exercises.single().localId
        val setId = loaded.draft.exercises.single().sets.single().localId
        viewModel.onTimedMode(exerciseId, true)
        assertFalse(viewModel.uiState.value.draft.exercises.single().sets.single().timedWork)
        access.grantPro()
        val enabled = awaitEditor {
            viewModel.uiState.first {
                it.timersEnabled && it.draft.exercises.single().sets.single().timedWork
            }
        }
        assertTrue(enabled.timedTabPreview.isEmpty())
        assertEquals("0", enabled.draft.exercises.single().sets.single().minutesText)
        assertEquals("30", enabled.draft.exercises.single().sets.single().secondsText)
        assertEquals("8", enabled.draft.exercises.single().sets.single().minRepsText)
        viewModel.onSeconds(exerciseId, setId, "40")
        val edited = viewModel.uiState.value.draft.exercises.single().sets.single()
        assertEquals("40", edited.secondsText)
        assertTrue(edited.timedWork)
    }

    @Test
    fun founderAndTimerGrantsCanEditTimedSetsAndRecognitionCannot() = runTest {
        val pull = savePull()
        val created = saveRepsPlan(pull, "Grants")
        val founder = editor(
            created,
            PolicyBackedEntitlements(
                FeatureAccessPolicy(
                    EffectiveEntitlement(
                        tier = EntitlementTier.Pro,
                        subscriptionValid = false,
                        founderLifetime = true,
                        temporaryTesterPro = false
                    )
                )
            )
        )
        val founderLoaded = awaitLoaded(founder, pull)
        founder.onTimedMode(founderLoaded.draft.exercises.single().localId, true)
        assertTrue(founder.uiState.value.timersEnabled)
        assertTrue(founder.uiState.value.draft.exercises.single().sets.single().timedWork)
        assertEquals("30", founder.uiState.value.draft.exercises.single().sets.single().secondsText)

        val recognition = editor(
            created,
            PolicyBackedEntitlements(
                FeatureAccessPolicy(
                    EffectiveEntitlement(
                        tier = EntitlementTier.Free,
                        subscriptionValid = false,
                        founderLifetime = false,
                        temporaryTesterPro = false,
                        founderRecognized = true
                    )
                )
            )
        )
        val recognized = awaitLoaded(recognition, pull)
        recognition.onTimedMode(recognized.draft.exercises.single().localId, true)
        assertFalse(recognition.uiState.value.timersEnabled)
        assertFalse(recognition.uiState.value.draft.exercises.single().sets.single().timedWork)
        assertEquals(true, recognition.uiState.value.timedTabPreview[recognized.draft.exercises.single().localId])

        val granted = editor(created, SelectiveFeatureEntitlements(setOf(AppFeature.WorkoutTimers)))
        val grantLoaded = awaitLoaded(granted, pull)
        granted.onTimedMode(grantLoaded.draft.exercises.single().localId, true)
        assertTrue(granted.uiState.value.timersEnabled)
        assertTrue(granted.uiState.value.draft.exercises.single().sets.single().timedWork)
    }

    private suspend fun awaitLoaded(
        viewModel: TemplateEditorViewModel,
        exerciseId: Long
    ): TemplateEditorUiState {
        return awaitEditor {
            viewModel.uiState.first { !it.loading && it.draft.exercises.isNotEmpty() && it.catalog.containsKey(exerciseId) }
        }
    }

    private suspend fun saveRepsPlan(exerciseId: Long, name: String): Long {
        val created = templates.save(
            TemplateDraft(
                name = name,
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = exerciseId,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -2L,
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        )
                    )
                )
            )
        ) as TemplateSaveResult.Created
        return created.id
    }

    private suspend fun saveTimedPlan(exerciseId: Long, minutes: String, seconds: String): Long {
        val created = templates.save(
            TemplateDraft(
                name = "Timed plan",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = exerciseId,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -2L,
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                                minutesText = minutes,
                                secondsText = seconds,
                                timedWork = true
                            )
                        )
                    )
                )
            )
        ) as TemplateSaveResult.Created
        return created.id
    }

    private suspend fun <T> awaitEditor(block: suspend () -> T): T {
        return withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { block() }
        }
    }

    private fun editor(
        templateId: Long? = null,
        entitlements: FeatureEntitlements = OpenFeatureEntitlements
    ): TemplateEditorViewModel {
        val handle = if (templateId == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(mapOf(TemplateEditorViewModel.TEMPLATE_ID_KEY to templateId))
        }
        return TemplateEditorViewModel(handle, templates, exercises, entitlements)
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

private class FlipEntitlements(pro: Boolean) : FeatureEntitlements {
    private val timers = MutableStateFlow(pro)
    private val revision = MutableStateFlow(0)

    override fun hasAccess(feature: AppFeature): Boolean {
        return feature != AppFeature.WorkoutTimers || timers.value
    }

    override fun changes(): Flow<Int> = revision

    fun grantPro() {
        timers.value = true
        revision.value += 1
    }
}
