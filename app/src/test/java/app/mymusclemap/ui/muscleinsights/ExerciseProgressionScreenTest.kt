package app.mymusclemap.ui.muscleinsights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.muscleinsights.MuscleInsight
import app.mymusclemap.domain.muscleinsights.MuscleInsightTopic
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.performance.ComparisonDimension
import app.mymusclemap.domain.performance.ComparisonLimitation
import app.mymusclemap.domain.performance.ExercisePerformanceComparison
import app.mymusclemap.domain.performance.ExerciseProgressionDemo
import app.mymusclemap.domain.performance.MatchedPerformance
import app.mymusclemap.domain.performance.MuscleExercisePerformance
import app.mymusclemap.domain.performance.RecordedPerformanceOutcome
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.testQuantity
import app.mymusclemap.testString
import app.mymusclemap.domain.theme.ColorSchemeFactory
import app.mymusclemap.domain.theme.ColorScience
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class ExerciseProgressionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 16)
    private val referenceDay = LocalDate.of(2026, 10, 9)

    @Test
    fun rowsShowClassificationWithoutAStrengthScore() {
        render(state(sampleProgression()))
        composeRule.onNodeWithText(sectionTitle()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(improvedLine()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(lowerLine()).assertIsDisplayed()
        composeRule.onNodeWithText(stableLine()).assertIsDisplayed()
        composeRule.onNodeWithText(mixedLine()).assertIsDisplayed()
        composeRule.onNode(
            hasTestTag(progressionRowTag(5L)) and
                hasAnyDescendant(hasText(testString(R.string.exercise_progression_insufficient))),
            useUnmergedTree = true
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_not_comparable)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_unsupported)).assertIsDisplayed()
        composeRule.onNodeWithText("Declining").assertDoesNotExist()
        composeRule.onNodeWithText("Strength decreased").assertDoesNotExist()
        composeRule.onNodeWithText("%", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(
            testString(R.string.muscle_insights_comparison),
            ignoreCase = true
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(R.string.muscle_insights_recovery),
            ignoreCase = true
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detailSheetShowsEveryComparisonInEngineOrderAndDismissesWithoutChangingSelection() {
        val periods = mutableListOf<MuscleInsightsPeriod>()
        val muscles = mutableListOf<MuscleGroup>()
        render(
            state(sampleProgression()),
            onPeriodSelected = { periods += it },
            onMuscleSelected = { muscles += it }
        )
        composeRule.onNodeWithTag(progressionRowTag(4L)).performScrollTo().performClick()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_DETAIL).assertIsDisplayed()
        assertDatesReadPreviousThenCurrent()
        composeRule.onAllNodesWithText(testString(R.string.exercise_progression_previous)).assertCountEquals(1)
        val lighter = composeRule.onNodeWithText(testString(R.string.exercise_progression_kg, "60"))
            .getUnclippedBoundsInRoot().top
        val heavier = composeRule.onNodeWithText(testString(R.string.exercise_progression_kg, "80"))
            .getUnclippedBoundsInRoot().top
        val dates = composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_PREVIOUS)
            .getUnclippedBoundsInRoot().top
        assertTrue(dates < lighter)
        assertTrue(lighter < heavier)
        composeRule.onNodeWithText(testString(R.string.exercise_progression_overall, testString(R.string.exercise_progression_mixed)))
            .assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_close)).assertDoesNotExist()
        composeRule.onNode(
            hasContentDescription(testString(R.string.muscle_insights_close)) and
                hasTestTag(MUSCLE_INSIGHTS_PROGRESSION_CLOSE)
        ).performClick()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_DETAIL).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_period_4w)).assertIsDisplayed()
        composeRule.onNodeWithText(sectionTitle()).performScrollTo().assertIsDisplayed()
        assertTrue(periods.isEmpty())
        assertTrue(muscles.isEmpty())
    }

    @Test
    fun durationAddedWeightAndPerSideUseTheirOwnUnits() {
        render(state(formatProgression()))
        composeRule.onNodeWithText(durationLine()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(addedLine()).assertIsDisplayed()
        composeRule.onNodeWithText(perSideLine()).assertIsDisplayed()
        composeRule.onNodeWithTag(progressionRowTag(8L)).performClick()
        composeRule.onNodeWithText(
            testString(R.string.exercise_progression_kg_each_side, "20")
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(
                R.string.exercise_progression_transition,
                testQuantity(R.plurals.exercise_progression_reps, 8),
                testQuantity(R.plurals.exercise_progression_reps, 10)
            )
        ).assertIsDisplayed()
    }

    @Test
    fun insufficientAndUnsupportedSheetsExplainTheLimitation() {
        render(state(sampleProgression()))
        composeRule.onNodeWithTag(progressionRowTag(5L)).performScrollTo().performClick()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_insufficient_only)).assertIsDisplayed()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_PREVIOUS).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_previous)).assertDoesNotExist()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_CURRENT).assertIsDisplayed()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_CLOSE).performClick()
        composeRule.onNodeWithTag(progressionRowTag(7L)).performScrollTo().performClick()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_unsupported_body)).assertIsDisplayed()
    }

    @Test
    fun informationSheetExplainsTheComparisonAndDismisses() {
        val shown = mutableStateOf(state(sampleProgression()))
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(Modifier.width(360.dp).height(2400.dp).fillMaxSize()) {
                    MuscleInsightsScreen(
                        state = shown.value,
                        onBack = {},
                        onMuscleSelected = {},
                        onPeriodSelected = {},
                        onExplain = { shown.value = shown.value.copy(explanation = it) },
                        onDismissExplanation = { shown.value = shown.value.copy(explanation = null) },
                        onUpgrade = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_INFO).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_explain)).assertExists()
        composeRule.onNodeWithText("best completed set", substring = true).assertExists()
        composeRule.onNodeWithText("not the total repetitions", substring = true).assertExists()
        composeRule.onNodeWithText("higher load at the same repetitions", substring = true).assertExists()
        composeRule.onNodeWithText("longer duration", substring = true).assertExists()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_close)).performClick()
        assertEquals(null, shown.value.explanation)
        composeRule.onNodeWithText(sectionTitle()).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "hu-rHU-w360dp-h2400dp")
    fun hungarianTitleAndClassificationComeFromResources() {
        render(state(sampleProgression()))
        composeRule.onNodeWithText(sectionTitle()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(improvedLine()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(lowerLine()).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun singleComparisonOmitsTheRepeatedOverallRow() {
        render(state(sampleProgression()))
        composeRule.onNodeWithTag(progressionRowTag(1L)).performScrollTo().performClick()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_improved)).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(
                R.string.exercise_progression_overall,
                testString(R.string.exercise_progression_improved)
            )
        ).assertDoesNotExist()
        assertDatesReadPreviousThenCurrent()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_CLOSE).performClick()
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_DETAIL).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_period_4w)).assertIsDisplayed()
    }

    @Test
    fun multipleImprovementsDoNotRepeatTheSameOutcome() {
        render(
            state(
                performance(
                    listOf(
                        exercise(
                            11L,
                            "Dip",
                            RecordedPerformanceOutcome.IMPROVED,
                            listOf(repMatch(20.0, 6, 8), repMatch(30.0, 4, 6))
                        )
                    )
                )
            )
        )
        composeRule.onNodeWithTag(progressionRowTag(11L)).performScrollTo().performClick()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_improved)).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(
                R.string.exercise_progression_overall,
                testString(R.string.exercise_progression_improved)
            )
        ).assertDoesNotExist()
        composeRule.onAllNodesWithText(testString(R.string.exercise_progression_previous)).assertCountEquals(1)
    }

    @Test
    fun summaryColorIsMoreReadableThanTheMutedVariantOnTheCard() {
        listOf(
            ThemeSeeds.DefaultLight to false,
            ThemeSeeds.DefaultDark to true
        ).forEach { (seeds, dark) ->
            val scheme = ColorSchemeFactory.derive(seeds, dark)
            val card = scheme.surfaceContainer
            val summary = ColorScience.contrastRatio(scheme.onSurface, card)
            val muted = ColorScience.contrastRatio(scheme.onSurfaceVariant, card)
            assertTrue("$dark summary=$summary muted=$muted", summary > muted)
            assertTrue(summary >= 4.5)
        }
    }

    @Test
    fun rowExposesAnAccessibilityDescription() {
        render(state(sampleProgression()))
        val description = testString(
            R.string.exercise_progression_row_with_summary,
            "Bench press",
            testString(R.string.exercise_progression_improved),
            summaryAt(
                signedCount(2, R.plurals.exercise_progression_reps),
                testString(R.string.exercise_progression_kg, "80")
            ),
            testString(R.string.exercise_progression_open_details)
        )
        composeRule.onNode(hasContentDescription(description)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription(testString(R.string.exercise_progression_info))
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h2400dp", fontScale = 2f)
    fun narrowLayoutAndLargeTextKeepTheComparisonVisible() {
        render(state(sampleProgression()))
        composeRule.onNodeWithText(improvedLine()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_comparison_window)).assertIsDisplayed()
        composeRule.onNodeWithTag(progressionRowTag(1L)).performScrollTo().performClick()
        val title = composeRule.onNode(
            hasText("Bench press") and hasAnyAncestor(hasTestTag(MUSCLE_INSIGHTS_PROGRESSION_DETAIL)),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        val close = composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_CLOSE)
            .getUnclippedBoundsInRoot()
        assertTrue(close.left >= title.right)
        assertDatesReadPreviousThenCurrent()
        composeRule.onNode(
            hasContentDescription(testString(R.string.muscle_insights_close))
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.muscle_insights_close)).assertDoesNotExist()
    }

    @Test
    fun emptyMuscleShowsTheProgressionEmptyState() {
        render(state(performance(emptyList())))
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_EMPTY).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.exercise_progression_empty)).assertIsDisplayed()
    }

    @Test
    fun freeDemoRendersSampleProgressionAndKeepsTheDemoLabel() {
        val progression = ExerciseProgressionDemo.summarize(
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            today
        )
        render(state(progression, demo = true))
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_DEMO).assertIsDisplayed()
        composeRule.onNodeWithText("Bench press").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(mixedLine()).assertIsDisplayed()
        composeRule.onNodeWithText("Secret press").assertDoesNotExist()
        composeRule.onNodeWithText("Archive press").assertDoesNotExist()
    }

    @Test
    fun informationCallbackUsesTheProgressionTopic() {
        val opened = mutableListOf<MuscleInsightTopic>()
        render(state(sampleProgression()), onExplain = { opened += it })
        composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_INFO).performScrollTo().performClick()
        assertEquals(listOf(MuscleInsightTopic.EXERCISE_PROGRESSION), opened)
    }

    private fun render(
        state: MuscleInsightsUiState,
        onExplain: (MuscleInsightTopic) -> Unit = {},
        onDismissExplanation: () -> Unit = {},
        onPeriodSelected: (MuscleInsightsPeriod) -> Unit = {},
        onMuscleSelected: (MuscleGroup) -> Unit = {}
    ) {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(Modifier.width(360.dp).height(2400.dp).fillMaxSize()) {
                    MuscleInsightsScreen(
                        state = state,
                        onBack = {},
                        onMuscleSelected = onMuscleSelected,
                        onPeriodSelected = onPeriodSelected,
                        onExplain = onExplain,
                        onDismissExplanation = onDismissExplanation,
                        onUpgrade = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun state(
        progression: MuscleExercisePerformance,
        demo: Boolean = false
    ): MuscleInsightsUiState {
        return MuscleInsightsUiState(
            loading = false,
            demo = demo,
            muscle = progression.muscle,
            period = progression.period,
            insight = MuscleInsight.empty(progression.muscle),
            progression = progression
        )
    }

    private fun sampleProgression(): MuscleExercisePerformance {
        return performance(
            listOf(
                exercise(1L, "Bench press", RecordedPerformanceOutcome.IMPROVED, listOf(repMatch(80.0, 8, 10))),
                exercise(2L, "Row", RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, listOf(repMatch(60.0, 10, 8))),
                exercise(3L, "Curl", RecordedPerformanceOutcome.STABLE, listOf(repMatch(40.0, 8, 8))),
                exercise(
                    4L,
                    "Press",
                    RecordedPerformanceOutcome.MIXED,
                    listOf(repMatch(60.0, 10, 12), repMatch(80.0, 8, 5))
                ),
                exercise(5L, "Once", RecordedPerformanceOutcome.INSUFFICIENT_HISTORY, emptyList(), ComparisonLimitation.ONLY_ONE_OCCURRENCE),
                exercise(6L, "Mismatch", RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, emptyList(), ComparisonLimitation.NO_MATCHING_CONDITION),
                exercise(7L, "Carry", RecordedPerformanceOutcome.UNSUPPORTED, emptyList(), ComparisonLimitation.UNSUPPORTED_TYPE)
            )
        )
    }

    private fun formatProgression(): MuscleExercisePerformance {
        return performance(
            listOf(
                exercise(
                    8L,
                    "Lateral raise",
                    RecordedPerformanceOutcome.IMPROVED,
                    listOf(
                        repMatch(
                            kilograms = 40.0,
                            referenceReps = 8,
                            currentReps = 10,
                            interpretation = WeightInterpretation.PER_SIDE
                        )
                    )
                ),
                exercise(
                    9L,
                    "Weighted pull-up",
                    RecordedPerformanceOutcome.IMPROVED,
                    listOf(
                        MatchedPerformance(
                            dimension = ComparisonDimension.KILOGRAMS,
                            delta = 5.0,
                            current = 15.0,
                            reference = 10.0,
                            measurementType = MeasurementType.REPETITIONS,
                            resistanceBasis = ResistanceBasis.BODYWEIGHT,
                            loadKind = PlannedLoadKind.ADDED_WEIGHT,
                            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                            matchedEffectiveKilograms = null,
                            matchedRepetitions = 8
                        )
                    )
                ),
                exercise(
                    10L,
                    "Plank",
                    RecordedPerformanceOutcome.IMPROVED,
                    listOf(
                        MatchedPerformance(
                            dimension = ComparisonDimension.DURATION_SECONDS,
                            delta = 15.0,
                            current = 60.0,
                            reference = 45.0,
                            measurementType = MeasurementType.DURATION,
                            resistanceBasis = ResistanceBasis.BODYWEIGHT,
                            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                            matchedEffectiveKilograms = null,
                            matchedRepetitions = null
                        )
                    )
                )
            )
        )
    }

    private fun performance(exercises: List<ExercisePerformanceComparison>): MuscleExercisePerformance {
        return MuscleExercisePerformance(
            muscle = MuscleGroup.CHEST,
            period = MuscleInsightsPeriod.Weeks4,
            exercises = exercises,
            improved = exercises.count { it.outcome == RecordedPerformanceOutcome.IMPROVED },
            lowerRecordedPerformance = exercises.count {
                it.outcome == RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE
            },
            stable = exercises.count { it.outcome == RecordedPerformanceOutcome.STABLE },
            mixed = exercises.count { it.outcome == RecordedPerformanceOutcome.MIXED },
            insufficientOrNotComparable = exercises.count {
                it.outcome == RecordedPerformanceOutcome.INSUFFICIENT_HISTORY ||
                    it.outcome == RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE ||
                    it.outcome == RecordedPerformanceOutcome.UNSUPPORTED
            }
        )
    }

    private fun exercise(
        id: Long,
        name: String,
        outcome: RecordedPerformanceOutcome,
        comparisons: List<MatchedPerformance>,
        limitation: ComparisonLimitation = if (comparisons.isEmpty()) {
            ComparisonLimitation.ONLY_ONE_OCCURRENCE
        } else {
            ComparisonLimitation.NONE
        }
    ): ExercisePerformanceComparison {
        val compared = comparisons.isNotEmpty()
        return ExercisePerformanceComparison(
            exerciseId = id,
            displayName = name,
            currentSessionId = id,
            currentDate = today,
            currentCompletedAt = 2_000L,
            referenceSessionId = if (compared || outcome == RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE) 50L else null,
            referenceDate = if (compared) referenceDay else null,
            referenceCompletedAt = if (compared) 1_000L else null,
            outcome = outcome,
            limitation = limitation,
            comparisons = comparisons,
            includesZeroDelta = comparisons.any { it.delta == 0.0 },
            matchedConditionCount = comparisons.size
        )
    }

    private fun repMatch(
        kilograms: Double,
        referenceReps: Int,
        currentReps: Int,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL
    ): MatchedPerformance {
        return MatchedPerformance(
            dimension = ComparisonDimension.REPETITIONS,
            delta = (currentReps - referenceReps).toDouble(),
            current = currentReps.toDouble(),
            reference = referenceReps.toDouble(),
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistanceBasis = ResistanceBasis.EXTERNAL,
            loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            weightInterpretation = interpretation,
            matchedEffectiveKilograms = kilograms,
            matchedRepetitions = null
        )
    }

    private fun assertDatesReadPreviousThenCurrent() {
        val previous = composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_PREVIOUS)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val current = composeRule.onNodeWithTag(MUSCLE_INSIGHTS_PROGRESSION_CURRENT)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(previous.left < current.left)
        composeRule.onNode(
            hasContentDescription(
                testString(R.string.exercise_progression_reference_date, progressionDate(referenceDay))
            )
        ).assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription(
                testString(R.string.exercise_progression_current_date, progressionDate(today))
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(progressionDate(referenceDay)).assertIsDisplayed()
        composeRule.onNodeWithText(progressionDate(today)).assertIsDisplayed()
    }

    private fun sectionTitle(): String {
        return testString(R.string.exercise_progression_title).uppercase(Locale.getDefault())
    }

    private fun improvedLine(): String {
        return line(
            R.string.exercise_progression_improved,
            summaryAt(
                signedCount(2, R.plurals.exercise_progression_reps),
                testString(R.string.exercise_progression_kg, "80")
            )
        )
    }

    private fun lowerLine(): String {
        return line(
            R.string.exercise_progression_lower,
            summaryAt(
                signedCount(-2, R.plurals.exercise_progression_reps),
                testString(R.string.exercise_progression_kg, "60")
            )
        )
    }

    private fun stableLine(): String {
        return line(
            R.string.exercise_progression_stable,
            summaryAt(
                signedCount(0, R.plurals.exercise_progression_reps),
                testString(R.string.exercise_progression_kg, "40")
            )
        )
    }

    private fun mixedLine(): String {
        return line(
            R.string.exercise_progression_mixed,
            testString(R.string.exercise_progression_mixed_results)
        )
    }

    private fun durationLine(): String {
        return line(
            R.string.exercise_progression_improved,
            signedCount(15, R.plurals.exercise_progression_seconds)
        )
    }

    private fun addedLine(): String {
        return line(
            R.string.exercise_progression_improved,
            summaryAt(
                testString(R.string.exercise_progression_kg_added, "+5"),
                testQuantity(R.plurals.exercise_progression_reps, 8)
            )
        )
    }

    private fun perSideLine(): String {
        return line(
            R.string.exercise_progression_improved,
            summaryAt(
                signedCount(2, R.plurals.exercise_progression_reps),
                testString(R.string.exercise_progression_kg_each_side, "20")
            )
        )
    }

    private fun line(classification: Int, summary: String): String {
        return "${testString(classification)} · $summary"
    }

    private fun summaryAt(change: String, condition: String): String {
        return testString(R.string.exercise_progression_summary_at, change, condition)
    }

    private fun signedCount(delta: Int, plural: Int): String {
        val body = testQuantity(plural, kotlin.math.abs(delta))
        return when {
            delta > 0 -> "+$body"
            delta < 0 -> "\u2212$body"
            else -> body
        }
    }
}
