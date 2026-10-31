package app.mymusclemap.domain.units

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Turns a displayed measurement back into the canonical unit once, and only when
 * the text is not the untouched display of a stored value.
 */
object MeasurementInput {
    val BODY_MIN_KG: BigDecimal = BigDecimal("30.0")
    val BODY_MAX_KG: BigDecimal = BigDecimal("300.0")
    private val WORKOUT_MAX_KG = BigDecimal("500")
    private val LENGTH_TOLERANCE_CM = BigDecimal("0.05")

    fun sameText(left: String, right: String): Boolean = normalize(left) == normalize(right)

    fun bodyWeightInput(kilograms: Double, system: UnitSystem): String {
        return MeasurementText.weightNumber(kilograms, system)
    }

    fun lengthInput(centimeters: Double, system: UnitSystem): String {
        return MeasurementText.lengthNumber(centimeters, system)
    }

    /**
     * @return the kilograms to persist, or null when [text] is not a usable body weight.
     * An untouched display of [storedKilograms] returns that stored value.
     */
    fun resolveBodyWeightKg(text: String, system: UnitSystem, storedKilograms: Double?): Double? {
        if (storedKilograms != null && sameText(text, bodyWeightInput(storedKilograms, system))) {
            return storedKilograms
        }
        val number = parseDecimal(text, 1) ?: return null
        val kilograms = toKilograms(number, system)
        if (!bodyWeightInRange(number, kilograms, system)) return null
        return kilograms.toDouble()
    }

    fun resolveLengthCm(
        text: String,
        system: UnitSystem,
        storedCentimeters: Double?,
        minimumCm: Double,
        maximumCm: Double
    ): Double? {
        if (storedCentimeters != null && sameText(text, lengthInput(storedCentimeters, system))) {
            return storedCentimeters
        }
        val number = parseDecimal(text, 1) ?: return null
        val centimeters = if (system == UnitSystem.IMPERIAL) {
            UnitConversions.inToCm(number)
        } else {
            number
        }
        val minimum = BigDecimal.valueOf(minimumCm)
        val maximum = BigDecimal.valueOf(maximumCm)
        if (centimeters < minimum.subtract(LENGTH_TOLERANCE_CM) ||
            centimeters > maximum.add(LENGTH_TOLERANCE_CM)
        ) {
            return null
        }
        return centimeters.toDouble()
    }

    fun reformatBodyWeight(text: String, storedKilograms: Double?, from: UnitSystem, to: UnitSystem): String {
        if (from == to) return text
        if (storedKilograms != null && sameText(text, bodyWeightInput(storedKilograms, from))) {
            return bodyWeightInput(storedKilograms, to)
        }
        val number = parseDecimal(text, 1) ?: return text
        val kilograms = toKilograms(number, from)
        return bodyWeightInput(kilograms.toDouble(), to)
    }

    fun reformatLength(
        text: String,
        storedCentimeters: Double?,
        from: UnitSystem,
        to: UnitSystem
    ): String {
        if (from == to) return text
        if (storedCentimeters != null && sameText(text, lengthInput(storedCentimeters, from))) {
            return lengthInput(storedCentimeters, to)
        }
        val number = parseDecimal(text, 1) ?: return text
        val centimeters = if (from == UnitSystem.IMPERIAL) UnitConversions.inToCm(number) else number
        return lengthInput(centimeters.toDouble(), to)
    }

    private fun bodyWeightInRange(display: BigDecimal, kilograms: BigDecimal, system: UnitSystem): Boolean {
        if (system == UnitSystem.METRIC) {
            return kilograms >= BODY_MIN_KG && kilograms <= BODY_MAX_KG
        }
        val minimum = UnitConversions.kgToLb(BODY_MIN_KG.toDouble()).setScale(1, RoundingMode.HALF_UP)
        val maximum = UnitConversions.kgToLb(BODY_MAX_KG.toDouble()).setScale(1, RoundingMode.HALF_UP)
        return display >= minimum && display <= maximum
    }

    private fun toKilograms(display: BigDecimal, system: UnitSystem): BigDecimal {
        return if (system == UnitSystem.IMPERIAL) UnitConversions.lbToKg(display) else display
    }

    fun parseDecimal(raw: String, maxDecimals: Int): BigDecimal? {
        val normalized = normalize(raw)
        if (!normalized.matches(Regex("""\d+(\.\d+)?"""))) return null
        val decimal = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return null
        }
        val scale = decimal.stripTrailingZeros().scale()
        if (scale > maxDecimals) return null
        return decimal
    }

    fun workoutKgAcceptable(kilograms: BigDecimal): Boolean {
        return kilograms > BigDecimal.ZERO && kilograms <= WORKOUT_MAX_KG
    }

    private fun normalize(raw: String): String {
        return raw.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace(',', '.')
    }
}
