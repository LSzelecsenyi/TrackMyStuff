package hu.laca.weighttracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.domain.exercise.ArchiveFilter
import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCatalogLogic
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDeleteResult
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseListUiState(
    val loading: Boolean = true,
    val visibleExercises: List<Exercise> = emptyList(),
    val query: String = "",
    val category: ExerciseCategory? = null,
    val muscle: MuscleGroup? = null,
    val archiveFilter: ArchiveFilter = ArchiveFilter.ACTIVE,
    val filtersActive: Boolean = false,
    val emptyKind: CatalogEmptyKind? = CatalogEmptyKind.Loading,
    val message: CatalogMessage? = null,
    val pendingDelete: Exercise? = null,
    val pendingBlocked: Exercise? = null,
    val referencedIds: Set<Long> = emptySet()
)

enum class CatalogEmptyKind {
    Loading,
    Active,
    Archived,
    Search
}

sealed interface CatalogMessage {
    data object Saved : CatalogMessage
    data object Updated : CatalogMessage
    data object DuplicateName : CatalogMessage
    data object Archived : CatalogMessage
    data object Restored : CatalogMessage
    data object Deleted : CatalogMessage
    data object DeleteBlocked : CatalogMessage
}

private data class ListQuery(
    val query: String,
    val category: ExerciseCategory?,
    val muscle: MuscleGroup?,
    val archiveFilter: ArchiveFilter
)

private data class ListCore(
    val exercises: List<Exercise>,
    val current: ListQuery,
    val currentMessage: CatalogMessage?,
    val delete: Exercise?,
    val blocked: Exercise?
)

class ExerciseListViewModel(
    private val repository: ExerciseRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<ExerciseCategory?>(null)
    private val muscle = MutableStateFlow<MuscleGroup?>(null)
    private val archiveFilter = MutableStateFlow(ArchiveFilter.ACTIVE)
    private val message = MutableStateFlow<CatalogMessage?>(null)
    private val pendingDelete = MutableStateFlow<Exercise?>(null)
    private val pendingBlocked = MutableStateFlow<Exercise?>(null)

    private val filters = combine(query, category, muscle, archiveFilter) { text, cat, mus, archive ->
        ListQuery(text, cat, mus, archive)
    }

    val uiState: StateFlow<ExerciseListUiState> = combine(
        combine(repository.observeAll(), filters, message, pendingDelete, pendingBlocked) {
                exercises, current, currentMessage, delete, blocked ->
            ListCore(exercises, current, currentMessage, delete, blocked)
        },
        repository.observeReferencedExerciseIds()
    ) { core, referenced ->
        val visible = ExerciseCatalogLogic.filter(
            exercises = core.exercises,
            query = core.current.query,
            category = core.current.category,
            muscle = core.current.muscle,
            archiveFilter = core.current.archiveFilter
        )
        val filtersActive = ExerciseCatalogLogic.hasActiveFilters(
            core.current.query,
            core.current.category,
            core.current.muscle,
            core.current.archiveFilter
        )
        ExerciseListUiState(
            loading = false,
            visibleExercises = visible,
            query = core.current.query,
            category = core.current.category,
            muscle = core.current.muscle,
            archiveFilter = core.current.archiveFilter,
            filtersActive = filtersActive,
            emptyKind = when {
                visible.isNotEmpty() -> null
                filtersActive -> CatalogEmptyKind.Search
                core.current.archiveFilter == ArchiveFilter.ARCHIVED -> CatalogEmptyKind.Archived
                else -> CatalogEmptyKind.Active
            },
            message = core.currentMessage,
            pendingDelete = core.delete,
            pendingBlocked = core.blocked,
            referencedIds = referenced
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ExerciseListUiState()
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategoryFilter(value: ExerciseCategory?) {
        category.value = value
    }

    fun onMuscleFilter(value: MuscleGroup?) {
        muscle.value = value
    }

    fun onArchiveFilter(value: ArchiveFilter) {
        archiveFilter.value = value
    }

    fun clearFilters() {
        query.value = ""
        category.value = null
        muscle.value = null
        archiveFilter.value = ArchiveFilter.ACTIVE
    }

    fun archive(id: Long) {
        viewModelScope.launch {
            if (repository.archive(id)) {
                message.value = CatalogMessage.Archived
            }
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            if (repository.restore(id)) {
                message.value = CatalogMessage.Restored
            }
        }
    }

    fun requestDelete(exercise: Exercise) {
        if (exercise.id in uiState.value.referencedIds) {
            pendingBlocked.value = exercise
        } else {
            pendingDelete.value = exercise
        }
    }

    fun dismissDelete() {
        pendingDelete.value = null
        pendingBlocked.value = null
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        viewModelScope.launch {
            pendingDelete.value = null
            message.value = when (repository.deletePermanently(target.id)) {
                ExerciseDeleteResult.Deleted -> CatalogMessage.Deleted
                ExerciseDeleteResult.BlockedByReferences -> CatalogMessage.DeleteBlocked
                ExerciseDeleteResult.NotFound -> CatalogMessage.DeleteBlocked
            }
        }
    }

    fun showSaved(created: Boolean) {
        message.value = if (created) CatalogMessage.Saved else CatalogMessage.Updated
    }

    fun consumeMessage() {
        message.value = null
    }
}
