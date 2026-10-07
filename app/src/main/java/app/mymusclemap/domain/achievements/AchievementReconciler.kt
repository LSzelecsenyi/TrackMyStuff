package app.mymusclemap.domain.achievements

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class ProgressEventKind {
    WEEKLY_GOAL_COMPLETED,
    HISTORY_RECOGNIZED,
    WEIGHT_GOAL_MILESTONE,
    /** A recorded product milestone used as source data. It is not itself a celebration. */
    JOURNEY_MARKER;

    companion object {
        fun fromStorage(raw: String): ProgressEventKind? = entries.firstOrNull { it.name == raw }
    }
}

data class StoredUnlock(
    val achievementId: AchievementId,
    val celebratedAt: Long?
)

data class StoredProgressEvent(
    val dedupeKey: String,
    val kind: ProgressEventKind,
    val celebratedAt: Long?
)

data class ReconcileRequest(
    val initialized: Boolean,
    val completedWorkoutCount: Int,
    val achievedWeeks: List<AchievedWeek>,
    val nowMillis: Long,
    val triggerClientWorkoutId: String?,
    val streakQualifications: List<StreakQualification> = emptyList(),
    val journeyQualifications: List<JourneyQualification> = emptyList(),
    val performanceQualifications: List<PerformanceQualification> = emptyList(),
    /** Canonical Pro grant at reconcile time. Not stored on the achievement row. */
    val grantsPro: Boolean = false,
    /**
     * Retained so callers stay source-compatible. Founder, Early Adopter, and Developer are
     * not written into Room. Their ownership comes from the trusted account authority.
     */
    val founderLifetime: Boolean = false,
    val proQualifications: List<ProQualification> = emptyList(),
    /** Local date of the workout that triggered this reconcile, when there is one. */
    val triggerWorkoutDate: LocalDate? = null,
    /** Writes the monthly-report marker when this generation has not been recorded yet. */
    val recordMonthlyReportMarker: Boolean = false
)

data class UnlockInsert(
    val achievementId: AchievementId,
    val unlockedAt: Long,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

data class ProgressEventInsert(
    val dedupeKey: String,
    val kind: ProgressEventKind,
    val payload: String,
    val occurredAt: Long,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

data class ReconcilePlan(
    val insertUnlocks: List<UnlockInsert>,
    val revoke: Set<AchievementId>,
    val insertEvents: List<ProgressEventInsert>,
    val deleteEventKeys: Set<String>,
    val markInitialized: Boolean
) {
    val changesNothing: Boolean
        get() = insertUnlocks.isEmpty() &&
            revoke.isEmpty() &&
            insertEvents.isEmpty() &&
            deleteEventKeys.isEmpty() &&
            !markInitialized
}

/**
 * Pure comparison of current history with stored awards.
 *
 * The first run records already-earned workout awards without individual celebrations
 * and, when at least one award is discovered, queues a single history summary.
 * Later runs celebrate a newly crossed threshold, and a single newly achieved week.
 * Two or more newly achieved weeks in one pass are recorded silently so a first goal
 * or a bulk import does not open one dialog per past week.
 */
object AchievementReconciler {
    fun plan(
        request: ReconcileRequest,
        unlocks: List<StoredUnlock>,
        events: List<StoredProgressEvent>
    ): ReconcilePlan {
        val qualified = WorkoutCountEvaluator.qualified(request.completedWorkoutCount)
        val storedIds = unlocks.map { it.achievementId }.toSet()
        val revoke = storedIds.filter { stored ->
            stored.revokesWhenWorkoutCountDrops && stored !in qualified
        }.toSet()
        val missing = AchievementCatalog.workoutCounts.filter { it in qualified && it !in storedIds }
        val silentAwards = !request.initialized
        val insertUnlocks = missing.map { id ->
            UnlockInsert(
                achievementId = id,
                unlockedAt = request.nowMillis,
                celebratedAt = if (silentAwards) request.nowMillis else null,
                triggerClientWorkoutId = if (silentAwards) null else request.triggerClientWorkoutId
            )
        }

        val achievedByKey = request.achievedWeeks.associateBy {
            WeeklyGoalCompletionEvaluator.dedupeKey(it.weekStart)
        }
        val weeklyEvents = events.filter { it.kind == ProgressEventKind.WEEKLY_GOAL_COMPLETED }
        val existingWeekKeys = weeklyEvents.map { it.dedupeKey }.toSet()
        val newWeeks = request.achievedWeeks.filter {
            WeeklyGoalCompletionEvaluator.dedupeKey(it.weekStart) !in existingWeekKeys
        }
        val storedIdsAfterRevoke = storedIds - revoke
        val missingStreaks = request.streakQualifications
            .filter { it.achievementId !in storedIdsAfterRevoke }
            .distinctBy { it.achievementId }
        val missingFreeStreaks = missingStreaks.filter { it.achievementId.access != AchievementAccess.PRO }
        val missingProStreaks = if (request.grantsPro) {
            missingStreaks.filter { it.achievementId.access == AchievementAccess.PRO }
        } else {
            emptyList()
        }
        val topStreak = missingFreeStreaks.maxByOrNull { it.achievementId.streakWeeks ?: 0 }
        val streakInserts = missingFreeStreaks.map { qualification ->
            val show = qualification.achievementId == topStreak?.achievementId
            UnlockInsert(
                achievementId = qualification.achievementId,
                unlockedAt = qualification.unlockedAt,
                celebratedAt = if (show) null else request.nowMillis,
                triggerClientWorkoutId = if (show) request.triggerClientWorkoutId else null
            )
        }
        val missingJourney = request.journeyQualifications
            .filter { it.achievementId.journeyMilestone != null && it.achievementId !in storedIdsAfterRevoke }
            .distinctBy { it.achievementId }
        val featuredJourney = missingJourney.maxWithOrNull(
            compareBy<JourneyQualification> { it.unlockedAt }.thenBy { journeyRank(it.achievementId) }
        )
        val journeyInserts = missingJourney.map { qualification ->
            val show = qualification.achievementId == featuredJourney?.achievementId
            UnlockInsert(
                achievementId = qualification.achievementId,
                unlockedAt = qualification.unlockedAt,
                celebratedAt = if (show) null else request.nowMillis,
                triggerClientWorkoutId = if (
                    show && qualification.achievementId == AchievementId.FIRST_WORKOUT
                ) {
                    request.triggerClientWorkoutId
                } else {
                    null
                }
            )
        }
        val missingPerformance = request.performanceQualifications
            .filter { it.achievementId.category == AchievementCategory.PERFORMANCE }
            .filter { it.achievementId !in storedIdsAfterRevoke }
            .distinctBy { it.achievementId }
        val livePerformance = missingPerformance.filter { qualification ->
            val trigger = request.triggerClientWorkoutId
            trigger != null && qualification.clientWorkoutId == trigger
        }
        val featuredPerformance = livePerformance
            .filter { it.achievementId != AchievementId.FIRST_PR }
            .maxByOrNull { performanceCelebrationRank(it.achievementId) }
            ?: livePerformance.firstOrNull { it.achievementId == AchievementId.FIRST_PR }
        val performanceInserts = missingPerformance.map { qualification ->
            val show = qualification.achievementId == featuredPerformance?.achievementId
            UnlockInsert(
                achievementId = qualification.achievementId,
                unlockedAt = qualification.unlockedAt,
                celebratedAt = if (show) null else request.nowMillis,
                triggerClientWorkoutId = if (show) request.triggerClientWorkoutId else null
            )
        }
        val missingPro = if (request.grantsPro) {
            (request.proQualifications + missingProStreaks.map { it.toProQualification(request) })
                .filter { it.achievementId.access == AchievementAccess.PRO }
                .filter { it.achievementId !in storedIdsAfterRevoke }
                .distinctBy { it.achievementId }
        } else {
            emptyList()
        }
        val livePro = missingPro.filter { qualification ->
            val trigger = request.triggerClientWorkoutId
            trigger != null && qualification.clientWorkoutId == trigger
        }
        val featuredPro = livePro.maxWithOrNull(
            compareBy<ProQualification> { it.unlockedAt }.thenByDescending { proCelebrationRank(it.achievementId) }
        )
        val proInserts = missingPro.map { qualification ->
            val show = qualification.achievementId == featuredPro?.achievementId
            UnlockInsert(
                achievementId = qualification.achievementId,
                unlockedAt = qualification.unlockedAt,
                celebratedAt = if (show) null else request.nowMillis,
                triggerClientWorkoutId = if (show) request.triggerClientWorkoutId else null
            )
        }
        val silenceWeeks = silentAwards || newWeeks.size > 1 ||
            missingFreeStreaks.isNotEmpty() || missingProStreaks.isNotEmpty()
        val weekInserts = newWeeks.map { week ->
            ProgressEventInsert(
                dedupeKey = WeeklyGoalCompletionEvaluator.dedupeKey(week.weekStart),
                kind = ProgressEventKind.WEEKLY_GOAL_COMPLETED,
                payload = WeeklyGoalCompletionEvaluator.payload(week),
                occurredAt = request.nowMillis,
                celebratedAt = if (silenceWeeks) request.nowMillis else null,
                triggerClientWorkoutId = if (silenceWeeks) null else request.triggerClientWorkoutId
            )
        }
        val deleteEventKeys = weeklyEvents
            .filter { it.celebratedAt == null && it.dedupeKey !in achievedByKey }
            .map { it.dedupeKey }
            .toSet()

        val markerKey = JourneyEvaluator.MONTHLY_REPORT_KEY
        val markerExists = events.any { it.dedupeKey == markerKey }
        val markerInsert = if (request.recordMonthlyReportMarker && !markerExists) {
            ProgressEventInsert(
                dedupeKey = markerKey,
                kind = ProgressEventKind.JOURNEY_MARKER,
                payload = JourneyEvaluator.MONTHLY_REPORT_PAYLOAD,
                occurredAt = request.nowMillis,
                celebratedAt = request.nowMillis,
                triggerClientWorkoutId = null
            )
        } else {
            null
        }

        val historyKey = WeeklyGoalCompletionEvaluator.HISTORY_RECOGNIZED_KEY
        val historyExists = events.any { it.dedupeKey == historyKey }
        val historyInsert = if (silentAwards && missing.isNotEmpty() && !historyExists) {
            ProgressEventInsert(
                dedupeKey = historyKey,
                kind = ProgressEventKind.HISTORY_RECOGNIZED,
                payload = missing.size.toString(),
                occurredAt = request.nowMillis,
                celebratedAt = null,
                triggerClientWorkoutId = null
            )
        } else {
            null
        }

        return ReconcilePlan(
            insertUnlocks = insertUnlocks + streakInserts + journeyInserts + performanceInserts +
                proInserts,
            revoke = revoke,
            insertEvents = weekInserts + listOfNotNull(historyInsert, markerInsert),
            deleteEventKeys = deleteEventKeys,
            markInitialized = !request.initialized
        )
    }

    /** Later completion wins. A tie prefers the higher threshold, then Volume Master. */
    private fun proCelebrationRank(id: AchievementId): Int {
        id.prHunterTarget?.let { return 1_000 + it }
        id.masterySetTarget?.let { return 1_000 + it }
        id.streakWeeks?.let { return 1_000 + it }
        return when (id) {
            AchievementId.VOLUME_MASTER -> 2
            AchievementId.IRON_DISCIPLINE -> 1
            else -> 0
        }
    }

    private fun StreakQualification.toProQualification(request: ReconcileRequest): ProQualification {
        val crossedOn = Instant.ofEpochMilli(unlockedAt).atZone(ZoneOffset.UTC).toLocalDate()
        val live = request.triggerWorkoutDate != null && request.triggerWorkoutDate == crossedOn
        return ProQualification(
            achievementId = achievementId,
            unlockedAt = unlockedAt,
            clientWorkoutId = if (live) request.triggerClientWorkoutId else null
        )
    }

    /**
     * One popup for a record just set in the triggering workout.
     * A specific record is shown ahead of First PR. Historical discoveries stay silent.
     */
    private fun performanceCelebrationRank(id: AchievementId): Int {
        return when (id) {
            AchievementId.WEIGHT_PR -> 3
            AchievementId.REP_RECORD -> 2
            AchievementId.VOLUME_RECORD -> 1
            AchievementId.FIRST_PR -> 0
            else -> -1
        }
    }

    /** Later timestamp wins. A tie prefers the later product milestone. */
    private fun journeyRank(id: AchievementId): Int {
        return when (id.journeyMilestone) {
            JourneyMilestone.FIRST_MONTHLY_REPORT -> 3
            JourneyMilestone.FIRST_PLAN -> 2
            JourneyMilestone.FIRST_WORKOUT -> 1
            null -> 0
        }
    }
}
