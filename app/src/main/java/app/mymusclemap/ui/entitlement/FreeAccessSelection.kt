package app.mymusclemap.ui.entitlement

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.R
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FreeAccessRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.entitlement.FreeAccessIntro
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.freeAccessIntro
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val FREE_ACCESS_ROOT = "free-access-root"
internal const val FREE_ACCESS_BAR = "free-access-bar"
internal const val FREE_ACCESS_TITLE = "free-access-title"
internal const val FREE_ACCESS_INTRO = "free-access-intro"
internal const val FREE_ACCESS_LIST = "free-access-list"
internal const val FREE_ACCESS_PLANS_HEADER = "free-access-plans-header"
internal const val FREE_ACCESS_EXERCISES_HEADER = "free-access-exercises-header"
internal const val FREE_ACCESS_ACTIONS = "free-access-actions"
internal const val FREE_ACCESS_CONFIRM = "free-access-confirm"
internal const val FREE_ACCESS_EXPLORE = "free-access-explore-pro"

data class FreeAccessChoiceItem(
    val id: Long,
    val title: String,
    val summary: String,
    val primaryMuscle: MuscleGroup? = null
)

data class FreeAccessSelectionUiState(
    val plans: List<FreeAccessChoiceItem> = emptyList(),
    val exercises: List<FreeAccessChoiceItem> = emptyList(),
    val draftPlanIds: Set<Long> = emptySet(),
    val draftExerciseIds: Set<Long> = emptySet(),
    val planLimit: Int = FreeAccessLogic.PLAN_LIMIT,
    val exerciseLimit: Int = FreeAccessLogic.CUSTOM_EXERCISE_LIMIT,
    val plansRequireChoice: Boolean = false,
    val exercisesRequireChoice: Boolean = false,
    val confirmEnabled: Boolean = false,
    val confirmVisible: Boolean = false,
    val finished: Boolean = false,
    val intro: FreeAccessIntro = FreeAccessIntro.WithinFreeLimits
)

class FreeAccessSelectionViewModel(
    private val templates: WorkoutTemplateRepository,
    private val exercises: ExerciseRepository,
    private val access: FreeAccessRepository,
    private val priorProGrantEnded: () -> Boolean = { false },
    entitlementChanges: Flow<Int> = flowOf(0)
) : ViewModel() {
    private val draftPlans = MutableStateFlow<Set<Long>>(emptySet())
    private val draftExercises = MutableStateFlow<Set<Long>>(emptySet())
    private val confirmVisible = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)

    val uiState: StateFlow<FreeAccessSelectionUiState> = combine(
        templates.observeAll(),
        exercises.observeAll(),
        access.decisions(),
        entitlementChanges,
        combine(draftPlans, draftExercises, confirmVisible, finished) { plans, custom, confirm, done ->
            Draft(plans, custom, confirm, done)
        }
    ) { planItems, exerciseItems, decision, _, draft ->
        val custom = exerciseItems.filter { it.custom && !it.archived }
        val plansRequire = planItems.size > decision.planLimit
        val exercisesRequire = custom.size > decision.customExerciseLimit
        FreeAccessSelectionUiState(
            plans = planItems.map { item ->
                FreeAccessChoiceItem(
                    id = item.template.id,
                    title = item.template.name,
                    summary = item.exerciseNames.joinToString()
                )
            },
            exercises = custom.map { exercise ->
                FreeAccessChoiceItem(
                    id = exercise.id,
                    title = exercise.name,
                    summary = "",
                    primaryMuscle = exercise.primaryMuscle
                )
            },
            draftPlanIds = draft.plans,
            draftExerciseIds = draft.exercises,
            planLimit = decision.planLimit,
            exerciseLimit = decision.customExerciseLimit,
            plansRequireChoice = plansRequire,
            exercisesRequireChoice = exercisesRequire,
            confirmEnabled = decision.selectionRequired &&
                fits(draft.plans, planItems.size, decision.planLimit) &&
                fits(draft.exercises, custom.size, decision.customExerciseLimit),
            confirmVisible = draft.confirm,
            finished = draft.finished || !decision.selectionRequired && decision.loaded,
            intro = freeAccessIntro(
                phase = decision.state.phase,
                periodId = decision.state.periodId,
                priorProGrantEnded = priorProGrantEnded()
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FreeAccessSelectionUiState()
    )

    fun togglePlan(id: Long) {
        draftPlans.update { current -> toggle(current, id, uiState.value.planLimit, uiState.value.plansRequireChoice) }
    }

    fun toggleExercise(id: Long) {
        draftExercises.update { current ->
            toggle(current, id, uiState.value.exerciseLimit, uiState.value.exercisesRequireChoice)
        }
    }

    fun requestConfirm() {
        if (uiState.value.confirmEnabled) confirmVisible.value = true
    }

    fun dismissConfirm() {
        confirmVisible.value = false
    }

    fun confirm() {
        if (!uiState.value.confirmEnabled) return
        val plans = draftPlans.value
        val custom = draftExercises.value
        viewModelScope.launch {
            val saved = access.confirm(plans, custom)
            confirmVisible.value = false
            if (saved) finished.value = true
        }
    }

    private fun toggle(current: Set<Long>, id: Long, limit: Int, required: Boolean): Set<Long> {
        if (!required) return current
        return if (id in current) {
            current - id
        } else if (current.size < limit) {
            current + id
        } else {
            current
        }
    }

    private fun fits(chosen: Set<Long>, count: Int, limit: Int): Boolean {
        return if (count <= limit) true else chosen.size <= limit
    }

    private data class Draft(
        val plans: Set<Long>,
        val exercises: Set<Long>,
        val confirm: Boolean,
        val finished: Boolean
    )
}

@Composable
fun FreeAccessLockNote(
    onExplorePro: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.free_locked_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.free_locked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(
            onClick = onExplorePro,
            modifier = Modifier.testTag(FREE_ACCESS_EXPLORE)
        ) {
            Text(stringResource(R.string.timed_set_explore_pro))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FreeAccessSelectionScreen(
    state: FreeAccessSelectionUiState,
    onBack: () -> Unit,
    onTogglePlan: (Long) -> Unit,
    onToggleExercise: (Long) -> Unit,
    onRequestConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onExplorePro: () -> Unit
) {
    val title = introTitle(state.intro)
    val lead = introLead(state)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(FREE_ACCESS_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FreeAccessTopBar(title = stringResource(R.string.free_access_bar_title), onBack = onBack)
        },
        bottomBar = {
            FreeAccessActions(
                confirmEnabled = state.confirmEnabled,
                onRequestConfirm = onRequestConfirm,
                onExplorePro = onExplorePro
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(FREE_ACCESS_LIST),
            contentPadding = PaddingValues(bottom = AppDimens.itemGap)
        ) {
            item(key = "free-access-intro") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.screenPadding)
                        .padding(top = AppDimens.headerStackGap, bottom = AppDimens.sectionGap)
                        .testTag(FREE_ACCESS_INTRO)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .testTag(FREE_ACCESS_TITLE)
                            .semantics { heading() }
                    )
                    Spacer(Modifier.height(AppDimens.itemGap))
                    Text(
                        text = lead,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    Text(
                        text = stringResource(R.string.free_access_keep_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    Text(
                        text = stringResource(R.string.free_access_final_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            stickyHeader(key = FREE_ACCESS_PLANS_HEADER) {
                ChoiceHeading(
                    title = stringResource(R.string.free_access_plans),
                    selected = if (state.plansRequireChoice) state.draftPlanIds.size else state.plans.size,
                    limit = state.planLimit,
                    instruction = if (state.plansRequireChoice) {
                        stringResource(R.string.free_access_plans_hint, state.planLimit)
                    } else {
                        stringResource(R.string.free_access_within, state.plans.size)
                    },
                    emphasizeCount = state.plansRequireChoice,
                    tag = FREE_ACCESS_PLANS_HEADER
                )
            }
            if (state.plansRequireChoice) {
                items(state.plans, key = { "plan-${it.id}" }) { item ->
                    ChoiceRow(
                        item = item,
                        selected = item.id in state.draftPlanIds,
                        tag = "free-access-plan-${item.id}",
                        onToggle = { onTogglePlan(item.id) }
                    )
                }
            }
            stickyHeader(key = FREE_ACCESS_EXERCISES_HEADER) {
                ChoiceHeading(
                    title = stringResource(R.string.free_access_exercises),
                    selected = if (state.exercisesRequireChoice) {
                        state.draftExerciseIds.size
                    } else {
                        state.exercises.size
                    },
                    limit = state.exerciseLimit,
                    instruction = if (state.exercisesRequireChoice) {
                        stringResource(R.string.free_access_exercises_hint, state.exerciseLimit)
                    } else {
                        stringResource(R.string.free_access_within, state.exercises.size)
                    },
                    emphasizeCount = state.exercisesRequireChoice,
                    tag = FREE_ACCESS_EXERCISES_HEADER
                )
            }
            if (state.exercisesRequireChoice) {
                items(state.exercises, key = { "exercise-${it.id}" }) { item ->
                    ChoiceRow(
                        item = item,
                        selected = item.id in state.draftExerciseIds,
                        tag = "free-access-exercise-${item.id}",
                        onToggle = { onToggleExercise(item.id) }
                    )
                }
            }
        }
    }
    if (state.confirmVisible) {
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text(stringResource(R.string.free_access_confirm_title)) },
            text = { Text(stringResource(R.string.free_access_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("free-access-dialog-confirm")
                ) {
                    Text(stringResource(R.string.free_access_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissConfirm,
                    modifier = Modifier.testTag("free-access-dialog-cancel")
                ) {
                    Text(stringResource(R.string.free_access_cancel))
                }
            }
        )
    }
}

@Composable
private fun introTitle(intro: FreeAccessIntro): String {
    return stringResource(
        when (intro) {
            FreeAccessIntro.ProEnded -> R.string.free_access_ended_title
            FreeAccessIntro.WithinFreeLimits -> R.string.free_access_choose_title
        }
    )
}

@Composable
private fun introLead(state: FreeAccessSelectionUiState): String {
    val res = when (state.intro) {
        FreeAccessIntro.ProEnded -> R.string.free_access_ended_body
        FreeAccessIntro.WithinFreeLimits -> R.string.free_access_limit_body
    }
    return stringResource(res, state.planLimit, state.exerciseLimit)
}

@Composable
private fun FreeAccessTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(end = AppDimens.itemGap)
            .testTag(FREE_ACCESS_BAR),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = title,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FreeAccessActions(
    confirmEnabled: Boolean,
    onRequestConfirm: () -> Unit,
    onExplorePro: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = AppDimens.screenPadding)
            .padding(top = AppDimens.itemGap, bottom = AppDimens.itemGap)
            .testTag(FREE_ACCESS_ACTIONS),
        verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
    ) {
        Button(
            onClick = onRequestConfirm,
            enabled = confirmEnabled,
            shape = AppShapeTokens.button,
            colors = StrictBrand.actionButtonColors(),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag(FREE_ACCESS_CONFIRM)
        ) {
            Text(
                text = stringResource(R.string.free_access_confirm_button),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onExplorePro,
            shape = AppShapeTokens.button,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag(FREE_ACCESS_EXPLORE)
        ) {
            Text(
                text = stringResource(R.string.timed_set_explore_pro),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChoiceHeading(
    title: String,
    selected: Int,
    limit: Int,
    instruction: String,
    emphasizeCount: Boolean,
    tag: String
) {
    val countColor = if (emphasizeCount) selectionAccent() else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = AppDimens.screenPadding)
            .padding(top = AppDimens.itemGap, bottom = AppDimens.headerStackGap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.free_access_ratio, selected, limit),
                style = AppTypeTokens.statValue,
                color = countColor
            )
        }
        Text(
            text = instruction,
            style = AppTypeTokens.sectionSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun selectionAccent(): Color {
    val background = MaterialTheme.colorScheme.background
    return if (background.luminance() < 0.5f) StrictBrand.lime else StrictBrand.ink(darkTheme = false)
}

@Composable
private fun ChoiceRow(
    item: FreeAccessChoiceItem,
    selected: Boolean,
    tag: String,
    onToggle: () -> Unit
) {
    val detail = item.primaryMuscle?.let { stringResource(it.labelRes()) } ?: item.summary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .testTag(tag)
            .padding(horizontal = AppDimens.screenPadding, vertical = AppDimens.headerStackGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = StrictBrand.lime,
                checkmarkColor = StrictBrand.dark
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
