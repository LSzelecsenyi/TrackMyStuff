package app.mymusclemap.domain.entitlement

import java.time.Duration
import java.time.Instant

/**
 * Remaining time until the same expiration instant the entitlement resolver uses.
 * The warning title is built from this value, so a short debug trial is not described as two days.
 */
fun formatTrialRemaining(now: Instant, expiresAt: Instant): String {
    val remaining = Duration.between(now, expiresAt)
    if (remaining.isZero || remaining.isNegative) {
        return "0 seconds"
    }
    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    val minutes = remaining.toMinutes() % 60
    val seconds = remaining.seconds % 60
    return when {
        days >= 1 -> join(quantity(days, "day"), quantity(hours, "hour"))
        remaining.toHours() >= 1 -> join(quantity(hours, "hour"), quantity(minutes, "minute"))
        remaining.toMinutes() >= 1 -> join(quantity(minutes, "minute"), quantity(seconds, "second"))
        else -> quantity(seconds, "second")
    }
}

private fun join(primary: String, secondary: String): String {
    return if (secondary.startsWith("0 ")) primary else "$primary $secondary"
}

private fun quantity(count: Long, unit: String): String {
    return "$count $unit" + if (count == 1L) "" else "s"
}
