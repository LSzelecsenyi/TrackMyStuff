package app.mymusclemap.domain.achievements

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

enum class TargetWeightDirection {
    LOSS,
    GAIN,
    ALREADY_THERE;

    companion object {
        fun fromStorage(raw: String?): TargetWeightDirection? {
            return entries.firstOrNull { it.name == raw }
        }
    }
}

/**
 * One stored target journey. [baselineKg] and [direction] stay null until a weight exists.
 * Direction is fixed after that and is not recomputed from later measurements.
 */
data class TargetWeightGoalFacts(
    val id: Long,
    val targetKg: Double,
    val baselineKg: Double?,
    val direction: TargetWeightDirection?,
    val active: Boolean,
    val createdAt: Long = 0L
)

data class TargetWeightProgress(
    val goalId: Long,
    val baselineKg: Double,
    val currentKg: Double,
    val targetKg: Double,
    val direction: TargetWeightDirection,
    val totalDistanceKg: Double,
    val progressedKg: Double,
    val remainingKg: Double,
    val progressFraction: Double,
    val reached: Boolean
) {
    val remainingTowardTargetKg: Double = remainingKg.coerceAtLeast(0.0)
}

enum class WeightMilestone(val keySuffix: String, val priority: Int) {
    REACHED("reached", 5),
    REMAINING_1("remaining-1", 4),
    REMAINING_2("remaining-2", 3),
    REMAINING_5("remaining-5", 2),
    HALFWAY("halfway", 1);

    companion object {
        fun fromSuffix(suffix: String): WeightMilestone? = entries.firstOrNull { it.keySuffix == suffix }

        fun fromPayload(payload: String): WeightMilestone? = entries.firstOrNull { it.name == payload }
    }
}

/**
 * Direction-aware progress for one target. Distances use the same 0.1 kg rounding as logged weights.
 * The evaluator does not write storage.
 */
object TargetWeightProgressEvaluator {
    fun roundKg(kg: Double): Double {
        return BigDecimal.valueOf(kg).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    fun sameKg(left: Double, right: Double): Boolean = roundKg(left) == roundKg(right)

    fun direction(baselineKg: Double, targetKg: Double): TargetWeightDirection {
        val baseline = roundKg(baselineKg)
        val target = roundKg(targetKg)
        return when {
            baseline < target -> TargetWeightDirection.GAIN
            baseline > target -> TargetWeightDirection.LOSS
            else -> TargetWeightDirection.ALREADY_THERE
        }
    }

    fun progress(goal: TargetWeightGoalFacts, currentKg: Double?): TargetWeightProgress? {
        if (!goal.active) return null
        val baseline = goal.baselineKg ?: return null
        val direction = goal.direction ?: return null
        val current = currentKg ?: return null
        val baselineRounded = roundKg(baseline)
        val currentRounded = roundKg(current)
        val targetRounded = roundKg(goal.targetKg)
        val total = when (direction) {
            TargetWeightDirection.LOSS -> baselineRounded - targetRounded
            TargetWeightDirection.GAIN -> targetRounded - baselineRounded
            TargetWeightDirection.ALREADY_THERE -> 0.0
        }
        val progressed = when (direction) {
            TargetWeightDirection.LOSS -> baselineRounded - currentRounded
            TargetWeightDirection.GAIN -> currentRounded - baselineRounded
            TargetWeightDirection.ALREADY_THERE -> 0.0
        }
        val remaining = when (direction) {
            TargetWeightDirection.LOSS -> currentRounded - targetRounded
            TargetWeightDirection.GAIN -> targetRounded - currentRounded
            TargetWeightDirection.ALREADY_THERE -> currentRounded - targetRounded
        }
        val reached = when (direction) {
            TargetWeightDirection.LOSS -> currentRounded <= targetRounded
            TargetWeightDirection.GAIN -> currentRounded >= targetRounded
            TargetWeightDirection.ALREADY_THERE -> currentRounded == targetRounded
        }
        val fraction = when {
            reached || total <= 0.0 -> 1.0
            else -> (progressed / total).coerceIn(0.0, 1.0)
        }
        return TargetWeightProgress(
            goalId = goal.id,
            baselineKg = baselineRounded,
            currentKg = currentRounded,
            targetKg = targetRounded,
            direction = direction,
            totalDistanceKg = total,
            progressedKg = progressed,
            remainingKg = if (direction == TargetWeightDirection.ALREADY_THERE) {
                abs(remaining)
            } else {
                remaining
            },
            progressFraction = fraction,
            reached = reached
        )
    }

    /**
     * Milestones the current progress has crossed.
     * A kilogram threshold is included only when the original journey started strictly beyond it.
     */
    fun crossed(progress: TargetWeightProgress): List<WeightMilestone> {
        val total = progress.totalDistanceKg
        val remaining = progress.remainingKg
        val crossed = mutableListOf<WeightMilestone>()
        if (total > 5.0 && remaining <= 5.0) crossed += WeightMilestone.REMAINING_5
        if (total > 2.0 && remaining <= 2.0) crossed += WeightMilestone.REMAINING_2
        if (total > 1.0 && remaining <= 1.0) crossed += WeightMilestone.REMAINING_1
        if (total > 0.0 && progress.progressedKg / total >= 0.5) crossed += WeightMilestone.HALFWAY
        if (progress.reached) crossed += WeightMilestone.REACHED
        return crossed.sortedByDescending { it.priority }
    }

    /**
     * Intermediate milestones stay crossed while any in-journey weight still proves them,
     * so a later fluctuation cannot mint a second celebration.
     * Target reached follows the current weight only.
     */
    fun crossedForJourney(
        goal: TargetWeightGoalFacts,
        currentKg: Double?,
        historicalKg: List<Double>
    ): List<WeightMilestone> {
        val current = progress(goal, currentKg)
        val remembered = historicalKg.mapNotNull { sample -> progress(goal, sample) }
            .flatMap { crossed(it) }
            .filter { it != WeightMilestone.REACHED }
        val currentMilestones = current?.let { crossed(it) }.orEmpty()
        val reached = if (current?.reached == true) listOf(WeightMilestone.REACHED) else emptyList()
        return (remembered + currentMilestones.filter { it != WeightMilestone.REACHED } + reached)
            .distinct()
            .sortedByDescending { it.priority }
    }

    fun dedupeKey(goalId: Long, milestone: WeightMilestone): String {
        return "weight-goal:$goalId:${milestone.keySuffix}"
    }

    fun goalIdFromKey(key: String): Long? {
        val parts = key.split(':')
        if (parts.size != 3 || parts[0] != "weight-goal") return null
        if (WeightMilestone.fromSuffix(parts[2]) == null) return null
        return parts[1].toLongOrNull()?.takeIf { it > 0L }
    }
}
