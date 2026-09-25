package app.mymusclemap.domain.workoutimport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class WorkoutImportPreviewCopyTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun dateRangeFormatsEnglishInclusiveBounds() {
        val range = LocalDate.parse("2026-09-13")..LocalDate.parse("2026-09-15")
        assertEquals("Sep 13, 2026–Sep 15, 2026", WorkoutImportPreviewCopy.dateRange(range))
    }

    @Test
    fun perSideWeightUsesEnglishLabel() {
        val line = WorkoutImportPreviewCopy.setLine(
            resources,
            set(
                reps = 10,
                loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                weightKg = BigDecimal("17.5")
            ),
            WeightInterpretation.PER_SIDE
        )
        assertTrue(line.contains(resources.getString(R.string.workout_import_load_per_side)))
        assertTrue(line.contains("17.5 kg"))
    }

    @Test
    fun totalWeightUsesEnglishLabel() {
        val line = WorkoutImportPreviewCopy.setLine(
            resources,
            set(
                reps = 1,
                loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                weightKg = BigDecimal("7.5")
            ),
            WeightInterpretation.TOTAL
        )
        assertTrue(line.contains(resources.getString(R.string.workout_import_load_total)))
        assertTrue(line.contains("7.5 kg"))
    }

    @Test
    fun runningSetFormatsDistanceAndDuration() {
        val line = WorkoutImportPreviewCopy.setLine(
            resources,
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
