package app.mymusclemap.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.local.ExercisePlanReference
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.FreeExerciseReleaseResult
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCatalogLogic
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDeleteResult
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.entitlement.FreeAccessDecision
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    val referencedIds: Set<Long> = emptySet(),
    val lockedFeature: AppFeature? = null,
    val lockedCustomExerciseIds: Set<Long> = emptySet(),
    val selectionRequired: Boolean = false,
    val freeReleaseExerciseIds: Set<Long> = emptySet(),
    val accessiblePlanIds: Set<Long> = emptySet(),
    val accessLoaded: Boolean = false,
    val canCreateCustomExercise: Boolean = false,
    val pendingRelease: FreeReleasePrompt? = null
)

data class FreeReleasePrompt(
    val exercise: Exercise,
    val accessiblePlanNames: List<String>,
    val lockedPlanNames: List<String>
)

internal fun summarizeFreeReleasePlans(names: List<String>, limit: Int = 3): Pair<String, Int> {
    val shown = names.take(limit)
    val extra = (names.size - shown.size).coerceAtLeast(0)
    return shown.joinToString(", ") to extra
}

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
    data object ReleaseBlocked : CatalogMessage
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
    val blocked: Exercise?,
    val release: FreeReleasePrompt?
)

class ExerciseListViewModel(
    private val repository: ExerciseRepository,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements,
    private val access: Flow<FreeAccessDecision> = flowOf(FreeAccessLogic.unknown()),
    private val planReferences: suspend (Long) -> List<ExercisePlanReference> = { emptyList() },
    private val exerciseHasPendingWork: suspend (Long) -> Boolean = { false },
    private val releaseExercise: suspend (Long) -> FreeExerciseReleaseResult = {
        FreeExerciseReleaseResult.NotAllocated
    }
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<ExerciseCategory?>(null)
    private val muscle = MutableStateFlow<MuscleGroup?>(null)
    private val archiveFilter = MutableStateFlow(ArchiveFilter.ACTIVE)
    private val message = MutableStateFlow<CatalogMessage?>(null)
    private val pendingDelete = MutableStateFlow<Exercise?>(null)
    private val pendingBlocked = MutableStateFlow<Exercise?>(null)
    private val pendingRelease = MutableStateFlow<FreeReleasePrompt?>(null)
    private val lockedFeature = MutableStateFlow<AppFeature?>(null)

    private val filters = combine(query, category, muscle, archiveFilter) { text, cat, mus, archive ->
        ListQuery(text, cat, mus, archive)
    }

    val uiState: StateFlow<ExerciseListUiState> = combine(
        combine(
            repository.observeAll(),
            filters,
            message,
            combine(pendingDelete, pendingBlocked, pendingRelease) { delete, blocked, release ->
                Triple(delete, blocked, release)
            }
        ) { exercises, current, currentMessage, dialogs ->
            ListCore(exercises, current, currentMessage, dialogs.first, dialogs.second, dialogs.third)
        },
        lockedFeature,
        repository.observeReferencedExerciseIds(),
        combine(access, entitlements.changes()) { decision, _ -> decision }
    ) { core, locked, referenced, decision ->
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
        val pro = entitlements.hasAccess(AppFeature.UnlimitedCustomExercises)
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
                ExerciseCatalogLogic.hasSearchQuery(core.current.query) ||
                    core.current.category != null ||
                    core.current.muscle != null -> CatalogEmptyKind.Search
                core.current.archiveFilter == ArchiveFilter.ARCHIVED -> CatalogEmptyKind.Archived
                else -> CatalogEmptyKind.Active
            },
            message = core.currentMessage,
            pendingDelete = core.delete,
            pendingBlocked = core.blocked,
            pendingRelease = core.release,
            lockedCustomExerciseIds = decision.lockedCustomExerciseIds,
            selectionRequired = decision.selectionRequired,
            freeReleaseExerciseIds = releasableCustomExercises(decision, pro),
            accessiblePlanIds = decision.accessiblePlanIds,
            accessLoaded = decision.loaded,
            canCreateCustomExercise = FreeAccessLogic.creationAllowed(
                decision,
                pro,
                core.exercises.count { it.custom && !it.archived }
            ),
            referencedIds = referenced,
            lockedFeature = locked
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

    fun requestCreate(onAllowed: () -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val allowed = if (state.loading) {
                FreeAccessLogic.creationAllowed(
                    FreeAccessLogic.unknown(),
                    entitlements.hasAccess(AppFeature.UnlimitedCustomExercises),
                    repository.activeCustomCount()
                )
            } else {
                state.canCreateCustomExercise
            }
            if (allowed) {
                onAllowed()
            } else {
                lockedFeature.value = AppFeature.UnlimitedCustomExercises
            }
        }
    }

    fun requestRelease(exercise: Exercise) {
        viewModelScope.launch {
            if (exercise.id !in uiState.value.freeReleaseExerciseIds) return@launch
            if (exerciseHasPendingWork(exercise.id)) {
                message.value = CatalogMessage.ReleaseBlocked
                return@launch
            }
            val plans = planReferences(exercise.id).filter { !it.archived }
            val state = uiState.value
            val accessiblePlans = plans.filter { plan ->
                !state.accessLoaded || plan.id in state.accessiblePlanIds
            }
            val accessibleIds = accessiblePlans.map { it.id }.toSet()
            pendingRelease.value = FreeReleasePrompt(
                exercise = exercise,
                accessiblePlanNames = accessiblePlans.map { it.name },
                lockedPlanNames = plans.filter { it.id !in accessibleIds }.map { it.name }
            )
        }
    }

    fun dismissRelease() {
        pendingRelease.value = null
    }

    fun confirmRelease() {
        val target = pendingRelease.value ?: return
        viewModelScope.launch {
            pendingRelease.value = null
            if (releaseExercise(target.exercise.id) == FreeExerciseReleaseResult.BlockedByActiveWorkout) {
                message.value = CatalogMessage.ReleaseBlocked
            }
        }
    }

    fun consumeLockedFeature() {
        lockedFeature.value = null
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

    private fun releasableCustomExercises(decision: FreeAccessDecision, pro: Boolean): Set<Long> {
        if (!decision.loaded || pro || decision.selectionRequired) return emptySet()
        if (decision.state.phase == FreeAccessPhase.Required) return emptySet()
        return decision.accessibleCustomExerciseIds
    }
}
