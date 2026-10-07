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
import app.mymusclemap.domain.workout.ExerciseHistoryGate
import app.mymusclemap.domain.workout.ExerciseHistorySelection
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.InWorkoutExerciseHistory
import app.mymusclemap.domain.workout.SessionExercise
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
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
    val completionSummary: WorkoutCompletionSummary? = null,
    val exerciseHistory: InWorkoutExerciseHistory = InWorkoutExerciseHistory.Loading
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
    private val clock: Clock = Clock.systemUTC(),
    private val loadExerciseHistory: suspend (WorkoutSessionAggregate, SessionExercise) -> ExerciseHistorySelection? =
        { aggregate, exercise -> sessionRepository.loadPreviousExerciseHistory(aggregate, exercise) }
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
    private val draftJobs = mutableMapOf<Long, Job>()
    private val exerciseHistory = MutableStateFlow<InWorkoutExerciseHistory>(InWorkoutExerciseHistory.Loading)
    private val historyGate = ExerciseHistoryGate()
    private var historyJob: Job? = null
    private var historyInitialized = false
    private var loadedHistoryExerciseId: Long? = null
    internal var historyLoadCount: Int = 0
        private set
    private val draftEpoch = ConcurrentHashMap<Long, Long>()
    private val locallyResolving = mutableSetOf<Long>()
    private var statusesInitialized = false
    private var knownStatuses = emptyMap<Long, SessionSetStatus>()

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

    private val workoutState: StateFlow<ActiveWorkoutUiState> = combine(
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

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(workoutState, exerciseHistory) { workout, history ->
        workout.copy(exerciseHistory = history)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ActiveWorkoutUiState()
    )

    init {
        viewModelScope.launch {
            sessionRepository.observeAggregate(sessionId).collect { aggregate ->
                if (aggregate == null) return@collect
                val exercise = SessionFocusLogic.currentPendingExercise(aggregate)?.exercise
                val key = exercise?.id
                if (historyInitialized && key == loadedHistoryExerciseId) return@collect
                historyInitialized = true
                loadedHistoryExerciseId = key
                val generation = historyGate.begin(key)
                exerciseHistory.value = InWorkoutExerciseHistory.Loading
                historyJob?.cancel()
                historyJob = viewModelScope.launch {
                    if (exercise == null) {
                        if (historyGate.accepts(generation, null)) {
                            exerciseHistory.value = InWorkoutExerciseHistory.None
                        }
                        return@launch
                    }
                    historyLoadCount += 1
                    val selection = withContext(Dispatchers.IO) {
                        loadExerciseHistory(aggregate, exercise)
                    }
                    if (historyGate.accepts(generation, exercise.id)) {
                        exerciseHistory.value = if (selection == null) {
                            InWorkoutExerciseHistory.None
                        } else {
                            InWorkoutExerciseHistory.Found(exercise.id, selection)
                        }
                    }
                }
            }
        }
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
        viewModelScope.launch {
            sessionRepository.observeAggregate(sessionId).collect { aggregate ->
                val summary = aggregate?.let(WorkoutCompletionLogic::from) ?: return@collect
                if (completionSummary.value == null && !discarding.value) {
                    completionSummary.value = summary
                    pendingFinishCount.value = null
                    finished.value = true
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.observeAggregate(sessionId).collect { aggregate ->
                if (aggregate != null) {
                    reconcileAuthoritativeSets(aggregate)
                }
            }
        }
    }

    /**
     * Writes dirty in-memory drafts immediately. Called when the workout screen stops, so a
     * lock-screen completion sees the values the user just typed instead of the debounced copy.
     */
    fun flushDirtyDrafts() {
        val snapshot = dirtyIds.value.mapNotNull { setId ->
            drafts.value[setId]?.let { draft -> setId to draft }
        }
        if (snapshot.isEmpty()) return
        snapshot.forEach { (setId, _) -> cancelDraftPersist(setId) }
        runBlocking(Dispatchers.IO + NonCancellable) {
            snapshot.forEach { (setId, draft) ->
                sessionRepository.saveSetDraft(setId, draft)
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
        cancelDraftPersist(setId)
        val snapshot = uiState.value
        val draft = snapshot.drafts[setId] ?: return
        val currentStatus = snapshot.aggregate
            ?.exercises
            ?.flatMap { it.sets }
            ?.firstOrNull { it.id == setId }
            ?.status
            ?: return
        if (currentStatus == SessionSetStatus.PENDING) {
            locallyResolving.add(setId)
        }
        completingIds.value = completingIds.value + setId
        viewModelScope.launch {
            try {
                when (val result = sessionRepository.completeSet(setId, draft)) {
                    SessionMutationResult.Updated -> {
                        dirtyIds.value = dirtyIds.value - setId
                        setErrors.value = setErrors.value - setId
                        if (currentStatus == SessionSetStatus.PENDING) {
                            advanceAfterResolved(setId)
                        } else {
                            locallyResolving.remove(setId)
                        }
                    }
                    is SessionMutationResult.Invalid -> {
                        locallyResolving.remove(setId)
                        setErrors.value = setErrors.value + (setId to result.errors)
                        scheduleDraftSave(setId)
                    }
                    SessionMutationResult.NotFound,
                    SessionMutationResult.NotActive,
                    SessionMutationResult.OriginalSetProtected -> {
                        locallyResolving.remove(setId)
                        message.value = ActiveWorkoutMessage.SaveFailed
                    }
                }
            } catch (cancelled: CancellationException) {
                locallyResolving.remove(setId)
                throw cancelled
            } catch (_: Exception) {
                locallyResolving.remove(setId)
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
        cancelDraftPersist(setId)
        locallyResolving.add(setId)
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
                        locallyResolving.remove(setId)
                        message.value = ActiveWorkoutMessage.SaveFailed
                    }
                    is SessionMutationResult.Invalid -> locallyResolving.remove(setId)
                }
            } catch (cancelled: CancellationException) {
                locallyResolving.remove(setId)
                throw cancelled
            } catch (_: Exception) {
                locallyResolving.remove(setId)
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
        cancelDraftPersist(setId)
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

    private fun reconcileAuthoritativeSets(aggregate: WorkoutSessionAggregate) {
        val current = aggregate.exercises
            .flatMap { it.sets }
            .associate { it.id to it.status }
        if (!statusesInitialized) {
            knownStatuses = current
            statusesInitialized = true
            locallyResolving.removeAll { id -> current[id] != SessionSetStatus.PENDING }
            return
        }
        if (aggregate.session.status != SessionStatus.IN_PROGRESS) {
            knownStatuses = current
            return
        }
        val newlyResolved = knownStatuses.filter { (id, status) ->
            status == SessionSetStatus.PENDING &&
                current[id] != null &&
                current[id] != SessionSetStatus.PENDING
        }
        knownStatuses = current
        if (newlyResolved.isEmpty()) return
        newlyResolved.keys.forEach { setId -> cancelDraftPersist(setId) }
        dirtyIds.value = dirtyIds.value - newlyResolved.keys
        setErrors.value = setErrors.value - newlyResolved.keys
        drafts.value = drafts.value - newlyResolved.keys
        val external = newlyResolved.keys.filter { it !in locallyResolving }
        locallyResolving.removeAll(newlyResolved.keys)
        if (external.isNotEmpty() && !discarding.value && !finished.value) {
            focusAuthoritative(aggregate)
        }
    }

    private fun focusAuthoritative(aggregate: WorkoutSessionAggregate) {
        val pending = SessionFocusLogic.currentPendingSet(aggregate)
        if (pending == null) {
            focusedSetId.value = null
            emitFocus(WorkoutFocusTarget.Finish)
            return
        }
        val index = aggregate.exercises.indexOfFirst { item ->
            item.sets.any { it.id == pending.id }
        }
        if (index >= 0) {
            savedStateHandle[SELECTED_INDEX] = index
            expandedIds.value = expandedIds.value + aggregate.exercises[index].exercise.id
        }
        focusedSetId.value = pending.id
        val exerciseId = aggregate.exercises.getOrNull(index)?.exercise?.id ?: return
        emitFocus(WorkoutFocusTarget.Set(pending.id, exerciseId))
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
        scheduleDraftSave(setId)
    }

    private fun scheduleDraftSave(setId: Long) {
        val epoch = (draftEpoch[setId] ?: 0L) + 1L
        draftEpoch[setId] = epoch
        draftJobs.remove(setId)?.cancel()
        draftJobs[setId] = viewModelScope.launch {
            delay(DRAFT_PERSIST_DELAY_MS)
            sessionRepository.saveSetDraft(setId) {
                if (draftEpoch[setId] != epoch) {
                    null
                } else {
                    drafts.value[setId]
                }
            }
            if (draftEpoch[setId] == epoch) {
                draftJobs.remove(setId)
            }
        }
    }

    private fun cancelDraftPersist(setId: Long) {
        draftEpoch[setId] = (draftEpoch[setId] ?: 0L) + 1L
        draftJobs.remove(setId)?.cancel()
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
        const val DRAFT_PERSIST_DELAY_MS = 400L
    }
}
