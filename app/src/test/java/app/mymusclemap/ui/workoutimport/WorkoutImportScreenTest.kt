package app.mymusclemap.ui.workoutimport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.workoutimport.WorkoutImportCsv
import app.mymusclemap.domain.workoutimport.WorkoutImportError
import app.mymusclemap.domain.workoutimport.WorkoutImportErrorCode
import app.mymusclemap.domain.workoutimport.WorkoutImportParseResult
import app.mymusclemap.domain.workoutimport.WorkoutImportResolver
import app.mymusclemap.domain.workoutimport.WorkoutImportWarning
import app.mymusclemap.domain.workoutimport.WorkoutImportWarningCode
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp")
class WorkoutImportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historicalFixtureSummaryIsConfirmable() {
        val plan = resolvedFixture()
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                WorkoutImportScreen(
                    state = WorkoutImportUiState(
                        phase = WorkoutImportPhase.PreviewReady,
                        fileName = "weighttracker-history-2026-09.csv",
                        plan = plan
                    ),
                    onBack = {},
                    onPickFile = {},
                    onFilePicked = {},
                    onOpenMappingPicker = {},
                    onDismissMappingPicker = {},
                    onMappingQueryChange = {},
                    onMapExercise = { _, _ -> },
                    onToggleWorkout = {},
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirmImport = {},
                    onViewJournal = {}
                )
            }
        }
        composeRule.onNodeWithText("4 edzés").assertIsDisplayed()
        composeRule.onNodeWithText("Időszak: 2026.09.13–2026.09.15").assertIsDisplayed()
        composeRule.onNodeWithText("10 különböző gyakorlat").assertIsDisplayed()
        composeRule.onNodeWithText("51 teljesített sorozat").assertIsDisplayed()
        composeRule.onNodeWithText("0 kihagyott sorozat").assertIsDisplayed()
        composeRule.onAllNodesWithText("Az aznapi testsúlymérésből", substring = true)
            .onFirst()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("workout_import_confirm").assertIsEnabled()
    }

    @Test
    fun warningsDoNotBlockConfirmation() {
        val plan = resolvedFixture()
        val withWarning = plan.copy(
            warnings = plan.warnings + WorkoutImportWarning(
                rowNumber = null,
                field = null,
                code = WorkoutImportWarningCode.ArchivedExercise,
                incomingExerciseName = "Pullup"
            )
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                WorkoutImportScreen(
                    state = WorkoutImportUiState(
                        phase = WorkoutImportPhase.PreviewReady,
                        fileName = "history.csv",
                        plan = withWarning
                    ),
                    onBack = {},
                    onPickFile = {},
                    onFilePicked = {},
                    onOpenMappingPicker = {},
                    onDismissMappingPicker = {},
                    onMappingQueryChange = {},
                    onMapExercise = { _, _ -> },
                    onToggleWorkout = {},
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirmImport = {},
                    onViewJournal = {}
                )
            }
        }
        composeRule.onNodeWithText("A „Pullup” gyakorlat archivált.").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_import_confirm").assertIsEnabled()
    }

    @Test
    fun errorsBlockConfirmation() {
        val plan = resolvedFixture()
        val blocked = plan.copy(
            errors = listOf(
                WorkoutImportError(
                    rowNumber = 14,
                    field = "reps",
                    code = WorkoutImportErrorCode.MissingRequiredActual,
                    incomingExerciseName = "Pullup"
                )
            )
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                WorkoutImportScreen(
                    state = WorkoutImportUiState(
                        phase = WorkoutImportPhase.PreviewReady,
                        fileName = "history.csv",
                        plan = blocked
                    ),
                    onBack = {},
                    onPickFile = {},
                    onFilePicked = {},
                    onOpenMappingPicker = {},
                    onDismissMappingPicker = {},
                    onMappingQueryChange = {},
                    onMapExercise = { _, _ -> },
                    onToggleWorkout = {},
                    onRequestConfirm = {},
                    onDismissConfirm = {},
                    onConfirmImport = {},
                    onViewJournal = {}
                )
            }
        }
        composeRule.onNodeWithText("A 14. sorban hiányzik az ismétlésszám.").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_import_confirm").assertDoesNotExist()
    }

    @Test
    fun mappingListAndPreviewStayUsableOnNarrowHighFont() {
        val catalog = intendedCatalog()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1.3f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(640.dp)
                    ) {
                        WorkoutImportScreen(
                            state = WorkoutImportUiState(
                                phase = WorkoutImportPhase.NeedsMappings,
                                fileName = "history.csv",
                                catalog = catalog,
                                mappingPickerIncoming = "mystery curl",
                                unresolved = listOf(
                                    WorkoutImportUnresolvedName(
                                        incomingName = "Mystery curl",
                                        normalizedName = "mystery curl",
                                        occurrenceCount = 1,
                                        appearances = listOf(
                                            WorkoutImportAppearance("Pull", LocalDate.parse("2026-09-15"))
                                        )
                                    )
                                )
                            ),
                            onBack = {},
                            onPickFile = {},
                            onFilePicked = {},
                            onOpenMappingPicker = {},
                            onDismissMappingPicker = {},
                            onMappingQueryChange = {},
                            onMapExercise = { _, _ -> },
                            onToggleWorkout = {},
                            onRequestConfirm = {},
                            onDismissConfirm = {},
                            onConfirmImport = {},
                            onViewJournal = {}
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("workout_import_mapping_picker").assertIsDisplayed()
        composeRule.onNodeWithText("Biceps curl").assertIsDisplayed()
        val bounds = composeRule.onNodeWithText("Biceps curl").getUnclippedBoundsInRoot()
        val labelWidth = bounds.right.value - bounds.left.value
        assertTrue("label collapsed to one character: $labelWidth", labelWidth > 48f)
    }

    @Test
    fun previewScrollKeepsConfirmReachable() {
        val plan = resolvedFixture()
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(modifier = Modifier.fillMaxSize().height(640.dp)) {
                    WorkoutImportScreen(
                        state = WorkoutImportUiState(
                            phase = WorkoutImportPhase.PreviewReady,
                            fileName = "history.csv",
                            plan = plan
                        ),
                        onBack = {},
                        onPickFile = {},
                        onFilePicked = {},
                        onOpenMappingPicker = {},
                        onDismissMappingPicker = {},
                        onMappingQueryChange = {},
                        onMapExercise = { _, _ -> },
                        onToggleWorkout = {},
                        onRequestConfirm = {},
                        onDismissConfirm = {},
                        onConfirmImport = {},
                        onViewJournal = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag("workout_import_confirm").assertIsDisplayed()
    }

    private fun resolvedFixture() = WorkoutImportResolver.resolve(
        (WorkoutImportCsv.parse(fixture(), LocalDate.parse("2026-09-16")) as WorkoutImportParseResult.Success).document,
        intendedCatalog(),
        measurements = listOf(
            WeightMeasurement(1, LocalDate.parse("2026-09-13"), 80.0, 1L, 1L),
            WeightMeasurement(2, LocalDate.parse("2026-09-14"), 80.0, 1L, 1L),
            WeightMeasurement(3, LocalDate.parse("2026-09-15"), 80.0, 1L, 1L)
        )
    )

    private fun fixture(): String {
        return javaClass.getResource("/app/mymusclemap/domain/workoutimport/history-v1-sample.csv")!!.readText()
    }

    private fun intendedCatalog(): List<Exercise> {
        return listOf(
            exercise(7, "Pullup", pattern = MovementPattern.VERTICAL_PULL, primary = MuscleGroup.LATS),
            exercise(8, "Chinup", pattern = MovementPattern.VERTICAL_PULL, primary = MuscleGroup.LATS),
            exercise(9, "Biceps curl", resistance = ResistanceBasis.EXTERNAL, interpretation = WeightInterpretation.PER_SIDE, primary = MuscleGroup.BICEPS),
            exercise(10, "Hammer curl", resistance = ResistanceBasis.EXTERNAL, interpretation = WeightInterpretation.PER_SIDE, primary = MuscleGroup.FOREARMS),
            exercise(12, "Wrist roll", resistance = ResistanceBasis.EXTERNAL, interpretation = WeightInterpretation.TOTAL, primary = MuscleGroup.FOREARMS),
            exercise(1, "Gyűrűn tolódzkodás", pattern = MovementPattern.VERTICAL_PUSH, primary = MuscleGroup.CHEST),
            exercise(2, "Tolódzkodás", pattern = MovementPattern.VERTICAL_PUSH, primary = MuscleGroup.TRICEPS),
            exercise(3, "Kézenállás kitolás", pattern = MovementPattern.VERTICAL_PUSH, primary = MuscleGroup.FRONT_DELTOID),
            exercise(4, "Decline Pushup", pattern = MovementPattern.HORIZONTAL_PUSH, primary = MuscleGroup.CHEST),
            exercise(
                11,
                "Futás",
                category = ExerciseCategory.CARDIO,
                pattern = MovementPattern.CARDIO,
                measurement = MeasurementType.DISTANCE_AND_DURATION,
                resistance = ResistanceBasis.NONE,
                primary = MuscleGroup.QUADRICEPS
            )
        )
    }

    private fun exercise(
        id: Long,
        name: String,
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        pattern: MovementPattern = MovementPattern.ISOLATION,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.BODYWEIGHT,
        interpretation: WeightInterpretation = WeightInterpretation.NOT_APPLICABLE,
        primary: MuscleGroup = MuscleGroup.LATS
    ) = Exercise(
        id = id,
        name = name,
        normalizedName = ExerciseNaming.normalize(name),
        category = category,
        movementPattern = pattern,
        measurementType = measurement,
        resistanceBasis = resistance,
        weightInterpretation = interpretation,
        primaryMuscle = primary,
        secondaryMuscles = emptyList(),
        notes = null,
        archived = false,
        createdAt = 1L,
        updatedAt = 1L
    )
}
