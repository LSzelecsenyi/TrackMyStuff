package app.mymusclemap.domain.units

import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.QuantityParser
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Display formatting. These strings are not written back to storage.
 * Metric weight labels match the historical one-decimal kilogram format.
 */
object MeasurementText {
    fun weightLabel(kilograms: Double, system: UnitSystem): String {
        val number = weightNumber(kilograms, system)
        return "$number ${weightUnit(system)}"
    }

    fun signedWeightLabel(kilograms: Double, system: UnitSystem): String {
        val formatted = weightLabel(abs(kilograms), system)
        return when {
            kilograms > 0.0 -> "+$formatted"
            kilograms < 0.0 -> "−$formatted"
            else -> formatted
        }
    }

    fun weightNumber(kilograms: Double, system: UnitSystem): String {
        val value = if (system == UnitSystem.IMPERIAL) {
            UnitConversions.kgToLb(kilograms)
        } else {
            UnitConversions.decimal(kilograms)
        }
        return oneDecimal(value)
    }

    fun weightUnit(system: UnitSystem): String = if (system == UnitSystem.IMPERIAL) "lb" else "kg"

    fun axisWeight(kilograms: Double, system: UnitSystem): String = weightNumber(kilograms, system)

    fun lengthLabel(centimeters: Double, system: UnitSystem): String {
        return if (system == UnitSystem.IMPERIAL) {
            "${lengthNumber(centimeters, system)} in"
        } else {
            "${oneDecimal(UnitConversions.decimal(centimeters))} cm"
        }
    }

    fun lengthNumber(centimeters: Double, system: UnitSystem): String {
        val value = if (system == UnitSystem.IMPERIAL) {
            UnitConversions.cmToIn(centimeters)
        } else {
            UnitConversions.decimal(centimeters)
        }
        return oneDecimal(value)
    }

    fun axisLength(centimeters: Double, percent: Boolean, system: UnitSystem): String {
        if (percent) return oneDecimal(UnitConversions.decimal(centimeters))
        return lengthNumber(centimeters, system)
    }

    /**
     * Workout load numbers. Metric keeps the existing plain kilogram text.
     * Imperial shows pounds at one decimal, without trailing zeros.
     */
    fun workoutWeightNumber(kilograms: Double, system: UnitSystem): String {
        if (system == UnitSystem.METRIC) {
            return plain(kilograms)
        }
        return plainScaled(UnitConversions.kgToLb(kilograms), 1)
    }

    fun workoutWeightLabel(kilograms: Double, system: UnitSystem): String {
        return "${workoutWeightNumber(kilograms, system)} ${weightUnit(system)}"
    }

    fun distanceNumber(meters: Double, system: UnitSystem): Pair<String, DistanceWord> {
        val absolute = abs(meters)
        return if (system == UnitSystem.IMPERIAL) {
            if (absolute >= UnitConversions.MI_TO_M.toDouble()) {
                plainScaled(UnitConversions.mToMi(absolute), 2) to DistanceWord.MILES
            } else {
                plainScaled(UnitConversions.mToFt(absolute), 1) to DistanceWord.FEET
            }
        } else if (absolute >= 1_000.0) {
            plain(QuantityParser.fromMeters(absolute, DistanceUnit.KILOMETERS)) to DistanceWord.KILOMETERS
        } else {
            plain(absolute) to DistanceWord.METERS
        }
    }

    fun axisDistance(meters: Double, system: UnitSystem): String = distanceNumber(meters, system).first

    fun volumeAmount(kilograms: Double, system: UnitSystem): String {
        val display = if (system == UnitSystem.IMPERIAL) {
            UnitConversions.kgToLb(kilograms).toDouble()
        } else {
            kilograms
        }
        val whole = display.toLong()
        return if (display == whole.toDouble()) {
            "%,d".format(AppLocale.UI, whole)
        } else {
            oneDecimal(UnitConversions.decimal(display))
        }
    }

    private fun oneDecimal(value: BigDecimal): String {
        return String.format(AppLocale.UI, "%.1f", value.toDouble())
    }

    private fun plain(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    private fun plainScaled(value: BigDecimal, scale: Int): String {
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

enum class DistanceWord {
    METERS,
    KILOMETERS,
    FEET,
    MILES
}
