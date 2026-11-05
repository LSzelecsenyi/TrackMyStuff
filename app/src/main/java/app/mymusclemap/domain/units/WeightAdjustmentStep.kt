package app.mymusclemap.domain.units

import java.math.BigDecimal

/**
 * Global plate jump used by the in-workout weight buttons.
 *
 * The value is stored and applied in the unit the user is looking at.
 * Workout drafts still persist kilograms. An imperial step is added to the
 * displayed pounds and then committed through the existing pound-to-kilogram
 * conversion. That conversion keeps two decimal kilograms, so a pound total
 * that cannot be represented exactly is rounded by the existing commit, not by
 * snapping the entered weight onto a multiple of the step.
 *
 * Each unit system keeps its own step. A missing or unreadable value uses
 * [WeightIncrements.defaultStep] for that system.
 */
data class WeightAdjustmentConfiguration(
    val metric: BigDecimal = WeightIncrements.defaultStep(UnitSystem.METRIC),
    val imperial: BigDecimal = WeightIncrements.defaultStep(UnitSystem.IMPERIAL)
) {
    fun stepFor(system: UnitSystem): BigDecimal {
        return if (system == UnitSystem.IMPERIAL) imperial else metric
    }

    fun withStep(system: UnitSystem, step: BigDecimal): WeightAdjustmentConfiguration {
        return if (system == UnitSystem.IMPERIAL) copy(imperial = step) else copy(metric = step)
    }
}

object WeightAdjustmentStep {
    const val MAX_DECIMALS = 2

    private val METRIC_PRESETS = listOf(
        BigDecimal("0.5"),
        BigDecimal("1"),
        BigDecimal("1.25"),
        BigDecimal("2.5"),
        BigDecimal("5")
    )
    private val IMPERIAL_PRESETS = listOf(
        BigDecimal("0.5"),
        BigDecimal("1"),
        BigDecimal("2.5"),
        BigDecimal("5"),
        BigDecimal("10")
    )
    private val METRIC_MAX = BigDecimal("500")
    private val IMPERIAL_MAX = BigDecimal("1102.3")

    fun presets(system: UnitSystem): List<BigDecimal> {
        return if (system == UnitSystem.IMPERIAL) IMPERIAL_PRESETS else METRIC_PRESETS
    }

    fun defaultFor(system: UnitSystem): BigDecimal = WeightIncrements.defaultStep(system)

    fun isPreset(value: BigDecimal, system: UnitSystem): Boolean {
        return presets(system).any { it.compareTo(value) == 0 }
    }

    /**
     * Accepts a positive finite decimal with at most [MAX_DECIMALS] places.
     * Zero, negatives, blanks, NaN, infinities, and other non-numeric text are rejected.
     */
    fun parse(raw: String, system: UnitSystem): BigDecimal? {
        val normalized = raw.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace(',', '.')
        if (normalized.isEmpty()) return null
        if (normalized.equals("NaN", ignoreCase = true) ||
            normalized.equals("Infinity", ignoreCase = true) ||
            normalized.equals("+Infinity", ignoreCase = true) ||
            normalized.equals("-Infinity", ignoreCase = true)
        ) {
            return null
        }
        if (!normalized.matches(Regex("""\d+(\.\d+)?"""))) return null
        val decimal = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return null
        }
        if (decimal.signum() <= 0) return null
        val stripped = decimal.stripTrailingZeros()
        val scale = stripped.scale()
        if (scale > MAX_DECIMALS) return null
        val maximum = if (system == UnitSystem.IMPERIAL) IMPERIAL_MAX else METRIC_MAX
        if (stripped.compareTo(maximum) > 0) return null
        return stripped
    }

    fun storedText(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
