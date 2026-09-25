package app.mymusclemap.domain.workoutimport

import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class WorkoutImportPreviewCopyTest {
    @Test
    fun dateRangeFormatsHungarianInclusiveBounds() {
        val range = LocalDate.parse("2026-09-13")..LocalDate.parse("2026-09-15")
        assertEquals("2026.09.13–2026.09.15", WorkoutImportPreviewCopy.dateRange(range))
    }

    @Test
    fun perSideWeightUsesHungarianLabel() {
        val line = WorkoutImportPreviewCopy.setLine(
            set(
                reps = 10,
                loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                weightKg = BigDecimal("17.5")
            ),
            WeightInterpretation.PER_SIDE
        )
        assertTrue(line.contains("kézenként"))
        assertTrue(line.contains("17,5 kg") || line.contains("17.5 kg"))
    }

    @Test
    fun totalWeightUsesHungarianLabel() {
        val line = WorkoutImportPreviewCopy.setLine(
            set(
                reps = 1,
                loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                weightKg = BigDecimal("7.5")
            ),
            WeightInterpretation.TOTAL
        )
        assertTrue(line.contains("összesen"))
        assertTrue(line.contains("7,5 kg") || line.contains("7.5 kg"))
    }

    @Test
    fun runningSetFormatsDistanceAndDuration() {
        val line = WorkoutImportPreviewCopy.setLine(
            set(
                durationSeconds = 720,
                distanceMeters = BigDecimal("2000"),
                loadKind = PlannedLoadKind.NONE
            ),
            WeightInterpretation.NOT_APPLICABLE
        )
        assertTrue(line, line.contains("2 km"))
        assertTrue(line, line.contains("12:00"))
    }

    private fun set(
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: BigDecimal? = null,
        loadKind: PlannedLoadKind?,
        weightKg: BigDecimal? = null
    ) = WorkoutImportResolvedSet(
        sourceRowNumber = 2,
        setIndex = 1,
        status = app.mymusclemap.domain.workout.SessionSetStatus.COMPLETED,
        reps = reps,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        loadKind = loadKind,
        weightKg = weightKg
    )
}
