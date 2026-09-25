package app.mymusclemap.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.workout.AbandonWorkoutResult
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.ActualSetLogic
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedLoadLogic
import app.mymusclemap.domain.workout.SessionFocusLogic
import app.mymusclemap.domain.workout.SessionMutationResult
import app.mymusclemap.domain.workout.SessionProgress
import app.mymusclemap.domain.workout.SessionProgressLogic
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.TemplateFieldError
import app.mymusclemap.domain.workout.WorkoutCompletionLogic
import app.mymusclemap.domain.workout.WorkoutCompletionSummary
import app.mymusclemap.domain.workout.WorkoutFocusTarget
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock

data class WorkoutFocusEvent(
    val generation: Long,
    val target: WorkoutFocusTarget
)

data class ActiveWorkoutUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val aggregate: WorkoutSessionAggregate? = null,
    val selectedIndex: Int = 0,
    val currentExerciseId: Long? = null,
    val currentSetId: Long? = null,
    val drafts: Map<Long, ActualSetDraft> = emptyMap(),
    val dirtySetIds: Set<Long> = emptySet(),
    val completingSetIds: Set<Long> = emptySet(),
    val setErrors: Map<Long, List<TemplateFieldError>> = emptyMap(),
    val nowMillis: Long = 0L,
    val pendingFinishCount: Int? = null,
    val confirmAbandon: Boolean = false,
    val finished: Boolean = false,
    val abandoned: Boolean = false,
    val discarding: Boolean = false,
    val message: ActiveWorkoutMessage? = null,
    val focusEvent: WorkoutFocusEvent? = null,
    val focusedSetId: Long? = null,
    val expandedExerciseIds: Set<Long> = emptySet(),
    val finishing: Boolean = false,
    val completionSummary: WorkoutCompletionSummary? = null
) {
    val progress: SessionProgress
        get() = aggregate?.let(SessionProgressLogic::fromAggregate) ?: SessionProgress(0, 0, 0, 0)

    val elapsedLabel: String
        get() {
            val started = aggregate?.session?.startedAt ?: return "0:00"
            return ElapsedTime.format(started, nowMillis)
        }
}

sealed interface ActiveWorkoutMessage {
    data object SetUpdated : ActiveWorkoutMessage
    data object ExtraAdded : ActiveWorkoutMessage
    data object ExtraRemoved : ActiveWorkoutMessage
    data object CannotRemoveOriginal : ActiveWorkoutMessage
    data object SaveFailed : ActiveWorkoutMessage
    data object DiscardFailed : ActiveWorkoutMessage
}

class ActiveWorkoutViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: WorkoutSessionRepository,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>(SESSION_ID) ?: -1L
    private val selectedIndex = savedStateHandle.getStateFlow(SELECTED_INDEX, 0)
    private val drafts = MutableStateFlow<Map<Long, ActualSetDraft>>(emptyMap())
    private val dirtyIds = MutableStateFlow<Set<Long>>(emptySet())
    private val completingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val setErrors = MutableStateFlow<Map<Long, List<TemplateFieldError>>>(emptyMap())
    private val nowMillis = MutableStateFlow(clock.millis())
    private val pendingFinishCount = MutableStateFlow<Int?>(null)
    private val confirmAbandon = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)
    private val finishing = MutableStateFlow(false)
    private val completionSummary = MutableStateFlow<WorkoutCompletionSummary?>(null)
    private val abandoned = MutableStateFlow(false)
    private val discarding = MutableStateFlow(false)
    private val message = MutableStateFlow<ActiveWorkoutMessage?>(null)
    private val focusEvent = MutableStateFlow<WorkoutFocusEvent?>(null)
    private val focusedSetId = MutableStateFlow<Long?>(null)
    private val expandedIds = MutableStateFlow<Set<Long>>(emptySet())
    private var focusGeneration = 0L

    private data class Dialogs(
        val now: Long,
        val pendingFinish: Int?,
        val confirmAbandon: Boolean,
        val finished: Boolean,
        val abandoned: Boolean,
        val discarding: Boolean,
        val finishing: Boolean
    )

    private data class EditorSignals(
        val drafts: Map<Long, ActualSetDraft>,
        val dirty: Set<Long>,
        val completing: Set<Long>,
        val errors: Map<Long, List<TemplateFieldError>>,
        val message: ActiveWorkoutMessage?,
        val completionSummary: WorkoutCompletionSummary?
    )

    private data class FocusChrome(
        val focus: WorkoutFocusEvent?,
        val focusedSetId: Long?,
        val expanded: Set<Long>
    )

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionRepository.observeAggregate(sessionId),
        selectedIndex,
        combine(drafts, dirtyIds, completingIds, setErrors, combine(message, completionSummary) { currentMessage, summary ->
            currentMessage to summary
        }) { currentDrafts, dirty, completing, errors, messageAndSummary ->
            EditorSignals(
                currentDrafts,
                dirty,
                completing,
                errors,
                messageAndSummary.first,
                messageAndSummary.second
            )
        },
        combine(
            nowMillis,
            pendingFinishCount,
            confirmAbandon,
            finished,
            combine(abandoned, discarding, finishing) { left, discardingNow, finishingNow ->
                Triple(left, discardingNow, finishingNow)
            }
        ) { now, pending, abandon, done, triple ->
            Dialogs(now, pending, abandon, done, triple.first, triple.second, triple.third)
        },
        combine(focusEvent, focusedSetId, expandedIds) { focus, focused, expanded ->
            FocusChrome(focus, focused, expanded)
        }
    ) { aggregate, index, signals, dialogs, chrome ->
        if (aggregate == null) {
            return@combine ActiveWorkoutUiState(
                loading = false,
                missing = !dialogs.abandoned && !dialogs.finished && !dialogs.discarding &&
                    signals.completionSummary == null,
                nowMillis = dialogs.now,
                pendingFinishCount = dialogs.pendingFinish,
                confirmAbandon = dialogs.confirmAbandon,
                finished = dialogs.finished,
                abandoned = dialogs.abandoned,
                discarding = dialogs.discarding,
                message = signals.message,
                focusEvent = chrome.focus,
                focusedSetId = chrome.focusedSetId,
                expandedExerciseIds = chrome.expanded,
                finishing = dialogs.finishing,
                completionSummary = signals.completionSummary
            )
        }
        val notActive = aggregate.session.status != SessionStatus.IN_PROGRESS
        val bounded = if (aggregate.exercises.isEmpty()) {
            0
        } else {
            index.coerceIn(0, aggregate.exercises.lastIndex)
        }
        ActiveWorkoutUiState(
            loading = false,
            missing = notActive && !dialogs.finished && signals.completionSummary == null,
            aggregate = aggregate,
            selectedIndex = bounded,
            currentExerciseId = SessionFocusLogic.currentPendingExercise(aggregate)?.exercise?.id,
            currentSetId = SessionFocusLogic.currentPendingSet(aggregate)?.id,
            drafts = mergeDrafts(aggregate, signals.drafts, signals.dirty),
            dirtySetIds = signals.dirty,
            completingSetIds = signals.completing,
            setErrors = signals.errors,
            nowMillis = dialogs.now,
            pendingFinishCount = dialogs.pendingFinish,
            confirmAbandon = dialogs.confirmAbandon,
            finished = dialogs.finished,
            abandoned = dialogs.abandoned || aggregate.session.status == SessionStatus.ABANDONED,
            discarding = dialogs.discarding,
            message = signals.message,
            focusEvent = chrome.focus,
            focusedSetId = chrome.focusedSetId,
            expandedExerciseIds = chrome.expanded,
            finishing = dialogs.finishing,
            completionSummary = signals.completionSummary
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ActiveWorkoutUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                nowMillis.value = clock.millis()
                delay(1_000)
            }
        }
        viewModelScope.launch {
            val aggregate = sessionRepository.observeAggregate(sessionId).first { it != null }
            val pendingId = aggregate?.let { SessionFocusLogic.currentPendingExercise(it)?.exercise?.id }
            if (pendingId != null) {
                expandedIds.value = expandedIds.value + pendingId
            }
        }
    }

    fun selectExercise(index: Int) {
        val max = uiState.value.aggregate?.exercises?.lastIndex ?: return
        savedStateHandle[SELECTED_INDEX] = index.coerceIn(0, max)
    }

    fun previousExercise() {
        selectExercise(uiState.value.selectedIndex - 1)
    }

    fun nextExercise() {
        selectExercise(uiState.value.selectedIndex + 1)
    }

    fun onReps(setId: Long, value: String) {
        updateDraft(setId) { it.copy(repsText = value) }
    }

    fun stepReps(setId: Long, delta: Int) {
        updateDraft(setId) { draft ->
            draft.copy(repsText = ActualSetLogic.adjustRepsText(draft.repsText, delta))
        }
    }

    fun onLoadKind(setId: Long, kind: PlannedLoadKind) {
        updateDraft(setId) { draft ->
            draft.copy(
                loadKind = kind,
                weightText = if (PlannedLoadLogic.requiresPositiveWeight(kind)) {
                    draft.weightText
                } else {
                    ""
                }
            )
        }
    }

    fun onWeight(setId: Long, value: String) {
        updateDraft(setId) { it.copy(weightText = value) }
    }

    fun onMinutes(setId: Long, value: String) {
        updateDraft(setId) { it.copy(minutesText = value) }
    }

    fun onSeconds(setId: Long, value: String) {
        updateDraft(setId) { it.copy(secondsText = value) }
    }

    fun onDistance(setId: Long, value: String) {
        updateDraft(setId) { it.copy(distanceText = value) }
    }

    fun onDistanceUnit(setId: Long, unit: DistanceUnit) {
        updateDraft(setId) { it.copy(distanceUnit = unit) }
    }

    fun completeSet(setId: Long) {
        if (discarding.value || setId in completingIds.value) {
            return
        }
        val snapshot = uiState.value
        val draft = snapshot.drafts[setId] ?: return
        val currentStatus = snapshot.aggregate
            ?.exercises
            ?.flatMap { it.sets }
            ?.firstOrNull { it.id == setId }
            ?.status
            ?: return
        completingIds.value = completingIds.value + setId
        viewModelScope.launch {
            try {
                when (val result = sessionRepository.completeSet(setId, draft)) {
                    SessionMutationResult.Updated -> {
                        dirtyIds.value = dirtyIds.value - setId
                        setErrors.value = setErrors.value - setId
                        if (currentStatus == SessionSetStatus.PENDING) {
                            advanceAfterResolved(setId)
                        }
                    }
                    is SessionMutationResult.Invalid -> {
                        setErrors.value = setErrors.value + (setId to result.errors)
                    }
                    SessionMutationResult.NotFound,
                    SessionMutationResult.NotActive,
                    SessionMutationResult.OriginalSetProtected -> {
                        message.value = ActiveWorkoutMessage.SaveFailed
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = ActiveWorkoutMessage.SaveFailed
            } finally {
                completingIds.value = completingIds.value - setId
            }
        }
    }

    fun skipSet(setId: Long) {
        if (discarding.value || setId in completingIds.value) {
            return
        }
        completingIds.value = completingIds.value + setId
        viewModelScope.launch {
            try {
                when (sessionRepository.skipSet(setId)) {
                    SessionMutationResult.Updated -> {
                        dirtyIds.value = dirtyIds.value - setId
                        setErrors.value = setErrors.value - setId
                        advanceAfterResolved(setId)
                    }
                    SessionMutationResult.NotFound,
                    SessionMutationResult.NotActive,
                    SessionMutationResult.OriginalSetProtected -> {
                        message.value = ActiveWorkoutMessage.SaveFailed
                    }
                    is SessionMutationResult.Invalid -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = ActiveWorkoutMessage.SaveFailed
            } finally {
                completingIds.value = completingIds.value - setId
            }
        }
    }

    fun undoSkip(setId: Long) {
        if (discarding.value) {
            return
        }
        viewModelScope.launch {
            sessionRepository.undoSkip(setId)
        }
    }

    fun addExtraSet(sessionExerciseId: Long) {
        if (discarding.value) {
            return
        }
        viewModelScope.launch {
            if (sessionRepository.addExtraSet(sessionExerciseId) == SessionMutationResult.Updated) {
                message.value = ActiveWorkoutMessage.ExtraAdded
            }
        }
    }

    fun removeExtraSet(setId: Long) {
        if (discarding.value) {
            return
        }
        viewModelScope.launch {
            when (sessionRepository.removeExtraSet(setId)) {
                SessionMutationResult.Updated -> message.value = ActiveWorkoutMessage.ExtraRemoved
                SessionMutationResult.OriginalSetProtected ->
                    message.value = ActiveWorkoutMessage.CannotRemoveOriginal
                else -> Unit
            }
        }
    }

    fun requestFinish() {
        if (discarding.value || finishing.value || finished.value) {
            return
        }
        val pending = uiState.value.progress.pending
        if (pending > 0) {
            pendingFinishCount.value = pending
        } else {
            pendingFinishCount.value = 0
        }
    }

    fun dismissFinish() {
        if (finishing.value) {
            return
        }
        pendingFinishCount.value = null
    }

    fun confirmFinish(skipRemaining: Boolean) {
        if (discarding.value || finishing.value || finished.value || completionSummary.value != null) {
            return
        }
        finishing.value = true
        viewModelScope.launch {
            val result = try {
                sessionRepository.finish(sessionId, skipRemaining)
            } catch (cancelled: CancellationException) {
                finishing.value = false
                throw cancelled
            } catch (_: Exception) {
                finishing.value = false
                message.value = ActiveWorkoutMessage.SaveFailed
                return@launch
            }
            when (result) {
                FinishWorkoutResult.Finished -> {
                    val aggregate = sessionRepository.getAggregate(sessionId)
                    val summary = aggregate?.let(WorkoutCompletionLogic::from)
                    if (summary == null) {
                        finishing.value = false
                        message.value = ActiveWorkoutMessage.SaveFailed
                    } else {
                        completionSummary.value = summary
                        pendingFinishCount.value = null
                        finished.value = true
                    }
                }
                FinishWorkoutResult.AlreadyTerminal,
                is FinishWorkoutResult.PendingRemaining,
                FinishWorkoutResult.NotFound -> {
                    finishing.value = false
                }
            }
        }
    }

    fun requestAbandon() {
        if (discarding.value) {
            return
        }
        confirmAbandon.value = true
    }

    fun dismissAbandon() {
        if (discarding.value) {
            return
        }
        confirmAbandon.value = false
    }

    fun confirmAbandon() {
        if (discarding.value) {
            return
        }
        discarding.value = true
        viewModelScope.launch {
            val result = try {
                withContext(NonCancellable) {
                    sessionRepository.abandon(sessionId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AbandonWorkoutResult.Failed
            }
            when (result) {
                AbandonWorkoutResult.Abandoned, AbandonWorkoutResult.NotFound -> {
                    confirmAbandon.value = false
                    abandoned.value = true
                }
                AbandonWorkoutResult.AlreadyTerminal -> {
                    confirmAbandon.value = false
                    discarding.value = false
                    finished.value = true
                }
                AbandonWorkoutResult.Failed -> {
                    discarding.value = false
                    message.value = ActiveWorkoutMessage.DiscardFailed
                }
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeFocusEvent() {
        focusEvent.value = null
    }

    fun toggleExercise(exerciseId: Long) {
        val current = expandedIds.value
        expandedIds.value = if (exerciseId in current) {
            current - exerciseId
        } else {
            current + exerciseId
        }
    }

    fun draftFor(set: SessionSet): ActualSetDraft {
        return uiState.value.drafts[set.id] ?: ActualSetLogic.draftFromSet(set)
    }

    private fun advanceAfterResolved(setId: Long) {
        val aggregate = uiState.value.aggregate ?: return
        val target = SessionFocusLogic.focusAfterResolving(aggregate.exercises, setId)
        if (target is WorkoutFocusTarget.Set) {
            val index = aggregate.exercises.indexOfFirst { it.exercise.id == target.exerciseId }
            if (index >= 0) {
                savedStateHandle[SELECTED_INDEX] = index
            }
            expandedIds.value = expandedIds.value + target.exerciseId
            focusedSetId.value = target.setId
        } else {
            focusedSetId.value = null
        }
        emitFocus(target)
    }

    private fun emitFocus(target: WorkoutFocusTarget) {
        focusGeneration += 1L
        focusEvent.value = WorkoutFocusEvent(focusGeneration, target)
    }

    private fun updateDraft(setId: Long, transform: (ActualSetDraft) -> ActualSetDraft) {
        val current = uiState.value.drafts[setId] ?: return
        dirtyIds.value = dirtyIds.value + setId
        drafts.value = uiState.value.drafts + (setId to transform(current))
        setErrors.value = setErrors.value - setId
    }

    private fun mergeDrafts(
        aggregate: WorkoutSessionAggregate,
        current: Map<Long, ActualSetDraft>,
        dirty: Set<Long>
    ): Map<Long, ActualSetDraft> {
        val result = current.toMutableMap()
        aggregate.exercises.forEach { item ->
            item.sets.forEach { set ->
                if (set.id !in dirty) {
                    result[set.id] = ActualSetLogic.draftFromSet(set)
                }
            }
        }
        return result
    }

    companion object {
        const val SESSION_ID = "sessionId"
        const val SELECTED_INDEX = "selectedIndex"
    }
}
