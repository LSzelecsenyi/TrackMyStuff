package hu.laca.weighttracker.domain.workoutimport

import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.BodyWeightProposal
import hu.laca.weighttracker.domain.workout.BodyWeightSnapshotLogic
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object WorkoutImportBodyWeight {
    fun propose(
        workoutDate: LocalDate,
        csvBodyWeightKg: BigDecimal?,
        measurements: List<WeightMeasurement>
    ): Pair<BodyWeightProposal, WorkoutImportWarningCode?> {
        val sameDay = measurements.filter { it.date == workoutDate }.maxByOrNull { it.id }
        val previous = measurements.filter { it.date.isBefore(workoutDate) }
            .maxWithOrNull(compareBy<WeightMeasurement> { it.date }.thenBy { it.id })
        if (csvBodyWeightKg != null) {
            val kilograms = csvBodyWeightKg.stripTrailingZeros().setScale(1, RoundingMode.HALF_UP).toDouble()
            val proposal = BodyWeightSnapshotLogic.afterManualEdit(kilograms)
            if (sameDay != null && !sameKilograms(sameDay.weightKg, kilograms)) {
                return proposal to WorkoutImportWarningCode.BodyWeightMismatch
            }
            return proposal to null
        }
        val proposal = BodyWeightSnapshotLogic.propose(workoutDate, sameDay, previous)
        val warning = when (proposal.source) {
            BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT -> WorkoutImportWarningCode.PreviousMeasurementFallback
            BodyWeightSource.UNKNOWN -> WorkoutImportWarningCode.UnknownBodyWeight
            BodyWeightSource.MEASURED_SAME_DAY, BodyWeightSource.MANUAL -> null
        }
        return proposal to warning
    }

    private fun sameKilograms(left: Double, right: Double): Boolean {
        val scaleLeft = BigDecimal.valueOf(left).setScale(1, RoundingMode.HALF_UP)
        val scaleRight = BigDecimal.valueOf(right).setScale(1, RoundingMode.HALF_UP)
        return scaleLeft.compareTo(scaleRight) == 0
    }
}
