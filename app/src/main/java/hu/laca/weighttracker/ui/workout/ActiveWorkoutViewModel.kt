package hu.laca.weighttracker.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.domain.workout.AbandonWorkoutResult
import hu.laca.weighttracker.domain.workout.ActualSetDraft
import hu.laca.weighttracker.domain.workout.ActualSetLogic
import hu.laca.weighttracker.domain.workout.DistanceUnit
import hu.laca.weighttracker.domain.workout.ElapsedTime
import hu.laca.weighttracker.domain.workout.FinishWorkoutResult
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.SessionMutationResult
import hu.laca.weighttracker.domain.workout.SessionProgress
import hu.laca.weighttracker.domain.workout.SessionProgressLogic
import hu.laca.weighttracker.domain.workout.SessionSet
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.TemplateFieldError
import hu.laca.weighttracker.domain.workout.WorkoutSessionAggregate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock

data class ActiveWorkoutUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val aggregate: WorkoutSessionAggregate? = null,
    val selectedIndex: Int = 0,
    val drafts: Map<Long, ActualSetDraft> = emptyMap(),
    val setErrors: Map<Long, List<TemplateFieldError>> = emptyMap(),
    val nowMillis: Long = 0L,
    val pendingFinishCount: Int? = null,
    val confirmAbandon: Boolean = false,
    val finished: Boolean = false,
    val abandoned: Boolean = false,
    val message: ActiveWorkoutMessage? = null
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
}

class ActiveWorkoutViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: WorkoutSessionRepository,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>(SESSION_ID) ?: -1L
    private val selectedIndex = savedStateHandle.getStateFlow(SELECTED_INDEX, 0)
    private val drafts = MutableStateFlow<Map<Long, ActualSetDraft>>(emptyMap())
    private val dirtyIds = mutableSetOf<Long>()
    private val setErrors = MutableStateFlow<Map<Long, List<TemplateFieldError>>>(emptyMap())
    private val nowMillis = MutableStateFlow(clock.millis())
    private val pendingFinishCount = MutableStateFlow<Int?>(null)
    private val confirmAbandon = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)
    private val abandoned = MutableStateFlow(false)
    private val message = MutableStateFlow<ActiveWorkoutMessage?>(null)

    private data class Dialogs(
        val now: Long,
        val pendingFinish: Int?,
        val confirmAbandon: Boolean,
        val finished: Boolean,
        val abandoned: Boolean
    )

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionRepository.observeAggregate(sessionId),
        selectedIndex,
        drafts,
        combine(setErrors, message) { errors, currentMessage -> errors to currentMessage },
        combine(nowMillis, pendingFinishCount, confirmAbandon, finished, abandoned) {
                now, pending, abandon, done, left ->
            Dialogs(now, pending, abandon, done, left)
        }
    ) { aggregate, index, currentDrafts, errorState, dialogs ->
        if (aggregate == null) {
            return@combine ActiveWorkoutUiState(
                loading = false,
                missing = true,
                nowMillis = dialogs.now,
                finished = dialogs.finished,
                abandoned = dialogs.abandoned
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
            missing = notActive,
            aggregate = aggregate,
            selectedIndex = bounded,
            drafts = mergeDrafts(aggregate, currentDrafts),
            setErrors = errorState.first,
            nowMillis = dialogs.now,
            pendingFinishCount = dialogs.pendingFinish,
            confirmAbandon = dialogs.confirmAbandon,
            finished = dialogs.finished || aggregate.session.status == SessionStatus.COMPLETED,
            abandoned = dialogs.abandoned || aggregate.session.status == SessionStatus.ABANDONED,
            message = errorState.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ActiveWorkoutUiState()
    )

    init {
        viewModelScope.launch {
            while (isActive) {
                nowMillis.value = clock.millis()
                delay(1_000)
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

    fun onLoadKind(setId: Long, kind: PlannedLoadKind) {
        updateDraft(setId) { draft ->
            draft.copy(
                loadKind = kind,
                weightText = if (hu.laca.weighttracker.domain.workout.PlannedLoadLogic.requiresPositiveWeight(kind)) {
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
        val draft = uiState.value.drafts[setId] ?: return
        viewModelScope.launch {
            when (val result = sessionRepository.completeSet(setId, draft)) {
                SessionMutationResult.Updated -> {
                    dirtyIds.remove(setId)
                    setErrors.value = setErrors.value - setId
                    advanceAfterComplete(setId)
                }
                is SessionMutationResult.Invalid -> {
                    setErrors.value = setErrors.value + (setId to result.errors)
                }
                else -> Unit
            }
        }
    }

    fun skipSet(setId: Long) {
        viewModelScope.launch {
            if (sessionRepository.skipSet(setId) == SessionMutationResult.Updated) {
                dirtyIds.remove(setId)
                setErrors.value = setErrors.value - setId
                advanceAfterComplete(setId)
            }
        }
    }

    fun undoSkip(setId: Long) {
        viewModelScope.launch {
            sessionRepository.undoSkip(setId)
        }
    }

    fun addExtraSet() {
        val exercise = currentExercise() ?: return
        viewModelScope.launch {
            if (sessionRepository.addExtraSet(exercise.exercise.id) == SessionMutationResult.Updated) {
                message.value = ActiveWorkoutMessage.ExtraAdded
            }
        }
    }

    fun removeExtraSet(setId: Long) {
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
        val pending = uiState.value.progress.pending
        if (pending > 0) {
            pendingFinishCount.value = pending
        } else {
            pendingFinishCount.value = 0
        }
    }

    fun dismissFinish() {
        pendingFinishCount.value = null
    }

    fun confirmFinish(skipRemaining: Boolean) {
        viewModelScope.launch {
            when (sessionRepository.finish(sessionId, skipRemaining)) {
                FinishWorkoutResult.Finished, FinishWorkoutResult.AlreadyTerminal -> {
                    pendingFinishCount.value = null
                    finished.value = true
                }
                is FinishWorkoutResult.PendingRemaining -> Unit
                FinishWorkoutResult.NotFound -> Unit
            }
        }
    }

    fun requestAbandon() {
        confirmAbandon.value = true
    }

    fun dismissAbandon() {
        confirmAbandon.value = false
    }

    fun confirmAbandon() {
        viewModelScope.launch {
            when (sessionRepository.abandon(sessionId)) {
                AbandonWorkoutResult.Abandoned, AbandonWorkoutResult.AlreadyTerminal -> {
                    confirmAbandon.value = false
                    abandoned.value = true
                }
                AbandonWorkoutResult.NotFound -> Unit
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun draftFor(set: SessionSet): ActualSetDraft {
        return uiState.value.drafts[set.id] ?: ActualSetLogic.draftFromSet(set)
    }

    private fun currentExercise() = uiState.value.aggregate?.exercises?.getOrNull(uiState.value.selectedIndex)

    private fun updateDraft(setId: Long, transform: (ActualSetDraft) -> ActualSetDraft) {
        val current = uiState.value.drafts[setId] ?: return
        dirtyIds += setId
        drafts.value = uiState.value.drafts + (setId to transform(current))
        setErrors.value = setErrors.value - setId
    }

    private fun mergeDrafts(
        aggregate: WorkoutSessionAggregate,
        current: Map<Long, ActualSetDraft>
    ): Map<Long, ActualSetDraft> {
        val result = current.toMutableMap()
        aggregate.exercises.forEach { item ->
            item.sets.forEach { set ->
                if (set.id !in dirtyIds) {
                    result[set.id] = ActualSetLogic.draftFromSet(set)
                }
            }
        }
        return result
    }

    private fun advanceAfterComplete(setId: Long) {
        val aggregate = uiState.value.aggregate ?: return
        val current = currentExercise() ?: return
        val remainingHere = current.sets.any { set ->
            set.id != setId && set.status == SessionSetStatus.PENDING
        }
        if (!remainingHere) {
            savedStateHandle[SELECTED_INDEX] =
                ActualSetLogic.nextExerciseIndex(aggregate.exercises, uiState.value.selectedIndex)
        }
    }

    companion object {
        const val SESSION_ID = "sessionId"
        const val SELECTED_INDEX = "selectedIndex"
    }
}
