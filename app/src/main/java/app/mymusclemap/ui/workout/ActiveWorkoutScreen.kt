package app.mymusclemap.ui.workout

import app.mymusclemap.ui.exercises.starterExerciseLabel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.ActualSetLogic
import app.mymusclemap.domain.workout.CurrentSetHistoryLine
import app.mymusclemap.domain.workout.ExerciseHistoryLogic
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedLoadLogic
import app.mymusclemap.domain.workout.PlannedSetLogic
import app.mymusclemap.domain.workout.RestTimerLogic
import app.mymusclemap.domain.workout.WorkPhase
import app.mymusclemap.domain.workout.PlannedTargetDisplay
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.TemplateFieldError
import app.mymusclemap.domain.workout.WorkoutCompletionSummary
import app.mymusclemap.domain.workout.WorkoutFocusTarget
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.templates.labelRes
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import app.mymusclemap.ui.theme.WorkoutColors
internal const val WORKOUT_HEADER_KEY = "workout-header"
internal const val WORKOUT_FINISH_KEY = "workout-finish"
internal const val WORKOUT_ADD_EXTRA_EXERCISE = "workout-add-extra-exercise"
internal const val WORKOUT_READY_ADD_EXTRA = "workout-ready-add-extra"
internal const val WORKOUT_FINISH_CTA = "workout-finish-cta"
internal const val WORKOUT_FINISH_CTA_STRONG = "workout-finish-cta-strong"
internal const val SET_COMPLETE_ACTION = "complete-set"
internal const val SET_SKIP_ACTION = "skip-set"
internal const val SET_COMPLETE_PROGRESS = "complete-set-progress"
internal const val SET_COMPLETE_CIRCLE = "complete-set-circle"
internal const val SET_CURRENT_ACTIONS = "current-set-actions"
internal const val TIMED_SET_LOCKED = "timed-set-locked"
internal const val TIMED_SET_EXPLORE_PRO = "timed-set-explore-pro"
internal const val SET_REPS_MINUS = "set-reps-minus"
internal const val SET_REPS_PLUS = "set-reps-plus"
internal const val SET_REPS_MINUS_CIRCLE = "set-reps-minus-circle"
internal const val SET_REPS_PLUS_CIRCLE = "set-reps-plus-circle"
internal const val SET_HEADER_TITLE = "set-header-title"
internal const val SET_HEADER_COUNT = "set-header-count"
internal const val SET_HEADER_STATUS = "set-header-status"
private val SetActionCircleSize = 32.dp
internal const val WORKOUT_TOP_BAR = "workout-top-bar"
internal const val WORKOUT_OVERFLOW_ANCHOR = "workout-overflow-anchor"
internal const val WORKOUT_OVERFLOW_BUTTON = "workout-overflow-button"
internal const val WORKOUT_OVERFLOW_MENU = "workout-overflow-menu"

internal fun workoutExerciseKey(exerciseId: Long): String = "exercise-$exerciseId"

internal fun workoutListIndexFor(
    target: WorkoutFocusTarget,
    exerciseIds: List<Long>
): Int? {
    return when (target) {
        is WorkoutFocusTarget.Set -> {
            val index = exerciseIds.indexOf(target.exerciseId)
            if (index < 0) null else index + 1
        }
        WorkoutFocusTarget.Finish -> exerciseIds.size + 1
    }
}

internal fun workoutScrollKey(target: WorkoutFocusTarget): String {
    return when (target) {
        is WorkoutFocusTarget.Set -> workoutExerciseKey(target.exerciseId)
        WorkoutFocusTarget.Finish -> WORKOUT_FINISH_KEY
    }
}

internal fun isWorkoutScrollReady(
    target: WorkoutFocusTarget,
    currentSetId: Long?,
    expandedExerciseIds: Set<Long>
): Boolean {
    return when (target) {
        is WorkoutFocusTarget.Set -> {
            currentSetId == target.setId && target.exerciseId in expandedExerciseIds
        }
        WorkoutFocusTarget.Finish -> currentSetId == null
    }
}

internal fun shouldApplyScrollEvent(eventGeneration: Long, latestGeneration: Long?): Boolean {
    return latestGeneration != null && latestGeneration == eventGeneration
}

internal fun currentSetTopInViewport(
    listCoordinates: LayoutCoordinates?,
    setCoordinates: LayoutCoordinates?
): Float {
    if (listCoordinates == null || setCoordinates == null) return 0f
    if (!listCoordinates.isAttached || !setCoordinates.isAttached) return 0f
    return listCoordinates.localPositionOf(setCoordinates, Offset.Zero).y
}

internal fun workoutTargetIsVisible(
    target: WorkoutFocusTarget,
    visibleKeys: Collection<Any>
): Boolean {
    return workoutScrollKey(target) in visibleKeys
}

/**
 * Scroll delta that places a newly current set around the vertical center of the
 * workout list viewport. The viewport already ends above the persistent finish bar.
 * A set taller than the viewport is pinned to the top so its editable controls stay
 * visible. A small tolerance avoids nudging a set that is already comfortable.
 * The lazy list clamps the result when the set is near either end of the content.
 */
internal fun currentSetScrollDeltaPx(
    setTopInViewport: Float,
    setHeight: Float,
    viewportHeight: Float
): Float {
    if (viewportHeight <= 0f || setHeight <= 0f) return 0f
    val desiredTop = if (setHeight >= viewportHeight) {
        0f
    } else {
        (viewportHeight - setHeight) / 2f
    }
    val delta = setTopInViewport - desiredTop
    val tolerance = viewportHeight * 0.1f
    if (kotlin.math.abs(delta) <= tolerance) return 0f
    return delta
}

@Composable
fun ActiveWorkoutScreen(
    state: ActiveWorkoutUiState,
    onBack: () -> Unit,
    onReps: (Long, String) -> Unit,
    onStepReps: (Long, Int) -> Unit,
    onLoadKind: (Long, PlannedLoadKind) -> Unit,
    onWeight: (Long, String) -> Unit,
    onMinutes: (Long, String) -> Unit,
    onSeconds: (Long, String) -> Unit,
    onDistance: (Long, String) -> Unit,
    onDistanceUnit: (Long, DistanceUnit) -> Unit,
    onComplete: (Long) -> Unit,
    onEditSet: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onUndoSkip: (Long) -> Unit,
    onAddExtra: (Long) -> Unit,
    onAddExtraExercise: (Long) -> Unit,
    onRemoveExtra: (Long) -> Unit,
    onRemoveExtraExercise: (Long) -> Unit,
    onToggleExercise: (Long) -> Unit,
    onRequestFinish: () -> Unit,
    onDismissFinish: () -> Unit,
    onConfirmFinish: (Boolean) -> Unit,
    onRequestAbandon: () -> Unit,
    onDismissAbandon: () -> Unit,
    onConfirmAbandon: () -> Unit,
    onFinished: (WorkoutCompletionSummary) -> Unit,
    onAbandoned: () -> Unit,
    onMessageConsumed: () -> Unit,
    onFocusConsumed: () -> Unit,
    onAddRest: () -> Unit = {},
    onSkipRest: () -> Unit = {},
    onContinueAfterRest: () -> Unit = {},
    onStartWork: (Long) -> Unit = {},
    onPauseWork: () -> Unit = {},
    onResumeWork: () -> Unit = {},
    onFinishWorkEarly: () -> Unit = {},
    onConfirmTimedSet: () -> Unit = {},
    onConfirmCancelWork: () -> Unit = {},
    onDismissCancelWork: () -> Unit = {},
    onExploreProBenefits: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var pendingSkipId by remember { mutableStateOf<Long?>(null) }
    var pendingRemoveExerciseId by remember { mutableStateOf<Long?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentExerciseId, state.currentSetId) {
        historyOpen = false
    }
    val dismissInput = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val completionSummary = state.completionSummary
    val readyToOpenCompletion = state.finished &&
        completionSummary != null &&
        state.pendingFinishCount == null
    var openedCompletion by remember { mutableStateOf(false) }
    // The finish dialog is removed in the same turn the session becomes completed.
    // Swallow back until that dialog is gone and this destination has been replaced,
    // so its dismissal cannot pop the completion screen.
    BackHandler(
        enabled = state.discarding || (state.finished && completionSummary != null)
    ) { }
    LaunchedEffect(readyToOpenCompletion, completionSummary) {
        if (!readyToOpenCompletion || completionSummary == null || openedCompletion) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        openedCompletion = true
        onFinished(completionSummary)
    }
    LaunchedEffect(state.abandoned) {
        if (state.abandoned) onAbandoned()
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(message.labelRes()))
        onMessageConsumed()
    }
    val aggregate = state.aggregate
    val exercises = aggregate?.exercises.orEmpty()
    val exerciseIds = remember(exercises) { exercises.map { it.exercise.id } }
    val focusGeneration = state.focusEvent?.generation
    val listCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentSetCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(
        focusGeneration,
        state.currentSetId,
        state.expandedExerciseIds,
        exerciseIds
    ) {
        val event = state.focusEvent ?: return@LaunchedEffect
        val generation = event.generation
        if (!shouldApplyScrollEvent(generation, focusGeneration)) {
            return@LaunchedEffect
        }
        if (!isWorkoutScrollReady(event.target, state.currentSetId, state.expandedExerciseIds)) {
            return@LaunchedEffect
        }
        val index = workoutListIndexFor(event.target, exerciseIds) ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (!shouldApplyScrollEvent(generation, focusGeneration)) {
            return@LaunchedEffect
        }
        val visibleKeys = listState.layoutInfo.visibleItemsInfo.map { it.key }
        if (!workoutTargetIsVisible(event.target, visibleKeys)) {
            listState.animateScrollToItem(index)
        }
        withFrameNanos { }
        withFrameNanos { }
        if (!shouldApplyScrollEvent(generation, focusGeneration)) {
            return@LaunchedEffect
        }
        if (event.target is WorkoutFocusTarget.Set) {
            var delta = 0f
            for (attempt in 0 until 3) {
                val setCoordinates = currentSetCoordinates.value
                val viewport = listCoordinates.value
                val setHeight = setCoordinates?.size?.height?.toFloat() ?: 0f
                val viewportHeight = viewport?.size?.height?.toFloat() ?: 0f
                if (setHeight > 0f && viewportHeight > 0f) {
                    delta = currentSetScrollDeltaPx(
                        setTopInViewport = currentSetTopInViewport(viewport, setCoordinates),
                        setHeight = setHeight,
                        viewportHeight = viewportHeight
                    )
                    break
                }
                if (attempt < 2) withFrameNanos { }
            }
            if (delta != 0f && shouldApplyScrollEvent(generation, focusGeneration)) {
                listState.animateScrollBy(delta)
            }
        }
        if (shouldApplyScrollEvent(generation, focusGeneration)) {
            onFocusConsumed()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val allResolved = state.progress.pending == 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = AppDimens.screenPadding, vertical = 8.dp)
                    .testTag(WORKOUT_FINISH_CTA)
            ) {
                val finishModifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .then(
                        if (allResolved) Modifier.testTag(WORKOUT_FINISH_CTA_STRONG) else Modifier
                    )
                if (allResolved) {
                    Button(
                        onClick = onRequestFinish,
                        enabled = !state.discarding && !state.finishing,
                        shape = AppShapeTokens.button,
                        colors = StrictBrand.actionButtonColors(),
                        modifier = finishModifier
                    ) {
                        Text(stringResource(R.string.action_finish_workout))
                    }
                } else {
                    OutlinedButton(
                        onClick = onRequestFinish,
                        enabled = !state.discarding && !state.finishing,
                        shape = AppShapeTokens.button,
                        modifier = finishModifier
                    ) {
                        Text(stringResource(R.string.action_finish_workout))
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .fillMaxSize()
        ) {
            ConsoleTopBar(
                title = aggregate?.session?.templateName ?: stringResource(R.string.active_workout_title),
                onBack = onBack,
                onRequestAbandon = onRequestAbandon,
                discarding = state.discarding
            )
            if (aggregate == null) {
                return@Column
            }
            WorkoutProgressHeader(state = state)
            state.rest?.let { rest ->
                RestTimerCard(
                    rest = rest,
                    onAddRest = onAddRest,
                    onSkipRest = onSkipRest,
                    onContinueAfterRest = onContinueAfterRest
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppDimens.screenPadding)
                    .onGloballyPositioned { listCoordinates.value = it }
            ) {
                item(key = WORKOUT_HEADER_KEY) {
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                }
                itemsIndexed(
                    items = exercises,
                    key = { _, item -> workoutExerciseKey(item.exercise.id) }
                ) { _, item ->
                    val expanded = item.exercise.id in state.expandedExerciseIds
                    ExerciseBlock(
                        item = item,
                        locked = item.exercise.id in state.lockedExerciseIds,
                        expanded = expanded,
                        state = state,
                        currentSetId = state.currentSetId,
                        currentSetCoordinates = currentSetCoordinates,
                        discarding = state.discarding,
                        onToggle = { onToggleExercise(item.exercise.id) },
                        onReps = onReps,
                        onStepReps = onStepReps,
                        onLoadKind = onLoadKind,
                        onWeight = onWeight,
                        onMinutes = onMinutes,
                        onSeconds = onSeconds,
                        onDistance = onDistance,
                        onDistanceUnit = onDistanceUnit,
                        onComplete = { setId ->
                            dismissInput()
                            onComplete(setId)
                        },
                        onEditSet = onEditSet,
                        onSkip = { setId ->
                            dismissInput()
                            if (setId in state.dirtySetIds) {
                                pendingSkipId = setId
                            } else {
                                onSkip(setId)
                            }
                        },
                        onUndoSkip = onUndoSkip,
                        onAddExtra = { onAddExtra(item.exercise.id) },
                        onRemoveExtra = onRemoveExtra,
                        onRemoveExercise = { pendingRemoveExerciseId = item.exercise.id },
                        onOpenHistory = { historyOpen = true },
                        onStartWork = onStartWork,
                        onPauseWork = onPauseWork,
                        onResumeWork = onResumeWork,
                    onFinishWorkEarly = onFinishWorkEarly,
                    onConfirmTimedSet = onConfirmTimedSet,
                    onExploreProBenefits = onExploreProBenefits
                )
                }
                item(key = WORKOUT_FINISH_KEY) {
                    val ready = state.progress.pending == 0
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WORKOUT_FINISH_KEY)
                            .padding(top = AppDimens.sectionGap, bottom = AppDimens.scrollEndPadding)
                    ) {
                        if (!ready) {
                            AddExtraExerciseAction(
                                label = stringResource(R.string.action_add_extra_exercise),
                                testTag = WORKOUT_ADD_EXTRA_EXERCISE,
                                enabled = !state.discarding,
                                onClick = { pickerOpen = true }
                            )
                            Spacer(Modifier.height(AppDimens.itemGap))
                        }
                        HorizontalDivider(
                            thickness = AppDimens.strokeThin,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(AppDimens.itemGap))
                        if (ready) {
                            Text(
                                text = stringResource(R.string.active_workout_ready_title),
                                style = AppTypeTokens.sectionTitle
                            )
                            Spacer(Modifier.height(AppDimens.statSecondaryGap))
                            Text(
                                text = stringResource(R.string.active_workout_ready_subtitle),
                                style = AppTypeTokens.statSecondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AddExtraExerciseAction(
                                label = stringResource(R.string.active_workout_ready_add_exercise),
                                testTag = WORKOUT_READY_ADD_EXTRA,
                                enabled = !state.discarding,
                                onClick = { pickerOpen = true }
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.action_finish_workout),
                                style = AppTypeTokens.sectionTitle
                            )
                            Spacer(Modifier.height(AppDimens.statSecondaryGap))
                            Text(
                                text = pluralStringResource(
                                    R.plurals.finish_pending_body,
                                    state.progress.pending,
                                    state.progress.pending
                                ),
                                style = AppTypeTokens.statSecondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    state.pendingFinishCount?.let { count ->
        if (count > 0) {
            AlertDialog(
                onDismissRequest = { if (!state.finishing) onDismissFinish() },
                title = { Text(stringResource(R.string.finish_pending_title)) },
                text = { Text(pluralStringResource(R.plurals.finish_pending_body, count, count)) },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirmFinish(true) },
                        enabled = !state.finishing
                    ) {
                        Text(stringResource(R.string.action_finish_skip_remaining))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissFinish,
                        enabled = !state.finishing
                    ) {
                        Text(stringResource(R.string.action_return_to_workout))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { if (!state.finishing) onDismissFinish() },
                title = { Text(stringResource(R.string.finish_confirm_title)) },
                text = { Text(stringResource(R.string.finish_confirm_body)) },
                confirmButton = {
                    TextButton(
                        onClick = { onConfirmFinish(false) },
                        enabled = !state.finishing
                    ) {
                        Text(stringResource(R.string.action_finish_workout))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismissFinish,
                        enabled = !state.finishing
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
    val historyExercise = exercises.firstOrNull { it.exercise.id == state.currentExerciseId }
    val historySet = historyExercise?.sets?.firstOrNull { it.id == state.currentSetId }
    val historyLine = if (historyExercise != null && historySet != null) {
        ExerciseHistoryLogic.currentLine(
            history = state.exerciseHistory,
            sessionExerciseId = historyExercise.exercise.id,
            setPosition = historySet.position
        )
    } else {
        CurrentSetHistoryLine.Hidden
    }
    val historySelection = when (val line = historyLine) {
        is CurrentSetHistoryLine.Recorded -> line.selection
        is CurrentSetHistoryLine.NoMatchingSet -> line.selection
        else -> null
    }
    if (historyOpen && historyExercise != null && historySet != null && historySelection != null) {
        ExerciseHistorySheet(
            exerciseName = starterExerciseLabel(historyExercise.exercise.name),
            selection = historySelection,
            comparisonPosition = historySet.position,
            onDismiss = { historyOpen = false }
        )
    }
    if (state.confirmCancelWork) {
        AlertDialog(
            onDismissRequest = onDismissCancelWork,
            title = { Text(stringResource(R.string.work_cancel_title)) },
            text = { Text(stringResource(R.string.work_cancel_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmCancelWork) {
                    Text(stringResource(R.string.work_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCancelWork) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (state.confirmAbandon) {
        AlertDialog(
            onDismissRequest = { if (!state.discarding) onDismissAbandon() },
            title = { Text(stringResource(R.string.abandon_title)) },
            text = { Text(stringResource(R.string.abandon_body)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmAbandon,
                    enabled = !state.discarding
                ) {
                    Text(
                        text = stringResource(R.string.action_abandon_workout),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissAbandon,
                    enabled = !state.discarding
                ) {
                    Text(stringResource(R.string.action_continue_workout))
                }
            }
        )
    }
    pendingRemoveExerciseId?.let { exerciseId ->
        AlertDialog(
            onDismissRequest = { pendingRemoveExerciseId = null },
            title = { Text(stringResource(R.string.remove_extra_exercise_title)) },
            text = { Text(stringResource(R.string.remove_extra_exercise_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveExerciseId = null
                        onRemoveExtraExercise(exerciseId)
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveExerciseId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (pickerOpen) {
        ExtraExercisePickerSheet(
            exercises = state.catalog,
            access = state.exerciseAccess,
            onDismiss = { pickerOpen = false },
            onSelect = { exercise ->
                pickerOpen = false
                onAddExtraExercise(exercise.id)
            }
        )
    }
    pendingSkipId?.let { setId ->
        AlertDialog(
            onDismissRequest = { pendingSkipId = null },
            title = { Text(stringResource(R.string.skip_dirty_title)) },
            text = { Text(stringResource(R.string.skip_dirty_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissInput()
                        pendingSkipId = null
                        onSkip(setId)
                    }
                ) {
                    Text(stringResource(R.string.action_skip_set))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSkipId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ConsoleTopBar(
    title: String,
    onBack: () -> Unit,
    onRequestAbandon: () -> Unit,
    discarding: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(WORKOUT_TOP_BAR),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = !discarding) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = title,
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        var menuOpen by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopEnd)
                .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
                .testTag(WORKOUT_OVERFLOW_ANCHOR),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = { menuOpen = true },
                enabled = !discarding,
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .testTag(WORKOUT_OVERFLOW_BUTTON)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more_workout)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.testTag(WORKOUT_OVERFLOW_MENU)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.action_abandon_workout),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRequestAbandon()
                    },
                    enabled = !discarding
                )
            }
        }
    }
}

@Composable
private fun WorkoutProgressHeader(state: ActiveWorkoutUiState) {
    val total = state.progress.completed + state.progress.skipped + state.progress.pending
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
            .padding(bottom = AppDimens.itemGap)
    ) {
        Text(
            text = state.elapsedLabel,
            style = AppTypeTokens.statHero,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.active_console_sets, state.progress.completed, total),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state.progress.skipped > 0) {
                Text(
                    text = stringResource(R.string.active_console_skipped, state.progress.skipped),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(AppDimens.headerStackGap))
        ThinProgressBar(fraction = state.progress.fraction)
    }
}

@Composable
private fun WorkTimerSection(
    work: WorkTimerUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinishEarly: () -> Unit
) {
    val accent = StrictBrand.actionContainer()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("work_timer")
    ) {
        Text(
            text = stringResource(
                when {
                    work.finishedEarly -> R.string.work_stopped_early
                    work.awaiting -> R.string.work_time_up
                    work.phase == WorkPhase.PAUSED -> R.string.work_paused
                    else -> R.string.work_status
                }
            ),
            style = AppTypeTokens.statCaption,
            color = accent
        )
        Text(
            text = work.countdown,
            style = AppTypeTokens.statHero,
            color = accent
        )
        if (work.awaiting) {
            Text(
                text = stringResource(R.string.work_completed_duration),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(AppDimens.headerStackGap))
        LinearProgressIndicator(
            progress = { work.progress },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = accent.copy(alpha = 0.24f)
        )
        if (!work.awaiting) {
        Spacer(Modifier.height(AppDimens.itemGap))
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap)) {
            when {
                work.phase == WorkPhase.PAUSED -> {
                    TimerOutlineButton(
                        label = stringResource(R.string.work_resume),
                        accent = accent,
                        testTag = "work_resume",
                        onClick = onResume
                    )
                    TimerOutlineButton(
                        label = stringResource(R.string.work_finish_early),
                        accent = accent,
                        testTag = "work_finish_early",
                        onClick = onFinishEarly
                    )
                }
                else -> {
                    TimerOutlineButton(
                        label = stringResource(R.string.work_pause),
                        accent = accent,
                        testTag = "work_pause",
                        onClick = onPause
                    )
                    TimerOutlineButton(
                        label = stringResource(R.string.work_finish_early),
                        accent = accent,
                        testTag = "work_finish_early",
                        onClick = onFinishEarly
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun TimerOutlineButton(
    label: String,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = true,
        shape = AppShapeTokens.chip,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = accent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = accent.copy(alpha = 0.38f)
        ),
        border = BorderStroke(AppDimens.strokeThin, accent),
        modifier = modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(testTag)
    ) {
        Text(label)
    }
}

@Composable
private fun RestTimerCard(
    rest: RestTimerUi,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onContinueAfterRest: () -> Unit
) {
    val accent = StrictBrand.restAccent()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
            .padding(bottom = AppDimens.itemGap)
            .testTag("rest_timer")
    ) {
        Text(
            text = stringResource(
                if (rest.awaitingNext) R.string.rest_complete else R.string.rest_status
            ),
            style = AppTypeTokens.statCaption,
            color = accent
        )
        if (!rest.awaitingNext) {
            Text(
                text = rest.countdown,
                style = AppTypeTokens.statHero,
                color = accent
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            LinearProgressIndicator(
                progress = { rest.progress },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = accent.copy(alpha = 0.24f)
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap)) {
                TimerOutlineButton(
                    label = stringResource(R.string.rest_add_ten),
                    accent = accent,
                    testTag = "rest_add_ten",
                    modifier = Modifier.weight(1f),
                    onClick = onAddRest
                )
                TimerOutlineButton(
                    label = stringResource(R.string.rest_skip),
                    accent = accent,
                    testTag = "rest_skip",
                    modifier = Modifier.weight(1f),
                    onClick = onSkipRest
                )
            }
        } else {
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = onContinueAfterRest,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag("rest_next"),
                shape = AppShapeTokens.chip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.background
                )
            ) {
                Text(stringResource(R.string.rest_next_set))
            }
        }
        if (rest.nextExerciseName.isNotEmpty()) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = stringResource(
                    R.string.rest_next,
                    rest.nextExerciseName,
                    rest.nextSetNumber,
                    rest.nextSetCount
                ),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ThinProgressBar(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(AppShapeTokens.compact)
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun ExerciseBlock(
    item: SessionExerciseItem,
    locked: Boolean = false,
    expanded: Boolean,
    state: ActiveWorkoutUiState,
    currentSetId: Long?,
    currentSetCoordinates: MutableState<LayoutCoordinates?>,
    discarding: Boolean,
    onToggle: () -> Unit,
    onReps: (Long, String) -> Unit,
    onStepReps: (Long, Int) -> Unit,
    onLoadKind: (Long, PlannedLoadKind) -> Unit,
    onWeight: (Long, String) -> Unit,
    onMinutes: (Long, String) -> Unit,
    onSeconds: (Long, String) -> Unit,
    onDistance: (Long, String) -> Unit,
    onDistanceUnit: (Long, DistanceUnit) -> Unit,
    onComplete: (Long) -> Unit,
    onEditSet: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onUndoSkip: (Long) -> Unit,
    onAddExtra: () -> Unit,
    onRemoveExtra: (Long) -> Unit,
    onRemoveExercise: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartWork: (Long) -> Unit = {},
    onPauseWork: () -> Unit = {},
    onResumeWork: () -> Unit = {},
    onFinishWorkEarly: () -> Unit = {},
    onConfirmTimedSet: () -> Unit = {},
    onExploreProBenefits: () -> Unit = {}
) {
    val done = item.sets.isNotEmpty() && item.sets.none { it.status == SessionSetStatus.PENDING }
    val completedCount = item.sets.count { it.status == SessionSetStatus.COMPLETED }
    val toggleLabel = stringResource(
        if (expanded) R.string.action_collapse_exercise else R.string.action_expand_exercise
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.headerStackGap)
            .testTag(workoutExerciseKey(item.exercise.id))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .clickable(onClick = onToggle)
                .semantics {
                    role = Role.Button
                    contentDescription = toggleLabel
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = starterExerciseLabel(item.exercise.name),
                        style = AppTypeTokens.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.exercise.addedDuringWorkout) {
                        Text(
                            text = stringResource(R.string.active_workout_extra_badge),
                            style = AppTypeTokens.statCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (locked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.free_locked_title),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (done && !locked) {
                        Text(
                            text = stringResource(R.string.set_status_completed_label),
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.active_console_sets,
                        completedCount,
                        item.sets.size
                    ),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val load = shortLoadInfo(item, stringResource(R.string.set_copy_bodyweight))
                if (load.isNotBlank()) {
                    Text(
                        text = load,
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded && locked) {
            Spacer(Modifier.height(AppDimens.itemGap))
            app.mymusclemap.ui.entitlement.FreeAccessLockNote(onExplorePro = onExploreProBenefits)
        }
        if (expanded && !locked) {
            Spacer(Modifier.height(AppDimens.itemGap))
            item.sets.forEachIndexed { index, set ->
                SetRow(
                    exerciseName = starterExerciseLabel(item.exercise.name),
                    item = item,
                    set = set,
                    draft = state.drafts[set.id] ?: ActualSetLogic.draftFromSet(set),
                    errors = state.setErrors[set.id].orEmpty(),
                    dirty = set.id in state.dirtySetIds,
                    completing = discarding || set.id in state.completingSetIds,
                    current = set.id == currentSetId,
                    editing = set.id in state.editingSetIds,
                    currentSetCoordinates = currentSetCoordinates,
                    onReps = { onReps(set.id, it) },
                    onStepReps = { onStepReps(set.id, it) },
                    onLoadKind = { onLoadKind(set.id, it) },
                    onWeight = { onWeight(set.id, it) },
                    onMinutes = { onMinutes(set.id, it) },
                    onSeconds = { onSeconds(set.id, it) },
                    onDistance = { onDistance(set.id, it) },
                    onDistanceUnit = { onDistanceUnit(set.id, it) },
                    onComplete = { onComplete(set.id) },
                    onSkip = { onSkip(set.id) },
                    onUndoSkip = { onUndoSkip(set.id) },
                    onRemoveExtra = { onRemoveExtra(set.id) },
                    onEdit = { onEditSet(set.id) },
                    historyLine = if (set.id == currentSetId) {
                        ExerciseHistoryLogic.currentLine(
                            history = state.exerciseHistory,
                            sessionExerciseId = item.exercise.id,
                            setPosition = set.position
                        )
                    } else {
                        CurrentSetHistoryLine.Hidden
                    },
                    onOpenHistory = onOpenHistory,
                    work = state.work,
                    preparation = state.preparation?.takeIf { it.setId == set.id },
                    timersEnabled = state.timersEnabled,
                    onStartWork = { onStartWork(set.id) },
                    onPauseWork = onPauseWork,
                    onResumeWork = onResumeWork,
                    onFinishWorkEarly = onFinishWorkEarly,
                    onConfirmTimedSet = onConfirmTimedSet,
                    onExploreProBenefits = onExploreProBenefits
                )
                if (index != item.sets.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = AppDimens.itemGap),
                        thickness = AppDimens.strokeThin,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                } else {
                    Spacer(Modifier.height(AppDimens.itemGap))
                }
            }
            TextButton(
                onClick = onAddExtra,
                modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
            ) {
                Text(stringResource(R.string.action_add_extra_set))
            }
            if (item.exercise.addedDuringWorkout) {
                TextButton(
                    onClick = onRemoveExercise,
                    enabled = !discarding,
                    modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(
                        stringResource(R.string.action_remove_extra_exercise),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = AppDimens.headerStackGap),
            thickness = AppDimens.strokeThin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun SetRow(
    exerciseName: String,
    item: SessionExerciseItem,
    set: SessionSet,
    draft: ActualSetDraft,
    errors: List<TemplateFieldError>,
    dirty: Boolean,
    completing: Boolean,
    current: Boolean,
    editing: Boolean,
    currentSetCoordinates: MutableState<LayoutCoordinates?>,
    onReps: (String) -> Unit,
    onStepReps: (Int) -> Unit,
    onLoadKind: (PlannedLoadKind) -> Unit,
    onWeight: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    onDistance: (String) -> Unit,
    onDistanceUnit: (DistanceUnit) -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onUndoSkip: () -> Unit,
    onRemoveExtra: () -> Unit,
    onEdit: () -> Unit,
    historyLine: CurrentSetHistoryLine,
    onOpenHistory: () -> Unit,
    work: WorkTimerUi? = null,
    preparation: WorkPreparationUi? = null,
    timersEnabled: Boolean = true,
    onStartWork: () -> Unit = {},
    onPauseWork: () -> Unit = {},
    onResumeWork: () -> Unit = {},
    onFinishWorkEarly: () -> Unit = {},
    onConfirmTimedSet: () -> Unit = {},
    onExploreProBenefits: () -> Unit = {}
) {
    val planned = buildPlannedLabel(set, stringResource(R.string.set_copy_bodyweight))
    val lockedTimed = set.timedWork &&
        set.status == SessionSetStatus.PENDING &&
        !timersEnabled
    val currentBadge = stringResource(R.string.active_exercise_badge)
    val statusLabel = when {
        current -> currentBadge
        set.status == SessionSetStatus.COMPLETED -> stringResource(R.string.set_status_completed)
        set.status == SessionSetStatus.SKIPPED -> stringResource(R.string.set_status_skipped)
        else -> ""
    }
    val actualLabel = draft.repsText.ifBlank { planned }
    val description = stringResource(
        R.string.set_row_description,
        exerciseName,
        set.position + 1,
        planned,
        actualLabel,
        statusLabel.ifBlank { stringResource(R.string.set_status_pending) }
    )
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentGreen = WorkoutColors.accent(MaterialTheme.colorScheme.background.luminance())
    val currentEmphasis = if (dark) StrictBrand.lime else currentGreen
    val glow = if (dark) Color(0x2281C784) else Color(0x182E7D32)
    val showFields = set.status != SessionSetStatus.SKIPPED &&
        (set.status == SessionSetStatus.PENDING || editing)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("set-${set.id}")
            .then(
                if (current) {
                    Modifier
                        .then(
                            if (lockedTimed) {
                                Modifier
                            } else {
                                Modifier.shadow(
                                    elevation = 2.dp,
                                    shape = AppShapeTokens.compact,
                                    clip = false,
                                    ambientColor = glow,
                                    spotColor = glow
                                )
                            }
                        )
                        .border(
                            AppDimens.strokeThin,
                            when {
                                lockedTimed -> MaterialTheme.colorScheme.outline
                                dark -> StrictBrand.lime
                                else -> StrictBrand.blue
                            },
                            AppShapeTokens.compact
                        )
                        .clip(AppShapeTokens.compact)
                        .then(
                            if (lockedTimed) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            } else {
                                Modifier
                            }
                        )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = description
                if (current) {
                    stateDescription = currentBadge
                }
            }
            .padding(horizontal = if (current) 10.dp else 0.dp, vertical = if (current) 8.dp else 4.dp)
            .then(
                if (current) {
                    Modifier.onGloballyPositioned { currentSetCoordinates.value = it }
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier.semantics { isTraversalGroup = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            if (current) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(AppShapeTokens.compact)
                        .background(currentEmphasis)
                )
            }
            Text(
                text = if (current) {
                    starterExerciseLabel(item.exercise.name)
                } else {
                    stringResource(
                        R.string.field_set_label_with_exercise,
                        set.position + 1,
                        starterExerciseLabel(item.exercise.name)
                    )
                },
                style = AppTypeTokens.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag(SET_HEADER_TITLE)
            )
            if (current) {
                Text(
                    text = stringResource(
                        R.string.active_console_sets,
                        set.position + 1,
                        item.sets.size
                    ),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.testTag(SET_HEADER_COUNT)
                )
            }
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .testTag(SET_HEADER_STATUS)
            ) {
                when {
                    current -> {
                        Text(
                            text = currentBadge,
                            style = AppTypeTokens.statSecondary,
                            color = currentEmphasis,
                            maxLines = 1
                        )
                    }
                    set.status == SessionSetStatus.COMPLETED -> {
                        CompletedSetIndicator(accent = StrictBrand.lime)
                    }
                    set.status == SessionSetStatus.SKIPPED -> {
                        SkippedSetIndicator()
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.active_set_planned, planned),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (current) Modifier.testTag(SET_PLAN_LINE) else Modifier
        )
        if (current && historyLine != CurrentSetHistoryLine.Hidden) {
            LastTimeRow(line = historyLine, onOpen = onOpenHistory)
        }
        if (set.status == SessionSetStatus.COMPLETED && !editing) {
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = actualSummary(set, draft),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!lockedTimed && work != null && work.setId == set.id) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            WorkTimerSection(
                work = work,
                onPause = onPauseWork,
                onResume = onResumeWork,
                onFinishEarly = onFinishWorkEarly
            )
        }
        if (showFields && !lockedTimed) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            ActualFields(
                item = item,
                draft = draft,
                errors = errors,
                steppersEnabled = !completing,
                timedWork = set.timedWork,
                onReps = onReps,
                onStepReps = onStepReps,
                onLoadKind = onLoadKind,
                onWeight = onWeight,
                onMinutes = onMinutes,
                onSeconds = onSeconds,
                onDistance = onDistance,
                onDistanceUnit = onDistanceUnit
            )
        }
        if (!lockedTimed && work != null && work.setId == set.id && work.awaiting) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Button(
                onClick = onConfirmTimedSet,
                enabled = !completing,
                shape = AppShapeTokens.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StrictBrand.lime,
                    contentColor = StrictBrand.onAction()
                ),
                modifier = Modifier.testTag("work_complete")
            ) {
                Text(stringResource(R.string.work_complete_set))
            }
        }
        }
        Spacer(Modifier.height(AppDimens.headerStackGap))
        when (set.status) {
            SessionSetStatus.PENDING -> {
                val timerOnThisSet = !lockedTimed && work?.setId == set.id
                val preparing = preparation
                if (!lockedTimed && preparing != null) {
                    Text(
                        text = stringResource(R.string.work_preparation),
                        style = AppTypeTokens.statCaption,
                        color = StrictBrand.actionContainer()
                    )
                    Text(
                        text = preparing.countdown,
                        style = AppTypeTokens.statHero,
                        color = StrictBrand.actionContainer(),
                        modifier = Modifier.testTag("work_preparation")
                    )
                }
                if (current && lockedTimed) {
                    LockedTimedSetActions(
                        completing = completing,
                        onSkip = onSkip,
                        onExplorePro = onExploreProBenefits
                    )
                    if (ActualSetLogic.canRemove(set)) {
                        TextButton(
                            onClick = onRemoveExtra,
                            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                        ) {
                            Text(
                                stringResource(R.string.action_remove_extra_set),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else if (current && !timerOnThisSet && preparing == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SET_CURRENT_ACTIONS),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkipSetAction(
                            enabled = !completing,
                            traversalIndex = 5f,
                            onClick = onSkip
                        )
                        if (set.timedWork && timersEnabled) {
                            Button(
                                onClick = onStartWork,
                                enabled = !completing,
                                shape = AppShapeTokens.button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StrictBrand.lime,
                                    contentColor = StrictBrand.onAction()
                                ),
                                modifier = Modifier.testTag("work_start")
                            ) {
                                Text(stringResource(R.string.work_start))
                            }
                        } else if (!set.timedWork) {
                            CompleteSetAction(
                                completing = completing,
                                accent = StrictBrand.lime,
                                traversalIndex = 6f,
                                onClick = onComplete
                            )
                        }
                    }
                    if (ActualSetLogic.canRemove(set)) {
                        TextButton(
                            onClick = onRemoveExtra,
                            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                        ) {
                            Text(
                                stringResource(R.string.action_remove_extra_set),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            SessionSetStatus.COMPLETED -> {
                if (editing) {
                    if (dirty) {
                        TextButton(
                            onClick = onComplete,
                            enabled = !completing,
                            modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                        ) {
                            Text(stringResource(R.string.action_update_set))
                        }
                    }
                } else {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Text(stringResource(R.string.action_edit_set))
                    }
                }
            }
            SessionSetStatus.SKIPPED -> {
                TextButton(
                    onClick = onUndoSkip,
                    modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                ) {
                    Text(stringResource(R.string.action_undo_skip))
                }
            }
        }
    }
}

@Composable
private fun AddExtraExerciseAction(
    label: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = if (enabled) StrictBrand.lime else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = if (enabled) StrictBrand.lime else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
private fun RepsStepButton(
    symbol: String,
    enabled: Boolean,
    contentDescription: String,
    testTag: String,
    circleTag: String,
    traversalIndex: Float,
    onClick: () -> Unit
) {
    val stroke = if (enabled) {
        StrictBrand.blue
    } else {
        MaterialTheme.colorScheme.outline
    }
    val glyph = if (enabled) {
        StrictBrand.blue
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val fill = if (enabled) {
        StrictBrand.blue.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                this.traversalIndex = traversalIndex
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(SetActionCircleSize)
                .background(fill, CircleShape)
                .border(AppDimens.strokeThin, stroke, CircleShape)
                .testTag(circleTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = AppTypeTokens.statValue,
                color = glyph
            )
        }
    }
}

@Composable
private fun CompleteSetAction(
    completing: Boolean,
    accent: Color,
    traversalIndex: Float,
    onClick: () -> Unit
) {
    val label = stringResource(R.string.action_complete_set_a11y)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
            .testTag(SET_COMPLETE_ACTION)
            .clickable(enabled = !completing, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                this.traversalIndex = traversalIndex
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(SetActionCircleSize)
                .background(StrictBrand.dark, CircleShape)
                .border(AppDimens.strokeThin, accent, CircleShape)
                .testTag(SET_COMPLETE_CIRCLE),
            contentAlignment = Alignment.Center
        ) {
            if (completing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .testTag(SET_COMPLETE_PROGRESS),
                    strokeWidth = 2.dp,
                    color = accent
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LockedTimedSetActions(
    completing: Boolean,
    onSkip: () -> Unit,
    onExplorePro: () -> Unit
) {
    Text(
        text = stringResource(R.string.work_requires_pro),
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(TIMED_SET_LOCKED)
    )
    Spacer(Modifier.height(AppDimens.headerStackGap))
    OutlinedButton(
        onClick = onExplorePro,
        enabled = !completing,
        shape = AppShapeTokens.button,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = StrictBrand.lime,
            disabledContentColor = StrictBrand.light.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            AppDimens.strokeThin,
            if (completing) StrictBrand.light.copy(alpha = 0.35f) else StrictBrand.lime
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(TIMED_SET_EXPLORE_PRO)
    ) {
        Text(
            text = stringResource(R.string.timed_set_explore_pro),
            color = if (completing) StrictBrand.light.copy(alpha = 0.45f) else StrictBrand.lime
        )
    }
    Spacer(Modifier.height(AppDimens.itemGap))
    LockedSkipAction(enabled = !completing, onClick = onSkip)
}

@Composable
private fun LockedSkipAction(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(R.string.action_skip_set_a11y)
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(SET_SKIP_ACTION)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SkipSetAction(
    enabled: Boolean,
    traversalIndex: Float,
    onClick: () -> Unit
) {
    val label = stringResource(R.string.action_skip_set_a11y)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
            .testTag(SET_SKIP_ACTION)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                this.traversalIndex = traversalIndex
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CompletedSetIndicator(accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(StrictBrand.dark, CircleShape)
                .border(AppDimens.strokeThin, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.set_status_completed),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SkippedSetIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.set_status_skipped),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActualFields(
    item: SessionExerciseItem,
    draft: ActualSetDraft,
    errors: List<TemplateFieldError>,
    steppersEnabled: Boolean,
    timedWork: Boolean,
    onReps: (String) -> Unit,
    onStepReps: (Int) -> Unit,
    onLoadKind: (PlannedLoadKind) -> Unit,
    onWeight: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit,
    onDistance: (String) -> Unit,
    onDistanceUnit: (DistanceUnit) -> Unit
) {
    val measurement = item.exercise.measurementType
    val resistance = item.exercise.resistanceBasis
    if (PlannedSetLogic.requiresReps(measurement) && !timedWork) {
        val parsedReps = draft.repsText.trim().toIntOrNull()
        val minusEnabled = steppersEnabled &&
            parsedReps != null &&
            parsedReps > ActualSetLogic.MIN_COMPLETED_REPS
        val plusEnabled = steppersEnabled &&
            (draft.repsText.isBlank() || (parsedReps != null && parsedReps < QuantityParser.MAX_REPS))
        Row(verticalAlignment = Alignment.Top) {
            ConsoleNumericField(
                value = draft.repsText,
                onValueChange = onReps,
                label = stringResource(R.string.field_actual_reps),
                modifier = Modifier.weight(1f),
                isError = errors.any { it.name.startsWith("Reps") },
                traversalIndex = 0f,
                valueInSemantics = true
            )
            Spacer(Modifier.width(AppDimens.itemGap))
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
            ) {
                RepsStepButton(
                    symbol = "−",
                    enabled = minusEnabled,
                    contentDescription = stringResource(R.string.action_decrease_reps_a11y),
                    testTag = SET_REPS_MINUS,
                    circleTag = SET_REPS_MINUS_CIRCLE,
                    traversalIndex = 1f,
                    onClick = { onStepReps(-1) }
                )
                RepsStepButton(
                    symbol = "+",
                    enabled = plusEnabled,
                    contentDescription = stringResource(R.string.action_increase_reps_a11y),
                    testTag = SET_REPS_PLUS,
                    circleTag = SET_REPS_PLUS_CIRCLE,
                    traversalIndex = 2f,
                    onClick = { onStepReps(1) }
                )
            }
        }
        Spacer(Modifier.height(AppDimens.headerStackGap))
    }
    val kinds = PlannedLoadLogic.compatibleKinds(resistance, measurement).toList()
    if (kinds.size > 1) {
        Text(
            text = stringResource(R.string.field_load),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        CompactChoiceChips(
            labels = kinds.map { stringResource(it.labelRes()) },
            selectedIndex = kinds.indexOf(draft.loadKind).coerceAtLeast(0),
            onSelected = { onLoadKind(kinds[it]) },
            traversalIndex = 3f
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
    }
    if (PlannedLoadLogic.requiresPositiveWeight(draft.loadKind)) {
        val hint = when (item.exercise.weightInterpretation) {
            WeightInterpretation.PER_SIDE -> stringResource(R.string.weight_interpretation_hint_per_side)
            WeightInterpretation.TOTAL -> stringResource(R.string.weight_interpretation_hint_total)
            WeightInterpretation.NOT_APPLICABLE -> null
        }
        ConsoleNumericField(
            value = draft.weightText,
            onValueChange = onWeight,
            label = stringResource(R.string.field_weight_kg),
            unit = "kg",
            supportingText = hint,
            isError = errors.any { it.name.startsWith("Weight") },
            decimal = true,
            traversalIndex = 4f
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
    }
    if (PlannedSetLogic.requiresDuration(measurement)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap)) {
            ConsoleNumericField(
                value = draft.minutesText,
                onValueChange = onMinutes,
                label = stringResource(R.string.field_minutes),
                modifier = Modifier.weight(1f),
                unit = "p"
            )
            ConsoleNumericField(
                value = draft.secondsText,
                onValueChange = onSeconds,
                label = stringResource(R.string.field_seconds),
                modifier = Modifier.weight(1f),
                unit = "mp"
            )
        }
        Spacer(Modifier.height(AppDimens.headerStackGap))
    }
    if (PlannedSetLogic.requiresDistance(measurement)) {
        ConsoleNumericField(
            value = draft.distanceText,
            onValueChange = onDistance,
            label = stringResource(R.string.field_distance),
            decimal = true
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        SegmentedControl(
            options = listOf(
                stringResource(R.string.distance_unit_m),
                stringResource(R.string.distance_unit_km)
            ),
            selectedIndex = if (draft.distanceUnit == DistanceUnit.KILOMETERS) 1 else 0,
            onSelected = { onDistanceUnit(if (it == 1) DistanceUnit.KILOMETERS else DistanceUnit.METERS) },
            compact = true
        )
    }
    errors.forEach { error ->
        Text(
            text = stringResource(error.labelRes()),
            color = MaterialTheme.colorScheme.error,
            style = AppTypeTokens.statSecondary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactChoiceChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    traversalIndex: Float? = null
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics {
            if (traversalIndex != null) {
                this.traversalIndex = traversalIndex
            }
        }
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = AppDimens.minTouch, minWidth = AppDimens.minTouch)
                    .border(
                        width = AppDimens.strokeThin,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = AppShapeTokens.compact
                    )
                    .clip(AppShapeTokens.compact)
                    .clickable { onSelected(index) }
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = AppTypeTokens.statSecondary,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun shortLoadInfo(item: SessionExerciseItem, bodyweightLabel: String): String {
    val set = item.sets.firstOrNull { it.status == SessionSetStatus.PENDING } ?: item.sets.firstOrNull()
    return set?.let { buildPlannedLabel(it, bodyweightLabel) }.orEmpty().let { label ->
        if (label == "—") "" else label
    }
}

private fun buildPlannedLabel(set: SessionSet, bodyweightLabel: String): String {
    val parts = mutableListOf<String>()
    if (!set.timedWork) {
        PlannedTargetDisplay.reps(set.plannedMinReps, set.plannedMaxReps)?.let { parts += it }
    }
    val load = PlannedTargetDisplay.load(set.plannedLoadKind, set.plannedWeightKg)
    if (load.isNotBlank() && load != "BW") {
        parts += load
    } else if (load == "BW") {
        parts += bodyweightLabel
    }
    set.plannedDurationSeconds?.let {
        val (minutes, seconds) = QuantityParser.fromSeconds(it)
        parts += "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
    set.plannedDistanceMeters?.let { parts += "${QuantityParser.formatDisplay(it)} m" }
    if (parts.isEmpty()) {
        parts += "—"
    }
    return parts.joinToString(" · ")
}

private fun actualSummary(set: SessionSet, draft: ActualSetDraft): String {
    val parts = mutableListOf<String>()
    if (!set.timedWork && draft.repsText.isNotBlank()) {
        parts += draft.repsText
    }
    val load = PlannedTargetDisplay.load(draft.loadKind, set.actualWeightKg)
    if (load.isNotBlank()) {
        parts += load
    } else if (draft.weightText.isNotBlank()) {
        parts += "${draft.weightText} kg"
    }
    if (draft.minutesText.isNotBlank() || draft.secondsText.isNotBlank()) {
        val minutes = draft.minutesText.ifBlank { "0" }
        val seconds = draft.secondsText.padStart(2, '0').ifBlank { "00" }
        parts += "$minutes:$seconds"
    }
    if (draft.distanceText.isNotBlank()) {
        parts += draft.distanceText
    }
    if (parts.isEmpty()) {
        parts += "—"
    }
    return parts.joinToString(" · ")
}

private fun ActiveWorkoutMessage.labelRes(): Int {
    return when (this) {
        ActiveWorkoutMessage.SetUpdated -> R.string.message_template_updated
        ActiveWorkoutMessage.ExtraAdded -> R.string.message_extra_set_added
        ActiveWorkoutMessage.ExtraRemoved -> R.string.message_extra_set_removed
        ActiveWorkoutMessage.ExtraExerciseAdded -> R.string.message_extra_exercise_added
        ActiveWorkoutMessage.ExtraExerciseRemoved -> R.string.message_extra_exercise_removed
        ActiveWorkoutMessage.CannotRemoveOriginal -> R.string.message_cannot_remove_original
        ActiveWorkoutMessage.CannotRemovePlannedExercise -> R.string.message_cannot_remove_planned_exercise
        ActiveWorkoutMessage.SaveFailed -> R.string.message_set_save_failed
        ActiveWorkoutMessage.DiscardFailed -> R.string.message_workout_discard_failed
    }
}
