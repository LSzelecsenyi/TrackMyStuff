package app.mymusclemap.ui.workoutimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.workoutimport.WorkoutImportFileReadResult
import app.mymusclemap.data.workoutimport.WorkoutImportFileReader
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.workout.TemplateNaming
import app.mymusclemap.domain.workoutimport.WorkoutImportCsv
import app.mymusclemap.domain.workoutimport.WorkoutImportDocument
import app.mymusclemap.domain.workoutimport.WorkoutImportError
import app.mymusclemap.domain.workoutimport.WorkoutImportFingerprint
import app.mymusclemap.domain.workoutimport.WorkoutImportLimits
import app.mymusclemap.domain.workoutimport.WorkoutImportMapping
import app.mymusclemap.domain.workoutimport.WorkoutImportParseResult
import app.mymusclemap.domain.workoutimport.WorkoutImportPersistenceResult
import app.mymusclemap.domain.workoutimport.WorkoutImportPlan
import app.mymusclemap.domain.workoutimport.WorkoutImportResolver
import app.mymusclemap.domain.workoutimport.WorkoutImportWarning
import app.mymusclemap.domain.workoutimport.WorkoutImportWarningCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class WorkoutImportPhase {
    Idle,
    SelectingFile,
    Parsing,
    NeedsMappings,
    PreviewReady,
    Importing,
    Success,
    Failure
}

sealed class WorkoutImportFailureKind {
    data class Structural(val errors: List<WorkoutImportError>) : WorkoutImportFailureKind()
    data object FileTooLarge : WorkoutImportFailureKind()
    data object Unreadable : WorkoutImportFailureKind()
    data object PermissionDenied : WorkoutImportFailureKind()
    data class Duplicate(val workoutNames: List<String>) : WorkoutImportFailureKind()
    data object StaleCatalog : WorkoutImportFailureKind()
    data object Database : WorkoutImportFailureKind()
    data object PlanNotConfirmable : WorkoutImportFailureKind()
}

data class WorkoutImportUnresolvedName(
    val incomingName: String,
    val normalizedName: String,
    val occurrenceCount: Int,
    val appearances: List<WorkoutImportAppearance>,
    val selectedExerciseId: Long? = null,
    val selectedExerciseName: String? = null,
    val errors: List<WorkoutImportError> = emptyList(),
    val warnings: List<WorkoutImportWarning> = emptyList()
)

data class WorkoutImportAppearance(
    val workoutName: String,
    val date: LocalDate
)

data class WorkoutImportUiState(
    val phase: WorkoutImportPhase = WorkoutImportPhase.Idle,
    val fileName: String? = null,
    val document: WorkoutImportDocument? = null,
    val plan: WorkoutImportPlan? = null,
    val catalog: List<Exercise> = emptyList(),
    val mappings: List<WorkoutImportMapping> = emptyList(),
    val unresolved: List<WorkoutImportUnresolvedName> = emptyList(),
    val duplicateWorkoutIds: List<String> = emptyList(),
    val duplicateWorkoutNames: List<String> = emptyList(),
    val sameDateNameWarnings: List<WorkoutImportWarning> = emptyList(),
    val expandedWorkoutIds: Set<String> = emptySet(),
    val mappingPickerIncoming: String? = null,
    val mappingQuery: String = "",
    val confirmVisible: Boolean = false,
    val imported: WorkoutImportPersistenceResult.Imported? = null,
    val failureKind: WorkoutImportFailureKind? = null
) {
    val isBusy: Boolean
        get() = phase == WorkoutImportPhase.Parsing ||
            phase == WorkoutImportPhase.Importing ||
            phase == WorkoutImportPhase.SelectingFile

    val displayedErrors: List<WorkoutImportError>
        get() = when (failureKind) {
            is WorkoutImportFailureKind.Structural -> failureKind.errors
            else -> plan?.errors.orEmpty()
        }

    val displayedWarnings: List<WorkoutImportWarning>
        get() = plan?.warnings.orEmpty() + sameDateNameWarnings

    val canOpenConfirm: Boolean
        get() = phase == WorkoutImportPhase.PreviewReady &&
            plan?.canConfirm == true &&
            duplicateWorkoutIds.isEmpty() &&
            !isBusy

    val mappingPickerExercise: WorkoutImportUnresolvedName?
        get() = unresolved.firstOrNull { it.normalizedName == mappingPickerIncoming }
}

fun interface WorkoutImportPersister {
    suspend fun persist(plan: WorkoutImportPlan): WorkoutImportPersistenceResult
}

class WorkoutImportViewModel(
    private val fileReader: WorkoutImportFileReader,
    private val exerciseRepository: ExerciseRepository,
    private val weightRepository: WeightRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val dateProvider: DateProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val persister: WorkoutImportPersister? = null
) : ViewModel() {
    private val state = MutableStateFlow(WorkoutImportUiState())
    val uiState: StateFlow<WorkoutImportUiState> = state.asStateFlow()
    private var generation = 0L

    fun onPickFileRequested() {
        if (state.value.phase == WorkoutImportPhase.Importing) {
            return
        }
        state.update { it.copy(phase = WorkoutImportPhase.SelectingFile, failureKind = null) }
    }

    fun onFileSelectionCancelled() {
        if (state.value.phase != WorkoutImportPhase.SelectingFile) {
            return
        }
        state.update { current ->
            val restored = when {
                current.plan != null && current.plan.unresolvedNames.isNotEmpty() -> WorkoutImportPhase.NeedsMappings
                current.plan != null -> WorkoutImportPhase.PreviewReady
                current.failureKind is WorkoutImportFailureKind.Structural -> WorkoutImportPhase.Failure
                else -> WorkoutImportPhase.Idle
            }
            current.copy(phase = restored)
        }
    }

    fun onFileSelected(uri: String) {
        if (state.value.phase == WorkoutImportPhase.Importing) {
            return
        }
        val token = ++generation
        viewModelScope.launch {
            state.update {
                WorkoutImportUiState(
                    phase = WorkoutImportPhase.Parsing,
                    fileName = it.fileName
                )
            }
            val read = withContext(ioDispatcher) {
                fileReader.read(uri, WorkoutImportLimits.MAX_UTF8_BYTES)
            }
            if (token != generation) {
                return@launch
            }
            when (read) {
                is WorkoutImportFileReadResult.Success -> parseSelectedFile(token, read.bytes, read.displayName)
                WorkoutImportFileReadResult.TooLarge -> fail(WorkoutImportFailureKind.FileTooLarge)
                WorkoutImportFileReadResult.PermissionDenied -> fail(WorkoutImportFailureKind.PermissionDenied)
                WorkoutImportFileReadResult.Unreadable -> fail(WorkoutImportFailureKind.Unreadable)
            }
        }
    }

    fun onMappingQueryChange(query: String) {
        state.update { it.copy(mappingQuery = query) }
    }

    fun onOpenMappingPicker(normalizedName: String) {
        state.update { it.copy(mappingPickerIncoming = normalizedName, mappingQuery = "") }
    }

    fun onDismissMappingPicker() {
        state.update { it.copy(mappingPickerIncoming = null, mappingQuery = "") }
    }

    fun onMapExercise(normalizedName: String, exerciseId: Long) {
        if (state.value.phase == WorkoutImportPhase.Importing) {
            return
        }
        val document = state.value.document ?: return
        val nextMappings = state.value.mappings
            .filterNot { it.incomingNormalizedName == normalizedName } +
            WorkoutImportMapping(normalizedName, exerciseId)
        val displayName = state.value.fileName.orEmpty()
        val token = ++generation
        state.update {
            it.copy(
                phase = WorkoutImportPhase.Parsing,
                mappingPickerIncoming = null,
                mappingQuery = ""
            )
        }
        viewModelScope.launch {
            resolveDocument(token, document, displayName, nextMappings)
        }
        state.update { it.copy(mappingPickerIncoming = null, mappingQuery = "") }
    }

    fun onToggleWorkoutExpanded(workoutId: String) {
        state.update { current ->
            val expanded = if (workoutId in current.expandedWorkoutIds) {
                current.expandedWorkoutIds - workoutId
            } else {
                current.expandedWorkoutIds + workoutId
            }
            current.copy(expandedWorkoutIds = expanded)
        }
    }

    fun onRequestConfirm() {
        if (!state.value.canOpenConfirm) {
            return
        }
        state.update { it.copy(confirmVisible = true) }
    }

    fun onDismissConfirm() {
        if (state.value.phase == WorkoutImportPhase.Importing) {
            return
        }
        state.update { it.copy(confirmVisible = false) }
    }

    fun onConfirmImport() {
        val current = state.value
        if (current.phase != WorkoutImportPhase.PreviewReady) {
            return
        }
        val plan = current.plan
        if (plan == null || !plan.canConfirm || current.duplicateWorkoutIds.isNotEmpty()) {
            return
        }
        state.update { it.copy(phase = WorkoutImportPhase.Importing, confirmVisible = false) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                persister?.persist(plan) ?: sessionRepository.importCompletedWorkouts(plan)
            }
            when (result) {
                is WorkoutImportPersistenceResult.Imported -> {
                    state.update {
                        it.copy(
                            phase = WorkoutImportPhase.Success,
                            imported = result,
                            confirmVisible = false
                        )
                    }
                }
                is WorkoutImportPersistenceResult.DuplicateWorkouts -> {
                    val names = plan.workouts
                        .filter { workout -> workout.workoutId in result.workoutIds }
                        .map { it.name }
                    fail(
                        WorkoutImportFailureKind.Duplicate(names),
                        current.copy(duplicateWorkoutIds = result.workoutIds, duplicateWorkoutNames = names)
                    )
                }
                is WorkoutImportPersistenceResult.PlanNotConfirmable ->
                    fail(WorkoutImportFailureKind.StaleCatalog, current)
                is WorkoutImportPersistenceResult.DatabaseError ->
                    fail(WorkoutImportFailureKind.Database, current)
            }
        }
    }

    private suspend fun parseSelectedFile(token: Long, bytes: ByteArray, displayName: String) {
        val parsed = withContext(ioDispatcher) {
            WorkoutImportCsv.parse(bytes, dateProvider.today())
        }
        if (token != generation) {
            return
        }
        when (parsed) {
            is WorkoutImportParseResult.Failure -> {
                state.update {
                    it.copy(
                        phase = WorkoutImportPhase.Failure,
                        fileName = displayName,
                        document = null,
                        plan = null,
                        catalog = emptyList(),
                        mappings = emptyList(),
                        unresolved = emptyList(),
                        failureKind = WorkoutImportFailureKind.Structural(parsed.errors)
                    )
                }
            }
            is WorkoutImportParseResult.Success -> {
                resolveDocument(token, parsed.document, displayName, emptyList())
            }
        }
    }

    private suspend fun resolveDocument(
        token: Long,
        document: WorkoutImportDocument,
        displayName: String,
        mappings: List<WorkoutImportMapping>
    ) {
        val snapshot = withContext(ioDispatcher) {
            val catalog = exerciseRepository.all()
            val measurements = weightRepository.all()
            val plan = WorkoutImportResolver.resolve(document, catalog, mappings, measurements)
            val fingerprints = sessionRepository.existingImportFingerprints()
            val completedNames = sessionRepository.completedWorkoutNames()
            ResolveSnapshot(catalog, plan, fingerprints, completedNames)
        }
        if (token != generation) {
            return
        }
        applyResolved(displayName, document, mappings, snapshot)
    }

    private fun applyResolved(
        displayName: String,
        document: WorkoutImportDocument,
        mappings: List<WorkoutImportMapping>,
        snapshot: ResolveSnapshot
    ) {
        val duplicateIds = snapshot.plan.workouts.mapNotNull { workout ->
            val fingerprint = WorkoutImportFingerprint.hash(workout)
            workout.workoutId.takeIf { fingerprint in snapshot.fingerprints }
        }
        val duplicateNames = snapshot.plan.workouts
            .filter { it.workoutId in duplicateIds }
            .map { it.name }
        val sameDateWarnings = snapshot.plan.workouts.mapNotNull { workout ->
            val conflict = snapshot.completedNames.any { (date, name) ->
                date == workout.workoutDate &&
                    TemplateNaming.normalize(name) == workout.normalizedName &&
                    workout.workoutId !in duplicateIds
            }
            if (!conflict) {
                null
            } else {
                WorkoutImportWarning(
                    rowNumber = workout.sourceRowNumber,
                    field = "workout_name",
                    code = WorkoutImportWarningCode.SameDateAndName,
                    detail = workout.name,
                    workoutId = workout.workoutId
                )
            }
        }
        val unresolved = unresolvedNames(document, snapshot.plan, snapshot.catalog, mappings)
        val phase = when {
            snapshot.plan.unresolvedNames.isNotEmpty() -> WorkoutImportPhase.NeedsMappings
            else -> WorkoutImportPhase.PreviewReady
        }
        state.update {
            it.copy(
                phase = phase,
                fileName = displayName,
                document = document,
                plan = snapshot.plan,
                catalog = snapshot.catalog,
                mappings = mappings,
                unresolved = unresolved,
                duplicateWorkoutIds = duplicateIds,
                duplicateWorkoutNames = duplicateNames,
                sameDateNameWarnings = sameDateWarnings,
                imported = null,
                failureKind = if (duplicateIds.isNotEmpty()) {
                    WorkoutImportFailureKind.Duplicate(duplicateNames)
                } else {
                    null
                },
                confirmVisible = false,
                mappingPickerIncoming = null,
                mappingQuery = ""
            )
        }
    }

    private fun unresolvedNames(
        document: WorkoutImportDocument,
        plan: WorkoutImportPlan,
        catalog: List<Exercise>,
        mappings: List<WorkoutImportMapping>
    ): List<WorkoutImportUnresolvedName> {
        val names = LinkedHashSet<String>()
        names += plan.unresolvedNames
        mappings.forEach { names += it.incomingNormalizedName }
        return names.map { normalized ->
            val occurrences = document.workouts.flatMap { workout ->
                workout.exercises.filter { it.normalizedName == normalized }.map { exercise ->
                    Triple(exercise.name, workout.name, workout.workoutDate)
                }
            }
            val selectedId = mappings.firstOrNull { it.incomingNormalizedName == normalized }?.catalogExerciseId
            val selected = catalog.firstOrNull { it.id == selectedId }
            WorkoutImportUnresolvedName(
                incomingName = occurrences.firstOrNull()?.first ?: normalized,
                normalizedName = normalized,
                occurrenceCount = occurrences.size,
                appearances = occurrences
                    .map { WorkoutImportAppearance(it.second, it.third) }
                    .distinct(),
                selectedExerciseId = selectedId,
                selectedExerciseName = selected?.name,
                errors = plan.errors.filter { it.incomingExerciseName == normalized },
                warnings = plan.warnings.filter { it.incomingExerciseName == normalized }
            )
        }
    }

    private fun fail(kind: WorkoutImportFailureKind, previous: WorkoutImportUiState = state.value) {
        state.update {
            previous.copy(
                phase = WorkoutImportPhase.Failure,
                failureKind = kind,
                confirmVisible = false
            )
        }
    }

    private data class ResolveSnapshot(
        val catalog: List<Exercise>,
        val plan: WorkoutImportPlan,
        val fingerprints: Set<String>,
        val completedNames: List<Pair<LocalDate, String>>
    )
}
