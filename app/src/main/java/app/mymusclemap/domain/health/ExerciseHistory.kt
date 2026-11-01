package app.mymusclemap.domain.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Whether this device can share exercise history older than Health Connect's default boundary.
 * [NotGranted] still allows the recent range Health Connect permits for the app.
 */
enum class HealthHistoryAccess {
    Unsupported,
    NotGranted,
    Granted
}

/**
 * [start] null is an open start. Health Connect then keeps records that fall inside the
 * caller's permission: everything when history is granted, otherwise nothing older than
 * 30 days before the first health permission grant.
 */
data class ExerciseHistoryQuery(
    val fullHistory: Boolean,
    val start: Instant?,
    val end: Instant
)

data class ExerciseHistoryRawPage(
    val sessions: List<HealthExerciseSession> = emptyList(),
    val nextPageToken: String? = null
)

data class ExerciseHistoryEntry(
    val id: String,
    val kind: HealthExerciseKind,
    val start: Instant,
    val date: LocalDate,
    val startTime: LocalTime,
    val duration: Duration,
    val sourcePackage: String?
)

data class ExerciseHistorySnapshot(
    val visible: Boolean = false,
    val access: HealthHistoryAccess = HealthHistoryAccess.Unsupported,
    val entries: List<ExerciseHistoryEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val limitedToRecentWindow: Boolean = false
)

/**
 * Health Connect 1.1.0, without [android.permission.health.READ_HEALTH_DATA_HISTORY],
 * omits records older than 30 days before the app's first health permission grant.
 * [safeRecentStart] is the latest such boundary can be, so a query from there is never
 * earlier than the platform allows. It is narrower when the grant is already in the past.
 */
object ExerciseHistoryBoundary {
    val PERMITTED_LOOKBACK: Duration = Duration.ofDays(30)

    fun earliestWithoutHistory(firstGrant: Instant): Instant {
        return firstGrant.minus(PERMITTED_LOOKBACK)
    }

    fun safeRecentStart(now: Instant): Instant = now.minus(PERMITTED_LOOKBACK)
}

object ExerciseHistory {
    const val PAGE_SIZE = 40

    fun identity(session: HealthExerciseSession): String {
        if (session.id.isNotBlank()) return "id:${session.id}"
        return "sig:${session.start}|${session.end}|${session.kind}|${session.sourcePackage.orEmpty()}"
    }

    fun dedupe(sessions: List<HealthExerciseSession>): List<HealthExerciseSession> {
        val seen = HashSet<String>()
        return sessions.filter { seen.add(identity(it)) }
    }

    fun entries(sessions: List<HealthExerciseSession>): List<ExerciseHistoryEntry> {
        return dedupe(sessions).mapNotNull { session ->
            if (session.kind == HealthExerciseKind.OTHER) return@mapNotNull null
            if (!session.end.isAfter(session.start)) return@mapNotNull null
            val local = session.start.atZone(session.zone)
            ExerciseHistoryEntry(
                id = identity(session),
                kind = session.kind,
                start = session.start,
                date = local.toLocalDate(),
                startTime = local.toLocalTime(),
                duration = Duration.between(session.start, session.end),
                sourcePackage = session.sourcePackage?.takeIf { it.isNotBlank() }
            )
        }.sortedByDescending { it.start }
    }

    fun merge(
        existing: List<ExerciseHistoryEntry>,
        page: List<ExerciseHistoryEntry>
    ): List<ExerciseHistoryEntry> {
        val seen = existing.mapTo(HashSet()) { it.id }
        val added = page.filter { seen.add(it.id) }
        return (existing + added).sortedByDescending { it.start }
    }
}
