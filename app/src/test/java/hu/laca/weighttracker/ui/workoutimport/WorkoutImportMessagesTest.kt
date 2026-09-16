package hu.laca.weighttracker.ui.workoutimport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportError
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportErrorCode
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportWarning
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportWarningCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutImportMessagesTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun unresolvedExerciseUsesHungarianName() {
        val text = WorkoutImportMessages.error(
            resources,
            WorkoutImportError(
                rowNumber = null,
                field = "exercise_name",
                code = WorkoutImportErrorCode.UnresolvedExercise,
                incomingExerciseName = "Wrist roll"
            )
        )
        assertEquals("A „Wrist roll” gyakorlathoz nincs katalógusbeli megfelelés.", text)
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
        assertEquals("A „Futás” gyakorlat távolságot és időt igényel.", text)
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
        assertEquals("A „Biceps curl” csak külső súlyos terheléssel importálható.", text)
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
        assertEquals("A 14. sorban hiányzik az ismétlésszám.", text)
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
        assertEquals("A „Mystery” gyakorlat kézi hozzárendeléssel került a katalógusba.", text)
    }
}
