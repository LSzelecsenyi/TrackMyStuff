package hu.laca.weighttracker.ui.workoutimport

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.data.workoutimport.FakeWorkoutImportFileReader
import hu.laca.weighttracker.data.workoutimport.WorkoutImportFileReadResult
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportCsv
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportDatabaseFailure
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportErrorCode
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportPersistenceResult
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportPlan
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportWarningCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutImportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.parse("2026-09-16")
    private val clock = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC)
    private val dateProvider = FixedDateProvider(today)
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var weights: WeightRepository
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var files: FakeWorkoutImportFileReader

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock,
            database.workoutSessionDao()
        )
        weights = WeightRepository(database.weightMeasurementDao(), clock)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            weights,
            clock,
            dateProvider
        )
        files = FakeWorkoutImportFileReader()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startsIdle() {
        val viewModel = viewModel()
        assertEquals(WorkoutImportPhase.Idle, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.canOpenConfirm)
        assertNull(viewModel.uiState.value.fileName)
    }

    @Test
    fun cancelledFileSelectionReturnsToIdleWithoutError() {
        val viewModel = viewModel()
        viewModel.onPickFileRequested()
        assertEquals(WorkoutImportPhase.SelectingFile, viewModel.uiState.value.phase)
        viewModel.onFileSelectionCancelled()
        assertEquals(WorkoutImportPhase.Idle, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.failureKind)
    }

    @Test
    fun validHistoricalFileParsesAutomatically() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(fixtureBytes(), "history.csv")
        val viewModel = viewModel()
        viewModel.onFileSelected("content://history.csv")
        val state = viewModel.awaitSettled()
        assertEquals(WorkoutImportPhase.PreviewReady, state.phase)
        assertEquals("history.csv", state.fileName)
        assertEquals(4, state.plan?.workoutCount)
        assertEquals(51, state.plan?.completedSetCount)
        assertEquals(10, state.plan?.workouts?.flatMap { workout ->
            workout.exercises.mapNotNull { it.snapshot?.exerciseId }
        }?.distinct()?.size)
        assertTrue(state.plan?.canConfirm == true)
        assertTrue(state.canOpenConfirm)
        assertTrue(state.unresolved.isEmpty())
    }

    @Test
    fun malformedFileStopsBeforeResolution() = runBlocking {
        files.result = WorkoutImportFileReadResult.Success(
            "this is not a workout csv".toByteArray(StandardCharsets.UTF_8),
            "bad.csv"
        )
        val viewModel = viewModel()
        viewModel.onFileSelected("content://bad.csv")
        val state = viewModel.awaitSettled()
        assertEquals(WorkoutImportPhase.Failure, state.phase)
        assertTrue(state.failureKind is WorkoutImportFailureKind.Structural)
        assertNull(state.plan)
        assertFalse(state.canOpenConfirm)
    }

    @Test
    fun structuralValidationFailureShowsErrors() = runBlocking {
        files.result = WorkoutImportFileReadResult.Success(ByteArray(0), "empty.csv")
        val viewModel = viewModel()
        viewModel.onFileSelected("content://empty.csv")
        val state = viewModel.awaitSettled()
        assertEquals(WorkoutImportPhase.Failure, state.phase)
        val errors = (state.failureKind as WorkoutImportFailureKind.Structural).errors
        assertTrue(errors.any { it.code == WorkoutImportErrorCode.EmptyFile })
    }

    @Test
    fun unresolvedNameRequiresManualMapping() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Mystery curl").toByteArray(StandardCharsets.UTF_8),
            "mystery.csv"
        )
        val viewModel = viewModel()
        viewModel.onFileSelected("content://mystery.csv")
        val state = viewModel.awaitSettled()
        assertEquals(WorkoutImportPhase.NeedsMappings, state.phase)
        assertEquals(listOf("mystery curl"), state.plan?.unresolvedNames)
        assertEquals("Mystery curl", state.unresolved.single().incomingName)
        assertEquals(1, state.unresolved.single().occurrenceCount)
        assertFalse(state.canOpenConfirm)
        assertFalse(state.plan?.canConfirm == true)
    }

    @Test
    fun mappingSelectionRecomputesPreview() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Mystery curl").toByteArray(StandardCharsets.UTF_8),
            "mystery.csv"
        )
        val viewModel = viewModel()
        viewModel.onFileSelected("content://mystery.csv")
        viewModel.awaitSettled()
        val pullup = exercises.observeAll().first().single { it.name == "Pullup" }
        viewModel.onMapExercise("mystery curl", pullup.id)
        val state = viewModel.awaitSettled()
        assertEquals(WorkoutImportPhase.PreviewReady, state.phase)
        assertTrue(state.plan?.canConfirm == true)
        assertTrue(state.plan?.warnings?.any { it.code == WorkoutImportWarningCode.ManualAliasMapping } == true)
        assertEquals("Pullup", state.unresolved.single().selectedExerciseName)
        assertTrue(state.canOpenConfirm)
    }

    @Test
    fun incompatibleMappingBlocksConfirmation() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Mystery curl").toByteArray(StandardCharsets.UTF_8),
            "mystery.csv"
        )
        val viewModel = viewModel()
        viewModel.onFileSelected("content://mystery.csv")
        viewModel.awaitSettled()
        val biceps = exercises.observeAll().first().single { it.name == "Biceps curl" }
        viewModel.onMapExercise("mystery curl", biceps.id)
        val state = viewModel.awaitSettled()
        assertFalse(state.plan?.canConfirm == true)
        assertFalse(state.canOpenConfirm)
        assertTrue(state.plan?.errors?.isNotEmpty() == true)
    }

    @Test
    fun staleMappingBlocksConfirmation() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Mystery curl").toByteArray(StandardCharsets.UTF_8),
            "mystery.csv"
        )
        val viewModel = viewModel()
        viewModel.onFileSelected("content://mystery.csv")
        viewModel.awaitSettled()
        viewModel.onMapExercise("mystery curl", 999_999L)
        val state = viewModel.awaitSettled()
        assertEquals(WorkoutImportPhase.NeedsMappings, state.phase)
        assertTrue(state.plan?.errors?.any { it.code == WorkoutImportErrorCode.StaleManualMapping } == true)
        assertFalse(state.canOpenConfirm)
    }

    @Test
    fun archivedMappingAddsWarningButAllowsConfirm() = runBlocking {
        seedCatalogAndWeights()
        val pullup = exercises.observeAll().first().single { it.name == "Pullup" }
        exercises.archive(pullup.id)
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Mystery curl").toByteArray(StandardCharsets.UTF_8),
            "mystery.csv"
        )
        val viewModel = viewModel()
        viewModel.onFileSelected("content://mystery.csv")
        viewModel.awaitSettled()
        viewModel.onMapExercise("mystery curl", pullup.id)
        val state = viewModel.awaitSettled()
        assertTrue(state.plan?.canConfirm == true)
        assertTrue(state.plan?.warnings?.any { it.code == WorkoutImportWarningCode.ArchivedExercise } == true)
        assertTrue(state.canOpenConfirm)
    }

    @Test
    fun canConfirmFalseDoesNotPersist() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Mystery curl").toByteArray(StandardCharsets.UTF_8),
            "mystery.csv"
        )
        var persistCount = 0
        val viewModel = viewModel { plan ->
            persistCount += 1
            sessions.importCompletedWorkouts(plan)
        }
        viewModel.onFileSelected("content://mystery.csv")
        viewModel.awaitSettled()
        viewModel.onConfirmImport()
        assertEquals(0, persistCount)
        assertEquals(0, database.workoutSessionDao().observeAll().first().size)
    }

    @Test
    fun doubleConfirmationIsIgnored() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(fixtureBytes(), "history.csv")
        var persistCount = 0
        val viewModel = viewModel { plan ->
            persistCount += 1
            sessions.importCompletedWorkouts(plan)
        }
        viewModel.onFileSelected("content://history.csv")
        assertTrue(viewModel.awaitSettled().canOpenConfirm)
        viewModel.onConfirmImport()
        viewModel.onConfirmImport()
        val done = viewModel.awaitSettled()
        assertEquals(1, persistCount)
        assertEquals(WorkoutImportPhase.Success, done.phase)
        assertEquals(4, done.imported?.workoutCount)
    }

    @Test
    fun persistenceSuccessUpdatesJournalObservers() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(fixtureBytes(), "history.csv")
        val viewModel = viewModel()
        viewModel.onFileSelected("content://history.csv")
        viewModel.awaitSettled()
        viewModel.onConfirmImport()
        assertEquals(WorkoutImportPhase.Success, viewModel.awaitSettled().phase)
        val completed = sessions.observeSummaries().first().filter { it.session.status == SessionStatus.COMPLETED }
        assertEquals(4, completed.size)
        val counts = sessions.observeCompletedCounts(
            LocalDate.parse("2026-09-01"),
            LocalDate.parse("2026-09-30")
        ).first()
        assertEquals(1, counts[LocalDate.parse("2026-09-13")])
        assertEquals(2, counts[LocalDate.parse("2026-09-14")])
        assertEquals(1, counts[LocalDate.parse("2026-09-15")])
        val details = completed.map { sessions.getAggregate(it.session.id)!! }
        assertEquals(51, details.sumOf { aggregate -> aggregate.exercises.sumOf { it.sets.size } })
        assertEquals(11, sessions.observeHeatmapExercises().first().size)
    }

    @Test
    fun duplicateFailureDisablesReplay() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(fixtureBytes(), "history.csv")
        val viewModel = viewModel()
        viewModel.onFileSelected("content://history.csv")
        viewModel.awaitSettled()
        viewModel.onConfirmImport()
        assertEquals(WorkoutImportPhase.Success, viewModel.awaitSettled().phase)
        val second = viewModel()
        second.onFileSelected("content://history.csv")
        val replay = second.awaitSettled()
        assertFalse(replay.canOpenConfirm)
        assertTrue(replay.duplicateWorkoutIds.isNotEmpty())
        second.onConfirmImport()
        assertEquals(4, database.workoutSessionDao().observeAll().first().size)
        assertEquals(51, database.workoutSessionDao().observeAllSets().first().size)
    }

    @Test
    fun transactionFailureNeverClaimsPartialSuccess() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(fixtureBytes(), "history.csv")
        val viewModel = viewModel {
            WorkoutImportPersistenceResult.DatabaseError(WorkoutImportDatabaseFailure.Unknown)
        }
        viewModel.onFileSelected("content://history.csv")
        viewModel.awaitSettled()
        viewModel.onConfirmImport()
        val failed = viewModel.awaitSettled()
        assertEquals(WorkoutImportPhase.Failure, failed.phase)
        assertEquals(WorkoutImportFailureKind.Database, failed.failureKind)
        assertNull(failed.imported)
        assertEquals(0, database.workoutSessionDao().observeAll().first().size)
    }

    @Test
    fun newFileClearsPreviousMappings() = runBlocking {
        seedCatalogAndWeights()
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Mystery curl").toByteArray(StandardCharsets.UTF_8),
            "mystery.csv"
        )
        val viewModel = viewModel()
        viewModel.onFileSelected("content://mystery.csv")
        viewModel.awaitSettled()
        val pullup = exercises.observeAll().first().single { it.name == "Pullup" }
        viewModel.onMapExercise("mystery curl", pullup.id)
        assertTrue(viewModel.awaitSettled().mappings.isNotEmpty())
        files.result = WorkoutImportFileReadResult.Success(
            oneSetCsv("Other mystery").toByteArray(StandardCharsets.UTF_8),
            "other.csv"
        )
        viewModel.onFileSelected("content://other.csv")
        val state = viewModel.awaitSettled()
        assertTrue(state.mappings.isEmpty())
        assertEquals("other.csv", state.fileName)
        assertEquals(listOf("other mystery"), state.plan?.unresolvedNames)
        assertNull(state.unresolved.single().selectedExerciseId)
    }

    private suspend fun WorkoutImportViewModel.awaitSettled(): WorkoutImportUiState {
        return withTimeout(10_000) {
            uiState.first { state ->
                state.phase != WorkoutImportPhase.Parsing &&
                    state.phase != WorkoutImportPhase.Importing &&
                    state.phase != WorkoutImportPhase.SelectingFile
            }
        }
    }

    private fun viewModel(
        persister: WorkoutImportPersister? = null
    ): WorkoutImportViewModel {
        return WorkoutImportViewModel(
            fileReader = files,
            exerciseRepository = exercises,
            weightRepository = weights,
            sessionRepository = sessions,
            dateProvider = dateProvider,
            persister = persister
        )
    }

    private suspend fun seedCatalogAndWeights() {
        saveBodyweight("Pullup", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.ABS))
        saveBodyweight("Chinup", MovementPattern.VERTICAL_PULL, MuscleGroup.LATS, listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.ABS))
        saveExternal("Biceps curl", MuscleGroup.BICEPS, listOf(MuscleGroup.FOREARMS), WeightInterpretation.PER_SIDE)
        saveExternal("Hammer curl", MuscleGroup.FOREARMS, listOf(MuscleGroup.BICEPS), WeightInterpretation.PER_SIDE)
        saveExternal("Wrist roll", MuscleGroup.FOREARMS, emptyList(), WeightInterpretation.TOTAL)
        saveBodyweight("Gyűrűn tolódzkodás", MovementPattern.VERTICAL_PUSH, MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS))
        saveBodyweight("Tolódzkodás", MovementPattern.VERTICAL_PUSH, MuscleGroup.TRICEPS, listOf(MuscleGroup.CHEST))
        saveBodyweight("Kézenállás kitolás", MovementPattern.VERTICAL_PUSH, MuscleGroup.FRONT_DELTOID, listOf(MuscleGroup.TRICEPS))
        saveBodyweight("Decline Pushup", MovementPattern.HORIZONTAL_PUSH, MuscleGroup.CHEST, listOf(MuscleGroup.FRONT_DELTOID, MuscleGroup.TRICEPS))
        exercises.save(
            ExerciseDraft(
                name = "Futás",
                category = ExerciseCategory.CARDIO,
                movementPattern = MovementPattern.CARDIO,
                measurementType = MeasurementType.DISTANCE_AND_DURATION,
                resistanceBasis = ResistanceBasis.NONE,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.QUADRICEPS,
                secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)
            )
        )
        listOf("2026-09-13", "2026-09-14", "2026-09-15").forEach { date ->
            weights.save(LocalDate.parse(date), 80.0)
        }
    }

    private suspend fun saveBodyweight(
        name: String,
        pattern: MovementPattern,
        primary: MuscleGroup,
        secondary: List<MuscleGroup>
    ) {
        exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = pattern,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = primary,
                secondaryMuscles = secondary
            )
        )
    }

    private suspend fun saveExternal(
        name: String,
        primary: MuscleGroup,
        secondary: List<MuscleGroup>,
        interpretation: WeightInterpretation
    ) {
        exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.ISOLATION,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = interpretation,
                primaryMuscle = primary,
                secondaryMuscles = secondary
            )
        )
    }

    private fun fixtureBytes(): ByteArray {
        return javaClass.getResource("/hu/laca/weighttracker/domain/workoutimport/history-v1-sample.csv")!!
            .readBytes()
    }

    private fun oneSetCsv(exerciseName: String): String {
        return WorkoutImportCsv.HEADER + "\n" + listOf(
            "1", "w1", "Pull", "2026-09-15", "2026-09-15T12:00:00", "2026-09-15T13:00:00", "", "",
            "1", exerciseName, "1", "COMPLETED", "5", "", "", "", "BODYWEIGHT_ONLY", ""
        ).joinToString(",") + "\n"
    }
}
