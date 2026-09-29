package app.mymusclemap.domain.health

import java.time.LocalDate

object HealthReadingMapper {
    fun steps(buckets: List<HealthMetricBucket>, today: LocalDate): List<DailyStepTotal> {
        return kept(buckets, today).map { (date, value) -> DailyStepTotal(date, value) }
    }

    fun restingHeartRate(
        buckets: List<HealthMetricBucket>,
        today: LocalDate
    ): List<DailyRestingHeartRate> {
        return kept(buckets, today).map { (date, value) -> DailyRestingHeartRate(date, value) }
    }

    private fun kept(buckets: List<HealthMetricBucket>, today: LocalDate): List<Pair<LocalDate, Long>> {
        val byDate = linkedMapOf<LocalDate, Long>()
        for (bucket in buckets) {
            val value = bucket.value ?: continue
            if (!HealthWindow.contains(bucket.date, today)) continue
            byDate[bucket.date] = value
        }
        return byDate.entries.sortedBy { it.key }.map { it.key to it.value }
    }
}
