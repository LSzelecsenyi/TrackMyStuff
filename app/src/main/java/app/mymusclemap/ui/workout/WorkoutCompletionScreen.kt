package app.mymusclemap.ui.workout

import app.mymusclemap.ui.exercises.starterExerciseLabel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.HighlightMeasure
import app.mymusclemap.domain.achievements.PerformanceHighlight
import app.mymusclemap.domain.achievements.PerformanceRecordKind
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.journal.WorkoutSetCopy
import app.mymusclemap.domain.workout.PlanSetProgression
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.ui.achievements.BadgeArtworkResolver
import app.mymusclemap.ui.achievements.celebrationDetail
import app.mymusclemap.ui.achievements.celebrationName
import app.mymusclemap.ui.achievements.artworkAchievementId
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand

internal const val WORKOUT_COMPLETE_SCREEN = "workout-complete-screen"
internal const val WORKOUT_COMPLETE_TITLE = "workout-complete-title"
internal const val WORKOUT_COMPLETE_SUMMARY = "workout-complete-summary"
internal const val WORKOUT_COMPLETE_BACK = "workout-complete-back"
internal const val WORKOUT_COMPLETE_HEATMAP = "workout-complete-heatmap"
internal const val WORKOUT_COMPLETE_CELEBRATIONS = "workout-complete-celebrations"
internal const val WORKOUT_COMPLETE_LOADING = "workout-complete-loading"
internal const val WORKOUT_COMPLETE_HIGHLIGHTS = "workout-complete-highlights"
internal const val WORKOUT_COMPLETE_PROGRESS = "workout-complete-progress"
internal const val WORKOUT_COMPLETE_PLAN_CHECKBOX = "workout-complete-plan-checkbox"
internal const val WORKOUT_COMPLETE_PLAN_SHOW_ALL = "workout-complete-plan-show-all"
private const val PLAN_PROGRESS_PREVIEW_LIMIT = 3

@Composable
fun WorkoutCompletionScreen(
    state: WorkoutSummaryUiState,
    onLeave: (applyPlanUpdates: Boolean) -> Unit,
    playAnimation: Boolean = true,
    celebrations: List<PendingCelebration> = emptyList(),
    notice: PlanProgressionNotice? = null,
    applying: Boolean = false,
    approvalEpoch: Int = 0
) {
    var acceptLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var approved by remember(approvalEpoch) { mutableStateOf(false) }
    var showAllChanges by remember(approvalEpoch) { mutableStateOf(false) }
    val leave: (Boolean) -> Unit = { apply ->
        if (acceptLeave && !leaving && !applying) {
            if (!apply) {
                leaving = true
            }
            onLeave(apply)
        }
    }
    LaunchedEffect(Unit) {
        // A finish-dialog dismissal can arrive as Back on the first frames of this screen.
        withFrameNanos { }
        withFrameNanos { }
        acceptLeave = true
    }
    BackHandler {
        leave(false)
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WORKOUT_COMPLETE_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding, vertical = AppDimens.sectionGap)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WorkoutCompletionMark(
                        contentDescription = stringResource(R.string.workout_complete_mark_a11y),
                        playAnimation = playAnimation
                    )
                    Spacer(Modifier.height(AppDimens.sectionGap))
                    Text(
                        text = stringResource(R.string.workout_complete_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .testTag(WORKOUT_COMPLETE_TITLE)
                            .semantics { heading() }
                    )
                }
                when (state) {
                    WorkoutSummaryUiState.Loading -> {
                        Spacer(Modifier.height(AppDimens.sectionGap))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag(WORKOUT_COMPLETE_LOADING),
                            color = StrictBrand.result()
                        )
                    }
                    WorkoutSummaryUiState.Unavailable -> {
                        Spacer(Modifier.height(AppDimens.sectionGap))
                        Text(
                            text = stringResource(R.string.workout_summary_unavailable),
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(WORKOUT_COMPLETE_SUMMARY)
                        )
                    }
                    is WorkoutSummaryUiState.Ready -> {
                        ReadySummary(
                            state = state,
                            celebrations = celebrations,
                            notice = notice,
                            approved = approved,
                            onApprovedChange = { approved = it },
                            showAllChanges = showAllChanges,
                            onShowAllChanges = { showAllChanges = true }
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = { leave(approved) },
                enabled = !leaving && !applying,
                shape = AppShapeTokens.button,
                colors = StrictBrand.actionButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(WORKOUT_COMPLETE_BACK)
            ) {
                Text(stringResource(R.string.workout_complete_back))
            }
        }
    }
}

@Composable
private fun ReadySummary(
    state: WorkoutSummaryUiState.Ready,
    celebrations: List<PendingCelebration>,
    notice: PlanProgressionNotice?,
    approved: Boolean,
    onApprovedChange: (Boolean) -> Unit,
    showAllChanges: Boolean,
    onShowAllChanges: () -> Unit
) {
    val summary = state.summary
    summary.workoutName?.let { name ->
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout-complete-name")
        )
    }
    Spacer(Modifier.height(AppDimens.sectionGap))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WORKOUT_COMPLETE_SUMMARY),
        verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
    ) {
        MetricRow(
            stringResource(R.string.workout_summary_duration),
            summary.durationLabel,
            "workout-complete-duration"
        )
        MetricRow(
            stringResource(R.string.workout_summary_exercises),
            summary.exerciseCount.toString(),
            "workout-complete-exercise-count"
        )
        MetricRow(
            stringResource(R.string.workout_summary_sets),
            summary.completedSetCount.toString(),
            "workout-complete-set-count"
        )
        summary.volumeKg?.let { volume ->
            MetricRow(
                stringResource(R.string.workout_summary_volume),
                stringResource(R.string.set_copy_weight, QuantityParser.formatDisplay(volume)),
                "workout-complete-volume"
            )
        }
    }
    if (state.progression.updates.isNotEmpty()) {
        PlanProgressionSection(
            updates = state.progression.updates,
            approved = approved,
            onApprovedChange = onApprovedChange,
            showAllChanges = showAllChanges,
            onShowAllChanges = onShowAllChanges
        )
    }
    notice?.let { current ->
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = progressionNotice(current),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout-complete-plan-notice")
        )
    }
    if (summary.highlights.isNotEmpty()) {
        SectionTitle(stringResource(R.string.workout_summary_highlights))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WORKOUT_COMPLETE_HIGHLIGHTS),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
        ) {
            summary.highlights.forEach { highlight ->
                HighlightCard(highlight)
            }
        }
    }
    if (celebrations.isNotEmpty()) {
        SectionTitle(stringResource(R.string.workout_summary_achievements))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WORKOUT_COMPLETE_CELEBRATIONS),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
        ) {
            celebrations.forEach { celebration ->
                AchievementRow(celebration)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(AppDimens.sectionGap))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
    )
    Spacer(Modifier.height(AppDimens.itemGap))
}

@Composable
private fun HighlightCard(highlight: PerformanceHighlight) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = highlightLabel(highlight.kind),
            style = MaterialTheme.typography.titleSmall,
            color = StrictBrand.result()
        )
        highlight.exerciseName?.let { name ->
            Text(
                text = starterExerciseLabel(name),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.workout_summary_previous, formatMeasure(highlight.previous, highlight.measure)),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.workout_summary_new, formatMeasure(highlight.current, highlight.measure)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun highlightLabel(kind: PerformanceRecordKind): String {
    return when (kind) {
        PerformanceRecordKind.WEIGHT -> stringResource(R.string.workout_summary_weight_pr)
        PerformanceRecordKind.REPS -> stringResource(R.string.workout_summary_rep_record)
        PerformanceRecordKind.VOLUME -> stringResource(R.string.workout_summary_volume_record)
    }
}

@Composable
private fun formatMeasure(value: Double, measure: HighlightMeasure): String {
    val number = if (measure == HighlightMeasure.REPS) {
        value.toInt().toString()
    } else {
        QuantityParser.formatDisplay(value)
    }
    return when (measure) {
        HighlightMeasure.ADDED_KG -> stringResource(R.string.set_copy_added_weight, number)
        HighlightMeasure.EXTERNAL_KG -> stringResource(R.string.set_copy_weight, number)
        HighlightMeasure.PER_SIDE_TOTAL_KG -> stringResource(R.string.set_copy_weight_total, number)
        HighlightMeasure.REPS -> stringResource(R.string.set_copy_reps, number)
        HighlightMeasure.VOLUME_KG -> stringResource(R.string.set_copy_weight, number)
    }
}

@Composable
private fun AchievementRow(celebration: PendingCelebration) {
    val artworkId = celebration.artworkAchievementId()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (artworkId != null) {
            Image(
                painter = painterResource(BadgeArtworkResolver.drawableFor(artworkId.name)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(72.dp)
                    .testTag("workout-complete-badge-${artworkId.name}")
            )
            Spacer(Modifier.width(AppDimens.itemGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = celebrationName(celebration),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = celebrationDetail(celebration),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanProgressionSection(
    updates: List<PlanSetProgression>,
    approved: Boolean,
    onApprovedChange: (Boolean) -> Unit,
    showAllChanges: Boolean,
    onShowAllChanges: () -> Unit
) {
    val visible = if (showAllChanges) updates else updates.take(PLAN_PROGRESS_PREVIEW_LIMIT)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WORKOUT_COMPLETE_PROGRESS)
    ) {
        SectionTitle(stringResource(R.string.plan_progress_title))
        Text(
            text = stringResource(R.string.plan_progress_subtitle),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = if (updates.size == 1) {
                stringResource(R.string.plan_progress_improved_one)
            } else {
                stringResource(R.string.plan_progress_improved_many, updates.size)
            },
            style = MaterialTheme.typography.titleSmall,
            color = StrictBrand.lime
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        visible.forEach { update ->
            PlanChangeRow(update)
            Spacer(Modifier.height(AppDimens.itemGap))
        }
        if (!showAllChanges && updates.size > PLAN_PROGRESS_PREVIEW_LIMIT) {
            TextButton(
                onClick = onShowAllChanges,
                modifier = Modifier.testTag(WORKOUT_COMPLETE_PLAN_SHOW_ALL)
            ) {
                Text(
                    text = stringResource(R.string.plan_progress_show_all, updates.size),
                    color = StrictBrand.lime
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .toggleable(
                    value = approved,
                    role = Role.Checkbox,
                    onValueChange = onApprovedChange
                )
                .testTag(WORKOUT_COMPLETE_PLAN_CHECKBOX),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = approved,
                onCheckedChange = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.plan_progress_update_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.plan_progress_update_support),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = stringResource(R.string.plan_progress_update_detail),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanChangeRow(update: PlanSetProgression) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workout-complete-plan-set-${update.sourceTemplateSetId}")
    ) {
        Text(
            text = starterExerciseLabel(update.displayName),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.plan_progress_set, update.setNumber),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.plan_progress_change,
                progressionValue(update, before = true),
                progressionValue(update, before = false)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun progressionValue(update: PlanSetProgression, before: Boolean): String {
    val resources = LocalResources.current
    val reps = if (before) update.beforeReps else update.afterReps
    val weight = if (before) update.beforeWeightKg else update.afterWeightKg
    val showReps = update.repsChanged || !update.weightChanged
    val showWeight = update.weightChanged || !update.repsChanged
    if (update.repsChanged && update.weightChanged) {
        return WorkoutSetCopy.formatValue(
            resources = resources,
            repsText = reps.toString(),
            loadKind = update.loadKind,
            weightKg = weight,
            durationSeconds = null,
            distanceMeters = null,
            interpretation = update.weightInterpretation
        )
    }
    if (showReps && update.repsChanged) {
        return stringResource(R.string.set_copy_reps, reps.toString())
    }
    return weightLabel(update.loadKind, weight, update.weightInterpretation)
}

@Composable
private fun weightLabel(
    kind: PlannedLoadKind,
    weightKg: Double?,
    interpretation: WeightInterpretation
): String {
    val resources = LocalResources.current
    return WorkoutSetCopy.formatValue(
        resources = resources,
        repsText = null,
        loadKind = kind,
        weightKg = weightKg,
        durationSeconds = null,
        distanceMeters = null,
        interpretation = interpretation
    )
}

@Composable
private fun progressionNotice(notice: PlanProgressionNotice): String {
    return when (notice) {
        is PlanProgressionNotice.Partial -> stringResource(
            R.string.plan_progress_partial,
            notice.updated,
            notice.skipped
        )
        PlanProgressionNotice.Unavailable -> stringResource(R.string.plan_progress_unavailable)
        PlanProgressionNotice.Failed -> stringResource(R.string.plan_progress_failed)
    }
}
