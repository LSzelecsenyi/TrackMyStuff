package app.mymusclemap.ui.workout

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.ConfinedMainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.workout.RestClockSample
import app.mymusclemap.domain.workout.WorkPhase
import app.mymusclemap.domain.workout.RestPhase
import app.mymusclemap.domain.workout.RestProgressionMode
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.ExerciseHistorySelection
import app.mymusclemap.domain.workout.InWorkoutExerciseHistory
import app.mymusclemap.domain.workout.NotificationSetCompletion
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.domain.workout.WorkoutFocusTarget
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ActiveWorkoutViewModelTest {
    @get:Rule
    val mainDispatcherRule = ConfinedMainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val viewModelStore = ViewModelStore()
    private var viewModelKey = 0
    private val today = LocalDate.parse("2026-09-16")
    private val restElapsed = java.util.concurrent.atomic.AtomicLong(60_000L)
    private val restWall = java.util.concurrent.atomic.AtomicLong(1_700_000_000_000L)

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
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(today)
        )
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        runBlocking {
            withContext(Dispatchers.Default) {
                withContext(Dispatchers.Main) { }
            }
        }
        database.close()
    }

    private var sessionId = 0L

    @Test
    fun keszPersistsEditedValuesAndCompletesWithoutSecondSave() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val exerciseId = viewModel.loaded().aggregate!!.exercises[0].exercise.id
        val remainingId = viewModel.loaded().aggregate!!.exercises[0].sets[1].id
        viewModel.onReps(first.id, "7")
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                state.focusEvent != null && first.id !in state.completingSetIds
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        val completed = stored.exercises[0].sets[0]
        assertEquals(7, completed.actualReps)
        assertEquals(SessionSetStatus.COMPLETED, completed.status)
        assertEquals(SessionSetStatus.PENDING, stored.exercises[0].sets[1].status)
        val focus = viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set
        assertEquals(remainingId, focus.setId)
        assertEquals(exerciseId, focus.exerciseId)
        assertEquals(exerciseId, viewModel.uiState.value.currentExerciseId)
        assertEquals(remainingId, viewModel.uiState.value.currentSetId)
        assertEquals(remainingId, viewModel.uiState.value.focusedSetId)
        assertTrue(exerciseId in viewModel.uiState.value.expandedExerciseIds)
        assertTrue(viewModel.uiState.value.dirtySetIds.isEmpty())
    }

    @Test
    fun plusIncreasesRepsByOneInDraft() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.stepReps(first.id, 1)
        val state = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "9" }
        }
        assertEquals("9", state.drafts[first.id]!!.repsText)
    }

    @Test
    fun minusDecreasesRepsByOneInDraft() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.stepReps(first.id, -1)
        val state = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "7" }
        }
        assertEquals("7", state.drafts[first.id]!!.repsText)
    }

    @Test
    fun minusAtMinimumKeepsValidCompletedReps() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "1")
        awaitReal { viewModel.uiState.first { it.drafts[first.id]?.repsText == "1" } }
        viewModel.stepReps(first.id, -1)
        val minState = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "1" }
        }
        assertEquals("1", minState.drafts[first.id]!!.repsText)
        viewModel.onReps(first.id, "")
        awaitReal { viewModel.uiState.first { it.drafts[first.id]?.repsText == "" } }
        viewModel.stepReps(first.id, -1)
        val emptyState = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "" }
        }
        assertEquals("", emptyState.drafts[first.id]!!.repsText)
    }

    @Test
    fun plusOnEmptyRepsUsesMinimumValidValue() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "")
        awaitReal { viewModel.uiState.first { it.drafts[first.id]?.repsText == "" } }
        viewModel.stepReps(first.id, 1)
        val state = awaitReal {
            viewModel.uiState.first { it.drafts[first.id]?.repsText == "1" }
        }
        assertEquals("1", state.drafts[first.id]!!.repsText)
    }

    @Test
    fun steppedRepsPersistThroughExistingCompleteFlow() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val next = viewModel.loaded().aggregate!!.exercises[0].sets[1]
        viewModel.stepReps(first.id, 1)
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.currentSetId == next.id
            }
        }
        val stored = sessions.getAggregate(sessionId)!!.exercises[0].sets[0]
        assertEquals(SessionSetStatus.COMPLETED, stored.status)
        assertEquals(9, stored.actualReps)
    }

    @Test
    fun completeSetSavesCompletedWithoutImeAction() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val next = viewModel.loaded().aggregate!!.exercises[0].sets[1]
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.currentSetId == next.id
            }
        }
        val stored = sessions.getAggregate(sessionId)!!.exercises[0].sets[0]
        assertEquals(SessionSetStatus.COMPLETED, stored.status)
        assertEquals(8, stored.actualReps)
        assertEquals(next.id, viewModel.uiState.value.currentSetId)
        assertEquals(next.id, (viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set).setId)
    }

    @Test
    fun skipDoesNotRequireImeAndAdvancesWithoutFinishingWorkout() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val next = viewModel.loaded().aggregate!!.exercises[0].sets[1]
        viewModel.skipSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.currentSetId == next.id
            }
        }
        assertEquals(SessionSetStatus.SKIPPED, sessions.getAggregate(sessionId)!!.exercises[0].sets[0].status)
        assertEquals(next.id, viewModel.uiState.value.currentSetId)
        assertEquals(false, viewModel.uiState.value.finished)
    }

    @Test
    fun invalidValuesDoNotComplete() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "0")
        viewModel.completeSet(first.id)
        val state = awaitReal {
            viewModel.uiState.first { first.id !in it.completingSetIds && it.setErrors[first.id].orEmpty().isNotEmpty() }
        }
        assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(sessionId)!!.exercises[0].sets[0].status)
        assertNull(state.focusEvent)
        assertEquals(first.id, state.currentSetId)
        assertNull(state.focusedSetId)
    }

    @Test
    fun doubleTapDoesNotDuplicateCompletion() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.onReps(first.id, "6")
        viewModel.completeSet(first.id)
        viewModel.completeSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                first.id !in state.completingSetIds &&
                    state.aggregate?.exercises?.get(0)?.sets?.get(0)?.status == SessionSetStatus.COMPLETED
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        assertEquals(1, stored.exercises[0].sets.count { it.status == SessionSetStatus.COMPLETED })
        assertEquals(4, stored.exercises.flatMap { it.sets }.size)
        assertEquals(6, stored.exercises[0].sets[0].actualReps)
        assertTrue(viewModel.uiState.value.completingSetIds.isEmpty())
    }

    @Test
    fun lastSetOfExerciseMovesHighlightToNextPendingExercise() = runOnMain {
        val viewModel = startTwoExercises()
        val loaded = viewModel.loaded().aggregate!!
        val firstExerciseId = loaded.exercises[0].exercise.id
        loaded.exercises[0].sets.forEach { set ->
            viewModel.onReps(set.id, "8")
            viewModel.completeSet(set.id)
            awaitReal {
                viewModel.uiState.first { state ->
                    set.id !in state.completingSetIds &&
                        state.aggregate?.exercises?.get(0)?.sets?.any {
                            it.id == set.id && it.status == SessionSetStatus.COMPLETED
                        } == true
                }
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        val second = stored.exercises[1]
        assertTrue(stored.exercises[0].sets.all { it.status == SessionSetStatus.COMPLETED })
        val focus = viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set
        assertEquals(second.sets.first().id, focus.setId)
        assertEquals(second.exercise.id, focus.exerciseId)
        assertEquals(second.exercise.id, viewModel.uiState.value.currentExerciseId)
        assertTrue(second.exercise.id in viewModel.uiState.value.expandedExerciseIds)
        assertEquals(second.sets.first().id, viewModel.uiState.value.focusedSetId)
        assertEquals(second.sets.first().id, viewModel.uiState.value.currentSetId)
        assertTrue(viewModel.uiState.value.currentExerciseId != firstExerciseId)
    }

    @Test
    fun skippedExerciseIsBypassed() = runOnMain {
        val viewModel = startTwoExercises()
        val loaded = viewModel.loaded().aggregate!!
        loaded.exercises[1].sets.forEach { set ->
            viewModel.skipSet(set.id)
            awaitReal {
                viewModel.uiState.first { state ->
                    state.aggregate?.exercises?.get(1)?.sets?.any {
                        it.id == set.id && it.status == SessionSetStatus.SKIPPED
                    } == true
                }
            }
        }
        loaded.exercises[0].sets.forEach { set ->
            viewModel.completeSet(set.id)
            awaitReal {
                viewModel.uiState.first { state ->
                    set.id !in state.completingSetIds &&
                        state.aggregate?.exercises?.get(0)?.sets?.any {
                            it.id == set.id && it.status == SessionSetStatus.COMPLETED
                        } == true
                }
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        assertTrue(stored.exercises[1].sets.all { it.status == SessionSetStatus.SKIPPED })
        assertTrue(stored.exercises[0].sets.all { it.status == SessionSetStatus.COMPLETED })
        assertEquals(WorkoutFocusTarget.Finish, viewModel.uiState.value.focusEvent!!.target)
        assertNull(viewModel.uiState.value.currentExerciseId)
        assertNull(viewModel.uiState.value.currentSetId)
    }

    @Test
    fun finalPendingSetFocusesFinishAndClearsHighlight() = runOnMain {
        val viewModel = startSingleSet()
        val set = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.completeSet(set.id)
        awaitReal {
            viewModel.uiState.first { state ->
                state.focusEvent?.target == WorkoutFocusTarget.Finish &&
                    state.currentExerciseId == null &&
                    state.aggregate?.exercises?.get(0)?.sets?.single()?.status == SessionSetStatus.COMPLETED
            }
        }
        assertEquals(SessionSetStatus.COMPLETED, sessions.getAggregate(sessionId)!!.exercises[0].sets.single().status)
        assertEquals(WorkoutFocusTarget.Finish, viewModel.uiState.value.focusEvent!!.target)
        assertNull(viewModel.uiState.value.currentExerciseId)
        assertNull(viewModel.uiState.value.currentSetId)
        assertNull(viewModel.uiState.value.focusedSetId)
        assertEquals(
            app.mymusclemap.domain.workout.SessionStatus.IN_PROGRESS,
            sessions.getAggregate(sessionId)!!.session.status
        )
        assertEquals(false, viewModel.uiState.value.finished)
    }

    @Test
    fun skipAdvancesToNextPendingSetInSameExercise() = runOnMain {
        val viewModel = startTwoExercises()
        val loaded = viewModel.loaded().aggregate!!
        val first = loaded.exercises[0].sets[0]
        val next = loaded.exercises[0].sets[1]
        val exerciseId = loaded.exercises[0].exercise.id
        viewModel.skipSet(first.id)
        awaitReal {
            viewModel.uiState.first { state ->
                state.focusEvent != null && first.id !in state.completingSetIds
            }
        }
        val stored = sessions.getAggregate(sessionId)!!
        assertEquals(SessionSetStatus.SKIPPED, stored.exercises[0].sets[0].status)
        assertEquals(SessionSetStatus.PENDING, stored.exercises[0].sets[1].status)
        val focus = viewModel.uiState.value.focusEvent!!.target as WorkoutFocusTarget.Set
        assertEquals(next.id, focus.setId)
        assertEquals(exerciseId, focus.exerciseId)
        assertEquals(exerciseId, viewModel.uiState.value.currentExerciseId)
        assertTrue(exerciseId in viewModel.uiState.value.expandedExerciseIds)
        assertEquals(next.id, viewModel.uiState.value.focusedSetId)
        assertEquals(next.id, viewModel.uiState.value.currentSetId)
    }

    @Test
    fun repositoryErrorKeepsCurrentSetActive() = runOnMain {
        val viewModel = startSingleSet()
        val set = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.confirmFinish(true)
        awaitReal { viewModel.uiState.first { it.finished } }
        viewModel.completeSet(set.id)
        val state = awaitReal {
            viewModel.uiState.first { snapshot ->
                set.id !in snapshot.completingSetIds &&
                    snapshot.message == ActiveWorkoutMessage.SaveFailed
            }
        }
        assertEquals(ActiveWorkoutMessage.SaveFailed, state.message)
        assertNull(state.focusEvent)
        assertEquals(SessionSetStatus.SKIPPED, sessions.getAggregate(sessionId)!!.exercises[0].sets.single().status)
    }

    @Test
    fun successfulFinishExposesCompletionSummaryOnce() = runOnMain {
        val viewModel = startSingleSet()
        val set = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.onReps(set.id, "8")
        viewModel.completeSet(set.id)
        awaitReal {
            viewModel.uiState.first { set.id !in it.completingSetIds && it.progress.pending == 0 }
        }
        viewModel.confirmFinish(false)
        viewModel.confirmFinish(false)
        val state = awaitReal {
            viewModel.uiState.first { it.finished && it.completionSummary != null }
        }
        viewModel.confirmFinish(false)
        val summary = state.completionSummary!!
        assertEquals(1, summary.exerciseCount)
        assertEquals(1, summary.completedSetCount)
        assertEquals(
            sessions.getAggregate(sessionId)!!.session.let { ElapsedTime.forSession(it) },
            summary.durationMillis
        )
        assertEquals(summary, viewModel.uiState.value.completionSummary)
        assertEquals(SessionStatus.COMPLETED, sessions.getAggregate(sessionId)!!.session.status)
    }

    @Test
    fun failedFinishDoesNotExposeCompletionSummary() = runOnMain {
        val viewModel = startTwoExercises()
        viewModel.loaded()
        viewModel.confirmFinish(false)
        val state = awaitReal {
            viewModel.uiState.first { !it.finishing }
        }
        assertFalse(state.finished)
        assertNull(state.completionSummary)
        assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
        assertNull(viewModel.uiState.value.completionSummary)
    }

    @Test
    fun restoredViewModelReopensCompletionWithoutFinishingAgain() = runOnMain {
        val viewModel = startSingleSet()
        viewModel.loaded()
        viewModel.confirmFinish(true)
        val original = awaitReal { viewModel.uiState.first { it.finished && it.completionSummary != null } }
        val restored = active(sessionId)
        val restoredState = awaitReal {
            restored.uiState.first { it.finished && it.completionSummary != null }
        }
        assertEquals(original.completionSummary, restoredState.completionSummary)
        assertEquals(SessionStatus.COMPLETED, sessions.getAggregate(sessionId)!!.session.status)
        restored.confirmFinish(true)
        assertEquals(original.completionSummary, restored.uiState.value.completionSummary)
        assertEquals(SessionStatus.COMPLETED, sessions.getAggregate(sessionId)!!.session.status)
    }

    @Test
    fun confirmAbandonDeletesSessionWithoutAbandonedJournalEntry() = runOnMain {
        val viewModel = startSingleSet()
        viewModel.loaded()
        viewModel.confirmAbandon()
        awaitReal { viewModel.uiState.first { it.abandoned } }
        assertNull(sessions.getAggregate(sessionId))
        assertNull(sessions.observeInProgress().first())
        assertTrue(sessions.observeSummaries().first().isEmpty())
        assertTrue(viewModel.uiState.value.discarding)
    }

    @Test
    fun dismissAbandonLeavesActiveSessionUnchanged() = runOnMain {
        val viewModel = startSingleSet()
        viewModel.loaded()
        viewModel.requestAbandon()
        assertTrue(viewModel.uiState.value.confirmAbandon)
        viewModel.dismissAbandon()
        assertFalse(viewModel.uiState.value.confirmAbandon)
        assertFalse(viewModel.uiState.value.discarding)
        assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
        assertEquals(1, sessions.getAggregate(sessionId)!!.exercises.single().sets.size)
    }

    @Test
    fun doubleConfirmAbandonDoesNotRestartDelete() = runOnMain {
        val viewModel = startSingleSet()
        viewModel.loaded()
        viewModel.confirmAbandon()
        viewModel.confirmAbandon()
        awaitReal { viewModel.uiState.first { it.abandoned } }
        assertNull(sessions.getAggregate(sessionId))
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
    }

    @Test
    fun consumeFocusEventDoesNotReplay() = runOnMain {
        val viewModel = startSingleSet()
        val set = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.completeSet(set.id)
        awaitReal { viewModel.uiState.first { it.focusEvent != null } }
        viewModel.consumeFocusEvent()
        awaitReal { viewModel.uiState.first { it.focusEvent == null } }
        viewModel.consumeFocusEvent()
        assertNull(viewModel.uiState.value.focusEvent)
    }

    @Test
    fun debouncedEditsKeepTheNewestDraft() = runOnMain {
        val viewModel = startSingleSet()
        val setId = viewModel.loaded().aggregate!!.exercises.single().sets.single().id
        viewModel.onReps(setId, "6")
        viewModel.onReps(setId, "11")
        val payload = awaitDraft(setId)
        assertTrue(payload.contains("\"reps\":\"11\""))
        assertFalse(payload.contains("\"reps\":\"6\""))
        val set = sessions.getAggregate(sessionId)!!.exercises.single().sets.single()
        assertEquals(SessionSetStatus.PENDING, set.status)
    }

    @Test
    fun flushBeforeStopPersistsTheNewestDraftForNotificationCompletion() = runOnMain {
        val viewModel = startSingleSet()
        val setId = viewModel.loaded().aggregate!!.exercises.single().sets.single().id
        viewModel.onReps(setId, "11")
        viewModel.flushDirtyDrafts()
        val payload = database.workoutSessionDao().getSet(setId)!!.draftPayload
        assertTrue(payload!!.contains("\"reps\":\"11\""))
        assertEquals(
            NotificationSetCompletion.Updated,
            sessions.completeCurrentPendingSetFromNotification(sessionId, setId)
        )
        assertEquals(11, sessions.getAggregate(sessionId)!!.exercises.single().sets.single().actualReps)
        mainDispatcherRule.advanceTimeBy(ActiveWorkoutViewModel.DRAFT_PERSIST_DELAY_MS)
        val row = database.workoutSessionDao().getSet(setId)!!
        assertEquals(SessionSetStatus.COMPLETED.name, row.status)
        assertNull(row.draftPayload)
        assertEquals(11, row.actualReps)
    }

    @Test
    fun externalCompletionClearsDirtyStateAndFocusesTheNextSet() = runOnMain {
        val viewModel = startTwoExercises()
        val loaded = viewModel.loaded()
        val exerciseId = loaded.aggregate!!.exercises[0].exercise.id
        val first = loaded.aggregate!!.exercises[0].sets[0]
        val next = loaded.aggregate!!.exercises[0].sets[1]
        viewModel.onReps(first.id, "6")
        assertTrue(first.id in viewModel.uiState.value.dirtySetIds)
        assertEquals(
            NotificationSetCompletion.Updated,
            sessions.completeCurrentPendingSetFromNotification(sessionId, first.id)
        )
        assertEquals(8, sessions.getAggregate(sessionId)!!.exercises[0].sets[0].actualReps)
        val state = awaitReal {
            viewModel.uiState.first {
                it.currentSetId == next.id && first.id !in it.dirtySetIds && it.focusEvent != null
            }
        }
        assertEquals(WorkoutFocusTarget.Set(next.id, exerciseId), state.focusEvent!!.target)
        assertTrue(exerciseId in state.expandedExerciseIds)
        assertEquals("8", state.drafts[first.id]?.repsText)
    }

    @Test
    fun inAppCompletionEmitsOneFocusEvent() = runOnMain {
        val viewModel = startTwoExercises()
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.completeSet(first.id)
        val state = awaitReal { viewModel.uiState.first { it.focusEvent != null } }
        assertEquals(1L, state.focusEvent!!.generation)
        mainDispatcherRule.advanceUntilIdle()
        assertEquals(1L, viewModel.uiState.value.focusEvent?.generation)
    }

    @Test
    fun editingRepsDoesNotReloadOrCopyPreviousHistory() = runOnMain {
        val pull = savePull()
        val templateId = saveTemplate(
            "Push A",
            listOf(pull to listOf(PlannedSetDraft(-1, "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)))
        )
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        sessionId = started.sessionId
        insertCompletedHistory(
            templateId = templateId,
            templateName = "Old Push",
            exerciseId = pull,
            startedAt = 100L,
            actualReps = 5
        )
        val viewModel = active(sessionId)
        val loaded = awaitReal {
            viewModel.uiState.first { state ->
                val history = state.exerciseHistory
                history is InWorkoutExerciseHistory.Found && history.selection.sets.single().reps == 5
            }
        }
        val setId = loaded.aggregate!!.exercises.single().sets.single().id
        assertEquals(1, viewModel.historyLoadCount)
        viewModel.onReps(setId, "11")
        awaitReal { awaitDraft(setId) }
        awaitReal { delay(150) }
        val state = viewModel.uiState.value
        assertEquals("11", state.drafts[setId]?.repsText)
        assertEquals(1, viewModel.historyLoadCount)
        val history = state.exerciseHistory as InWorkoutExerciseHistory.Found
        assertEquals(5, history.selection.sets.single().reps)
        assertEquals(SessionSetStatus.PENDING, state.aggregate!!.exercises.single().sets.single().status)
    }

    @Test
    fun lateHistoryForThePreviousExerciseIsIgnored() = runOnMain {
        val pull = savePull()
        val dip = saveDip()
        val templateId = saveTemplate("Push A", listOf(pull to twoSets(), dip to twoSets()))
        insertCompletedHistory(
            templateId = templateId,
            templateName = "Old Push",
            exerciseId = pull,
            startedAt = 100L,
            actualReps = 5
        )
        insertCompletedHistory(
            templateId = templateId,
            templateName = "Old Push",
            exerciseId = dip,
            startedAt = 200L,
            actualReps = 12
        )
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        sessionId = started.sessionId
        val startedLoad = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        val viewModel = active(sessionId) { aggregate, exercise ->
            if (exercise.name == "Húzódzkodás") {
                startedLoad.complete(Unit)
                releaseLoad.await()
            }
            sessions.loadPreviousExerciseHistory(aggregate, exercise)
        }
        awaitReal { startedLoad.await() }
        val loaded = viewModel.loaded()
        val dipId = loaded.aggregate!!.exercises[1].exercise.id
        val firstSets = loaded.aggregate!!.exercises[0].sets
        viewModel.completeSet(firstSets[0].id)
        awaitReal { viewModel.uiState.first { it.currentSetId == firstSets[1].id } }
        viewModel.completeSet(firstSets[1].id)
        val switched = awaitReal {
            viewModel.uiState.first { state ->
                val history = state.exerciseHistory
                state.currentExerciseId == dipId &&
                    history is InWorkoutExerciseHistory.Found &&
                    history.sessionExerciseId == dipId &&
                    history.selection.sets.first().reps == 12
            }
        }
        releaseLoad.complete(Unit)
        awaitReal { delay(200) }
        val history = viewModel.uiState.value.exerciseHistory as InWorkoutExerciseHistory.Found
        assertEquals(dipId, history.sessionExerciseId)
        assertEquals(12, history.selection.sets.first().reps)
        assertEquals(switched.currentExerciseId, viewModel.uiState.value.currentExerciseId)
        assertEquals("8", viewModel.uiState.value.drafts[viewModel.uiState.value.currentSetId]?.repsText)
    }

    @Test
    fun completingDuringAPendingDraftWriteDoesNotRestoreTheDraft() = runOnMain {
        val viewModel = startSingleSet()
        val setId = viewModel.loaded().aggregate!!.exercises.single().sets.single().id
        viewModel.onReps(setId, "9")
        viewModel.completeSet(setId)
        viewModel.uiState.first {
            it.aggregate?.exercises?.single()?.sets?.single()?.status == SessionSetStatus.COMPLETED
        }
        mainDispatcherRule.advanceTimeBy(ActiveWorkoutViewModel.DRAFT_PERSIST_DELAY_MS)
        val row = database.workoutSessionDao().getSet(setId)!!
        assertEquals(SessionSetStatus.COMPLETED.name, row.status)
        assertNull(row.draftPayload)
        assertEquals(9, row.actualReps)
    }

    @Test
    fun reopeningACompletedSetExpandsOnlyThatSet() = runOnMain {
        val viewModel = startPullSets(4)
        val sets = viewModel.loaded().aggregate!!.exercises.single().sets
        sets.take(3).forEach { viewModel.finishPending(it.id) }
        val earlier = sets[0]
        val edited = sets[1]
        val later = sets[2]
        val active = sets[3]
        viewModel.consumeFocusEvent()
        awaitReal { viewModel.uiState.first { it.focusEvent == null } }

        viewModel.editCompletedSet(active.id)
        assertFalse(active.id in viewModel.uiState.value.editingSetIds)

        viewModel.editCompletedSet(edited.id)

        val state = viewModel.uiState.value
        assertEquals(setOf(edited.id), state.editingSetIds)
        assertFalse(earlier.id in state.editingSetIds)
        assertFalse(later.id in state.editingSetIds)
        assertEquals(SessionSetStatus.COMPLETED, state.setRow(edited.id).status)
        assertEquals("8", state.drafts[edited.id]?.repsText)
        assertEquals(active.id, state.currentSetId)
        assertEquals(active.id, state.focusedSetId)
        assertEquals(SessionSetStatus.PENDING, state.setRow(active.id).status)
        assertNull(state.focusEvent)
    }

    @Test
    fun savingAnEditedCompletedSetCollapsesItAndKeepsTheActiveSet() = runOnMain {
        val viewModel = startPullSets(4)
        val exerciseId = viewModel.loaded().aggregate!!.exercises.single().exercise.id
        val sets = viewModel.uiState.value.aggregate!!.exercises.single().sets
        sets.take(3).forEach { viewModel.finishPending(it.id) }
        val earlier = sets[0]
        val edited = sets[1]
        val later = sets[2]
        val active = sets[3]
        viewModel.consumeFocusEvent()
        awaitReal { viewModel.uiState.first { it.focusEvent == null } }
        val focusedBefore = viewModel.uiState.value.focusedSetId
        val expandedBefore = viewModel.uiState.value.expandedExerciseIds
        val selectedBefore = viewModel.uiState.value.selectedIndex

        viewModel.editCompletedSet(edited.id)
        viewModel.onReps(edited.id, "12")
        val open = viewModel.uiState.value
        assertTrue(edited.id in open.editingSetIds)
        assertEquals("12", open.drafts[edited.id]?.repsText)
        assertEquals(active.id, open.currentSetId)

        viewModel.completeSet(edited.id)
        val state = awaitReal {
            viewModel.uiState.first { ui ->
                edited.id !in ui.editingSetIds &&
                    edited.id !in ui.completingSetIds &&
                    edited.id !in ui.dirtySetIds &&
                    ui.setRow(edited.id).actualReps == 12
            }
        }
        assertEquals("12", state.drafts[edited.id]?.repsText)
        assertEquals(SessionSetStatus.COMPLETED, state.setRow(edited.id).status)
        assertEquals(8, state.setRow(earlier.id).actualReps)
        assertEquals(8, state.setRow(later.id).actualReps)
        assertEquals("8", state.drafts[earlier.id]?.repsText)
        assertEquals("8", state.drafts[later.id]?.repsText)
        assertFalse(earlier.id in state.editingSetIds)
        assertFalse(later.id in state.editingSetIds)
        assertTrue(state.editingSetIds.isEmpty())
        assertEquals(SessionSetStatus.PENDING, state.setRow(active.id).status)
        assertEquals(active.id, state.currentSetId)
        assertEquals(focusedBefore, state.focusedSetId)
        assertEquals(expandedBefore, state.expandedExerciseIds)
        assertEquals(selectedBefore, state.selectedIndex)
        assertTrue(exerciseId in state.expandedExerciseIds)
        assertNull(state.focusEvent)
        assertNull(state.message)
        assertFalse(state.finished)
        assertEquals(3, state.aggregate!!.exercises.single().sets.count { it.status == SessionSetStatus.COMPLETED })
        val persisted = sessions.getAggregate(sessionId)!!
        assertEquals(SessionStatus.IN_PROGRESS, persisted.session.status)
        assertEquals(12, persisted.exercises.single().sets.first { it.id == edited.id }.actualReps)
        assertEquals(8, persisted.exercises.single().sets.first { it.id == earlier.id }.actualReps)
        assertEquals(8, persisted.exercises.single().sets.first { it.id == later.id }.actualReps)
        assertEquals(
            SessionSetStatus.PENDING,
            persisted.exercises.single().sets.first { it.id == active.id }.status
        )
    }

    @Test
    fun failedEditOfACompletedSetKeepsTheEditorAndUnsavedValues() = runOnMain {
        val viewModel = startPullSets(4)
        val sets = viewModel.loaded().aggregate!!.exercises.single().sets
        sets.take(3).forEach { viewModel.finishPending(it.id) }
        val earlier = sets[0]
        val edited = sets[1]
        val later = sets[2]
        val active = sets[3]
        viewModel.editCompletedSet(edited.id)
        viewModel.onReps(edited.id, "0")
        viewModel.completeSet(edited.id)
        val state = awaitReal {
            viewModel.uiState.first { ui ->
                edited.id !in ui.completingSetIds && ui.setErrors[edited.id].orEmpty().isNotEmpty()
            }
        }
        assertTrue(edited.id in state.editingSetIds)
        assertEquals("0", state.drafts[edited.id]?.repsText)
        assertTrue(edited.id in state.dirtySetIds)
        assertEquals(SessionSetStatus.COMPLETED, state.setRow(edited.id).status)
        assertEquals(8, state.setRow(edited.id).actualReps)
        assertEquals(active.id, state.currentSetId)
        assertEquals(SessionSetStatus.PENDING, state.setRow(active.id).status)
        assertFalse(earlier.id in state.editingSetIds)
        assertFalse(later.id in state.editingSetIds)
        assertEquals(8, state.setRow(earlier.id).actualReps)
        assertEquals(8, state.setRow(later.id).actualReps)
        val persisted = sessions.getAggregate(sessionId)!!
        assertEquals(8, persisted.exercises.single().sets.first { it.id == edited.id }.actualReps)
        assertEquals(SessionStatus.IN_PROGRESS, persisted.session.status)
        assertFalse(state.finished)
    }

    @Test
    fun completingAPendingSetStillCollapsesItAndAdvances() = runOnMain {
        val viewModel = startPullSets(2)
        val sets = viewModel.loaded().aggregate!!.exercises.single().sets
        val first = sets[0]
        val next = sets[1]
        viewModel.onReps(first.id, "9")
        viewModel.completeSet(first.id)
        val state = awaitReal {
            viewModel.uiState.first { ui ->
                first.id !in ui.completingSetIds && ui.currentSetId == next.id
            }
        }
        assertTrue(state.editingSetIds.isEmpty())
        assertEquals(SessionSetStatus.COMPLETED, state.setRow(first.id).status)
        assertEquals(9, state.setRow(first.id).actualReps)
        assertEquals("9", state.drafts[first.id]?.repsText)
        assertEquals(SessionSetStatus.PENDING, state.setRow(next.id).status)
        assertEquals(next.id, state.currentSetId)
        assertEquals(next.id, state.focusedSetId)
        assertFalse(state.finished)
    }

    private fun ActiveWorkoutUiState.setRow(setId: Long) =
        aggregate!!.exercises.single().sets.first { it.id == setId }

    private suspend fun ActiveWorkoutViewModel.finishPending(setId: Long) {
        completeSet(setId)
        awaitReal {
            uiState.first { state ->
                setId !in state.completingSetIds &&
                    state.aggregate?.exercises?.flatMap { it.sets }?.any {
                        it.id == setId && it.status == SessionSetStatus.COMPLETED
                    } == true
            }
        }
    }

    private suspend fun awaitDraft(setId: Long): String {
        mainDispatcherRule.advanceTimeBy(ActiveWorkoutViewModel.DRAFT_PERSIST_DELAY_MS)
        lateinit var payload: String
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (true) {
                    withContext(Dispatchers.Main) { }
                    val stored = database.workoutSessionDao().getSet(setId)?.draftPayload
                    if (stored != null) {
                        payload = stored
                        return@withTimeout
                    }
                    yield()
                }
            }
        }
        return payload
    }

    @Test
    fun completingASetStartsInheritedRestWithoutAdvancingOrCompletingTheNextSet() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 60)
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        val second = viewModel.loaded().aggregate!!.exercises[0].sets[1]
        viewModel.completeSet(first.id)
        val resting = awaitReal {
            viewModel.uiState.first { it.rest?.phase == RestPhase.COUNTING }
        }
        assertEquals("01:00", resting.rest!!.countdown)
        assertNull(resting.focusEvent)
        assertNull(resting.currentSetId)
        assertEquals(SessionSetStatus.PENDING, resting.aggregate!!.exercises[0].sets[1].status)
        assertEquals(second.id, resting.aggregate!!.session.restNextSetId)
        assertEquals(RestProgressionMode.STRICT, resting.aggregate!!.session.restProgressionMode)
    }

    @Test
    fun strictExpirySelectsTheNextSetOnceAndDoesNotCompleteIt() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 60)
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.completeSet(sets[0].id)
        awaitReal { viewModel.uiState.first { it.rest?.phase == RestPhase.COUNTING } }
        restElapsed.addAndGet(60_000L)
        restWall.addAndGet(60_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        val next = awaitReal {
            viewModel.uiState.first { it.rest == null && it.focusedSetId == sets[1].id }
        }
        assertEquals(sets[1].id, next.currentSetId)
        assertEquals(SessionSetStatus.PENDING, next.aggregate!!.exercises[0].sets[1].status)
        restElapsed.addAndGet(60_000L)
        restWall.addAndGet(60_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        val still = viewModel.uiState.value
        assertEquals(sets[1].id, still.focusedSetId)
        assertEquals(SessionSetStatus.PENDING, still.aggregate!!.exercises[0].sets[1].status)
    }

    @Test
    fun flexibleExpiryWaitsForNextSet() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 45, mode = RestProgressionMode.FLEXIBLE)
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.completeSet(sets[0].id)
        awaitReal { viewModel.uiState.first { it.rest?.phase == RestPhase.COUNTING } }
        restElapsed.addAndGet(45_000L)
        restWall.addAndGet(45_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        val waiting = awaitReal {
            viewModel.uiState.first { it.rest?.awaitingNext == true }
        }
        assertNull(waiting.focusEvent)
        assertEquals(SessionSetStatus.PENDING, waiting.aggregate!!.exercises[0].sets[1].status)
        viewModel.continueAfterRest()
        val moved = awaitReal { viewModel.uiState.first { it.focusedSetId == sets[1].id && it.rest == null } }
        assertEquals(SessionSetStatus.PENDING, moved.aggregate!!.exercises[0].sets[1].status)
    }

    @Test
    fun skipRestAdvancesInStrictModeWithoutCompletingTheNextSet() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 30)
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.completeSet(sets[0].id)
        awaitReal { viewModel.uiState.first { it.rest != null } }
        viewModel.skipRest()
        val next = awaitReal { viewModel.uiState.first { it.focusedSetId == sets[1].id && it.rest == null } }
        assertEquals(SessionSetStatus.PENDING, next.aggregate!!.exercises[0].sets[1].status)
    }

    @Test
    fun skipRestAdvancesInFlexibleModeWithoutCompletingTheNextSet() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 30, mode = RestProgressionMode.FLEXIBLE)
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.completeSet(sets[0].id)
        awaitReal { viewModel.uiState.first { it.rest != null } }
        viewModel.skipRest()
        val next = awaitReal { viewModel.uiState.first { it.focusedSetId == sets[1].id && it.rest == null } }
        assertEquals(SessionSetStatus.PENDING, next.aggregate!!.exercises[0].sets[1].status)
    }

    @Test
    fun addingTenSecondsExtendsTheCurrentIntervalWithoutChangingThePlan() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 60)
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.completeSet(first.id)
        awaitReal { viewModel.uiState.first { it.rest?.countdown == "01:00" } }
        viewModel.addRestTenSeconds()
        awaitReal { viewModel.uiState.first { it.rest?.countdown == "01:10" } }
        viewModel.addRestTenSeconds()
        val extended = awaitReal { viewModel.uiState.first { it.rest?.countdown == "01:20" } }
        assertEquals(60, extended.aggregate!!.exercises[0].exercise.restSeconds)
        assertEquals(80_000L, extended.aggregate!!.session.restDurationMillis)
    }

    @Test
    fun theFinalSetDoesNotStartRest() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 60, setCount = 1)
        val only = viewModel.loaded().aggregate!!.exercises[0].sets.single()
        viewModel.completeSet(only.id)
        val done = awaitReal { viewModel.uiState.first { only.id !in it.completingSetIds && it.aggregate != null } }
        assertNull(done.rest)
        assertEquals(SessionSetStatus.COMPLETED, done.aggregate!!.exercises[0].sets.single().status)
    }

    @Test
    fun anExplicitOffSetDoesNotStartRest() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 60, setRest = listOf(0, null))
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.completeSet(sets[0].id)
        val advanced = awaitReal { viewModel.uiState.first { it.focusedSetId == sets[1].id } }
        assertNull(advanced.rest)
        assertEquals(SessionSetStatus.PENDING, advanced.aggregate!!.exercises[0].sets[1].status)
    }

    @Test
    fun manualExerciseSelectionCancelsRestAndAStaleExpiryDoesNotMoveBack() = runOnMain {
        val pull = savePull()
        val dip = saveDip()
        useRestClock()
        val templateId = saveTemplate(
            "Both",
            listOf(pull to twoSets(), dip to twoSets()),
            exerciseRest = 90
        )
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        sessionId = started.sessionId
        val viewModel = active(started.sessionId, restClock = ::restSample)
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.completeSet(first.id)
        awaitReal { viewModel.uiState.first { it.rest?.phase == RestPhase.COUNTING } }
        viewModel.selectExercise(1)
        val cancelled = awaitReal { viewModel.uiState.first { it.rest == null && it.selectedIndex == 1 } }
        assertEquals(1, cancelled.selectedIndex)
        restElapsed.addAndGet(90_000L)
        restWall.addAndGet(90_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        assertEquals(1, viewModel.uiState.value.selectedIndex)
        assertNull(viewModel.uiState.value.rest)
    }

    @Test
    fun abandonClearsRest() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 30)
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.completeSet(first.id)
        awaitReal { viewModel.uiState.first { it.rest != null } }
        viewModel.requestAbandon()
        viewModel.confirmAbandon()
        val left = awaitReal { viewModel.uiState.first { it.abandoned && it.rest == null } }
        assertNull(sessions.getAggregate(sessionId))
    }

    @Test
    fun aNewViewModelResumesTheStoredDeadlineAndARebootUsesTheWallClock() = runOnMain {
        val viewModel = startRestWorkout(exerciseRest = 60)
        val first = viewModel.loaded().aggregate!!.exercises[0].sets[0]
        viewModel.completeSet(first.id)
        awaitReal { viewModel.uiState.first { it.rest?.countdown == "01:00" } }
        restElapsed.addAndGet(20_000L)
        restWall.addAndGet(20_000L)
        val resumed = active(sessionId, restClock = ::restSample)
        val recovered = awaitReal { resumed.uiState.first { it.rest?.countdown == "00:40" } }
        assertEquals(RestPhase.COUNTING, recovered.rest!!.phase)
        val anchor = recovered.aggregate!!.session.restAnchorElapsedRealtimeMillis!!
        restElapsed.set(anchor - 10_000L)
        restWall.addAndGet(15_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        val afterReboot = awaitReal { resumed.uiState.first { it.rest?.countdown == "00:25" } }
        assertEquals(RestPhase.COUNTING, afterReboot.rest!!.phase)
    }

    private fun runOnMain(block: suspend () -> Unit) = runTest {
        withContext(Dispatchers.Main) {
            block()
        }
    }

    @Test
    fun extraExerciseBeforeThePlanIsDoneDoesNotStealTheCurrentSet() = runOnMain {
        val dip = saveDip()
        val viewModel = startSingleSet()
        val initial = viewModel.loaded()
        viewModel.addExtraExercise(dip)
        val after = awaitReal {
            viewModel.uiState.first { state ->
                val exercises = state.aggregate?.exercises.orEmpty()
                exercises.size == 2 && exercises.last().sets.any { it.status == SessionSetStatus.PENDING }
            }
        }
        assertEquals(initial.currentSetId, after.currentSetId)
        assertTrue(after.progress.pending > 1)
        assertFalse(after.aggregate!!.exercises.first().exercise.addedDuringWorkout)
        assertTrue(after.aggregate!!.exercises.last().exercise.addedDuringWorkout)
        val restored = active(sessionId).loaded()
        assertEquals(2, restored.aggregate!!.exercises.size)
        assertTrue(restored.aggregate!!.exercises.last().exercise.addedDuringWorkout)
    }

    @Test
    fun extraExerciseReopensAFinishedPlanUntilItsOwnSetsAreDone() = runOnMain {
        val dip = saveDip()
        val viewModel = startSingleSet()
        val initial = viewModel.loaded()
        val templateId = initial.aggregate!!.session.templateId!!
        val planBefore = database.workoutTemplateDao().getExercises(templateId)
        viewModel.completeSet(initial.currentSetId!!)
        awaitReal { viewModel.uiState.first { it.progress.pending == 0 } }
        viewModel.addExtraExercise(dip)
        val reopened = awaitReal {
            viewModel.uiState.first { it.aggregate!!.exercises.size == 2 && it.progress.pending > 0 }
        }
        val extra = reopened.aggregate!!.exercises.single { it.exercise.addedDuringWorkout }
        assertEquals(dip, extra.exercise.exerciseId)
        assertTrue(extra.sets.all { it.addedDuringWorkout })
        assertEquals(extra.sets.single().id, reopened.currentSetId)
        assertEquals(planBefore, database.workoutTemplateDao().getExercises(templateId))
        viewModel.completeSet(extra.sets.single().id)
        val done = awaitReal { viewModel.uiState.first { it.progress.pending == 0 } }
        assertEquals(0, done.progress.pending)
        assertEquals(2, done.aggregate!!.exercises.size)
    }

    private suspend fun startTwoExercises(): ActiveWorkoutViewModel {
        val pull = savePull()
        val dip = saveDip()
        val templateId = saveTemplate("Push A", listOf(pull to twoSets(), dip to twoSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        sessionId = started.sessionId
        return active(started.sessionId)
    }

    private suspend fun startPullSets(count: Int): ActiveWorkoutViewModel {
        val pull = savePull()
        val planned = List(count) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
            )
        }
        val templateId = saveTemplate("Push A", listOf(pull to planned))
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        sessionId = started.sessionId
        return active(started.sessionId)
    }

    private suspend fun startSingleSet(): ActiveWorkoutViewModel {
        val pull = savePull()
        val templateId = saveTemplate(
            "Push A",
            listOf(pull to listOf(PlannedSetDraft(-1, "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)))
        )
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        sessionId = started.sessionId
        return active(started.sessionId)
    }

    private fun active(
        sessionId: Long,
        restClock: (() -> RestClockSample)? = null,
        entitlements: app.mymusclemap.domain.entitlement.FeatureEntitlements =
            app.mymusclemap.domain.entitlement.OpenFeatureEntitlements,
        loadExerciseHistory: (suspend (WorkoutSessionAggregate, SessionExercise) -> ExerciseHistorySelection?)? = null
    ): ActiveWorkoutViewModel {
        val viewModel = if (loadExerciseHistory == null) {
            ActiveWorkoutViewModel(
                SavedStateHandle(mapOf(ActiveWorkoutViewModel.SESSION_ID to sessionId)),
                sessions,
                restClock = restClock ?: {
                    RestClockSample(
                        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime(),
                        wallClockMillis = java.time.Clock.systemUTC().millis()
                    )
                },
                entitlements = entitlements
            )
        } else {
            ActiveWorkoutViewModel(
                SavedStateHandle(mapOf(ActiveWorkoutViewModel.SESSION_ID to sessionId)),
                sessions,
                loadExerciseHistory = loadExerciseHistory,
                restClock = restClock ?: {
                    RestClockSample(
                        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime(),
                        wallClockMillis = java.time.Clock.systemUTC().millis()
                    )
                },
                entitlements = entitlements
            )
        }
        viewModelStore.put("active-${viewModelKey++}", viewModel)
        return viewModel
    }

    private suspend fun insertCompletedHistory(
        templateId: Long,
        templateName: String,
        exerciseId: Long,
        startedAt: Long,
        actualReps: Int
    ) {
        database.workoutSessionDao().insertAggregate(
            WorkoutSessionEntity(
                templateId = templateId,
                templateName = templateName,
                status = SessionStatus.COMPLETED.name,
                workoutDate = "2026-09-01",
                startedAt = startedAt,
                finishedAt = startedAt + 10,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN.name,
                bodyWeightSourceDate = null,
                createdAt = startedAt,
                updatedAt = startedAt,
                activeLock = null,
                clientWorkoutId = UUID.randomUUID().toString()
            ),
            listOf(
                Triple(
                    WorkoutSessionExerciseEntity(
                        sessionId = 0L,
                        exerciseId = exerciseId,
                        position = 0,
                        name = "Exercise",
                        category = ExerciseCategory.STRENGTH.name,
                        movementPattern = MovementPattern.VERTICAL_PULL.name,
                        measurementType = MeasurementType.REPETITIONS.name,
                        resistanceBasis = ResistanceBasis.BODYWEIGHT.name,
                        weightInterpretation = WeightInterpretation.NOT_APPLICABLE.name,
                        primaryMuscle = MuscleGroup.LATS.name,
                        notes = null
                    ),
                    emptyList(),
                    listOf(
                        WorkoutSessionSetEntity(
                            sessionExerciseId = 0L,
                            position = 0,
                            plannedMinReps = 8,
                            plannedMaxReps = 8,
                            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY.name,
                            plannedWeightKg = null,
                            plannedDurationSeconds = null,
                            plannedDistanceMeters = null,
                            actualReps = actualReps,
                            actualLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY.name,
                            actualWeightKg = null,
                            actualDurationSeconds = null,
                            actualDistanceMeters = null,
                            status = SessionSetStatus.COMPLETED.name,
                            completedAt = startedAt + 5,
                            addedDuringWorkout = false
                        )
                    )
                )
            )
        )
    }

    private suspend fun ActiveWorkoutViewModel.loaded(): ActiveWorkoutUiState {
        return awaitReal {
            uiState.first { !it.loading && it.aggregate != null && it.drafts.isNotEmpty() }
        }
    }

    private suspend fun <T> awaitReal(block: suspend () -> T): T {
        return withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { block() }
        }
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

    private fun twoSets(): List<PlannedSetDraft> {
        return List(2) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
            )
        }
    }

    private suspend fun saveTemplate(
        name: String,
        items: List<Pair<Long, List<PlannedSetDraft>>>,
        exerciseRest: Int? = null,
        mode: RestProgressionMode = RestProgressionMode.STRICT
    ): Long {
        val draft = TemplateDraft(
            name = name,
            restProgressionMode = mode,
            exercises = items.mapIndexed { index, item ->
                TemplateExerciseDraft(
                    localId = -(index + 1L),
                    exerciseId = item.first,
                    sets = item.second,
                    restSeconds = exerciseRest
                )
            }
        )
        return (templates.save(draft) as TemplateSaveResult.Created).id
    }

    private fun restSample(): RestClockSample {
        return RestClockSample(restElapsed.get(), restWall.get())
    }

    private fun useRestClock() {
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(today),
            restClock = ::restSample
        )
    }

    private suspend fun startRestWorkout(
        exerciseRest: Int?,
        setCount: Int = 2,
        setRest: List<Int?> = emptyList(),
        mode: RestProgressionMode = RestProgressionMode.STRICT,
        name: String = "Push A"
    ): ActiveWorkoutViewModel {
        val pull = savePull()
        useRestClock()
        val planned = List(setCount) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                restSeconds = setRest.getOrNull(index)
            )
        }
        val templateId = saveTemplate(name, listOf(pull to planned), exerciseRest, mode)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        sessionId = started.sessionId
        return active(started.sessionId, restClock = ::restSample)
    }

    @Test
    fun finishEarlyKeepsTheSetPendingUntilConfirmThenStartsRest() = runOnMain {
        val viewModel = startTimedWorkout()
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.startWork(sets[0].id)
        val running = awaitReal { viewModel.uiState.first { it.work?.phase == WorkPhase.RUNNING } }
        assertEquals(SessionSetStatus.PENDING, running.aggregate!!.exercises[0].sets[0].status)
        assertNull(running.rest)
        restElapsed.addAndGet(10_000L)
        viewModel.pauseWork()
        val paused = awaitReal { viewModel.uiState.first { it.work?.phase == WorkPhase.PAUSED } }
        assertEquals(10, paused.work?.actualSeconds)
        assertEquals("00:20", paused.work?.countdown)
        val pausedProgress = paused.work?.progress
        restElapsed.addAndGet(25_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        val stillPaused = viewModel.uiState.value
        assertEquals(WorkPhase.PAUSED, stillPaused.work?.phase)
        assertEquals(10, stillPaused.work?.actualSeconds)
        assertEquals("00:20", stillPaused.work?.countdown)
        assertEquals(pausedProgress, stillPaused.work?.progress)
        viewModel.pauseWork()
        assertEquals(WorkPhase.PAUSED, viewModel.uiState.value.work?.phase)
        viewModel.resumeWork()
        awaitReal { viewModel.uiState.first { it.work?.phase == WorkPhase.RUNNING } }
        viewModel.finishWorkEarly()
        val awaiting = awaitReal { viewModel.uiState.first { it.work?.awaiting == true } }
        assertEquals(SessionSetStatus.PENDING, awaiting.aggregate!!.exercises[0].sets[0].status)
        assertEquals(10, awaiting.work?.actualSeconds)
        viewModel.confirmTimedSet()
        val resting = awaitReal { viewModel.uiState.first { it.rest?.phase == RestPhase.COUNTING } }
        val completed = resting.aggregate!!.exercises[0].sets[0]
        assertEquals(SessionSetStatus.COMPLETED, completed.status)
        assertEquals(10, completed.actualDurationSeconds)
        assertNull(completed.actualReps)
        assertEquals(30, completed.plannedDurationSeconds)
        assertNull(resting.work)
        viewModel.confirmTimedSet()
        assertNull(viewModel.uiState.value.work)
    }

    @Test
    fun pausedWorkSurvivesANewViewModelAndCannotBeCompletedEarly() = runOnMain {
        val viewModel = startTimedWorkout()
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.completeSet(sets[0].id)
        mainDispatcherRule.advanceTimeBy(400L)
        assertEquals(SessionSetStatus.PENDING, viewModel.uiState.value.aggregate!!.exercises[0].sets[0].status)
        viewModel.startWork(sets[0].id)
        awaitReal { viewModel.uiState.first { it.work?.phase == WorkPhase.RUNNING } }
        viewModel.completeSet(sets[0].id)
        restElapsed.addAndGet(12_000L)
        viewModel.pauseWork()
        val paused = awaitReal { viewModel.uiState.first { it.work?.countdown == "00:18" } }
        assertEquals(WorkPhase.PAUSED, paused.work?.phase)
        val progress = paused.work!!.progress
        viewModel.completeSet(sets[0].id)
        viewModel.skipSet(sets[0].id)
        restElapsed.addAndGet(10_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        assertEquals(SessionSetStatus.PENDING, viewModel.uiState.value.aggregate!!.exercises[0].sets[0].status)
        assertEquals("00:18", viewModel.uiState.value.work?.countdown)
        assertEquals(progress, viewModel.uiState.value.work?.progress)
        val recreated = active(sessionId, restClock = ::restSample)
        val recovered = awaitReal {
            recreated.uiState.first { it.work?.phase == WorkPhase.PAUSED && it.work?.countdown == "00:18" }
        }
        assertEquals(progress, recovered.work?.progress)
        recreated.pauseWork()
        val pausedAgain = awaitReal { recreated.uiState.first { it.work?.phase == WorkPhase.PAUSED } }
        assertEquals("00:18", pausedAgain.work?.countdown)
        recreated.pauseWork()
        assertEquals(WorkPhase.PAUSED, recreated.uiState.value.work?.phase)
        recreated.resumeWork()
        val running = awaitReal { recreated.uiState.first { it.work?.phase == WorkPhase.RUNNING } }
        assertEquals("00:18", running.work?.countdown)
    }

    @Test
    fun workExpiryWaitsForConfirmAndDoesNotStartTheNextInterval() = runOnMain {
        val viewModel = startTimedWorkout()
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.startWork(sets[0].id)
        awaitReal { viewModel.uiState.first { it.work?.phase == WorkPhase.RUNNING } }
        restElapsed.addAndGet(30_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        val awaiting = awaitReal { viewModel.uiState.first { it.work?.awaiting == true } }
        assertEquals(SessionSetStatus.PENDING, awaiting.aggregate!!.exercises[0].sets[0].status)
        assertNull(awaiting.rest)
        restElapsed.addAndGet(5_000L)
        mainDispatcherRule.advanceTimeBy(1_000L)
        assertEquals(WorkPhase.AWAITING, viewModel.uiState.value.work?.phase)
        viewModel.confirmTimedSet()
        val resting = awaitReal { viewModel.uiState.first { it.rest?.phase == RestPhase.COUNTING } }
        assertEquals(30, resting.aggregate!!.exercises[0].sets[0].actualDurationSeconds)
        assertNull(resting.aggregate!!.exercises[0].sets[0].actualReps)
        assertNull(resting.work)
        assertEquals(SessionSetStatus.PENDING, resting.aggregate!!.exercises[0].sets[1].status)
    }

    @Test
    fun freeUserCompletesASetWithoutStartingRest() = runOnMain {
        val pull = savePull()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(today),
            timersAllowed = { false }
        )
        val templateId = saveTemplate("Free", listOf(pull to twoSets()), exerciseRest = 60)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        sessionId = started.sessionId
        val viewModel = active(
            started.sessionId,
            entitlements = SelectiveFeatureEntitlements(emptySet())
        )
        val sets = viewModel.loaded().aggregate!!.exercises[0].sets
        viewModel.completeSet(sets[0].id)
        val advanced = awaitReal { viewModel.uiState.first { it.focusedSetId == sets[1].id } }
        assertNull(advanced.rest)
        assertFalse(advanced.timersEnabled)
        assertEquals(SessionSetStatus.COMPLETED, advanced.aggregate!!.exercises[0].sets[0].status)
    }

    private suspend fun startTimedWorkout(): ActiveWorkoutViewModel {
        val pull = savePull()
        useRestClock()
        val planned = List(2) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                minutesText = "0",
                secondsText = "30",
                timedWork = true
            )
        }
        val templateId = saveTemplate("Burpees", listOf(pull to planned), exerciseRest = 45)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        sessionId = started.sessionId
        return active(started.sessionId, restClock = ::restSample)
    }
}
