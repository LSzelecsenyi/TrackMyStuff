package app.mymusclemap.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.journal.WorkoutSetDisplay
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.components.UserMessageEffect
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    state: WorkoutDetailUiState,
    onBack: () -> Unit,
    onOpenActive: (Long) -> Unit,
    onRequestDeleteWorkout: () -> Unit,
    onDismissDeleteWorkout: () -> Unit,
    onConfirmDeleteWorkout: () -> Unit,
    onDeleted: () -> Unit,
    onMessageConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    BackHandler(enabled = state.deleting) { }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    LaunchedEffect(state.activeSessionId) {
        val id = state.activeSessionId ?: return@LaunchedEffect
        onOpenActive(id)
    }
    val session = state.aggregate?.session
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(session?.templateName ?: stringResource(R.string.workout_detail_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !state.deleting,
                        modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (session != null) {
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = { menuOpen = true },
                                enabled = !state.deleting,
                                modifier = Modifier.defaultMinSize(minHeight = AppDimens.minTouch)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_workout)
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.action_delete_workout),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onRequestDeleteWorkout()
                                    },
                                    enabled = !state.deleting,
                                    modifier = Modifier.testTag(WORKOUT_DELETE_ACTION)
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            state.missing -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(AppDimens.screenPadding)
                ) {
                    Text(
                        text = stringResource(R.string.workout_detail_missing),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            state.aggregate != null && session != null -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppDimens.screenPadding)
                        .padding(bottom = AppDimens.scrollEndPadding)
                ) {
                    Text(
                        text = UiFormatters.longDate(session.workoutDate),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            if (session.status == SessionStatus.ABANDONED) {
                                R.string.workout_status_abandoned
                            } else {
                                R.string.workout_status_completed
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.journal_started, formatClock(session.startedAt)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    session.finishedAt?.let {
                        Text(
                            text = stringResource(R.string.workout_detail_finished, formatClock(it)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    session.abandonedAt?.let {
                        Text(
                            text = stringResource(R.string.workout_detail_abandoned_at, formatClock(it)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.journal_duration, ElapsedTime.formatSession(session)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.workout_detail_counts,
                            state.aggregate.exercises.size,
                            state.progress.completed,
                            state.progress.skipped,
                            state.progress.total
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    BodyWeightSection(session)
                    session.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(Modifier.height(8.dp))
                        Text(notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    state.aggregate.exercises.forEach { item ->
                        ExerciseDetailSection(
                            item = item,
                            displays = state.setDisplays
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    if (state.confirmDelete && session != null) {
        DeleteWorkoutDialog(
            name = session.templateName,
            dateLabel = UiFormatters.longDate(session.workoutDate),
            deleting = state.deleting,
            onDismiss = onDismissDeleteWorkout,
            onConfirm = onConfirmDeleteWorkout
        )
    }
}

@Composable
private fun BodyWeightSection(session: WorkoutSession) {
    val kilograms = session.bodyWeightKg ?: return
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.workout_detail_body_weight, UiFormatters.weightKg(kilograms)),
        style = MaterialTheme.typography.bodyMedium
    )
    val source = when (session.bodyWeightSource) {
        BodyWeightSource.MEASURED_SAME_DAY -> stringResource(R.string.workout_detail_source_same_day)
        BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT -> stringResource(
            R.string.workout_detail_source_previous,
            session.bodyWeightSourceDate?.let(::formatMonthDay).orEmpty()
        )
        BodyWeightSource.MANUAL -> stringResource(R.string.workout_detail_source_manual)
        BodyWeightSource.UNKNOWN -> null
    }
    source?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExerciseDetailSection(
    item: SessionExerciseItem,
    displays: Map<Long, WorkoutSetDisplay>
) {
    Text(
        text = stringResource(R.string.workout_detail_exercise_position, item.exercise.position + 1),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(item.exercise.name, style = MaterialTheme.typography.titleLarge)
    Text(
        text = stringResource(item.exercise.primaryMuscle.labelRes()),
        style = MaterialTheme.typography.bodyMedium
    )
    if (item.exercise.secondaryMuscles.isNotEmpty()) {
        Text(
            text = item.exercise.secondaryMuscles.map { stringResource(it.labelRes()) }.joinToString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
    item.sets.forEach { set ->
        val display = displays[set.id]
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.field_set_label, set.position + 1),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(
                        when (set.status) {
                            SessionSetStatus.SKIPPED -> R.string.set_status_skipped_label
                            SessionSetStatus.COMPLETED -> R.string.set_status_completed_label
                            SessionSetStatus.PENDING -> R.string.set_status_pending
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
                if (display != null && display.valuesDiffer) {
                    Text(stringResource(R.string.workout_detail_planned, display.planned))
                    Text(stringResource(R.string.workout_detail_performed, display.performed))
                } else if (display != null) {
                    val value = display.performed.ifBlank { display.planned }
                    if (value.isNotBlank()) {
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (display.planned.isNotBlank() && display.performed.isBlank() && set.status == SessionSetStatus.SKIPPED) {
                        Text(
                            text = stringResource(R.string.workout_detail_planned, display.planned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (set.addedDuringWorkout) {
                    Text(
                        text = stringResource(R.string.workout_detail_extra),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatClock(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("H:mm"))
}

private fun formatMonthDay(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("MMMM d", AppLocale.UI))
}
