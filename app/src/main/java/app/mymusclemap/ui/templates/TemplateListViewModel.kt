package app.mymusclemap.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.workout.TemplateCatalogLogic
import app.mymusclemap.domain.workout.TemplateDeleteResult
import app.mymusclemap.domain.workout.TemplateListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateListUiState(
    val loading: Boolean = true,
    val visibleItems: List<TemplateListItem> = emptyList(),
    val query: String = "",
    val archiveFilter: ArchiveFilter = ArchiveFilter.ACTIVE,
    val emptyKind: TemplateEmptyKind? = TemplateEmptyKind.Loading,
    val message: TemplateMessage? = null,
    val pendingDelete: TemplateListItem? = null,
    val pendingBlocked: TemplateListItem? = null,
    val referencedIds: Set<Long> = emptySet()
)

enum class TemplateEmptyKind {
    Loading,
    Active,
    Archived,
    Search
}

sealed interface TemplateMessage {
    data object Saved : TemplateMessage
    data object Updated : TemplateMessage
    data object Archived : TemplateMessage
    data object Restored : TemplateMessage
    data object Deleted : TemplateMessage
    data object DeleteBlocked : TemplateMessage
}

class TemplateListViewModel(
    private val repository: WorkoutTemplateRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val archiveFilter = MutableStateFlow(ArchiveFilter.ACTIVE)
    private val message = MutableStateFlow<TemplateMessage?>(null)
    private val pendingDelete = MutableStateFlow<TemplateListItem?>(null)
    private val pendingBlocked = MutableStateFlow<TemplateListItem?>(null)

    private data class Controls(
        val query: String,
        val archiveFilter: ArchiveFilter,
        val message: TemplateMessage?,
        val pendingDelete: TemplateListItem?,
        val pendingBlocked: TemplateListItem?
    )

    val uiState: StateFlow<TemplateListUiState> = combine(
        repository.observeAll(),
        repository.observeReferencedTemplateIds(),
        combine(query, archiveFilter, message, pendingDelete, pendingBlocked) {
                text, filter, currentMessage, delete, blocked ->
            Controls(text, filter, currentMessage, delete, blocked)
        }
    ) { items, referenced, controls ->
        val visible = TemplateCatalogLogic.filter(
            items = items,
            query = controls.query,
            archiveFilter = controls.archiveFilter
        )
        TemplateListUiState(
            loading = false,
            visibleItems = visible,
            query = controls.query,
            archiveFilter = controls.archiveFilter,
            emptyKind = when {
                visible.isNotEmpty() -> null
                TemplateCatalogLogic.hasSearchQuery(controls.query) -> TemplateEmptyKind.Search
                controls.archiveFilter == ArchiveFilter.ARCHIVED -> TemplateEmptyKind.Archived
                else -> TemplateEmptyKind.Active
            },
            message = controls.message,
            pendingDelete = controls.pendingDelete,
            pendingBlocked = controls.pendingBlocked,
            referencedIds = referenced
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TemplateListUiState()
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onArchiveFilter(value: ArchiveFilter) {
        archiveFilter.value = value
    }

    fun archive(id: Long) {
        viewModelScope.launch {
            if (repository.archive(id)) {
                message.value = TemplateMessage.Archived
            }
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            if (repository.restore(id)) {
                message.value = TemplateMessage.Restored
            }
        }
    }

    fun requestDelete(item: TemplateListItem) {
        if (item.template.id in uiState.value.referencedIds) {
            pendingBlocked.value = item
        } else {
            pendingDelete.value = item
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
            message.value = when (repository.deletePermanently(target.template.id)) {
                TemplateDeleteResult.Deleted -> TemplateMessage.Deleted
                TemplateDeleteResult.BlockedByReferences -> TemplateMessage.DeleteBlocked
                TemplateDeleteResult.NotFound -> TemplateMessage.DeleteBlocked
            }
        }
    }

    fun showSaved(created: Boolean) {
        message.value = if (created) TemplateMessage.Saved else TemplateMessage.Updated
    }

    fun consumeMessage() {
        message.value = null
    }
}
