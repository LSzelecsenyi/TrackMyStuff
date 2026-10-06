package app.mymusclemap.data.repository

import androidx.room.withTransaction
import app.mymusclemap.data.local.AchievementStateEntity
import app.mymusclemap.data.local.ProgressEventEntity
import app.mymusclemap.data.local.UnlockedAchievementEntity
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.achievements.AchievementBoard
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.AchievementReconciler
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.ProgressEventKind
import app.mymusclemap.domain.achievements.ProgressEventSnapshot
import app.mymusclemap.domain.achievements.ReconcilePlan
import app.mymusclemap.domain.achievements.ReconcileRequest
import app.mymusclemap.domain.achievements.StoredProgressEvent
import app.mymusclemap.domain.achievements.StoredUnlock
import app.mymusclemap.domain.achievements.TargetWeightGoalFacts
import app.mymusclemap.domain.achievements.TargetWeightMilestonePlanner
import app.mymusclemap.domain.achievements.TargetWeightProgressEvaluator
import app.mymusclemap.domain.achievements.UnlockSnapshot
import app.mymusclemap.domain.achievements.WeeklyGoalCompletionEvaluator
import app.mymusclemap.domain.achievements.WeightMilestonePlan
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate

/**
 * Durable achievement truth. Callers never insert unlocks themselves.
 * A repeated [reconcile] with unchanged history is a no-op.
 */
class AchievementRepository(
    private val database: WeightDatabase,
    private val clock: Clock,
    private val dateProvider: DateProvider
) {
    fun observeBoard(): Flow<AchievementBoard> {
        return combine(
            database.workoutSessionDao().observeCompletedCount(),
            database.achievementDao().observeUnlocks(),
            database.achievementDao().observeEvents()
        ) { count, unlocks, events ->
            AchievementBoardAssembler.assemble(
                completedWorkoutCount = count,
                unlocks = unlocks.mapNotNull { it.toSnapshot() },
                events = events.mapNotNull { it.toSnapshot() }
            )
        }
    }

    suspend fun board(): AchievementBoard {
        return AchievementBoardAssembler.assemble(
            completedWorkoutCount = database.workoutSessionDao().countCompleted(),
            unlocks = database.achievementDao().unlocks().mapNotNull { it.toSnapshot() },
            events = database.achievementDao().events().mapNotNull { it.toSnapshot() }
        )
    }

    suspend fun reconcile(triggerClientWorkoutId: String? = null) {
        database.withTransaction {
            captureWeightBaseline()
            val sessions = database.workoutSessionDao()
            val goals = database.weeklyWorkoutGoalDao().getAll().map { it.toRevision() }
            val today = dateProvider.today()
            val status = WeeklyGoalLogic.evaluate(
                history = goals,
                completedByDate = sessions.completedCounts(),
                today = today
            )
            val achievementDao = database.achievementDao()
            val storedUnlocks = achievementDao.unlocks().mapNotNull { row ->
                AchievementId.fromStorage(row.achievementId)?.let { id ->
                    StoredUnlock(achievementId = id, celebratedAt = row.celebratedAt)
                }
            }
            val storedEvents = achievementDao.events().mapNotNull { row ->
                ProgressEventKind.fromStorage(row.kind)?.let { kind ->
                    StoredProgressEvent(
                        dedupeKey = row.dedupeKey,
                        kind = kind,
                        celebratedAt = row.celebratedAt
                    )
                }
            }
            val now = clock.millis()
            val plan = AchievementReconciler.plan(
                request = ReconcileRequest(
                    initialized = achievementDao.state()?.initialized == true,
                    completedWorkoutCount = sessions.countCompleted(),
                    achievedWeeks = WeeklyGoalCompletionEvaluator.achievedWeeks(status),
                    nowMillis = now,
                    triggerClientWorkoutId = triggerClientWorkoutId
                ),
                unlocks = storedUnlocks,
                events = storedEvents
            )
            val weightGoal = database.targetWeightGoalDao().active()
            val facts = weightGoal?.toFacts()
            val journey = weightsForJourney(weightGoal?.createdAt)
            val weightPlan = TargetWeightMilestonePlanner.plan(
                goal = facts,
                currentKg = journey.currentKg,
                historicalKg = journey.historicalKg,
                events = storedEvents,
                targetWeightUnlocked = storedUnlocks.any { it.achievementId == AchievementId.TARGET_WEIGHT_REACHED },
                nowMillis = now
            )
            if (plan.changesNothing && weightPlan.changesNothing) return@withTransaction
            apply(plan)
            applyWeight(weightPlan)
        }
    }

    suspend fun acknowledge(acknowledgements: List<CelebrationAcknowledgement>) {
        if (acknowledgements.isEmpty()) return
        val now = clock.millis()
        val achievementIds = acknowledgements.mapNotNull { it.achievementId }.distinct()
        val eventKeys = acknowledgements.mapNotNull { it.progressEventKey }.distinct()
        val dao = database.achievementDao()
        database.withTransaction {
            if (achievementIds.isNotEmpty()) {
                dao.acknowledgeUnlocks(achievementIds, now)
            }
            if (eventKeys.isNotEmpty()) {
                dao.acknowledgeEvents(eventKeys, now)
            }
        }
    }

    private suspend fun weightsForJourney(createdAt: Long?): WeightJourneyWeights {
        val measurements = database.weightMeasurementDao().getAllAscending()
        if (createdAt == null) {
            return WeightJourneyWeights(currentKg = measurements.lastOrNull()?.weightKg, historicalKg = emptyList())
        }
        val inJourney = measurements.filter { it.createdAt >= createdAt }
        val baseline = database.targetWeightGoalDao().active()?.baselineKg
        val current = inJourney.maxByOrNull { it.date }?.weightKg ?: baseline
        return WeightJourneyWeights(
            currentKg = current,
            historicalKg = inJourney.map { it.weightKg }
        )
    }

    private suspend fun captureWeightBaseline() {
        val goal = database.targetWeightGoalDao().active() ?: return
        if (goal.baselineKg != null) return
        val latest = database.weightMeasurementDao().getAllAscending().lastOrNull() ?: return
        val direction = TargetWeightProgressEvaluator.direction(latest.weightKg, goal.targetKg)
        database.targetWeightGoalDao().captureBaseline(
            id = goal.id,
            baselineKg = TargetWeightProgressEvaluator.roundKg(latest.weightKg),
            direction = direction.name,
            updatedAt = clock.millis()
        )
    }

    private suspend fun applyWeight(plan: WeightMilestonePlan) {
        if (plan.changesNothing) return
        val dao = database.achievementDao()
        val now = clock.millis()
        if (plan.deleteEventKeys.isNotEmpty()) {
            dao.deleteEvents(plan.deleteEventKeys.toList())
        }
        if (plan.silenceEventKeys.isNotEmpty()) {
            dao.acknowledgeEvents(plan.silenceEventKeys.toList(), now)
        }
        plan.insertUnlock?.let { insert ->
            dao.insertUnlocks(
                listOf(
                    UnlockedAchievementEntity(
                        achievementId = insert.achievementId.name,
                        unlockedAt = insert.unlockedAt,
                        celebratedAt = insert.celebratedAt,
                        triggerClientWorkoutId = insert.triggerClientWorkoutId
                    )
                )
            )
        }
        if (plan.insertEvents.isNotEmpty()) {
            dao.insertEvents(
                plan.insertEvents.map { insert ->
                    ProgressEventEntity(
                        dedupeKey = insert.dedupeKey,
                        kind = insert.kind.name,
                        payload = insert.payload,
                        occurredAt = insert.occurredAt,
                        celebratedAt = insert.celebratedAt,
                        triggerClientWorkoutId = insert.triggerClientWorkoutId
                    )
                }
            )
        }
    }

    private suspend fun apply(plan: ReconcilePlan) {
        val dao = database.achievementDao()
        val now = clock.millis()
        database.withTransaction {
            if (plan.revoke.isNotEmpty()) {
                dao.deleteUnlocks(plan.revoke.map { it.name })
            }
            if (plan.deleteEventKeys.isNotEmpty()) {
                dao.deleteEvents(plan.deleteEventKeys.toList())
            }
            if (plan.insertUnlocks.isNotEmpty()) {
                dao.insertUnlocks(
                    plan.insertUnlocks.map { insert ->
                        UnlockedAchievementEntity(
                            achievementId = insert.achievementId.name,
                            unlockedAt = insert.unlockedAt,
                            celebratedAt = insert.celebratedAt,
                            triggerClientWorkoutId = insert.triggerClientWorkoutId
                        )
                    }
                )
            }
            if (plan.insertEvents.isNotEmpty()) {
                dao.insertEvents(
                    plan.insertEvents.map { insert ->
                        ProgressEventEntity(
                            dedupeKey = insert.dedupeKey,
                            kind = insert.kind.name,
                            payload = insert.payload,
                            occurredAt = insert.occurredAt,
                            celebratedAt = insert.celebratedAt,
                            triggerClientWorkoutId = insert.triggerClientWorkoutId
                        )
                    }
                )
            }
            if (plan.markInitialized) {
                dao.upsertState(
                    AchievementStateEntity(
                        id = AchievementStateEntity.SINGLETON_ID,
                        initialized = true,
                        updatedAt = now
                    )
                )
            }
        }
    }
}

private data class WeightJourneyWeights(
    val currentKg: Double?,
    val historicalKg: List<Double>
)

private suspend fun app.mymusclemap.data.local.WorkoutSessionDao.completedCounts(): Map<LocalDate, Int> {
    return allCompletedCounts().associate { LocalDate.parse(it.date) to it.completedCount }
}

private fun WeeklyWorkoutGoalEntity.toRevision(): WeeklyGoalRevision {
    return WeeklyGoalRevision(
        effectiveWeekStart = LocalDate.parse(effectiveWeekStart),
        workoutsPerWeek = workoutsPerWeek,
        graceWeek = graceWeek
    )
}

private fun UnlockedAchievementEntity.toSnapshot(): UnlockSnapshot? {
    val id = AchievementId.fromStorage(achievementId) ?: return null
    return UnlockSnapshot(
        achievementId = id,
        unlockedAt = unlockedAt,
        celebratedAt = celebratedAt,
        triggerClientWorkoutId = triggerClientWorkoutId
    )
}

private fun ProgressEventEntity.toSnapshot(): ProgressEventSnapshot? {
    val kind = ProgressEventKind.fromStorage(kind) ?: return null
    return ProgressEventSnapshot(
        dedupeKey = dedupeKey,
        kind = kind,
        payload = payload,
        celebratedAt = celebratedAt,
        triggerClientWorkoutId = triggerClientWorkoutId
    )
}
