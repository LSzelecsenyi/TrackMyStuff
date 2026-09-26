package app.mymusclemap.domain.model

/**
 * Generic numeric series used by the shared line chart.
 * Body-weight charts keep [ChartPoint] and map through [ChartPoint.toSeriesPoint].
 */
data class SeriesPoint(
    val date: java.time.LocalDate,
    val value: Double
)

enum class ChartValueDomain {
    /** Pads above and below the observed min/max. Used for body weight. */
    Padded,

    /** Floor is always 0; used for volume and other non-negative metrics. */
    NonNegative
}

object ChartScale {
    data class YBounds(val min: Double, val max: Double)

    fun yBounds(values: List<Double>, domain: ChartValueDomain): YBounds {
        if (values.isEmpty()) {
            return YBounds(min = 0.0, max = 1.0)
        }
        val minValue = values.min()
        val maxValue = values.max()
        val span = maxOf(maxValue - minValue, 0.0)
        return when (domain) {
            ChartValueDomain.Padded -> {
                val padding = if (span == 0.0) 1.0 else maxOf(span * 0.12, 0.3)
                YBounds(min = minValue - padding, max = maxValue + padding)
            }
            ChartValueDomain.NonNegative -> {
                val paddedMax = if (maxValue <= 0.0) 1.0 else maxOf(maxValue * 1.12, 0.3)
                YBounds(min = 0.0, max = paddedMax)
            }
        }
    }
}
