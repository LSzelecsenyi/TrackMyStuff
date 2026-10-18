package app.mymusclemap.ui.entitlement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.R
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FreeAccessRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FreeAccessChoiceItem(
    val id: Long,
    val title: String,
    val summary: String
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
    val finished: Boolean = false
)

class FreeAccessSelectionViewModel(
    private val templates: WorkoutTemplateRepository,
    private val exercises: ExerciseRepository,
    private val access: FreeAccessRepository
) : ViewModel() {
    private val draftPlans = MutableStateFlow<Set<Long>>(emptySet())
    private val draftExercises = MutableStateFlow<Set<Long>>(emptySet())
    private val confirmVisible = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)

    val uiState: StateFlow<FreeAccessSelectionUiState> = combine(
        templates.observeAll(),
        exercises.observeAll(),
        access.decisions(),
        combine(draftPlans, draftExercises, confirmVisible, finished) { plans, custom, confirm, done ->
            Draft(plans, custom, confirm, done)
        }
    ) { planItems, exerciseItems, decision, draft ->
        val custom = exerciseItems.filter { it.custom && !it.archived }
        val plansRequire = planItems.size > decision.planLimit
        val exercisesRequire = custom.size > decision.customExerciseLimit
        FreeAccessSelectionUiState(
            plans = planItems.map { item ->
                FreeAccessChoiceItem(
                    id = item.template.id,
                    title = item.template.name,
                    summary = item.exerciseNames.joinToString().ifBlank { item.exerciseCount.toString() }
                )
            },
            exercises = custom.map { exercise ->
                FreeAccessChoiceItem(
                    id = exercise.id,
                    title = exercise.name,
                    summary = listOfNotNull(exercise.notes?.takeIf { it.isNotBlank() })
                        .joinToString()
                        .ifBlank { exercise.primaryMuscle.name }
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
            finished = draft.finished || !decision.selectionRequired && decision.loaded
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
            modifier = Modifier.testTag("free-access-explore-pro")
        ) {
            Text(stringResource(R.string.timed_set_explore_pro))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.free_access_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.free_access_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                ChoiceHeading(
                    title = stringResource(R.string.free_access_plans),
                    selected = state.draftPlanIds.size,
                    limit = state.planLimit,
                    requiresChoice = state.plansRequireChoice,
                    count = state.plans.size
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
            item {
                Spacer(Modifier.height(16.dp))
                ChoiceHeading(
                    title = stringResource(R.string.free_access_exercises),
                    selected = state.draftExerciseIds.size,
                    limit = state.exerciseLimit,
                    requiresChoice = state.exercisesRequireChoice,
                    count = state.exercises.size
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
            item {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onRequestConfirm,
                    enabled = state.confirmEnabled,
                    modifier = Modifier.testTag("free-access-confirm")
                ) {
                    Text(stringResource(R.string.free_access_confirm_action))
                }
                TextButton(onClick = onExplorePro) {
                    Text(stringResource(R.string.timed_set_explore_pro))
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
private fun ChoiceHeading(
    title: String,
    selected: Int,
    limit: Int,
    requiresChoice: Boolean,
    count: Int
) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Text(
        text = if (requiresChoice) {
            stringResource(R.string.free_access_count, selected, limit)
        } else {
            stringResource(R.string.free_access_within, count)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ChoiceRow(
    item: FreeAccessChoiceItem,
    selected: Boolean,
    tag: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag(tag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (item.summary.isNotBlank()) {
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
