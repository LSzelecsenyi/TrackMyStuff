package app.mymusclemap.domain.journal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.ExerciseHistorySelection
import app.mymusclemap.domain.workout.ExerciseHistorySet
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ExerciseHistoryCopyTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun repsAndTotalWeightUseTheRecordedValues() {
        val selection = selection(
            sets = listOf(
                recorded(0, reps = 10, weight = 12.0),
                recorded(1, reps = 8, weight = 12.0),
                recorded(2, reps = 8, weight = 12.0),
                recorded(3, reps = 7, weight = 12.0)
            )
        )
        assertEquals("8 · 12 kg", ExerciseHistoryCopy.inlineValue(resources, selection, selection.sets[1]))
        val table = ExerciseHistoryCopy.table(resources, selection, comparisonPosition = 1)
        assertEquals(listOf(HistoryColumn.SET, HistoryColumn.REPS, HistoryColumn.WEIGHT), table.columns)
        assertEquals(listOf("1", "10", "12 kg"), table.rows[0].cells)
        assertEquals(listOf("2", "8", "12 kg"), table.rows[1].cells)
        assertTrue(table.rows[1].comparison)
        assertFalse(table.rows[0].comparison)
        assertFalse(table.rows[1].cells[2].contains("total"))
    }

    @Test
    fun addedAssistanceAndPerSideKeepTheirLabels() {
        val added = selection(
            sets = listOf(
                recorded(0, reps = 8, kind = PlannedLoadKind.ADDED_WEIGHT, weight = 12.0)
            ),
            resistance = ResistanceBasis.BODYWEIGHT,
            measurement = MeasurementType.REPETITIONS
        )
        assertEquals("8 · +12 kg", ExerciseHistoryCopy.inlineValue(resources, added, added.sets.single()))

        val assistance = selection(
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            resistance = ResistanceBasis.BODYWEIGHT,
            measurement = MeasurementType.REPETITIONS,
            sets = listOf(
                recorded(0, reps = 6, kind = PlannedLoadKind.ASSISTANCE, weight = 10.0)
            )
        )
        val assistanceText = ExerciseHistoryCopy.inlineValue(resources, assistance, assistance.sets.single())
        assertEquals("6 · 10 kg assistance", assistanceText)
        assertFalse(assistanceText.contains("−") || assistanceText.contains("-10"))

        val perSide = selection(
            interpretation = WeightInterpretation.PER_SIDE,
            sets = listOf(recorded(0, reps = 8, weight = 12.0))
        )
        assertEquals(
            "8 · 12 kg per side",
            ExerciseHistoryCopy.inlineValue(resources, perSide, perSide.sets.single())
        )
    }

    @Test
    fun incompatibleLoadIsNotReinterpretedAsTheCurrentKind() {
        val selection = selection(
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(
                recorded(
                    position = 0,
                    reps = 8,
                    kind = PlannedLoadKind.EXTERNAL_WEIGHT,
                    weight = 12.0,
                    durationSeconds = 90
                )
            )
        )
        assertEquals("8", ExerciseHistoryCopy.inlineValue(resources, selection, selection.sets.single()))
        val table = ExerciseHistoryCopy.table(resources, selection, comparisonPosition = 0)
        assertFalse(table.columns.contains(HistoryColumn.DURATION))
        assertEquals("—", table.rows.single().cells[table.columns.indexOf(HistoryColumn.WEIGHT)])
    }

    @Test
    fun durationDistanceAndCompletionUseTheirOwnColumns() {
        val duration = selection(
            measurement = MeasurementType.DURATION,
            resistance = ResistanceBasis.NONE,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(
                ExerciseHistorySet(
                    position = 0,
                    status = SessionSetStatus.COMPLETED,
                    loadKind = PlannedLoadKind.NONE,
                    durationSeconds = 52
                )
            )
        )
        assertEquals("52 sec", ExerciseHistoryCopy.inlineValue(resources, duration, duration.sets.single()))
        assertEquals(
            listOf(HistoryColumn.SET, HistoryColumn.DURATION),
            ExerciseHistoryCopy.columns(MeasurementType.DURATION, ResistanceBasis.NONE)
        )

        val distance = selection(
            measurement = MeasurementType.DISTANCE_AND_DURATION,
            resistance = ResistanceBasis.NONE,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(
                ExerciseHistorySet(
                    position = 0,
                    status = SessionSetStatus.COMPLETED,
                    loadKind = PlannedLoadKind.NONE,
                    durationSeconds = 1720,
                    distanceMeters = 5200.0
                )
            )
        )
        assertEquals(
            "5.2 km · 28:40",
            ExerciseHistoryCopy.inlineValue(resources, distance, distance.sets.single())
        )

        val completion = selection(
            measurement = MeasurementType.COMPLETION_ONLY,
            resistance = ResistanceBasis.NONE,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(
                ExerciseHistorySet(
                    position = 0,
                    status = SessionSetStatus.COMPLETED,
                    loadKind = PlannedLoadKind.NONE
                )
            )
        )
        assertEquals("Done", ExerciseHistoryCopy.inlineValue(resources, completion, completion.sets.single()))
        assertEquals(
            listOf(HistoryColumn.SET, HistoryColumn.RESULT),
            ExerciseHistoryCopy.columns(MeasurementType.COMPLETION_ONLY, ResistanceBasis.NONE)
        )
    }

    private fun selection(
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.EXTERNAL,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL,
        sets: List<ExerciseHistorySet>
    ): ExerciseHistorySelection {
        return ExerciseHistorySelection(
            sessionId = 2L,
            templateId = 7L,
            templateName = "Push A",
            workoutDate = LocalDate.parse("2026-09-01"),
            startedAt = 100L,
            fromOtherPlan = false,
            measurementType = measurement,
            resistanceBasis = resistance,
            weightInterpretation = interpretation,
            sets = sets
        )
    }

    private fun recorded(
        position: Int,
        reps: Int?,
        weight: Double? = null,
        kind: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        durationSeconds: Int? = null
    ): ExerciseHistorySet {
        return ExerciseHistorySet(
            position = position,
            status = SessionSetStatus.COMPLETED,
            reps = reps,
            loadKind = kind,
            weightKg = weight,
            durationSeconds = durationSeconds
        )
    }
}
