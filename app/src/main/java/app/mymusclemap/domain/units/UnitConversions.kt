package app.mymusclemap.domain.units

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Exact factors for display and for a single conversion when the user edits a value.
 * Historical rows are not passed through these functions.
 */
object UnitConversions {
    val KG_TO_LB: BigDecimal = BigDecimal("2.2046226218487757")
    val IN_TO_CM: BigDecimal = BigDecimal("2.54")
    val FT_TO_M: BigDecimal = BigDecimal("0.3048")
    val MI_TO_M: BigDecimal = BigDecimal("1609.344")
    val M_PER_KM: BigDecimal = BigDecimal("1000")

    private const val DIVIDE_SCALE = 12

    fun kgToLb(kilograms: Double): BigDecimal = decimal(kilograms).multiply(KG_TO_LB)

    fun lbToKg(pounds: BigDecimal): BigDecimal = pounds.divide(KG_TO_LB, DIVIDE_SCALE, RoundingMode.HALF_UP)

    fun cmToIn(centimeters: Double): BigDecimal = decimal(centimeters).divide(IN_TO_CM, DIVIDE_SCALE, RoundingMode.HALF_UP)

    fun inToCm(inches: BigDecimal): BigDecimal = inches.multiply(IN_TO_CM)

    fun mToFt(meters: Double): BigDecimal = decimal(meters).divide(FT_TO_M, DIVIDE_SCALE, RoundingMode.HALF_UP)

    fun ftToM(feet: BigDecimal): BigDecimal = feet.multiply(FT_TO_M)

    fun mToMi(meters: Double): BigDecimal = decimal(meters).divide(MI_TO_M, DIVIDE_SCALE, RoundingMode.HALF_UP)

    fun miToM(miles: BigDecimal): BigDecimal = miles.multiply(MI_TO_M)

    fun mToKm(meters: Double): BigDecimal = decimal(meters).divide(M_PER_KM, DIVIDE_SCALE, RoundingMode.HALF_UP)

    fun kmToM(kilometers: BigDecimal): BigDecimal = kilometers.multiply(M_PER_KM)

    fun decimal(value: Double): BigDecimal = BigDecimal.valueOf(value)
}
