package app.mymusclemap.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.workout.AbandonWorkoutResult
import app.mymusclemap.domain.workout.AddExtraExerciseResult
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
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.workout.RestClockSample
import app.mymusclemap.data.preferences.TimerSoundStore
import app.mymusclemap.domain.workout.TimerAudioMode
import app.mymusclemap.domain.workout.TimerAudioPhase
import app.mymusclemap.domain.workout.TimerCueKind
import app.mymusclemap.domain.workout.TimerIntervalId
import app.mymusclemap.domain.workout.TimerIntervalSlot
import app.mymusclemap.domain.workout.TimerSpokenWord
import app.mymusclemap.domain.workout.WorkCompletionCue
import app.mymusclemap.domain.workout.WorkPhase
import app.mymusclemap.domain.workout.WorkPreparationLogic
import app.mymusclemap.domain.workout.WorkPreparationSnapshot
import app.mymusclemap.domain.workout.WorkTimerLogic
import app.mymusclemap.domain.workout.WorkoutTimerAccess
import app.mymusclemap.domain.workout.toWorkSnapshot
import app.mymusclemap.domain.workout.RestExpiryResult
import app.mymusclemap.domain.workout.RestPhase
import app.mymusclemap.domain.workout.RestRemaining
import app.mymusclemap.domain.workout.RestTimerLogic
import app.mymusclemap.domain.workout.restNextSet
import app.mymusclemap.domain.workout.toRestSnapshot
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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

data class RestTimerUi(
    val phase: RestPhase,
    val countdown: String,
    val progress: Float,
    val nextExerciseName: String,
    val nextSetNumber: Int,
    val nextSetCount: Int,
    val awaitingNext: Boolean
)

data class WorkTimerUi(
    val phase: WorkPhase,
    val setId: Long,
    val countdown: String,
    val progress: Float,
    val actualSeconds: Int,
    val targetSeconds: Int,
    val awaiting: Boolean,
    val finishedEarly: Boolean = false
)

data class WorkPreparationUi(
    val setId: Long,
    val countdown: String
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
    val editingSetIds: Set<Long> = emptySet(),
    val finishing: Boolean = false,
    val completionSummary: WorkoutCompletionSummary? = null,
    val exerciseHistory: InWorkoutExerciseHistory = InWorkoutExerciseHistory.Loading,
    val catalog: List<Exercise> = emptyList(),
    val rest: RestTimerUi? = null,
    val work: WorkTimerUi? = null,
    val timersEnabled: Boolean = true,
    val confirmCancelWork: Boolean = false,
    val preparation: WorkPreparationUi? = null
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
    data object ExtraExerciseAdded : ActiveWorkoutMessage
    data object ExtraExerciseRemoved : ActiveWorkoutMessage
    data object CannotRemoveOriginal : ActiveWorkoutMessage
    data object CannotRemovePlannedExercise : ActiveWorkoutMessage
    data object SaveFailed : ActiveWorkoutMessage
    data object DiscardFailed : ActiveWorkoutMessage
}

class ActiveWorkoutViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val sessionRepository: WorkoutSessionRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val observeCatalog: Flow<List<Exercise>> = flowOf(emptyList()),
    private val loadExerciseHistory: suspend (WorkoutSessionAggregate, SessionExercise) -> ExerciseHistorySelection? =
        { aggregate, exercise -> sessionRepository.loadPreviousExerciseHistory(aggregate, exercise) },
    private val restClock: () -> RestClockSample = {
        RestClockSample(
            elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime(),
            wallClockMillis = clock.millis()
        )
    },
    private val restEffects: RestTimerEffects = RestTimerEffects.None,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements,
    private val timerSounds: TimerSoundStore = TimerSoundStore.On,
    private val timerCuePlayer: TimerCuePlayer = TimerCuePlayer.None,
    private val completionLedger: TimerCompletionLedger = TimerCompletionLedger.process,
    private val speechLanguage: () -> app.mymusclemap.domain.workout.TimerSpeechLanguage = {
        currentTimerSpeechLanguage()
    }
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>(SESSION_ID) ?: -1L
    private val selectedIndex = savedStateHandle.getStateFlow(SELECTED_INDEX, 0)
    private val drafts = MutableStateFlow<Map<Long, ActualSetDraft>>(emptyMap())
    private val dirtyIds = MutableStateFlow<Set<Long>>(emptySet())
    private val completingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val setErrors = MutableStateFlow<Map<Long, List<TemplateFieldError>>>(emptyMap())
    private val nowMillis = MutableStateFlow(clock.millis())
    private val elapsedRealtime = MutableStateFlow(restClock().elapsedRealtimeMillis)
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
    private val editingSetIds = MutableStateFlow<Set<Long>>(emptySet())
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
    private var latestAggregate: WorkoutSessionAggregate? = null
    private var expiryJob: Job? = null
    private var expiryGeneration: Long? = null
    private var scheduledRest: Pair<Long, Long>? = null
    private var scheduledWork: Pair<Long, Long>? = null
    private val timerAccess = MutableStateFlow(WorkoutTimerAccess.allowed(entitlements))
    private val confirmCancelWork = MutableStateFlow(false)
    private var pendingExerciseIndex: Int? = null
    private var workExpiryJob: Job? = null
    private var workExpiryGeneration: Long? = null
    private val audioMode = MutableStateFlow(TimerAudioMode.BEEPS)
    private val preparation = MutableStateFlow<WorkPreparationSnapshot?>(null)
    private var preparationGeneration = 0L
    private val audioCoordinator = TimerAudioCoordinator(
        scope = viewModelScope,
        elapsedNow = { restClock().elapsedRealtimeMillis },
        player = timerCuePlayer,
        ledger = completionLedger,
        language = speechLanguage,
        onPreparationFinished = { setId ->
            if (preparation.value?.setId == setId) {
                preparation.value = null
                viewModelScope.launch { beginMeasuredWork(setId) }
            }
        }
    )

    private data class ClockSample(
        val wall: Long,
        val elapsed: Long,
        val timersEnabled: Boolean,
        val preparation: WorkPreparationSnapshot?
    )

    private data class DialogFlags(
        val abandoned: Boolean,
        val discarding: Boolean,
        val finishing: Boolean,
        val confirmCancelWork: Boolean
    )

    private data class Dialogs(
        val now: Long,
        val elapsedRealtime: Long,
        val pendingFinish: Int?,
        val confirmAbandon: Boolean,
        val finished: Boolean,
        val abandoned: Boolean,
        val discarding: Boolean,
        val finishing: Boolean,
        val confirmCancelWork: Boolean,
        val timersEnabled: Boolean,
        val preparation: WorkPreparationSnapshot?
    )

    private data class EditorSignals(
        val drafts: Map<Long, ActualSetDraft>,
        val dirty: Set<Long>,
        val completing: Set<Long>,
        val errors: Map<Long, List<TemplateFieldError>>,
        val message: ActiveWorkoutMessage?,
        val completionSummary: WorkoutCompletionSummary?,
        val editingSetIds: Set<Long>
    )

    private data class FocusChrome(
        val focus: WorkoutFocusEvent?,
        val focusedSetId: Long?,
        val expanded: Set<Long>
    )

    private val workoutState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionRepository.observeAggregate(sessionId),
        selectedIndex,
        combine(
            drafts,
            dirtyIds,
            completingIds,
            setErrors,
            combine(message, completionSummary, editingSetIds) { currentMessage, summary, editing ->
                Triple(currentMessage, summary, editing)
            }
        ) { currentDrafts, dirty, completing, errors, messageSummaryEditing ->
            EditorSignals(
                currentDrafts,
                dirty,
                completing,
                errors,
                messageSummaryEditing.first,
                messageSummaryEditing.second,
                messageSummaryEditing.third
            )
        },
        combine(
            combine(nowMillis, elapsedRealtime, timerAccess, preparation) { wall, elapsed, access, prep ->
                ClockSample(wall, elapsed, access, prep)
            },
            pendingFinishCount,
            confirmAbandon,
            finished,
            combine(abandoned, discarding, finishing, confirmCancelWork) { left, discardingNow, finishingNow, cancelWork ->
                DialogFlags(left, discardingNow, finishingNow, cancelWork)
            }
        ) { clock, pending, abandon, done, flags ->
            Dialogs(
                clock.wall,
                clock.elapsed,
                pending,
                abandon,
                done,
                flags.abandoned,
                flags.discarding,
                flags.finishing,
                flags.confirmCancelWork,
                clock.timersEnabled,
                clock.preparation
            )
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
                editingSetIds = signals.editingSetIds,
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
            currentSetId = if (aggregate.session.restPhase != null) {
                null
            } else {
                SessionFocusLogic.currentPendingSet(aggregate)?.id
            },
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
            editingSetIds = signals.editingSetIds,
            finishing = dialogs.finishing,
            completionSummary = signals.completionSummary,
            rest = restUi(aggregate, dialogs.elapsedRealtime, dialogs.now),
            work = workUi(aggregate, dialogs.elapsedRealtime, dialogs.now),
            timersEnabled = dialogs.timersEnabled,
            confirmCancelWork = dialogs.confirmCancelWork,
            preparation = preparationUi(dialogs.preparation, dialogs.elapsedRealtime)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ActiveWorkoutUiState()
    )

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        workoutState,
        exerciseHistory,
        observeCatalog
    ) { workout, history, catalog ->
        workout.copy(exerciseHistory = history, catalog = catalog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ActiveWorkoutUiState()
    )

    init {
        viewModelScope.launch {
            entitlements.changes().collect {
                val allowed = WorkoutTimerAccess.allowed(entitlements)
                timerAccess.value = allowed
                if (!allowed) {
                    val origin = sessionRepository.releaseProTimers(sessionId)
                    preparation.value = null
                    audioCoordinator.stop()
                    restEffects.cancel(sessionId)
                    restEffects.cancelWork(sessionId)
                    if (origin != null) advanceAfterResolved(origin)
                }
            }
        }
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
        viewModelScope.launch {
            while (isActive) {
                val sample = restClock()
                nowMillis.value = sample.wallClockMillis
                elapsedRealtime.value = sample.elapsedRealtimeMillis
                considerExpiry()
                considerWorkExpiry()
                syncTimerAudio()
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
                latestAggregate = aggregate
                syncRestAlarm(aggregate)
                syncWorkAlarm(aggregate)
                if (aggregate != null) {
                    considerExpiry()
                    considerWorkExpiry()
                    reconcileAuthoritativeSets(aggregate)
                }
                syncTimerAudio()
            }
        }
        viewModelScope.launch {
            timerSounds.migrate()
            timerSounds.mode.collect {
                audioMode.value = it
                syncTimerAudio()
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
        val aggregate = uiState.value.aggregate ?: return
        val max = aggregate.exercises.lastIndex
        val bounded = index.coerceIn(0, max)
        val workSet = aggregate.session.workSetId
        val phase = aggregate.session.workPhase
        if (phase != null && workSet != null) {
            val stays = aggregate.exercises.getOrNull(bounded)?.sets?.any { it.id == workSet } == true
            if (!stays) {
                pendingExerciseIndex = bounded
                confirmCancelWork.value = true
                return
            }
        }
        dismissRest()
        savedStateHandle[SELECTED_INDEX] = bounded
    }

    fun confirmCancelWorkNavigation() {
        val index = pendingExerciseIndex
        confirmCancelWork.value = false
        pendingExerciseIndex = null
        viewModelScope.launch {
            val generation = uiState.value.aggregate?.session?.workGeneration
            if (generation != null) {
                sessionRepository.discardWork(sessionId, generation)
                restEffects.cancelWork(sessionId)
            }
            if (index != null) {
                val max = uiState.value.aggregate?.exercises?.lastIndex ?: return@launch
                savedStateHandle[SELECTED_INDEX] = index.coerceIn(0, max)
            }
        }
    }

    fun dismissCancelWorkNavigation() {
        confirmCancelWork.value = false
        pendingExerciseIndex = null
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

    fun editCompletedSet(setId: Long) {
        if (discarding.value) {
            return
        }
        val set = uiState.value.aggregate
            ?.exercises
            ?.flatMap { it.sets }
            ?.firstOrNull { it.id == setId }
            ?: return
        if (set.status != SessionSetStatus.COMPLETED) {
            return
        }
        editingSetIds.value = editingSetIds.value + setId
    }

    fun completeSet(setId: Long) {
        if (discarding.value || setId in completingIds.value) {
            return
        }
        val snapshot = uiState.value
        val running = snapshot.aggregate?.session
        val pendingSet = snapshot.aggregate
            ?.exercises
            ?.flatMap { it.sets }
            ?.firstOrNull { it.id == setId }
        if (pendingSet?.status == SessionSetStatus.PENDING && pendingSet.timedWork) {
            val awaiting = running?.workSetId == setId &&
                running.workPhase == WorkPhase.AWAITING.name
            if (!awaiting) return
        }
        if (running?.workSetId == setId &&
            running.workPhase != null &&
            running.workPhase != WorkPhase.AWAITING.name
        ) {
            return
        }
        cancelDraftPersist(setId)
        val draft = drafts.value[setId] ?: snapshot.drafts[setId] ?: return
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
                            val resting = sessionRepository.getAggregate(sessionId)?.session
                            val started = resting?.restPhase == RestPhase.COUNTING &&
                                resting.restOriginSetId == setId
                            if (!started) {
                                advanceAfterResolved(setId)
                            }
                        } else {
                            locallyResolving.remove(setId)
                            if (currentStatus == SessionSetStatus.COMPLETED) {
                                editingSetIds.value = editingSetIds.value - setId
                            }
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
        if (preparation.value?.setId == setId) {
            preparation.value = null
            syncTimerAudio()
        }
        val running = uiState.value.aggregate?.session
        if (running?.workSetId == setId && running.workPhase != null) {
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

    fun addExtraExercise(exerciseId: Long) {
        if (discarding.value) {
            return
        }
        val keepCurrentSet = uiState.value.progress.pending > 0
        viewModelScope.launch {
            when (val result = sessionRepository.addExtraExercise(sessionId, exerciseId)) {
                is AddExtraExerciseResult.Added -> {
                    expandedIds.value = expandedIds.value + result.sessionExerciseId
                    message.value = ActiveWorkoutMessage.ExtraExerciseAdded
                    if (!keepCurrentSet) {
                        focusedSetId.value = result.setId
                        emitFocus(WorkoutFocusTarget.Set(result.setId, result.sessionExerciseId))
                    }
                }
                AddExtraExerciseResult.SessionNotFound,
                AddExtraExerciseResult.NotActive,
                AddExtraExerciseResult.ExerciseNotFound,
                AddExtraExerciseResult.ExerciseArchived -> message.value = ActiveWorkoutMessage.SaveFailed
            }
        }
    }

    fun removeExtraExercise(sessionExerciseId: Long) {
        if (discarding.value) {
            return
        }
        val removedSets = uiState.value.aggregate
            ?.exercises
            ?.firstOrNull { it.exercise.id == sessionExerciseId }
            ?.sets
            .orEmpty()
        val wasCurrent = uiState.value.currentExerciseId == sessionExerciseId
        removedSets.forEach { set -> cancelDraftPersist(set.id) }
        viewModelScope.launch {
            when (sessionRepository.removeExtraExercise(sessionExerciseId)) {
                SessionMutationResult.Updated -> {
                    val ids = removedSets.map { it.id }.toSet()
                    dirtyIds.value = dirtyIds.value - ids
                    setErrors.value = setErrors.value - ids
                    drafts.value = drafts.value - ids
                    editingSetIds.value = editingSetIds.value - ids
                    expandedIds.value = expandedIds.value - sessionExerciseId
                    message.value = ActiveWorkoutMessage.ExtraExerciseRemoved
                    if (wasCurrent) {
                        sessionRepository.getAggregate(sessionId)?.let { focusAuthoritative(it) }
                    }
                }
                SessionMutationResult.OriginalSetProtected ->
                    message.value = ActiveWorkoutMessage.CannotRemovePlannedExercise
                else -> message.value = ActiveWorkoutMessage.SaveFailed
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

    fun addRestTenSeconds() {
        val generation = uiState.value.aggregate?.session?.restGeneration ?: return
        viewModelScope.launch {
            sessionRepository.extendRest(sessionId, generation)
        }
    }

    fun skipRest() {
        val generation = uiState.value.aggregate?.session?.restGeneration ?: return
        viewModelScope.launch {
            val origin = sessionRepository.skipRest(sessionId, generation) ?: return@launch
            advanceAfterResolved(origin)
        }
    }

    fun continueAfterRest() {
        val generation = uiState.value.aggregate?.session?.restGeneration ?: return
        viewModelScope.launch {
            val origin = sessionRepository.acknowledgeRest(sessionId, generation) ?: return@launch
            advanceAfterResolved(origin)
        }
    }

    fun startWork(setId: Long) {
        if (!WorkoutTimerAccess.allowed(entitlements)) return
        if (preparation.value != null) return
        val session = uiState.value.aggregate?.session
        if (session?.workPhase != null || session?.restPhase != null) return
        viewModelScope.launch {
            if (audioMode.value == TimerAudioMode.VOICE &&
                timerCuePlayer.prepareVoice(VOICE_READY_WAIT_MILLIS) == VoiceReadiness.READY
            ) {
                val sample = restClock()
                preparationGeneration += 1L
                preparation.value = WorkPreparationLogic.started(
                    setId = setId,
                    elapsedRealtimeMillis = sample.elapsedRealtimeMillis,
                    generation = preparationGeneration
                )
                elapsedRealtime.value = sample.elapsedRealtimeMillis
                syncTimerAudio()
            } else {
                beginMeasuredWork(setId)
            }
        }
    }

    private suspend fun beginMeasuredWork(setId: Long) {
        val exerciseId = uiState.value.aggregate
            ?.exercises
            ?.firstOrNull { exercise -> exercise.sets.any { it.id == setId } }
            ?.exercise
            ?.id
        if (sessionRepository.startWork(sessionId, setId) && exerciseId != null) {
            emitFocus(WorkoutFocusTarget.Set(setId, exerciseId))
        }
    }

    fun pauseWork() {
        val generation = uiState.value.aggregate?.session?.workGeneration ?: return
        viewModelScope.launch { sessionRepository.pauseWork(sessionId, generation) }
    }

    fun resumeWork() {
        val generation = uiState.value.aggregate?.session?.workGeneration ?: return
        viewModelScope.launch { sessionRepository.resumeWork(sessionId, generation) }
    }

    fun finishWorkEarly() {
        val generation = uiState.value.aggregate?.session?.workGeneration ?: return
        viewModelScope.launch { sessionRepository.finishWorkEarly(sessionId, generation) }
    }

    fun confirmTimedSet() {
        val session = uiState.value.aggregate?.session ?: return
        if (session.workPhase != WorkPhase.AWAITING.name) return
        val setId = session.workSetId ?: return
        val seconds = WorkTimerLogic.actualSeconds(session.toWorkSnapshot(), restClock())
        val (minutes, remainder) = QuantityParser.fromSeconds(seconds)
        val current = drafts.value[setId] ?: uiState.value.drafts[setId] ?: return
        drafts.value = drafts.value + (setId to current.copy(
            minutesText = minutes.toString(),
            secondsText = remainder.toString()
        ))
        completeSet(setId)
    }

    private fun dismissRest() {
        val session = uiState.value.aggregate?.session ?: return
        if (session.restPhase == null) return
        val generation = session.restGeneration
        viewModelScope.launch {
            sessionRepository.clearRest(sessionId, generation)
        }
    }

    private fun syncRestAlarm(aggregate: WorkoutSessionAggregate?) {
        val session = aggregate?.session
        if (session?.restPhase == RestPhase.COUNTING) {
            val key = session.restGeneration to (session.restDeadlineWallClockMillis ?: 0L)
            if (scheduledRest != key) {
                scheduledRest = key
                val next = aggregate.restNextSet()
                restEffects.schedule(
                    sessionId = sessionId,
                    deadlineWallClockMillis = key.second,
                    generation = key.first,
                    nextLabel = next?.let { "${it.exerciseName} ${it.setNumber}/${it.setCount}" }.orEmpty()
                )
            }
        } else if (scheduledRest != null) {
            scheduledRest = null
            restEffects.cancel(sessionId)
        }
    }

    private fun syncWorkAlarm(aggregate: WorkoutSessionAggregate?) {
        val session = aggregate?.session
        if (session?.workPhase == WorkPhase.RUNNING.name) {
            val key = session.workGeneration to (session.workDeadlineWallClockMillis ?: 0L)
            if (scheduledWork != key) {
                scheduledWork = key
                restEffects.scheduleWork(
                    sessionId = sessionId,
                    deadlineWallClockMillis = key.second,
                    generation = key.first
                )
            }
        } else if (scheduledWork != null) {
            scheduledWork = null
            restEffects.cancelWork(sessionId)
        }
    }

    private fun considerWorkExpiry() {
        val session = latestAggregate?.session ?: return
        if (session.workPhase != WorkPhase.RUNNING.name) return
        val sample = restClock()
        val snapshot = session.toWorkSnapshot()
        if (sample.elapsedRealtimeMillis < (snapshot.anchorElapsedRealtimeMillis ?: 0L)) {
            val generation = session.workGeneration
            viewModelScope.launch { sessionRepository.reanchorWork(sessionId, generation) }
        }
        if (WorkTimerLogic.remaining(snapshot, sample) !is RestRemaining.Expired) return
        val generation = session.workGeneration
        if (workExpiryJob?.isActive == true && workExpiryGeneration == generation) return
        workExpiryGeneration = generation
        workExpiryJob = viewModelScope.launch {
            val expired = sessionRepository.expireWork(sessionId, generation)
            if (expired) restEffects.onRestFinished()
            if (!expired && workExpiryGeneration == generation) workExpiryGeneration = null
        }
    }

    private fun workUi(
        aggregate: WorkoutSessionAggregate,
        elapsedRealtimeMillis: Long,
        wallClockMillis: Long
    ): WorkTimerUi? {
        val phase = WorkPhase.fromStored(aggregate.session.workPhase) ?: return null
        val setId = aggregate.session.workSetId ?: return null
        val sample = RestClockSample(elapsedRealtimeMillis, wallClockMillis)
        val snapshot = aggregate.session.toWorkSnapshot()
        val remaining = WorkTimerLogic.remaining(snapshot, sample)
        val active = remaining as? RestRemaining.Active
        val targetMillis = snapshot.targetMillis ?: 0L
        val target = (targetMillis / 1000L).toInt()
        val elapsedMillis = snapshot.elapsedActiveMillis ?: 0L
        val countdownMillis = if (phase == WorkPhase.AWAITING) elapsedMillis else active?.remainingMillis ?: 0L
        return WorkTimerUi(
            phase = phase,
            setId = setId,
            countdown = RestTimerLogic.formatCountdown(countdownMillis),
            progress = if (phase == WorkPhase.AWAITING) {
                1f
            } else {
                RestTimerLogic.progress(remaining)
            },
            actualSeconds = WorkTimerLogic.actualSeconds(snapshot, sample),
            targetSeconds = target,
            awaiting = phase == WorkPhase.AWAITING,
            finishedEarly = phase == WorkPhase.AWAITING && elapsedMillis < targetMillis
        )
    }

    private fun considerExpiry() {
        val session = latestAggregate?.session ?: return
        if (session.restPhase != RestPhase.COUNTING) return
        val sample = restClock()
        if (RestTimerLogic.remaining(session.toRestSnapshot(), sample) !is RestRemaining.Expired) return
        val generation = session.restGeneration
        if (expiryJob?.isActive == true && expiryGeneration == generation) return
        expiryGeneration = generation
        expiryJob = viewModelScope.launch {
            when (val result = sessionRepository.expireRest(sessionId, generation)) {
                is RestExpiryResult.Advanced -> {
                    restEffects.onRestFinished()
                    advanceAfterResolved(result.originSetId)
                }
                RestExpiryResult.Waiting -> restEffects.onRestFinished()
                RestExpiryResult.Ignored -> {
                    if (expiryGeneration == generation) expiryGeneration = null
                }
            }
        }
    }

    private fun restUi(
        aggregate: WorkoutSessionAggregate,
        elapsedRealtimeMillis: Long,
        wallClockMillis: Long
    ): RestTimerUi? {
        val phase = aggregate.session.restPhase ?: return null
        val next = aggregate.restNextSet()
        val remaining = RestTimerLogic.remaining(
            aggregate.session.toRestSnapshot(),
            RestClockSample(elapsedRealtimeMillis, wallClockMillis)
        )
        val active = remaining as? RestRemaining.Active
        return RestTimerUi(
            phase = phase,
            countdown = RestTimerLogic.formatCountdown(active?.remainingMillis ?: 0L),
            progress = RestTimerLogic.progress(remaining),
            nextExerciseName = next?.exerciseName.orEmpty(),
            nextSetNumber = next?.setNumber ?: 0,
            nextSetCount = next?.setCount ?: 0,
            awaitingNext = phase == RestPhase.AWAITING_NEXT
        )
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

    private fun preparationUi(
        snapshot: WorkPreparationSnapshot?,
        elapsedRealtimeMillis: Long
    ): WorkPreparationUi? {
        snapshot ?: return null
        val remaining = snapshot.deadlineElapsed - elapsedRealtimeMillis
        val count = WorkPreparationLogic.displayCount(remaining)
        if (count <= 0) return null
        return WorkPreparationUi(setId = snapshot.setId, countdown = count.toString())
    }

    private fun syncTimerAudio() {
        val aggregate = latestAggregate
        if (aggregate != null && aggregate.session.status != SessionStatus.IN_PROGRESS) {
            preparation.value = null
            audioCoordinator.stop()
            return
        }
        audioCoordinator.sync(currentAudioSlot(), audioMode.value)
    }

    private fun currentAudioSlot(): TimerIntervalSlot? {
        val sample = restClock()
        val prep = preparation.value
        if (prep != null) {
            val remaining = prep.deadlineElapsed - sample.elapsedRealtimeMillis
            return TimerIntervalSlot(
                id = TimerIntervalId(TimerCueKind.PREPARATION, prep.generation),
                deadlineElapsed = prep.deadlineElapsed,
                phase = if (remaining <= 0L) TimerAudioPhase.FINISHED_NATURALLY else TimerAudioPhase.RUNNING,
                remainingMillis = remaining,
                durationMillis = WorkPreparationLogic.DURATION_MILLIS,
                completion = TimerSpokenWord.GO,
                sessionId = sessionId,
                ledgerGeneration = prep.generation,
                setId = prep.setId
            )
        }
        val aggregate = latestAggregate ?: return null
        val session = aggregate.session
        val workPhase = WorkPhase.fromStored(session.workPhase)
        if (workPhase != null && session.workSetId != null) {
            val snapshot = session.toWorkSnapshot()
            val remaining = WorkTimerLogic.remaining(snapshot, sample)
            val phase = when (workPhase) {
                WorkPhase.PAUSED -> TimerAudioPhase.PAUSED
                WorkPhase.RUNNING -> if (remaining is RestRemaining.Expired) {
                    TimerAudioPhase.FINISHED_NATURALLY
                } else {
                    TimerAudioPhase.RUNNING
                }
                WorkPhase.AWAITING -> if (snapshot.elapsedActiveMillis == snapshot.targetMillis) {
                    TimerAudioPhase.FINISHED_NATURALLY
                } else {
                    TimerAudioPhase.CANCELLED
                }
            }
            return TimerIntervalSlot(
                id = TimerIntervalId(TimerCueKind.WORK, session.workGeneration),
                deadlineElapsed = snapshot.deadlineElapsedRealtimeMillis,
                phase = phase,
                remainingMillis = (remaining as? RestRemaining.Active)?.remainingMillis,
                durationMillis = snapshot.targetMillis,
                completion = WorkCompletionCue.word(
                    aggregate.exercises,
                    session.workSetId,
                    WorkoutTimerAccess.allowed(entitlements)
                ),
                sessionId = sessionId,
                ledgerGeneration = session.workGeneration,
                setId = session.workSetId
            )
        }
        if (session.restPhase == RestPhase.COUNTING) {
            val snapshot = session.toRestSnapshot()
            val remaining = RestTimerLogic.remaining(snapshot, sample)
            return TimerIntervalSlot(
                id = TimerIntervalId(TimerCueKind.REST, session.restGeneration),
                deadlineElapsed = snapshot.deadlineElapsedRealtimeMillis,
                phase = if (remaining is RestRemaining.Expired) {
                    TimerAudioPhase.FINISHED_NATURALLY
                } else {
                    TimerAudioPhase.RUNNING
                },
                remainingMillis = (remaining as? RestRemaining.Active)?.remainingMillis,
                durationMillis = snapshot.durationMillis,
                completion = TimerSpokenWord.GO,
                sessionId = sessionId,
                ledgerGeneration = session.restGeneration,
                setId = snapshot.nextSetId
            )
        }
        return null
    }

    override fun onCleared() {
        audioCoordinator.release()
        super.onCleared()
    }

    companion object {
        const val SESSION_ID = "sessionId"
        const val SELECTED_INDEX = "selectedIndex"
        const val DRAFT_PERSIST_DELAY_MS = 400L
        private const val VOICE_READY_WAIT_MILLIS = 400L
    }
}
