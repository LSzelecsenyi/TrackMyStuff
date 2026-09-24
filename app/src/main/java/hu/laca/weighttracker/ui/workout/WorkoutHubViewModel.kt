package hu.laca.weighttracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.R
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.ScheduledWorkoutRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.locale.LocalizedLabelOrder
import hu.laca.weighttracker.domain.workout.ActiveSessionSummary
import hu.laca.weighttracker.domain.workout.QuickStartAssembler
import hu.laca.weighttracker.domain.workout.ScheduledWorkout
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutHubUiState(
    val loading: Boolean = true,
    val activeCount: Int = 0,
    val archivedCount: Int = 0,
    val activeTemplateCount: Int = 0,
    val archivedTemplateCount: Int = 0,
    val templates: List<TemplateListItem> = emptyList(),
    val todayPlanned: List<ScheduledWorkout> = emptyList(),
    val todayInProgress: List<ScheduledWorkout> = emptyList(),
    val activeSession: ActiveSessionSummary? = null,
    val isStarting: Boolean = false,
    val message: WorkoutHubMessage? = null,
    val startedSessionId: Long? = null,
    val recentCompleted: WorkoutSessionSummary? = null,
    val pickerVisible: Boolean = false
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

sealed interface WorkoutPrimaryAction {
    data class Resume(val sessionId: Long) : WorkoutPrimaryAction
    data object ShowPicker : WorkoutPrimaryAction
    data object Ignored : WorkoutPrimaryAction
}

class WorkoutHubViewModel(
    exerciseRepository: ExerciseRepository,
    templateRepository: WorkoutTemplateRepository,
    private val sessionRepository: WorkoutSessionRepository,
    scheduledWorkoutRepository: ScheduledWorkoutRepository,
    dateProvider: DateProvider
) : ViewModel() {
    private val starting = MutableStateFlow(false)
    private val preparingStart = MutableStateFlow(false)
    private val message = MutableStateFlow<WorkoutHubMessage?>(null)
    private val startedSessionId = MutableStateFlow<Long?>(null)
    private val pickerOpen = MutableStateFlow(false)

    private data class Counts(
        val exercises: Int,
        val archivedExercises: Int,
        val templates: Int,
        val archivedTemplates: Int
    )

    private data class StartExtras(
        val currentMessage: WorkoutHubMessage?,
        val started: Long?,
        val isStarting: Boolean,
        val pickerVisible: Boolean
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
        combine(message, startedSessionId, starting, pickerOpen) {
            currentMessage, started, isStarting, pickerVisible ->
            StartExtras(currentMessage, started, isStarting, pickerVisible)
        },
        combine(
            sessionRepository.observeLatestCompleted(),
            scheduledWorkoutRepository.observeOnDate(dateProvider.today())
        ) { recent, todaySchedules ->
            recent to todaySchedules
        }
    ) { counts, templates, active, extras, recentAndToday ->
        val (recent, todaySchedules) = recentAndToday
        val assembled = QuickStartAssembler.assemble(
            templates = LocalizedLabelOrder.sorted(
                templates,
                label = { it.template.name },
                key = { it.template.id.toString() }
            ),
            todaySchedules = todaySchedules
        )
        WorkoutHubUiState(
            loading = false,
            activeCount = counts.exercises,
            archivedCount = counts.archivedExercises,
            activeTemplateCount = counts.templates,
            archivedTemplateCount = counts.archivedTemplates,
            templates = assembled.remainingTemplates,
            todayPlanned = assembled.todayPlanned,
            todayInProgress = assembled.todayInProgress,
            activeSession = active,
            isStarting = extras.isStarting,
            message = extras.currentMessage,
            startedSessionId = extras.started,
            recentCompleted = recent,
            pickerVisible = extras.pickerVisible && active == null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutHubUiState()
    )

    fun onPrimaryWorkoutAction(): WorkoutPrimaryAction {
        val active = uiState.value.activeSession
        if (active != null) {
            pickerOpen.value = false
            return WorkoutPrimaryAction.Resume(active.session.id)
        }
        if (starting.value || preparingStart.value || pickerOpen.value) {
            return WorkoutPrimaryAction.Ignored
        }
        pickerOpen.value = true
        return WorkoutPrimaryAction.ShowPicker
    }

    fun dismissPicker() {
        if (starting.value || preparingStart.value) {
            return
        }
        pickerOpen.value = false
    }

    fun chooseTemplate(item: TemplateListItem) {
        if (uiState.value.activeSession != null) {
            pickerOpen.value = false
            message.value = WorkoutHubMessage.AlreadyActive
            return
        }
        if (!pickerOpen.value) {
            return
        }
        requestStart(item.template.id, scheduledWorkoutId = null)
    }

    fun startScheduled(item: ScheduledWorkout) {
        if (uiState.value.activeSession != null) {
            pickerOpen.value = false
            message.value = WorkoutHubMessage.AlreadyActive
            return
        }
        if (!pickerOpen.value) {
            return
        }
        requestStart(item.templateId, scheduledWorkoutId = item.id)
    }

    fun continueScheduled(item: ScheduledWorkout) {
        val sessionId = item.sessionId ?: return
        pickerOpen.value = false
        startedSessionId.value = sessionId
    }

    fun requestStart(item: TemplateListItem) {
        requestStart(item.template.id, scheduledWorkoutId = null)
    }

    private fun requestStart(templateId: Long, scheduledWorkoutId: Long?) {
        if (uiState.value.activeSession != null) {
            pickerOpen.value = false
            message.value = WorkoutHubMessage.AlreadyActive
            return
        }
        if (starting.value || preparingStart.value) {
            return
        }
        preparingStart.value = true
        starting.value = true
        viewModelScope.launch {
            try {
                if (uiState.value.activeSession != null) {
                    pickerOpen.value = false
                    message.value = WorkoutHubMessage.AlreadyActive
                    return@launch
                }
                when (val result = sessionRepository.start(templateId, scheduledWorkoutId)) {
                    is StartWorkoutResult.Started -> {
                        pickerOpen.value = false
                        startedSessionId.value = result.sessionId
                    }
                    StartWorkoutResult.AlreadyActive -> {
                        pickerOpen.value = false
                        message.value = WorkoutHubMessage.AlreadyActive
                    }
                    StartWorkoutResult.TemplateArchived -> message.value = WorkoutHubMessage.TemplateArchived
                    StartWorkoutResult.TemplateEmpty -> message.value = WorkoutHubMessage.TemplateEmpty
                    StartWorkoutResult.TemplateNotFound -> message.value = WorkoutHubMessage.TemplateNotFound
                    StartWorkoutResult.ScheduleNotFound,
                    StartWorkoutResult.ScheduleTemplateMismatch -> {
                        message.value = WorkoutHubMessage.TemplateNotFound
                    }
                    StartWorkoutResult.ScheduleAlreadyStarted -> {
                        pickerOpen.value = false
                        message.value = WorkoutHubMessage.AlreadyActive
                    }
                    is StartWorkoutResult.InvalidBodyWeight -> {
                        message.value = WorkoutHubMessage.TemplateEmpty
                    }
                }
            } finally {
                starting.value = false
                preparingStart.value = false
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
}

fun WorkoutHubMessage.labelRes(): Int {
    return when (this) {
        WorkoutHubMessage.AlreadyActive -> R.string.message_workout_already_active
        WorkoutHubMessage.TemplateArchived -> R.string.message_workout_template_archived
        WorkoutHubMessage.TemplateEmpty -> R.string.message_workout_template_empty
        WorkoutHubMessage.TemplateNotFound -> R.string.message_workout_template_empty
        WorkoutHubMessage.WorkoutFinished -> R.string.message_workout_finished
        WorkoutHubMessage.WorkoutAbandoned -> R.string.message_workout_abandoned
    }
}
