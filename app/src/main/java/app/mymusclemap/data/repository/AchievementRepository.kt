package app.mymusclemap.data.repository

import androidx.room.withTransaction
import app.mymusclemap.data.local.AchievementStateEntity
import app.mymusclemap.data.local.ProgressEventEntity
import app.mymusclemap.data.local.UnlockedAchievementEntity
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.toModel
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.achievements.AccountAchievementAuthority
import app.mymusclemap.domain.achievements.AchievementBoard
import app.mymusclemap.domain.achievements.AchievementBoardAssembler
import app.mymusclemap.domain.achievements.BadgeWallItem
import app.mymusclemap.domain.achievements.CountProgress
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.AchievementReconciler
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.JourneyEvaluator
import app.mymusclemap.domain.achievements.ProgressEventKind
import app.mymusclemap.domain.achievements.ProgressEventSnapshot
import app.mymusclemap.domain.achievements.ReconcilePlan
import app.mymusclemap.domain.achievements.ReconcileRequest
import app.mymusclemap.domain.achievements.StoredProgressEvent
import app.mymusclemap.domain.achievements.StoredUnlock
import app.mymusclemap.domain.achievements.TargetWeightGoalFacts
import app.mymusclemap.domain.achievements.TargetWeightMilestonePlanner
import app.mymusclemap.domain.achievements.TargetWeightProgress
import app.mymusclemap.domain.achievements.TargetWeightProgressEvaluator
import app.mymusclemap.domain.achievements.UnlockSnapshot
import app.mymusclemap.domain.achievements.VolumeProgress
import app.mymusclemap.domain.achievements.PerformanceRecordEvaluator
import app.mymusclemap.domain.achievements.ExerciseMasteryEvaluator
import app.mymusclemap.domain.achievements.PrHunterEvaluator
import app.mymusclemap.domain.achievements.ProAchievementEvaluator
import app.mymusclemap.domain.achievements.WeeklyGoalCompletionEvaluator
import app.mymusclemap.domain.achievements.WeeklyGoalStreakEvaluator
import app.mymusclemap.domain.achievements.WeightMilestonePlan
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val dateProvider: DateProvider,
    private val grantsPro: () -> Boolean = { false },
    private val founderLifetime: () -> Boolean = { false },
    private val accountAuthority: () -> AccountAchievementAuthority = { AccountAchievementAuthority() }
) {
    private val entitlementEpoch = MutableStateFlow(0)

    fun notifyEntitlementChanged() {
        entitlementEpoch.value = entitlementEpoch.value + 1
    }

    fun observeBoard(): Flow<AchievementBoard> {
        val awards = combine(
            combine(
                database.workoutSessionDao().observeCompletedCount(),
                database.achievementDao().observeUnlocks(),
                database.achievementDao().observeEvents(),
                database.weeklyWorkoutGoalDao().observeAll(),
                database.workoutSessionDao().observeAllCompletedCounts()
            ) { count, unlocks, events, goals, dateCounts ->
                val status = weeklyStatus(goals, dateCounts)
                AchievementBoardAssembler.assemble(
                    completedWorkoutCount = count,
                    unlocks = unlocks.mapNotNull { it.toSnapshot() },
                    events = events.mapNotNull { it.toSnapshot() },
                    currentStreak = WeeklyGoalStreakEvaluator.currentStreak(status),
                    bestStreak = WeeklyGoalStreakEvaluator.bestStreak(status),
                    grantsPro = grantsPro(),
                    accountAuthority = accountAuthority()
                )
            },
            observePractice()
        ) { board, practice ->
            board.copy(items = board.items.map { applyPractice(it, practice) })
        }
        return combine(
            awards,
            database.targetWeightGoalDao().observeActive(),
            database.weightMeasurementDao().observeAllAscending(),
            entitlementEpoch
        ) { board, goal, measurements, _ ->
            board.copy(targetWeightProgress = targetProgress(goal, measurements))
                .withCurrentOwnership(grantsPro(), accountAuthority())
        }
    }

    suspend fun board(): AchievementBoard {
        val status = weeklyStatus(
            database.weeklyWorkoutGoalDao().getAll(),
            database.workoutSessionDao().allCompletedCounts()
        )
        val history = completedAggregates()
        val recordEvents = PerformanceRecordEvaluator.events(history)
        val assembled = AchievementBoardAssembler.assemble(
            completedWorkoutCount = database.workoutSessionDao().countCompleted(),
            unlocks = database.achievementDao().unlocks().mapNotNull { it.toSnapshot() },
            events = database.achievementDao().events().mapNotNull { it.toSnapshot() },
            currentStreak = WeeklyGoalStreakEvaluator.currentStreak(status),
            bestStreak = WeeklyGoalStreakEvaluator.bestStreak(status),
            lifetimeVolumeKg = ProAchievementEvaluator.lifetimeVolumeKg(history),
            prEventCount = recordEvents.size,
            leadingExerciseSets = ExerciseMasteryEvaluator.leadingCount(history),
            grantsPro = grantsPro(),
            accountAuthority = accountAuthority()
        )
        return assembled.copy(
            targetWeightProgress = targetProgress(
                database.targetWeightGoalDao().active(),
                database.weightMeasurementDao().getAllAscending()
            )
        )
    }

    private fun weeklyStatus(
        goals: List<WeeklyWorkoutGoalEntity>,
        dateCounts: List<app.mymusclemap.data.local.WorkoutDateCount>
    ): app.mymusclemap.domain.workout.WeeklyGoalStatus {
        return WeeklyGoalLogic.evaluate(
            history = goals.map { it.toRevision() },
            completedByDate = dateCounts.associate { LocalDate.parse(it.date) to it.completedCount },
            today = dateProvider.today()
        )
    }

    /**
     * Records that a monthly report was generated, then reconciles.
     * Calling this again does not move the original unlock time.
     * There is no report history to reconstruct generations from before this marker.
     */
    suspend fun recordMonthlyReportGenerated() {
        reconcile(recordMonthlyReport = true)
    }

    suspend fun reconcile(
        triggerClientWorkoutId: String? = null,
        recordMonthlyReport: Boolean = false
    ) {
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
            val storedEventRows = achievementDao.events()
            val storedEvents = storedEventRows.mapNotNull { row ->
                ProgressEventKind.fromStorage(row.kind)?.let { kind ->
                    StoredProgressEvent(
                        dedupeKey = row.dedupeKey,
                        kind = kind,
                        celebratedAt = row.celebratedAt
                    )
                }
            }
            val now = clock.millis()
            val history = completedAggregates()
            val recordEvents = PerformanceRecordEvaluator.events(history)
            val triggerDate = triggerClientWorkoutId?.let { id ->
                sessions.getByClientWorkoutId(id)?.workoutDate?.let(LocalDate::parse)
            }
            val monthlyMarkerAt = storedEventRows
                .firstOrNull { it.dedupeKey == JourneyEvaluator.MONTHLY_REPORT_KEY }
                ?.occurredAt
            val native = sessions.earliestNativeCompleted()
            val plan = AchievementReconciler.plan(
                request = ReconcileRequest(
                    initialized = achievementDao.state()?.initialized == true,
                    completedWorkoutCount = sessions.countCompleted(),
                    achievedWeeks = WeeklyGoalCompletionEvaluator.achievedWeeks(status),
                    nowMillis = now,
                    triggerClientWorkoutId = triggerClientWorkoutId,
                    streakQualifications = WeeklyGoalStreakEvaluator.qualifications(status),
                    journeyQualifications = JourneyEvaluator.qualifications(
                        earliestNativeCompletedAt = native?.let {
                            JourneyEvaluator.nativeCompletedAt(it.finishedAt, it.startedAt)
                        },
                        earliestCustomPlanAt = database.workoutTemplateDao().earliestCreatedAt(),
                        monthlyReportGeneratedAt = monthlyMarkerAt ?: if (recordMonthlyReport) now else null
                    ),
                    performanceQualifications = PerformanceRecordEvaluator.qualificationsFrom(recordEvents),
                    grantsPro = grantsPro(),
                    founderLifetime = founderLifetime(),
                    proQualifications = ProAchievementEvaluator.qualifications(history) +
                        PrHunterEvaluator.qualifications(recordEvents) +
                        ExerciseMasteryEvaluator.qualifications(history),
                    triggerWorkoutDate = triggerDate,
                    recordMonthlyReportMarker = recordMonthlyReport && monthlyMarkerAt == null
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

    private fun targetProgress(
        goal: app.mymusclemap.data.local.TargetWeightGoalEntity?,
        measurements: List<app.mymusclemap.data.local.WeightMeasurementEntity>
    ): TargetWeightProgress? {
        val facts = goal?.toFacts() ?: return null
        val inJourney = measurements.filter { it.createdAt >= facts.createdAt }
        val current = inJourney.maxByOrNull { it.date }?.weightKg ?: facts.baselineKg
        return TargetWeightProgressEvaluator.progress(facts, current)
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

    private data class PracticeFacts(
        val lifetimeVolumeKg: Double,
        val prEventCount: Int,
        val leadingExerciseSets: Int
    )

    private fun observePractice(): Flow<PracticeFacts> {
        val sessions = database.workoutSessionDao()
        return combine(
            sessions.observeCompletedSessions(),
            sessions.observeCompletedExercises(),
            sessions.observeCompletedSets()
        ) { completedSessions, exercises, sets ->
            val history = aggregatesFrom(completedSessions, exercises, sets)
            PracticeFacts(
                lifetimeVolumeKg = ProAchievementEvaluator.lifetimeVolumeKg(history),
                prEventCount = PerformanceRecordEvaluator.events(history).size,
                leadingExerciseSets = ExerciseMasteryEvaluator.leadingCount(history)
            )
        }
    }

    private fun applyPractice(item: BadgeWallItem, practice: PracticeFacts): BadgeWallItem {
        val volumeThreshold = item.id.volumeThresholdKg
        if (volumeThreshold != null) {
            return item.copy(
                volumeProgress = VolumeProgress(currentKg = practice.lifetimeVolumeKg, thresholdKg = volumeThreshold),
                requirementMet = practice.lifetimeVolumeKg >= volumeThreshold
            )
        }
        val hunter = item.id.prHunterTarget
        if (hunter != null) {
            return item.copy(
                countProgress = CountProgress(
                    current = practice.prEventCount.coerceAtMost(hunter),
                    threshold = hunter
                ),
                requirementMet = practice.prEventCount >= hunter
            )
        }
        val mastery = item.id.masterySetTarget
        if (mastery != null) {
            return item.copy(
                countProgress = CountProgress(
                    current = practice.leadingExerciseSets.coerceAtMost(mastery),
                    threshold = mastery
                ),
                requirementMet = practice.leadingExerciseSets >= mastery
            )
        }
        return item
    }

    private suspend fun completedAggregates(): List<WorkoutSessionAggregate> {
        val sessions = database.workoutSessionDao()
        return aggregatesFrom(
            sessions.getCompletedSessions(),
            sessions.getCompletedExercises(),
            sessions.getCompletedSets()
        )
    }

    private fun aggregatesFrom(
        sessions: List<app.mymusclemap.data.local.WorkoutSessionEntity>,
        exercises: List<app.mymusclemap.data.local.WorkoutSessionExerciseEntity>,
        sets: List<app.mymusclemap.data.local.WorkoutSessionSetEntity>
    ): List<WorkoutSessionAggregate> {
        val setsByExercise = sets.groupBy { it.sessionExerciseId }
        val exercisesBySession = exercises.groupBy { it.sessionId }
        return sessions.map { session ->
            WorkoutSessionAggregate(
                session = session.toModel(),
                exercises = exercisesBySession[session.id].orEmpty()
                    .sortedWith(compareBy({ it.position }, { it.id }))
                    .map { exercise ->
                        SessionExerciseItem(
                            exercise = exercise.toModel(emptyList()),
                            sets = setsByExercise[exercise.id].orEmpty()
                                .sortedWith(compareBy({ it.position }, { it.id }))
                                .map { it.toModel() }
                        )
                    }
            )
        }
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
