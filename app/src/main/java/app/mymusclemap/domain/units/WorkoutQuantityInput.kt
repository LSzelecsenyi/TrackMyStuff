package app.mymusclemap.domain.units

import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.QuantityParser
import java.math.BigDecimal
import java.math.RoundingMode

object WeightIncrements {
    fun defaultStep(system: UnitSystem): BigDecimal {
        return if (system == UnitSystem.IMPERIAL) BigDecimal("5") else BigDecimal("2.5")
    }

    fun fineStep(system: UnitSystem): BigDecimal {
        return if (system == UnitSystem.IMPERIAL) BigDecimal.ONE else BigDecimal("0.5")
    }
}

/**
 * Workout drafts keep weight text in kilograms. Imperial editing converts only
 * when the displayed text differs from the formatted canonical value.
 */
object WorkoutWeightInput {
    private val MAX_KG = BigDecimal("500")
    private val MAX_LB = BigDecimal("1102.3")

    fun displayOf(canonicalText: String, system: UnitSystem): String {
        if (system == UnitSystem.METRIC) return canonicalText
        val kilograms = MeasurementInput.parseDecimal(canonicalText, QuantityParser.WEIGHT_MAX_DECIMALS)
            ?: return canonicalText
        return formatNumber(kilograms.multiply(UnitConversions.KG_TO_LB), 1)
    }

    fun commit(canonicalText: String, displayText: String, system: UnitSystem): String {
        if (MeasurementInput.sameText(displayText, displayOf(canonicalText, system))) {
            return canonicalText
        }
        if (system == UnitSystem.METRIC) return displayText
        val pounds = MeasurementInput.parseDecimal(displayText, 1) ?: return displayText
        if (pounds <= BigDecimal.ZERO || pounds > MAX_LB) return displayText
        val kilograms = pounds.divide(UnitConversions.KG_TO_LB, 2, RoundingMode.HALF_UP)
        if (!MeasurementInput.workoutKgAcceptable(kilograms) || kilograms > MAX_KG) return displayText
        return formatNumber(kilograms, 2)
    }

    fun step(displayText: String, system: UnitSystem, direction: Int, fine: Boolean): String {
        val increment = if (fine) WeightIncrements.fineStep(system) else WeightIncrements.defaultStep(system)
        val scale = if (system == UnitSystem.IMPERIAL) 1 else 2
        val current = MeasurementInput.parseDecimal(displayText, scale)
        val base = when {
            current != null -> current
            direction > 0 && displayText.isBlank() -> BigDecimal.ZERO
            else -> return displayText
        }
        val next = base.add(increment.multiply(BigDecimal.valueOf(direction.toLong())))
        if (next <= BigDecimal.ZERO) return displayText
        val max = if (system == UnitSystem.IMPERIAL) MAX_LB else MAX_KG
        if (next > max) return displayText
        return formatNumber(next, scale)
    }

    fun reexpress(displayText: String, from: UnitSystem, to: UnitSystem): String {
        if (from == to) return displayText
        val scale = if (from == UnitSystem.IMPERIAL) 1 else 2
        val number = MeasurementInput.parseDecimal(displayText, scale) ?: return displayText
        val kilograms = if (from == UnitSystem.IMPERIAL) {
            number.divide(UnitConversions.KG_TO_LB, 8, RoundingMode.HALF_UP)
        } else {
            number
        }
        return if (to == UnitSystem.IMPERIAL) {
            formatNumber(kilograms.multiply(UnitConversions.KG_TO_LB), 1)
        } else {
            formatNumber(kilograms, 2)
        }
    }

    private fun formatNumber(value: BigDecimal, scale: Int): String {
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

enum class DistanceDisplay {
    METERS,
    KILOMETERS,
    FEET,
    MILES
}

/**
 * Distance drafts stay in meters or the kilometers the user already typed.
 * Imperial display converts for viewing and writes meters only after an edit.
 */
object WorkoutDistanceInput {
    fun meters(text: String, unit: DistanceUnit): Double? {
        val number = MeasurementInput.parseDecimal(text, QuantityParser.DISTANCE_MAX_DECIMALS) ?: return null
        return QuantityParser.toMeters(number.toDouble(), unit)
    }

    fun displayOf(meters: Double?, display: DistanceDisplay): String {
        if (meters == null) return ""
        val value = when (display) {
            DistanceDisplay.METERS -> UnitConversions.decimal(meters)
            DistanceDisplay.KILOMETERS -> UnitConversions.mToKm(meters)
            DistanceDisplay.FEET -> UnitConversions.mToFt(meters)
            DistanceDisplay.MILES -> UnitConversions.mToMi(meters)
        }
        val scale = when (display) {
            DistanceDisplay.MILES -> 2
            DistanceDisplay.KILOMETERS -> 3
            else -> 1
        }
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /**
     * @return meter text to store, or null when [displayText] is still the formatted
     * view of [meters] and must not be written.
     */
    fun commitMeters(meters: Double?, displayText: String, display: DistanceDisplay): String? {
        if (MeasurementInput.sameText(displayText, displayOf(meters, display))) return null
        val scale = when (display) {
            DistanceDisplay.MILES -> 2
            DistanceDisplay.KILOMETERS -> 3
            else -> 1
        }
        val number = MeasurementInput.parseDecimal(displayText, scale) ?: return displayText
        if (number <= BigDecimal.ZERO) return displayText
        val asMeters = when (display) {
            DistanceDisplay.METERS -> number
            DistanceDisplay.KILOMETERS -> UnitConversions.kmToM(number)
            DistanceDisplay.FEET -> UnitConversions.ftToM(number)
            DistanceDisplay.MILES -> UnitConversions.miToM(number)
        }
        if (asMeters.toDouble() > QuantityParser.MAX_DISTANCE_METERS) return displayText
        return asMeters.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
