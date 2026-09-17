package hu.laca.weighttracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.WeightParseError
import hu.laca.weighttracker.domain.WeightParser
import hu.laca.weighttracker.domain.workout.ActiveSessionSummary
import hu.laca.weighttracker.domain.workout.BodyWeightProposal
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class StartWorkoutDraft(
    val template: TemplateListItem,
    val proposal: BodyWeightProposal,
    val weightText: String,
    val editedManually: Boolean,
    val weightError: WeightParseError? = null
)

data class WorkoutHubUiState(
    val loading: Boolean = true,
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
    val activeTemplateCount: Int = 0,
    val archivedTemplateCount: Int = 0,
    val templates: List<TemplateListItem> = emptyList(),
    val activeSession: ActiveSessionSummary? = null,
    val startDraft: StartWorkoutDraft? = null,
    val isStarting: Boolean = false,
    val message: WorkoutHubMessage? = null,
    val startedSessionId: Long? = null,
    val recentCompleted: WorkoutSessionSummary? = null
) {
    val isEmpty: Boolean
        get() = !loading &&
            activeCount == 0 &&
            archivedCount == 0 &&
            activeTemplateCount == 0 &&
            archivedTemplateCount == 0
}

sealed interface WorkoutHubMessage {
    data object AlreadyActive : WorkoutHubMessage
    data object TemplateArchived : WorkoutHubMessage
    data object TemplateEmpty : WorkoutHubMessage
    data object TemplateNotFound : WorkoutHubMessage
    data object WorkoutFinished : WorkoutHubMessage
    data object WorkoutAbandoned : WorkoutHubMessage
}

class WorkoutHubViewModel(
    exerciseRepository: ExerciseRepository,
    templateRepository: WorkoutTemplateRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val dateProvider: DateProvider
) : ViewModel() {
    private val startDraft = MutableStateFlow<StartWorkoutDraft?>(null)
    private val starting = MutableStateFlow(false)
    private val preparingStart = MutableStateFlow(false)
    private val message = MutableStateFlow<WorkoutHubMessage?>(null)
    private val startedSessionId = MutableStateFlow<Long?>(null)

    private data class Counts(
        val exercises: Int,
        val archivedExercises: Int,
        val templates: Int,
        val archivedTemplates: Int
    )

    private data class StartExtras(
        val draft: StartWorkoutDraft?,
        val currentMessage: WorkoutHubMessage?,
        val started: Long?,
        val isStarting: Boolean
    )

    val uiState: StateFlow<WorkoutHubUiState> = combine(
        combine(
            exerciseRepository.observeActiveCount(),
            exerciseRepository.observeArchivedCount(),
            templateRepository.observeActiveCount(),
            templateRepository.observeArchivedCount()
        ) { exercises, archivedExercises, templates, archivedTemplates ->
            Counts(exercises, archivedExercises, templates, archivedTemplates)
        },
        templateRepository.observeActive(),
        sessionRepository.observeInProgress(),
        combine(startDraft, message, startedSessionId, starting) { draft, currentMessage, started, isStarting ->
            StartExtras(draft, currentMessage, started, isStarting)
        },
        sessionRepository.observeLatestCompleted()
    ) { counts, templates, active, extras, recent ->
        WorkoutHubUiState(
            loading = false,
            activeCount = counts.exercises,
            archivedCount = counts.archivedExercises,
            activeTemplateCount = counts.templates,
            archivedTemplateCount = counts.archivedTemplates,
            templates = templates,
            activeSession = active,
            startDraft = extras.draft,
            isStarting = extras.isStarting,
            message = extras.currentMessage,
            startedSessionId = extras.started,
            recentCompleted = recent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutHubUiState()
    )

    fun requestStart(item: TemplateListItem) {
        if (uiState.value.activeSession != null) {
            message.value = WorkoutHubMessage.AlreadyActive
            return
        }
        if (starting.value || startDraft.value != null || preparingStart.value) {
            return
        }
        preparingStart.value = true
        viewModelScope.launch {
            try {
                if (uiState.value.activeSession != null) {
                    message.value = WorkoutHubMessage.AlreadyActive
                    return@launch
                }
                val proposal = sessionRepository.proposeBodyWeight(dateProvider.today())
                if (startDraft.value != null || uiState.value.activeSession != null) {
                    if (uiState.value.activeSession != null) {
                        message.value = WorkoutHubMessage.AlreadyActive
                    }
                    return@launch
                }
                startDraft.value = StartWorkoutDraft(
                    template = item,
                    proposal = proposal,
                    weightText = proposal.kilograms?.let(::formatBodyWeight).orEmpty(),
                    editedManually = false
                )
            } finally {
                preparingStart.value = false
            }
        }
    }

    fun dismissStart() {
        if (starting.value) {
            return
        }
        startDraft.value = null
    }

    fun onStartWeightChange(value: String) {
        val current = startDraft.value ?: return
        if (starting.value) {
            return
        }
        val filtered = WeightParser.filterUserInput(value)
        val error = if (filtered.isBlank()) {
            null
        } else {
            when (val parsed = WeightParser.parseUserInput(filtered)) {
                is hu.laca.weighttracker.domain.WeightParseResult.Valid -> null
                is hu.laca.weighttracker.domain.WeightParseResult.Invalid -> parsed.error
            }
        }
        startDraft.value = current.copy(
            weightText = filtered,
            editedManually = true,
            weightError = error
        )
    }

    fun confirmStart() {
        val draft = startDraft.value ?: return
        if (draft.weightError != null || starting.value) {
            return
        }
        starting.value = true
        viewModelScope.launch {
            try {
                val current = startDraft.value ?: return@launch
                when (
                    val result = sessionRepository.start(
                        templateId = current.template.template.id,
                        bodyWeightText = current.weightText,
                        proposal = current.proposal,
                        editedManually = current.editedManually
                    )
                ) {
                    is StartWorkoutResult.Started -> {
                        startDraft.value = null
                        startedSessionId.value = result.sessionId
                    }
                    StartWorkoutResult.AlreadyActive -> {
                        startDraft.value = null
                        message.value = WorkoutHubMessage.AlreadyActive
                    }
                    StartWorkoutResult.TemplateArchived -> message.value = WorkoutHubMessage.TemplateArchived
                    StartWorkoutResult.TemplateEmpty -> message.value = WorkoutHubMessage.TemplateEmpty
                    StartWorkoutResult.TemplateNotFound -> message.value = WorkoutHubMessage.TemplateNotFound
                    is StartWorkoutResult.InvalidBodyWeight -> {
                        startDraft.value = current.copy(weightError = result.error)
                    }
                }
            } finally {
                starting.value = false
            }
        }
    }

    fun consumeStartedSession() {
        startedSessionId.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    fun showFinished() {
        message.value = WorkoutHubMessage.WorkoutFinished
    }

    fun showAbandoned() {
        message.value = WorkoutHubMessage.WorkoutAbandoned
    }

    private fun formatBodyWeight(value: Double): String {
        return String.format(Locale.forLanguageTag("hu-HU"), "%.1f", value)
    }
}
