package app.mymusclemap.ui.workoutimport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workoutimport.WorkoutImportError
import app.mymusclemap.domain.workoutimport.WorkoutImportErrorCode
import app.mymusclemap.domain.workoutimport.WorkoutImportWarning
import app.mymusclemap.domain.workoutimport.WorkoutImportWarningCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutImportMessagesTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun unresolvedExerciseUsesIncomingName() {
        val text = WorkoutImportMessages.error(
            resources,
            WorkoutImportError(
                rowNumber = null,
                field = "exercise_name",
                code = WorkoutImportErrorCode.UnresolvedExercise,
                incomingExerciseName = "Wrist roll"
            )
        )
        assertEquals(
            resources.getString(R.string.workout_import_error_unresolved, "Wrist roll"),
            text
        )
        assertFalse(text.contains("UNRESOLVED", ignoreCase = true))
    }

    @Test
    fun runningMeasurementUsesDistanceAndDurationCopy() {
        val text = WorkoutImportMessages.error(
            resources,
            WorkoutImportError(
                rowNumber = 40,
                field = "distance",
                code = WorkoutImportErrorCode.MissingRequiredActual,
                detail = MeasurementType.DISTANCE_AND_DURATION.name,
                incomingExerciseName = "Futás"
            )
        )
        assertEquals(
            resources.getString(R.string.workout_import_error_needs_distance_duration, "Futás"),
            text
        )
        assertFalse(text.contains("DISTANCE_AND_DURATION"))
    }

    @Test
    fun bicepsLoadIncompatibilityUsesExternalWeightCopy() {
        val text = WorkoutImportMessages.error(
            resources,
            WorkoutImportError(
                rowNumber = 10,
                field = "load_kind",
                code = WorkoutImportErrorCode.IncompatibleLoadKind,
                detail = PlannedLoadKind.BODYWEIGHT_ONLY.name,
                incomingExerciseName = "Biceps curl"
            )
        )
        assertEquals(
            resources.getString(R.string.workout_import_error_incompatible_load, "Biceps curl"),
            text
        )
    }

    @Test
    fun missingRepsUsesRowNumber() {
        val text = WorkoutImportMessages.error(
            resources,
            WorkoutImportError(
                rowNumber = 14,
                field = "reps",
                code = WorkoutImportErrorCode.MissingRequiredActual,
                incomingExerciseName = "Pullup"
            )
        )
        assertEquals(
            resources.getString(R.string.workout_import_error_missing_reps, 14),
            text
        )
    }

    @Test
    fun warningsStayNonTechnical() {
        val text = WorkoutImportMessages.warning(
            resources,
            WorkoutImportWarning(
                rowNumber = null,
                field = null,
                code = WorkoutImportWarningCode.ManualAliasMapping,
                incomingExerciseName = "Mystery"
            )
        )
        assertEquals(
            resources.getString(R.string.workout_import_warning_manual, "Mystery"),
            text
        )
    }
}
