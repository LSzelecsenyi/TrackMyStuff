package hu.laca.weighttracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutHubUiState(
    val loading: Boolean = true,
    val activeCount: Int = 0,
    val archivedCount: Int = 0
) {
    val isEmpty: Boolean
        get() = !loading && activeCount == 0 && archivedCount == 0
}

class WorkoutHubViewModel(
    repository: ExerciseRepository
) : ViewModel() {
    val uiState: StateFlow<WorkoutHubUiState> = combine(
        repository.observeActiveCount(),
        repository.observeArchivedCount()
    ) { active, archived ->
        WorkoutHubUiState(
            loading = false,
            activeCount = active,
            archivedCount = archived
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutHubUiState()
    )
}
