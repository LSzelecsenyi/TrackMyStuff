package app.mymusclemap.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.mymusclemap.data.local.InsertStartedSessionResult
import app.mymusclemap.data.local.WorkoutSessionDao
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseMuscleEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.data.local.WorkoutTemplateDao
import app.mymusclemap.data.local.activeLock
import app.mymusclemap.data.local.toModel
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.exercise.ExerciseEnumCodec
import app.mymusclemap.domain.exercise.MuscleRole
import app.mymusclemap.domain.workout.AbandonWorkoutResult
import app.mymusclemap.domain.workout.AddExtraExerciseResult
import app.mymusclemap.domain.workout.DeleteWorkoutResult
import app.mymusclemap.domain.workout.ActiveSessionSummary
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.ActualSetLogic
import app.mymusclemap.domain.workout.BodyWeightProposal
import app.mymusclemap.domain.workout.BodyWeightSnapshotLogic
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.NotificationSetCompletion
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionFocusLogic
import app.mymusclemap.domain.workout.RestClockSample
import app.mymusclemap.domain.workout.RestExpiryResult
import app.mymusclemap.domain.workout.RestPhase
import app.mymusclemap.domain.workout.RestProgressionMode
import app.mymusclemap.domain.workout.RestRemaining
import app.mymusclemap.domain.workout.RestTimerLogic
import app.mymusclemap.domain.workout.RestTimerSnapshot
import app.mymusclemap.domain.workout.SessionMutationResult
import app.mymusclemap.domain.workout.SessionProgressLogic
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.SetDraftCodec
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.ExerciseHistoryCandidate
import app.mymusclemap.domain.workout.ExerciseHistoryLogic
import app.mymusclemap.domain.workout.ExerciseHistoryOccurrence
import app.mymusclemap.domain.workout.ExerciseHistoryRequest
import app.mymusclemap.domain.workout.ExerciseHistorySelection
import app.mymusclemap.domain.workout.ExerciseHistorySet
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workoutimport.WorkoutImportDatabaseFailure
import app.mymusclemap.domain.workoutimport.WorkoutImportFingerprint
import app.mymusclemap.domain.workoutimport.WorkoutImportPersistenceResult
import app.mymusclemap.domain.workoutimport.WorkoutImportPlan
import app.mymusclemap.domain.workoutimport.WorkoutImportResolvedWorkout
import app.mymusclemap.data.local.PlanProgressionWriteException
import app.mymusclemap.data.local.PlanProgressionWriteResult
import app.mymusclemap.data.local.PlanSetTargetWrite
import app.mymusclemap.domain.workout.ApplyPlanProgressionResult
import app.mymusclemap.domain.workout.PlanProgressionProposal
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetLogic
import app.mymusclemap.domain.workout.WorkDuration
import app.mymusclemap.domain.workout.WorkPhase
import app.mymusclemap.domain.workout.WorkTimerLogic
import app.mymusclemap.domain.workout.WorkTimerSnapshot
import app.mymusclemap.domain.workout.WorkoutPlanProgressionEvaluator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

class WorkoutSessionRepository(
    private val sessionDao: WorkoutSessionDao,
    private val templateDao: WorkoutTemplateDao,
    private val exerciseDao: app.mymusclemap.data.local.ExerciseDao,
    private val weightRepository: WeightRepository,
    private val clock: Clock,
    private val dateProvider: DateProvider,
    private val onHeatmapDataChanged: () -> Unit = {},
    private val onNativeWorkoutCompleted: suspend (String) -> Unit = { _ -> },
    private val onTrainingHistoryChanged: suspend (String?) -> Unit = { _ -> },
    private val restClock: () -> RestClockSample = {
        RestClockSample(
            elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime(),
            wallClockMillis = clock.millis()
        )
    },
    private val timersAllowed: () -> Boolean = { true }
) {
    private val mutex = Mutex()

    fun observeInProgress(): Flow<ActiveSessionSummary?> {
        return combine(
            sessionDao.observeInProgress(),
            sessionDao.observeAllExercises(),
            sessionDao.observeAllSets()
        ) { sessions, exercises, sets ->
            val session = sessions.firstOrNull() ?: return@combine null
            val items = exercises.filter { it.sessionId == session.id }.sortedBy { it.position }
            val setsByExercise = sets.groupBy { it.sessionExerciseId }
            val allSets = items.flatMap { setsByExercise[it.id].orEmpty() }
            val progress = SessionProgressLogic.from(allSets.map { it.toModel() })
            val current = items.firstOrNull { exercise ->
                setsByExercise[exercise.id].orEmpty().any { it.status == SessionSetStatus.PENDING.name }
            } ?: items.firstOrNull()
            ActiveSessionSummary(
                session = session.toModel(),
                completedSets = progress.completed,
                skippedSets = progress.skipped,
                pendingSets = progress.pending,
                totalSets = progress.total,
                currentExerciseName = current?.name,
                currentExercisePosition = current?.position?.plus(1),
                exerciseCount = items.size
            )
        }
    }

    fun observeSummaries(): Flow<List<WorkoutSessionSummary>> {
        return combine(
            sessionDao.observeAll(),
            sessionDao.observeAllExercises(),
            sessionDao.observeAllSets(),
            sessionDao.observeAllMuscles()
        ) { sessions, exercises, sets, muscles ->
            sessions
                .filter { it.status != SessionStatus.IN_PROGRESS.name }
                .map { toSummary(it, exercises, sets, muscles) }
        }
    }

    fun observeCompletedCounts(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, Int>> {
        return sessionDao.observeCompletedCountsBetween(start.toString(), end.toString()).map { rows ->
            rows.associate { LocalDate.parse(it.date) to it.completedCount }
        }
    }

    fun observeAllCompletedCounts(): Flow<Map<LocalDate, Int>> {
        return sessionDao.observeAllCompletedCounts().map { rows ->
            rows.associate { LocalDate.parse(it.date) to it.completedCount }
        }
    }

    fun observeSummariesBetween(start: LocalDate, end: LocalDate): Flow<List<WorkoutSessionSummary>> {
        return combine(
            sessionDao.observeBetween(start.toString(), end.toString()),
            sessionDao.observeAllExercises(),
            sessionDao.observeAllSets(),
            sessionDao.observeAllMuscles()
        ) { sessions, exercises, sets, muscles ->
            sessions
                .filter { it.status != SessionStatus.IN_PROGRESS.name }
                .map { toSummary(it, exercises, sets, muscles) }
        }
    }

    fun observeSummariesOnDate(date: LocalDate): Flow<List<WorkoutSessionSummary>> {
        return combine(
            sessionDao.observeByWorkoutDate(date.toString()),
            sessionDao.observeAllExercises(),
            sessionDao.observeAllSets(),
            sessionDao.observeAllMuscles()
        ) { sessions, exercises, sets, muscles ->
            sessions
                .filter { it.status != SessionStatus.IN_PROGRESS.name }
                .map { toSummary(it, exercises, sets, muscles) }
        }
    }

    fun observeHasCompletedWorkout(): Flow<Boolean> {
        return sessionDao.observeCompletedSessions().map { it.isNotEmpty() }
    }

    /** Every persisted session, including in-progress and abandoned, for report history bounds. */
    fun observeSessions(): Flow<List<WorkoutSession>> {
        return sessionDao.observeAll().map { rows -> rows.map { it.toModel() } }
    }

    fun observeCompletedAggregates(): Flow<List<WorkoutSessionAggregate>> {
        return combine(
            sessionDao.observeCompletedSessions(),
            sessionDao.observeCompletedExercises(),
            sessionDao.observeCompletedSets(),
            sessionDao.observeCompletedMuscles()
        ) { sessions, exercises, sets, muscles ->
            sessions.map { session ->
                toAggregate(session, exercises, sets, muscles)
            }
        }
    }

    fun observeHeatmapExercises(): Flow<List<MuscleTrainingExercise>> {
        return combine(
            sessionDao.observeCompletedSessions(),
            sessionDao.observeCompletedExercises(),
            sessionDao.observeCompletedSets(),
            sessionDao.observeCompletedMuscles()
        ) { sessions, exercises, sets, muscles ->
            val sessionDates = sessions.associate { it.id to LocalDate.parse(it.workoutDate) }
            val setsByExercise = sets.groupBy { it.sessionExerciseId }
            val musclesByExercise = muscles.groupBy { it.sessionExerciseId }
            exercises.mapNotNull { exercise ->
                val date = sessionDates[exercise.sessionId] ?: return@mapNotNull null
                val model = exercise.toModel(musclesByExercise[exercise.id].orEmpty())
                val completedSets = setsByExercise[exercise.id].orEmpty().count { set ->
                    set.status == SessionSetStatus.COMPLETED.name
                }
                MuscleTrainingExercise(
                    status = SessionStatus.COMPLETED,
                    workoutDate = date,
                    primaryMuscle = model.primaryMuscle,
                    secondaryMuscles = model.secondaryMuscles,
                    completedSetCount = completedSets
                )
            }
        }
    }

    fun observeLatestCompleted(): Flow<WorkoutSessionSummary?> {
        return combine(
            sessionDao.observeLatestCompleted(),
            sessionDao.observeAllExercises(),
            sessionDao.observeAllSets(),
            sessionDao.observeAllMuscles()
        ) { session, exercises, sets, muscles ->
            session?.let { toSummary(it, exercises, sets, muscles) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveAggregate(): Flow<WorkoutSessionAggregate?> {
        return sessionDao.observeInProgress().flatMapLatest { sessions ->
            val sessionId = sessions.firstOrNull()?.id ?: return@flatMapLatest flowOf(null)
            observeAggregate(sessionId)
        }
    }

    suspend fun activeAggregate(): WorkoutSessionAggregate? {
        val session = sessionDao.getInProgress() ?: return null
        return getAggregate(session.id)
    }

    fun observeAggregate(sessionId: Long): Flow<WorkoutSessionAggregate?> {
        return combine(
            sessionDao.observeById(sessionId),
            sessionDao.observeAllExercises(),
            sessionDao.observeAllSets(),
            sessionDao.observeAllMuscles()
        ) { session, exercises, sets, muscles ->
            session ?: return@combine null
            toAggregate(session, exercises, sets, muscles)
        }
    }

    suspend fun getAggregate(sessionId: Long): WorkoutSessionAggregate? {
        val session = sessionDao.getById(sessionId) ?: return null
        return toAggregate(
            session,
            sessionDao.getExercises(sessionId),
            sessionDao.getExercises(sessionId).flatMap { sessionDao.getSets(it.id) },
            sessionDao.getExercises(sessionId).flatMap { sessionDao.getMuscles(it.id) }
        )
    }

    /** Completed session for a client workout id. In-progress and unknown ids return null. */
    suspend fun completedAggregate(clientWorkoutId: String): WorkoutSessionAggregate? {
        if (clientWorkoutId.isBlank()) return null
        val session = sessionDao.getByClientWorkoutId(clientWorkoutId) ?: return null
        if (session.status != SessionStatus.COMPLETED.name) return null
        return getAggregate(session.id)
    }

    suspend fun planProgression(clientWorkoutId: String): PlanProgressionProposal {
        val aggregate = completedAggregate(clientWorkoutId) ?: return PlanProgressionProposal.EMPTY
        return progressionFor(aggregate)
    }

    /**
     * Revalidates the completed workout against the current plan and writes only the
     * targets that still match the session's copied baseline. The completed session
     * rows are not rewritten. The apply flag and the plan writes commit together.
     */
    suspend fun applyPlanProgression(
        clientWorkoutId: String,
        approvedSourceSetIds: Set<Long> = emptySet()
    ): ApplyPlanProgressionResult = mutex.withLock {
        val aggregate = completedAggregate(clientWorkoutId) ?: return ApplyPlanProgressionResult.SessionMissing
        if (aggregate.session.planProgressionApplied) {
            return ApplyPlanProgressionResult.AlreadyApplied
        }
        if (templateMissing(aggregate)) {
            return ApplyPlanProgressionResult.PlanMissing
        }
        if (approvedSourceSetIds.isEmpty()) {
            return ApplyPlanProgressionResult.NothingToApply
        }
        val proposal = progressionFor(aggregate)
        val requested = proposal.updates.filter { it.sourceTemplateSetId in approvedSourceSetIds }
        val skippedBeforeWrite = approvedSourceSetIds.count { id ->
            requested.none { it.sourceTemplateSetId == id }
        }
        if (requested.isEmpty()) {
            return if (skippedBeforeWrite > 0) {
                ApplyPlanProgressionResult.Applied(0, skippedBeforeWrite)
            } else {
                ApplyPlanProgressionResult.NothingToApply
            }
        }
        val templateId = aggregate.session.templateId ?: return ApplyPlanProgressionResult.PlanMissing
        val setsById = aggregate.exercises.flatMap { it.sets }.associateBy { it.id }
        val writes = requested.mapNotNull { update ->
            val set = setsById[update.sessionSetId] ?: return@mapNotNull null
            PlanSetTargetWrite(
                sourceTemplateExerciseId = update.sourceTemplateExerciseId,
                sourceTemplateSetId = update.sourceTemplateSetId,
                baselineMinReps = set.plannedMinReps,
                baselineMaxReps = set.plannedMaxReps,
                baselineLoadKind = set.plannedLoadKind.name,
                baselineWeightKg = set.plannedWeightKg,
                baselineDurationSeconds = set.plannedDurationSeconds,
                baselineDistanceMeters = set.plannedDistanceMeters,
                newMinReps = update.newMinReps,
                newMaxReps = update.newMaxReps,
                newWeightKg = update.newWeightKg
            )
        }
        if (writes.isEmpty()) {
            return ApplyPlanProgressionResult.NothingToApply
        }
        return try {
            when (
                val written = sessionDao.applyPlanProgression(
                    sessionId = aggregate.session.id,
                    templateId = templateId,
                    updatedAt = clock.millis(),
                    updates = writes
                )
            ) {
                is PlanProgressionWriteResult.Applied ->
                    ApplyPlanProgressionResult.Applied(written.updated, skippedBeforeWrite + written.skipped)
                is PlanProgressionWriteResult.NothingApplied ->
                    if (skippedBeforeWrite + written.skipped > 0) {
                        ApplyPlanProgressionResult.Applied(0, skippedBeforeWrite + written.skipped)
                    } else {
                        ApplyPlanProgressionResult.NothingToApply
                    }
                PlanProgressionWriteResult.AlreadyApplied -> ApplyPlanProgressionResult.AlreadyApplied
                PlanProgressionWriteResult.PlanMissing -> ApplyPlanProgressionResult.PlanMissing
                PlanProgressionWriteResult.SessionMissing -> ApplyPlanProgressionResult.SessionMissing
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PlanProgressionWriteException) {
            ApplyPlanProgressionResult.Failed
        } catch (_: Exception) {
            ApplyPlanProgressionResult.Failed
        }
    }

    private suspend fun templateMissing(aggregate: WorkoutSessionAggregate): Boolean {
        val templateId = aggregate.session.templateId ?: return true
        return templateDao.getById(templateId) == null
    }

    private suspend fun progressionFor(aggregate: WorkoutSessionAggregate): PlanProgressionProposal {
        val templateId = aggregate.session.templateId ?: return PlanProgressionProposal.EMPTY
        val template = templateDao.getById(templateId) ?: return PlanProgressionProposal.EMPTY
        val exercises = templateDao.getExercises(templateId).map { it.toModel() }
        val sets = exercises.flatMap { exercise ->
            templateDao.getSets(exercise.id).map { it.toModel() }
        }
        return WorkoutPlanProgressionEvaluator.evaluate(
            aggregate,
            template.toModel(),
            exercises,
            sets
        )
    }

    /** Completed history used to compare a finished workout with earlier performances. */
    suspend fun completedAggregates(): List<WorkoutSessionAggregate> {
        val sessions = sessionDao.getCompletedSessions()
        val exercises = sessionDao.getCompletedExercises()
        val sets = sessionDao.getCompletedSets()
        return sessions.map { session -> toAggregate(session, exercises, sets, emptyList()) }
    }

    /**
     * Read-only lookup of one previous occurrence for [exercise]. Actual recorded
     * values are used. Planned values and set drafts are not read.
     */
    suspend fun loadPreviousExerciseHistory(
        current: WorkoutSessionAggregate,
        exercise: SessionExercise
    ): ExerciseHistorySelection? {
        val ordinal = ExerciseHistoryLogic.occurrenceOrdinal(current.exercises.map { it.exercise }, exercise.id)
            ?: return null
        val occurrenceRows = sessionDao.previousExerciseOccurrences(
            exerciseId = exercise.exerciseId,
            currentSessionId = current.session.id,
            currentStartedAt = current.session.startedAt
        )
        if (occurrenceRows.isEmpty()) return null
        val setsByExercise = sessionDao.previousExerciseSets(
            exerciseId = exercise.exerciseId,
            currentSessionId = current.session.id,
            currentStartedAt = current.session.startedAt
        ).groupBy { it.sessionExerciseId }
        val candidates = occurrenceRows.groupBy { it.sessionId }.map { (sessionId, rows) ->
            val session = rows.first()
            ExerciseHistoryCandidate(
                sessionId = sessionId,
                templateId = session.templateId,
                templateName = session.templateName,
                workoutDate = LocalDate.parse(session.workoutDate),
                startedAt = session.startedAt,
                status = runCatching { SessionStatus.valueOf(session.status) }
                    .getOrDefault(SessionStatus.ABANDONED),
                occurrences = rows.map { row ->
                    ExerciseHistoryOccurrence(
                        sessionExerciseId = row.sessionExerciseId,
                        position = row.exercisePosition,
                        measurementType = ExerciseEnumCodec.measurement(row.measurementType),
                        resistanceBasis = ExerciseEnumCodec.resistance(row.resistanceBasis),
                        weightInterpretation = ExerciseEnumCodec.weight(row.weightInterpretation),
                        sets = setsByExercise[row.sessionExerciseId].orEmpty().map { set ->
                            ExerciseHistorySet(
                                position = set.position,
                                status = runCatching { SessionSetStatus.valueOf(set.status) }
                                    .getOrDefault(SessionSetStatus.PENDING),
                                reps = set.actualReps,
                                loadKind = set.actualLoadKind?.let { kind ->
                                    runCatching { PlannedLoadKind.valueOf(kind) }.getOrNull()
                                },
                                weightKg = set.actualWeightKg,
                                durationSeconds = set.actualDurationSeconds,
                                distanceMeters = set.actualDistanceMeters,
                                timedWork = set.timedWork
                            )
                        }
                    )
                }
            )
        }
        return ExerciseHistoryLogic.select(
            ExerciseHistoryRequest(
                currentSessionId = current.session.id,
                currentStartedAt = current.session.startedAt,
                currentTemplateId = current.session.templateId,
                currentMeasurement = exercise.measurementType,
                occurrenceOrdinal = ordinal
            ),
            candidates
        )
    }

    /**
     * Inserts already resolved historical workouts as completed session aggregates.
     * Does not call [start], [completeSet], [finish], or [abandon].
     *
     * Timestamps: CSV start/finish are interpreted in [clock]'s zone. Session
     * `createdAt`/`updatedAt` and completed-set `completedAt` use the historical
     * finish instant so import time cannot become "latest workout". Skipped sets
     * keep `completedAt = null`. These completion timestamps are persistence
     * completeness, not known user events.
     */
    suspend fun importCompletedWorkouts(plan: WorkoutImportPlan): WorkoutImportPersistenceResult = mutex.withLock {
        if (!plan.canConfirm) {
            return WorkoutImportPersistenceResult.PlanNotConfirmable(
                errorCount = plan.errorCount,
                unresolvedNames = plan.unresolvedNames
            )
        }
        if (plan.workouts.any { workout ->
                workout.exercises.any { it.snapshot == null }
            }
        ) {
            return WorkoutImportPersistenceResult.PlanNotConfirmable(
                errorCount = plan.errorCount,
                unresolvedNames = plan.unresolvedNames
            )
        }
        val fingerprinted = plan.workouts.map { workout ->
            workout.workoutId to WorkoutImportFingerprint.hash(workout)
        }
        val duplicatesInPlan = fingerprinted
            .groupBy({ it.second }, { it.first })
            .filter { it.value.size > 1 }
            .values
            .flatten()
            .distinct()
        if (duplicatesInPlan.isNotEmpty()) {
            return WorkoutImportPersistenceResult.DuplicateWorkouts(duplicatesInPlan)
        }
        val fingerprints = fingerprinted.map { it.second }
        if (fingerprints.isNotEmpty()) {
            val existing = sessionDao.findExistingImportFingerprints(fingerprints).toSet()
            if (existing.isNotEmpty()) {
                val duplicateIds = fingerprinted
                    .filter { it.second in existing }
                    .map { it.first }
                return WorkoutImportPersistenceResult.DuplicateWorkouts(duplicateIds)
            }
        }
        val aggregates = plan.workouts.map { workout ->
            val fingerprint = fingerprinted.first { it.first == workout.workoutId }.second
            toImportedAggregate(workout, fingerprint)
        }
        return try {
            val ids = sessionDao.insertImportedAggregates(aggregates)
            onHeatmapDataChanged()
            notifyTrainingHistoryChanged(null)
            WorkoutImportPersistenceResult.Imported(
                sessionIds = ids,
                workoutCount = ids.size,
                exerciseCount = plan.resolvedExerciseCount,
                completedSetCount = plan.completedSetCount,
                skippedSetCount = plan.skippedSetCount
            )
        } catch (error: Exception) {
            when {
                isUniqueConstraint(error) -> {
                    val existing = if (fingerprints.isEmpty()) {
                        emptySet()
                    } else {
                        sessionDao.findExistingImportFingerprints(fingerprints).toSet()
                    }
                    val duplicateIds = fingerprinted
                        .filter { it.second in existing }
                        .map { it.first }
                        .ifEmpty { plan.workouts.map { it.workoutId } }
                    WorkoutImportPersistenceResult.DuplicateWorkouts(duplicateIds)
                }
                isForeignKeyConstraint(error) -> WorkoutImportPersistenceResult.DatabaseError(
                    WorkoutImportDatabaseFailure.ForeignKey
                )
                else -> WorkoutImportPersistenceResult.DatabaseError(WorkoutImportDatabaseFailure.Unknown)
            }
        }
    }

    suspend fun proposeBodyWeight(workoutDate: LocalDate = dateProvider.today()): BodyWeightProposal {
        val sameDay = weightRepository.getByDate(workoutDate)
        val previous = weightRepository.getLatestBefore(workoutDate)
        return BodyWeightSnapshotLogic.propose(workoutDate, sameDay, previous)
    }

    suspend fun start(
        templateId: Long,
        scheduledWorkoutId: Long? = null
    ): StartWorkoutResult = mutex.withLock {
        if (sessionDao.getInProgress() != null) {
            return StartWorkoutResult.AlreadyActive
        }
        val template = templateDao.getById(templateId) ?: return StartWorkoutResult.TemplateNotFound
        if (template.archived) {
            return StartWorkoutResult.TemplateArchived
        }
        if (scheduledWorkoutId != null) {
            val scheduled = sessionDao.getScheduledWorkout(scheduledWorkoutId)
                ?: return StartWorkoutResult.ScheduleNotFound
            if (scheduled.templateId != templateId) {
                return StartWorkoutResult.ScheduleTemplateMismatch
            }
            if (scheduled.cancelledAt != null) {
                return StartWorkoutResult.ScheduleNotFound
            }
            if (scheduled.scheduledDate > dateProvider.today().toString()) {
                return StartWorkoutResult.ScheduleNotOnToday
            }
            if (sessionDao.getSessionIdByScheduledWorkoutId(scheduledWorkoutId) != null) {
                return StartWorkoutResult.ScheduleAlreadyStarted
            }
        }
        val relations = templateDao.getExercises(templateId)
        if (relations.isEmpty() || relations.all { templateDao.getSets(it.id).isEmpty() }) {
            return StartWorkoutResult.TemplateEmpty
        }
        val workoutDate = dateProvider.today()
        val resolvedProposal = proposeBodyWeight(workoutDate)
        val now = clock.millis()
        val session = WorkoutSessionEntity(
            templateId = template.id,
            templateName = template.name,
            status = SessionStatus.IN_PROGRESS.name,
            workoutDate = workoutDate.toString(),
            startedAt = now,
            finishedAt = null,
            abandonedAt = null,
            notes = null,
            bodyWeightKg = resolvedProposal.kilograms,
            bodyWeightSource = resolvedProposal.source.name,
            bodyWeightSourceDate = resolvedProposal.sourceDate?.toString(),
            createdAt = now,
            updatedAt = now,
            activeLock = SessionStatus.IN_PROGRESS.activeLock(),
            importFingerprint = null,
            scheduledWorkoutId = scheduledWorkoutId,
            clientWorkoutId = java.util.UUID.randomUUID().toString(),
            restProgressionMode = template.restProgressionMode
        )
        val children = relations.sortedBy { it.position }.mapNotNull { relation ->
            val exercise = exerciseDao.getById(relation.exerciseId) ?: return@mapNotNull null
            val catalogMuscles = exerciseDao.getMuscles(relation.exerciseId)
            val sets = templateDao.getSets(relation.id).sortedBy { it.position }
            if (sets.isEmpty()) {
                return@mapNotNull null
            }
            val snapshot = WorkoutSessionExerciseEntity(
                sessionId = 0L,
                exerciseId = exercise.id,
                position = 0,
                name = exercise.name,
                category = exercise.category,
                movementPattern = exercise.movementPattern,
                measurementType = exercise.measurementType,
                resistanceBasis = exercise.resistanceBasis,
                weightInterpretation = exercise.weightInterpretation,
                primaryMuscle = catalogMuscles.firstOrNull { it.role == MuscleRole.PRIMARY.name }?.muscleGroup
                    ?: catalogMuscles.firstOrNull()?.muscleGroup
                    ?: "FULL_BODY",
                notes = relation.notes,
                addedDuringWorkout = false,
                sourceTemplateExerciseId = relation.id,
                restSeconds = relation.restSeconds
            )
            val muscleSnapshots = catalogMuscles.map { muscle ->
                WorkoutSessionExerciseMuscleEntity(
                    sessionExerciseId = 0L,
                    muscleGroup = muscle.muscleGroup,
                    role = muscle.role
                )
            }
            val setSnapshots = sets.map { planned ->
                WorkoutSessionSetEntity(
                    sessionExerciseId = 0L,
                    position = 0,
                    plannedMinReps = planned.minReps,
                    plannedMaxReps = planned.maxReps,
                    plannedLoadKind = planned.loadKind,
                    plannedWeightKg = planned.weightKg,
                    plannedDurationSeconds = planned.durationSeconds,
                    plannedDistanceMeters = planned.distanceMeters,
                    actualReps = if (planned.timedWork) null else planned.minReps,
                    actualLoadKind = planned.loadKind,
                    actualWeightKg = planned.weightKg,
                    actualDurationSeconds = planned.durationSeconds,
                    actualDistanceMeters = planned.distanceMeters,
                    status = SessionSetStatus.PENDING.name,
                    completedAt = null,
                    addedDuringWorkout = false,
                    sourceTemplateSetId = planned.id,
                    restSeconds = planned.restSeconds,
                    timedWork = planned.timedWork
                )
            }
            Triple(snapshot, muscleSnapshots, setSnapshots)
        }
        if (children.isEmpty()) {
            return StartWorkoutResult.TemplateEmpty
        }
        return try {
            when (
                val inserted = sessionDao.insertStartedSession(
                    session,
                    children,
                    scheduledWorkoutId,
                    templateId,
                    workoutDate.toString()
                )
            ) {
                is InsertStartedSessionResult.Inserted -> StartWorkoutResult.Started(inserted.sessionId)
                InsertStartedSessionResult.AlreadyActive -> StartWorkoutResult.AlreadyActive
                InsertStartedSessionResult.TemplateNotFound -> StartWorkoutResult.TemplateNotFound
                InsertStartedSessionResult.TemplateArchived -> StartWorkoutResult.TemplateArchived
                InsertStartedSessionResult.ScheduleNotFound -> StartWorkoutResult.ScheduleNotFound
                InsertStartedSessionResult.ScheduleTemplateMismatch -> StartWorkoutResult.ScheduleTemplateMismatch
                InsertStartedSessionResult.ScheduleAlreadyStarted -> StartWorkoutResult.ScheduleAlreadyStarted
                InsertStartedSessionResult.ScheduleNotOnToday -> StartWorkoutResult.ScheduleNotOnToday
            }
        } catch (error: Exception) {
            if (isUniqueConstraint(error)) {
                val message = error.message.orEmpty()
                if (message.contains("scheduledWorkoutId", ignoreCase = true)) {
                    StartWorkoutResult.ScheduleAlreadyStarted
                } else {
                    StartWorkoutResult.AlreadyActive
                }
            } else {
                throw error
            }
        }
    }

    suspend fun saveSetDraft(setId: Long, draft: ActualSetDraft): SessionMutationResult =
        saveSetDraft(setId) { draft }

    /**
     * [resolveDraft] runs inside the session mutex. Return null to skip the write when a newer
     * edit, completion, or deletion has already superseded this attempt.
     */
    suspend fun saveSetDraft(
        setId: Long,
        resolveDraft: () -> ActualSetDraft?
    ): SessionMutationResult = mutex.withLock {
        val draft = resolveDraft() ?: return SessionMutationResult.NotActive
        val set = sessionDao.getSet(setId) ?: return SessionMutationResult.NotFound
        if (set.status != SessionSetStatus.PENDING.name) {
            return SessionMutationResult.NotActive
        }
        val exercise = sessionDao.getExercise(set.sessionExerciseId) ?: return SessionMutationResult.NotFound
        val session = sessionDao.getById(exercise.sessionId) ?: return SessionMutationResult.NotFound
        if (session.status != SessionStatus.IN_PROGRESS.name) {
            return SessionMutationResult.NotActive
        }
        sessionDao.updateSet(set.copy(draftPayload = SetDraftCodec.encode(draft)))
        SessionMutationResult.Updated
    }

    suspend fun completeSet(setId: Long, draft: ActualSetDraft): SessionMutationResult = mutex.withLock {
        val before = sessionDao.getSet(setId)
        if (before?.status == SessionSetStatus.PENDING.name && before.timedWork) {
            val exercise = sessionDao.getExercise(before.sessionExerciseId)
            val session = exercise?.let { sessionDao.getById(it.sessionId) }
            val awaiting = session?.workPhase == WorkPhase.AWAITING.name && session.workSetId == setId
            if (!awaiting) return@withLock SessionMutationResult.NotActive
        }
        val result = mutateActiveSet(setId) { set, exercise, now ->
            writeCompletedSet(set, exercise, draft, now)
        }
        if (result == SessionMutationResult.Updated && before?.status == SessionSetStatus.PENDING.name) {
            val exercise = sessionDao.getExercise(before.sessionExerciseId)
            if (exercise != null && !startRestAfterPendingCompletion(before.id)) {
                clearRestOnSession(exercise.sessionId)
            }
        }
        result
    }

    /**
     * Completes a set from a notification action.
     *
     * The write is the same row update as [completeSet], but it runs only when [setId] is still
     * [SessionFocusLogic.currentPendingSet] for [sessionId] inside this repository's mutex.
     * A stale or repeated action therefore cannot complete a later set, and this entry point
     * does not edit an already completed set.
     */
    suspend fun completeCurrentPendingSetFromNotification(
        sessionId: Long,
        setId: Long
    ): NotificationSetCompletion = mutex.withLock {
        val aggregate = getAggregate(sessionId) ?: return@withLock NotificationSetCompletion.NotFound
        if (aggregate.session.status != SessionStatus.IN_PROGRESS) {
            return@withLock NotificationSetCompletion.Stale
        }
        val current = SessionFocusLogic.currentPendingSet(aggregate)
            ?: return@withLock NotificationSetCompletion.Stale
        if (current.id != setId || current.status != SessionSetStatus.PENDING) {
            return@withLock NotificationSetCompletion.Stale
        }
        val set = sessionDao.getSet(setId) ?: return@withLock NotificationSetCompletion.NotFound
        if (set.status != SessionSetStatus.PENDING.name) {
            return@withLock NotificationSetCompletion.Stale
        }
        val exercise = sessionDao.getExercise(set.sessionExerciseId)
            ?: return@withLock NotificationSetCompletion.NotFound
        if (exercise.sessionId != sessionId) {
            return@withLock NotificationSetCompletion.Stale
        }
        val sessionRow = sessionDao.getById(sessionId)
            ?: return@withLock NotificationSetCompletion.NotFound
        if (set.timedWork || sessionRow.workPhase != null) {
            return@withLock NotificationSetCompletion.Stale
        }
        val now = clock.millis()
        when (val written = writeCompletedSet(set, exercise, ActualSetLogic.draftFromSet(current), now)) {
            SessionMutationResult.Updated -> {
                if (!startRestAfterPendingCompletion(setId)) {
                    val session = sessionDao.getById(sessionId)
                        ?: return@withLock NotificationSetCompletion.NotFound
                    if (session.restPhase != null) {
                        clearRestOnSession(sessionId)
                    } else {
                        touchSession(session, now)
                    }
                }
                NotificationSetCompletion.Updated
            }
            is SessionMutationResult.Invalid -> NotificationSetCompletion.Invalid
            SessionMutationResult.NotFound -> NotificationSetCompletion.NotFound
            SessionMutationResult.NotActive,
            SessionMutationResult.OriginalSetProtected -> NotificationSetCompletion.Stale
        }
    }

    suspend fun skipSet(setId: Long): SessionMutationResult = mutex.withLock {
        val before = sessionDao.getSet(setId)
        val result = mutateActiveSet(setId) { set, _, _ ->
            sessionDao.updateSet(
                set.copy(
                    actualReps = null,
                    actualLoadKind = null,
                    actualWeightKg = null,
                    actualDurationSeconds = null,
                    actualDistanceMeters = null,
                    status = SessionSetStatus.SKIPPED.name,
                    completedAt = null,
                    draftPayload = null
                )
            )
            SessionMutationResult.Updated
        }
        if (result == SessionMutationResult.Updated && before != null) {
            val exercise = sessionDao.getExercise(before.sessionExerciseId)
            if (exercise != null) clearRestOnSession(exercise.sessionId)
        }
        result
    }

    suspend fun undoSkip(setId: Long): SessionMutationResult = mutex.withLock {
        val before = sessionDao.getSet(setId)
        val result = mutateActiveSet(setId) { set, _, _ ->
            if (set.status != SessionSetStatus.SKIPPED.name) {
                return@mutateActiveSet SessionMutationResult.Updated
            }
            sessionDao.updateSet(
                set.copy(
                    actualReps = set.plannedMinReps,
                    actualLoadKind = set.plannedLoadKind,
                    actualWeightKg = set.plannedWeightKg,
                    actualDurationSeconds = set.plannedDurationSeconds,
                    actualDistanceMeters = set.plannedDistanceMeters,
                    status = SessionSetStatus.PENDING.name,
                    completedAt = null,
                    draftPayload = null
                )
            )
            SessionMutationResult.Updated
        }
        if (result == SessionMutationResult.Updated && before?.status == SessionSetStatus.SKIPPED.name) {
            val exercise = sessionDao.getExercise(before.sessionExerciseId)
            if (exercise != null) clearRestOnSession(exercise.sessionId)
        }
        result
    }

    suspend fun addExtraSet(sessionExerciseId: Long): SessionMutationResult = mutex.withLock {
        val exercise = sessionDao.getExercise(sessionExerciseId) ?: return SessionMutationResult.NotFound
        val session = sessionDao.getById(exercise.sessionId) ?: return SessionMutationResult.NotFound
        if (session.status != SessionStatus.IN_PROGRESS.name) {
            return SessionMutationResult.NotActive
        }
        val existing = sessionDao.getSets(sessionExerciseId)
        val measurement = ExerciseEnumCodec.measurement(exercise.measurementType)
        val resistance = ExerciseEnumCodec.resistance(exercise.resistanceBasis)
        val previous = existing.maxByOrNull { it.position }?.toModel()
        val (planned, actual) = if (previous != null) {
            ActualSetLogic.extraSetFromPrevious(previous)
        } else {
            ActualSetLogic.extraSetDefaults(measurement, resistance)
        }
        val previousEntity = existing.maxByOrNull { it.position }
        val timed = previousEntity?.timedWork == true
        val parsed = ActualSetLogic.parse(actual, measurement, resistance, timedWork = timed).first
        sessionDao.insertSet(
            WorkoutSessionSetEntity(
                sessionExerciseId = sessionExerciseId,
                position = existing.size,
                plannedMinReps = planned.minReps,
                plannedMaxReps = planned.maxReps,
                plannedLoadKind = planned.loadKind.name,
                plannedWeightKg = planned.weightKg,
                plannedDurationSeconds = planned.durationSeconds,
                plannedDistanceMeters = planned.distanceMeters,
                actualReps = if (timed) null else parsed?.reps ?: planned.minReps,
                actualLoadKind = (parsed?.loadKind ?: planned.loadKind).name,
                actualWeightKg = parsed?.weightKg ?: planned.weightKg,
                actualDurationSeconds = parsed?.durationSeconds ?: planned.durationSeconds,
                actualDistanceMeters = parsed?.distanceMeters ?: planned.distanceMeters,
                status = SessionSetStatus.PENDING.name,
                completedAt = null,
                addedDuringWorkout = true,
                restSeconds = previousEntity?.restSeconds,
                timedWork = timed
            )
        )
        touchSession(session)
        clearRestOnSession(session.id)
        SessionMutationResult.Updated
    }

    /**
     * Appends a catalog exercise to the active session. The saved plan is not read or written.
     * The new occurrence and its first set are both marked as added during the workout.
     * Selecting an existing exercise does not create a catalog row.
     */
    suspend fun addExtraExercise(sessionId: Long, exerciseId: Long): AddExtraExerciseResult = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return AddExtraExerciseResult.SessionNotFound
        if (session.status != SessionStatus.IN_PROGRESS.name) {
            return AddExtraExerciseResult.NotActive
        }
        val exercise = exerciseDao.getById(exerciseId) ?: return AddExtraExerciseResult.ExerciseNotFound
        if (exercise.archived) {
            return AddExtraExerciseResult.ExerciseArchived
        }
        val catalogMuscles = exerciseDao.getMuscles(exerciseId)
        val existing = sessionDao.getExercises(sessionId)
        val measurement = ExerciseEnumCodec.measurement(exercise.measurementType)
        val resistance = ExerciseEnumCodec.resistance(exercise.resistanceBasis)
        val (planned, actual) = ActualSetLogic.extraSetDefaults(measurement, resistance)
        val parsed = ActualSetLogic.parse(actual, measurement, resistance).first
        val position = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val inserted = sessionDao.insertAddedExercise(
            exercise = WorkoutSessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exercise.id,
                position = position,
                name = exercise.name,
                category = exercise.category,
                movementPattern = exercise.movementPattern,
                measurementType = exercise.measurementType,
                resistanceBasis = exercise.resistanceBasis,
                weightInterpretation = exercise.weightInterpretation,
                primaryMuscle = catalogMuscles.firstOrNull { it.role == MuscleRole.PRIMARY.name }?.muscleGroup
                    ?: catalogMuscles.firstOrNull()?.muscleGroup
                    ?: "FULL_BODY",
                notes = null,
                addedDuringWorkout = true
            ),
            muscles = catalogMuscles.map { muscle ->
                WorkoutSessionExerciseMuscleEntity(
                    sessionExerciseId = 0L,
                    muscleGroup = muscle.muscleGroup,
                    role = muscle.role
                )
            },
            set = WorkoutSessionSetEntity(
                sessionExerciseId = 0L,
                position = 0,
                plannedMinReps = planned.minReps,
                plannedMaxReps = planned.maxReps,
                plannedLoadKind = planned.loadKind.name,
                plannedWeightKg = planned.weightKg,
                plannedDurationSeconds = planned.durationSeconds,
                plannedDistanceMeters = planned.distanceMeters,
                actualReps = parsed?.reps ?: planned.minReps,
                actualLoadKind = (parsed?.loadKind ?: planned.loadKind).name,
                actualWeightKg = parsed?.weightKg ?: planned.weightKg,
                actualDurationSeconds = parsed?.durationSeconds ?: planned.durationSeconds,
                actualDistanceMeters = parsed?.distanceMeters ?: planned.distanceMeters,
                status = SessionSetStatus.PENDING.name,
                completedAt = null,
                addedDuringWorkout = true
            )
        )
        touchSession(session)
        clearRestOnSession(session.id)
        AddExtraExerciseResult.Added(inserted.sessionExerciseId, inserted.setId)
    }

    suspend fun removeExtraExercise(sessionExerciseId: Long): SessionMutationResult = mutex.withLock {
        val exercise = sessionDao.getExercise(sessionExerciseId) ?: return SessionMutationResult.NotFound
        if (!exercise.addedDuringWorkout) {
            return SessionMutationResult.OriginalSetProtected
        }
        val session = sessionDao.getById(exercise.sessionId) ?: return SessionMutationResult.NotFound
        if (session.status != SessionStatus.IN_PROGRESS.name) {
            return SessionMutationResult.NotActive
        }
        sessionDao.deleteExerciseAndReindex(exercise.id, session.id)
        touchSession(session)
        clearRestOnSession(session.id)
        SessionMutationResult.Updated
    }

    suspend fun removeExtraSet(setId: Long): SessionMutationResult = mutex.withLock {
        val set = sessionDao.getSet(setId) ?: return SessionMutationResult.NotFound
        if (!set.addedDuringWorkout) {
            return SessionMutationResult.OriginalSetProtected
        }
        if (set.status != SessionSetStatus.PENDING.name) {
            return SessionMutationResult.OriginalSetProtected
        }
        val exercise = sessionDao.getExercise(set.sessionExerciseId) ?: return SessionMutationResult.NotFound
        val session = sessionDao.getById(exercise.sessionId) ?: return SessionMutationResult.NotFound
        if (session.status != SessionStatus.IN_PROGRESS.name) {
            return SessionMutationResult.NotActive
        }
        sessionDao.deleteSet(setId)
        sessionDao.getSets(exercise.id).sortedBy { it.position }.forEachIndexed { index, remaining ->
            if (remaining.position != index) {
                sessionDao.updateSetPosition(remaining.id, index)
            }
        }
        touchSession(session)
        clearRestOnSession(session.id)
        SessionMutationResult.Updated
    }

    suspend fun deleteWorkout(sessionId: Long): DeleteWorkoutResult = mutex.withLock {
        withContext(NonCancellable) {
            try {
                val session = sessionDao.getById(sessionId) ?: return@withContext DeleteWorkoutResult.NotFound
                if (session.status == SessionStatus.IN_PROGRESS.name) {
                    return@withContext DeleteWorkoutResult.ActiveSession
                }
                val affectsHeatmap = session.status == SessionStatus.COMPLETED.name
                val deleted = sessionDao.deleteSessionAggregate(sessionId)
                if (deleted <= 0) {
                    DeleteWorkoutResult.NotFound
                } else {
                    if (affectsHeatmap) {
                        onHeatmapDataChanged()
                        notifyTrainingHistoryChanged(null)
                    }
                    DeleteWorkoutResult.Deleted
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DeleteWorkoutResult.Failed
            }
        }
    }

    suspend fun finish(sessionId: Long, skipRemaining: Boolean): FinishWorkoutResult = mutex.withLock {
        withContext(NonCancellable) {
            val outcome = sessionDao.finishActiveSession(sessionId, clock.millis(), skipRemaining)
            when (outcome) {
                1 -> {
                    onHeatmapDataChanged()
                    val completed = try {
                        sessionDao.getById(sessionId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    if (completed != null) {
                        try {
                            onNativeWorkoutCompleted(completed.clientWorkoutId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                        }
                        notifyTrainingHistoryChanged(completed.clientWorkoutId)
                    }
                    FinishWorkoutResult.Finished
                }
                -1 -> FinishWorkoutResult.AlreadyTerminal
                -2 -> {
                    val pending = sessionDao.getExercises(sessionId)
                        .flatMap { sessionDao.getSets(it.id) }
                        .count { it.status == SessionSetStatus.PENDING.name }
                    FinishWorkoutResult.PendingRemaining(pending)
                }
                else -> FinishWorkoutResult.NotFound
            }
        }
    }

    suspend fun abandon(sessionId: Long): AbandonWorkoutResult = mutex.withLock {
        withContext(NonCancellable) {
            try {
                val session = sessionDao.getById(sessionId) ?: return@withContext AbandonWorkoutResult.NotFound
                if (session.status != SessionStatus.IN_PROGRESS.name) {
                    return@withContext AbandonWorkoutResult.AlreadyTerminal
                }
                val deleted = sessionDao.deleteSessionAggregate(sessionId)
                if (deleted <= 0) {
                    AbandonWorkoutResult.NotFound
                } else {
                    AbandonWorkoutResult.Abandoned
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AbandonWorkoutResult.Failed
            }
        }
    }

    fun observeReferencedTemplateIds(): Flow<Set<Long>> {
        return sessionDao.observeReferencedTemplateIds().map { it.toSet() }
    }

    fun observeReferencedExerciseIds(): Flow<Set<Long>> {
        return sessionDao.observeReferencedExerciseIds().map { it.toSet() }
    }

    suspend fun hasTemplateReferences(templateId: Long): Boolean {
        return sessionDao.countTemplateReferences(templateId) > 0
    }

    suspend fun hasExerciseReferences(exerciseId: Long): Boolean {
        return sessionDao.countExerciseReferences(exerciseId) > 0
    }

    suspend fun existingImportFingerprints(): Set<String> {
        return sessionDao.getImportFingerprints().toSet()
    }

    suspend fun completedWorkoutNames(): List<Pair<LocalDate, String>> {
        return sessionDao.getCompletedDateNames().map { row ->
            LocalDate.parse(row.date) to row.name
        }
    }

    private suspend fun notifyTrainingHistoryChanged(clientWorkoutId: String?) {
        try {
            onTrainingHistoryChanged(clientWorkoutId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private suspend fun writeCompletedSet(
        set: WorkoutSessionSetEntity,
        exercise: WorkoutSessionExerciseEntity,
        draft: ActualSetDraft,
        now: Long
    ): SessionMutationResult {
        val (values, errors) = ActualSetLogic.parse(
            draft,
            ExerciseEnumCodec.measurement(exercise.measurementType),
            ExerciseEnumCodec.resistance(exercise.resistanceBasis),
            timedWork = set.timedWork
        )
        if (values == null) {
            return SessionMutationResult.Invalid(errors)
        }
        sessionDao.updateSet(
            set.copy(
                actualReps = values.reps,
                actualLoadKind = values.loadKind.name,
                actualWeightKg = values.weightKg,
                actualDurationSeconds = values.durationSeconds,
                actualDistanceMeters = values.distanceMeters,
                status = SessionSetStatus.COMPLETED.name,
                completedAt = set.completedAt ?: now,
                draftPayload = null
            )
        )
        return SessionMutationResult.Updated
    }

    private suspend fun mutateActiveSet(
        setId: Long,
        block: suspend (WorkoutSessionSetEntity, WorkoutSessionExerciseEntity, Long) -> SessionMutationResult
    ): SessionMutationResult {
        val set = sessionDao.getSet(setId) ?: return SessionMutationResult.NotFound
        val exercise = sessionDao.getExercise(set.sessionExerciseId) ?: return SessionMutationResult.NotFound
        val session = sessionDao.getById(exercise.sessionId) ?: return SessionMutationResult.NotFound
        if (session.status != SessionStatus.IN_PROGRESS.name) {
            return SessionMutationResult.NotActive
        }
        val now = clock.millis()
        val result = block(set, exercise, now)
        if (result == SessionMutationResult.Updated) {
            touchSession(session, now)
        }
        return result
    }

    private suspend fun touchSession(session: WorkoutSessionEntity, now: Long = clock.millis()) {
        sessionDao.updateSession(session.copy(updatedAt = now))
    }

    suspend fun extendRest(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return@withLock false
        if (session.restPhase != RestPhase.COUNTING.name || session.restGeneration != generation) {
            return@withLock false
        }
        val sample = restClock()
        val anchored = RestTimerLogic.reanchorAfterReboot(session.toRestSnapshot(), sample)
            ?: session.toRestSnapshot()
        val extended = RestTimerLogic.extend(anchored, sample) ?: return@withLock false
        sessionDao.updateSession(session.withRest(extended, clock.millis()))
        true
    }

    /**
     * Stops the current interval and returns the completed set that rest followed.
     * A mismatched generation means another action already owned this interval.
     */
    suspend fun skipRest(sessionId: Long, generation: Long): Long? = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return@withLock null
        if (session.restPhase == null || session.restGeneration != generation) return@withLock null
        val origin = session.restOriginSetId
        sessionDao.updateSession(session.clearedRest(clock.millis()))
        origin
    }

    suspend fun clearRest(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return@withLock false
        if (session.restPhase == null || session.restGeneration != generation) return@withLock false
        sessionDao.updateSession(session.clearedRest(clock.millis()))
        true
    }

    suspend fun acknowledgeRest(sessionId: Long, generation: Long): Long? = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return@withLock null
        if (session.restPhase != RestPhase.AWAITING_NEXT.name || session.restGeneration != generation) {
            return@withLock null
        }
        val origin = session.restOriginSetId
        sessionDao.updateSession(session.clearedRest(clock.millis()))
        origin
    }

    suspend fun expireRest(sessionId: Long, generation: Long): RestExpiryResult = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return@withLock RestExpiryResult.Ignored
        if (session.status != SessionStatus.IN_PROGRESS.name ||
            session.restPhase != RestPhase.COUNTING.name ||
            session.restGeneration != generation
        ) {
            return@withLock RestExpiryResult.Ignored
        }
        val sample = restClock()
        val snapshot = session.toRestSnapshot()
        val reanchored = RestTimerLogic.reanchorAfterReboot(snapshot, sample)
        if (reanchored != null) {
            sessionDao.updateSession(session.withRest(reanchored, clock.millis()))
            return@withLock RestExpiryResult.Ignored
        }
        if (RestTimerLogic.remaining(snapshot, sample) !is RestRemaining.Expired) {
            return@withLock RestExpiryResult.Ignored
        }
        val origin = session.restOriginSetId ?: return@withLock RestExpiryResult.Ignored
        val now = clock.millis()
        if (RestProgressionMode.fromStored(session.restProgressionMode) == RestProgressionMode.FLEXIBLE) {
            sessionDao.updateSession(
                session.copy(
                    restPhase = RestPhase.AWAITING_NEXT.name,
                    restAnchorElapsedRealtimeMillis = null,
                    restDeadlineElapsedRealtimeMillis = null,
                    restDeadlineWallClockMillis = null,
                    restDurationMillis = null,
                    restGeneration = session.restGeneration + 1,
                    updatedAt = now
                )
            )
            return@withLock RestExpiryResult.Waiting
        }
        sessionDao.updateSession(session.clearedRest(now))
        RestExpiryResult.Advanced(origin)
    }

    private suspend fun startRestAfterPendingCompletion(setId: Long): Boolean {
        if (!timersAllowed()) return false
        val set = sessionDao.getSet(setId) ?: return false
        val exercise = sessionDao.getExercise(set.sessionExerciseId) ?: return false
        val aggregate = getAggregate(exercise.sessionId) ?: return false
        val plan = RestTimerLogic.planAfterCompletion(aggregate, setId) ?: return false
        val session = sessionDao.getById(exercise.sessionId) ?: return false
        val started = RestTimerLogic.started(plan, restClock(), session.restGeneration + 1)
        sessionDao.updateSession(session.withRest(started, clock.millis()))
        return true
    }

    private suspend fun clearRestOnSession(sessionId: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.restPhase == null && session.workPhase == null) return
        sessionDao.updateSession(session.clearedRest(clock.millis()))
    }

    suspend fun startWork(sessionId: Long, setId: Long): Boolean = mutex.withLock {
        if (!timersAllowed()) return@withLock false
        val set = sessionDao.getSet(setId) ?: return@withLock false
        val exercise = sessionDao.getExercise(set.sessionExerciseId) ?: return@withLock false
        if (exercise.sessionId != sessionId) return@withLock false
        if (set.status != SessionSetStatus.PENDING.name) return@withLock false
        val measurement = ExerciseEnumCodec.measurement(exercise.measurementType)
        val target = set.plannedDurationSeconds ?: 0
        val eligible = set.timedWork || (PlannedSetLogic.requiresDuration(measurement) && target > 0)
        if (!eligible || target <= 0 || target > WorkDuration.MAX_SECONDS) return@withLock false
        val session = sessionDao.getById(sessionId) ?: return@withLock false
        if (session.status != SessionStatus.IN_PROGRESS.name) return@withLock false
        if (session.restPhase != null || session.workPhase != null) return@withLock false
        val started = WorkTimerLogic.started(
            setId = setId,
            targetMillis = target * 1000L,
            clock = restClock(),
            generation = session.workGeneration + 1
        )
        sessionDao.updateSession(session.withWork(started, clock.millis()))
        true
    }

    suspend fun pauseWork(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        updateWork(sessionId, generation) { snapshot ->
            WorkTimerLogic.pause(snapshot, restClock())
        }
    }

    suspend fun resumeWork(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        updateWork(sessionId, generation) { snapshot ->
            WorkTimerLogic.resume(snapshot, restClock())
        }
    }

    suspend fun finishWorkEarly(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        updateWork(sessionId, generation) { snapshot ->
            WorkTimerLogic.finishEarly(snapshot, restClock())
        }
    }

    suspend fun expireWork(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        updateWork(sessionId, generation) { snapshot ->
            WorkTimerLogic.expire(snapshot)
        }
    }

    suspend fun discardWork(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return@withLock false
        if (session.workPhase == null || session.workGeneration != generation) return@withLock false
        sessionDao.updateSession(
            session.copy(
                workPhase = null,
                workSetId = null,
                workTargetMillis = null,
                workElapsedActiveMillis = null,
                workAnchorElapsedRealtimeMillis = null,
                workDeadlineElapsedRealtimeMillis = null,
                workDeadlineWallClockMillis = null,
                workGeneration = session.workGeneration + 1,
                updatedAt = clock.millis()
            )
        )
        true
    }

    suspend fun reanchorWork(sessionId: Long, generation: Long): Boolean = mutex.withLock {
        updateWork(sessionId, generation) { snapshot ->
            WorkTimerLogic.reanchorAfterReboot(snapshot, restClock())
        }
    }

    /**
     * Drops a rest countdown and stops a running or paused work interval without completing a set.
     * Measured work is kept as an unconfirmed result. Returns the rest origin when a rest
     * countdown was cancelled, so the caller can restore normal set focus.
     */
    suspend fun releaseProTimers(sessionId: Long): Long? = mutex.withLock {
        val session = sessionDao.getById(sessionId) ?: return@withLock null
        if (session.status != SessionStatus.IN_PROGRESS.name) return@withLock null
        val origin = if (session.restPhase != null) session.restOriginSetId else null
        val clockSample = restClock()
        val frozen = if (session.workPhase == WorkPhase.RUNNING.name || session.workPhase == WorkPhase.PAUSED.name) {
            WorkTimerLogic.finishEarly(session.toWorkSnapshot(), clockSample)
        } else {
            null
        }
        val cleared = if (session.restPhase != null) session.clearedRest(clock.millis()) else session
        val next = if (frozen != null) {
            cleared.withWork(frozen, clock.millis())
        } else if (session.restPhase != null) {
            cleared
        } else {
            return@withLock null
        }
        sessionDao.updateSession(next)
        origin
    }

    private suspend fun updateWork(
        sessionId: Long,
        generation: Long,
        transform: (WorkTimerSnapshot) -> WorkTimerSnapshot?
    ): Boolean {
        val session = sessionDao.getById(sessionId) ?: return false
        if (session.workGeneration != generation || session.workPhase == null) return false
        val updated = transform(session.toWorkSnapshot()) ?: return false
        sessionDao.updateSession(session.withWork(updated, clock.millis()))
        return true
    }

    private fun toSummary(
        session: WorkoutSessionEntity,
        exercises: List<WorkoutSessionExerciseEntity>,
        sets: List<WorkoutSessionSetEntity>,
        muscles: List<WorkoutSessionExerciseMuscleEntity>
    ): WorkoutSessionSummary {
        val aggregate = toAggregate(session, exercises, sets, muscles)
        val progress = SessionProgressLogic.fromAggregate(aggregate)
        val primaries = aggregate.exercises.map { it.exercise.primaryMuscle }.distinct()
        return WorkoutSessionSummary(
            session = aggregate.session,
            progress = progress,
            exerciseCount = aggregate.exercises.size,
            primaryMuscles = primaries,
            durationMillis = ElapsedTime.forSession(aggregate.session)
        )
    }

    private fun toAggregate(
        session: WorkoutSessionEntity,
        exercises: List<WorkoutSessionExerciseEntity>,
        sets: List<WorkoutSessionSetEntity>,
        muscles: List<WorkoutSessionExerciseMuscleEntity>
    ): WorkoutSessionAggregate {
        val sessionExercises = exercises.filter { it.sessionId == session.id }.sortedBy { it.position }
        val setsByExercise = sets.groupBy { it.sessionExerciseId }
        val musclesByExercise = muscles.groupBy { it.sessionExerciseId }
        return WorkoutSessionAggregate(
            session = session.toModel(),
            exercises = sessionExercises.map { exercise ->
                SessionExerciseItem(
                    exercise = exercise.toModel(musclesByExercise[exercise.id].orEmpty()),
                    sets = setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.map { it.toModel() }
                )
            }
        )
    }

    private fun toImportedAggregate(
        workout: WorkoutImportResolvedWorkout,
        fingerprint: String
    ): Pair<WorkoutSessionEntity, List<Triple<WorkoutSessionExerciseEntity, List<WorkoutSessionExerciseMuscleEntity>, List<WorkoutSessionSetEntity>>>> {
        val startedAt = toEpochMilli(workout.startedAt)
        val finishedAt = toEpochMilli(workout.finishedAt)
        val session = WorkoutSessionEntity(
            templateId = null,
            templateName = workout.name,
            status = SessionStatus.COMPLETED.name,
            workoutDate = workout.workoutDate.toString(),
            startedAt = startedAt,
            finishedAt = finishedAt,
            abandonedAt = null,
            notes = workout.notes,
            bodyWeightKg = workout.bodyWeight.kilograms,
            bodyWeightSource = workout.bodyWeight.source.name,
            bodyWeightSourceDate = workout.bodyWeight.sourceDate?.toString(),
            createdAt = finishedAt,
            updatedAt = finishedAt,
            activeLock = null,
            importFingerprint = fingerprint,
            clientWorkoutId = java.util.UUID.randomUUID().toString()
        )
        val children = workout.exercises.map { exercise ->
            val snapshot = exercise.snapshot!!
            val exerciseRow = WorkoutSessionExerciseEntity(
                sessionId = 0L,
                exerciseId = snapshot.exerciseId,
                position = 0,
                name = snapshot.catalogName,
                category = snapshot.category.name,
                movementPattern = snapshot.movementPattern.name,
                measurementType = snapshot.measurementType.name,
                resistanceBasis = snapshot.resistanceBasis.name,
                weightInterpretation = snapshot.weightInterpretation.name,
                primaryMuscle = snapshot.primaryMuscle.name,
                notes = snapshot.notes
            )
            val muscles = snapshot.muscles.map { muscle ->
                WorkoutSessionExerciseMuscleEntity(
                    sessionExerciseId = 0L,
                    muscleGroup = muscle.muscleGroup.name,
                    role = muscle.role.name
                )
            }
            val sets = exercise.sets.map { set ->
                val plannedKind = (set.loadKind ?: PlannedLoadKind.NONE).name
                val completed = set.status == SessionSetStatus.COMPLETED
                WorkoutSessionSetEntity(
                    sessionExerciseId = 0L,
                    position = 0,
                    plannedMinReps = set.reps,
                    plannedMaxReps = set.reps,
                    plannedLoadKind = plannedKind,
                    plannedWeightKg = decimal(set.weightKg),
                    plannedDurationSeconds = set.durationSeconds,
                    plannedDistanceMeters = decimal(set.distanceMeters),
                    actualReps = if (completed) set.reps else null,
                    actualLoadKind = if (completed) plannedKind else null,
                    actualWeightKg = if (completed) decimal(set.weightKg) else null,
                    actualDurationSeconds = if (completed) set.durationSeconds else null,
                    actualDistanceMeters = if (completed) decimal(set.distanceMeters) else null,
                    status = set.status.name,
                    completedAt = if (completed) finishedAt else null,
                    addedDuringWorkout = false
                )
            }
            Triple(exerciseRow, muscles, sets)
        }
        return session to children
    }

    private fun toEpochMilli(value: LocalDateTime): Long {
        return value.atZone(clock.zone).toInstant().toEpochMilli()
    }

    private fun decimal(value: java.math.BigDecimal?): Double? {
        return value?.toDouble()
    }

    private fun isForeignKeyConstraint(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SQLiteConstraintException &&
                current.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true)
            ) {
                return true
            }
            if (current.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun isUniqueConstraint(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("UNIQUE", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }
}

private fun WorkoutSessionEntity.toRestSnapshot(): RestTimerSnapshot {
    return RestTimerSnapshot(
        phase = RestPhase.fromStored(restPhase),
        originSetId = restOriginSetId,
        nextSetId = restNextSetId,
        anchorElapsedRealtimeMillis = restAnchorElapsedRealtimeMillis,
        deadlineElapsedRealtimeMillis = restDeadlineElapsedRealtimeMillis,
        deadlineWallClockMillis = restDeadlineWallClockMillis,
        durationMillis = restDurationMillis,
        generation = restGeneration,
        mode = RestProgressionMode.fromStored(restProgressionMode)
    )
}

private fun WorkoutSessionEntity.withRest(snapshot: RestTimerSnapshot, updatedAt: Long): WorkoutSessionEntity {
    return copy(
        restPhase = snapshot.phase?.name,
        restOriginSetId = snapshot.originSetId,
        restNextSetId = snapshot.nextSetId,
        restAnchorElapsedRealtimeMillis = snapshot.anchorElapsedRealtimeMillis,
        restDeadlineElapsedRealtimeMillis = snapshot.deadlineElapsedRealtimeMillis,
        restDeadlineWallClockMillis = snapshot.deadlineWallClockMillis,
        restDurationMillis = snapshot.durationMillis,
        restGeneration = snapshot.generation,
        restProgressionMode = snapshot.mode.name,
        workPhase = null,
        workSetId = null,
        workTargetMillis = null,
        workElapsedActiveMillis = null,
        workAnchorElapsedRealtimeMillis = null,
        workDeadlineElapsedRealtimeMillis = null,
        workDeadlineWallClockMillis = null,
        workGeneration = workGeneration + 1,
        updatedAt = updatedAt
    )
}

private fun WorkoutSessionEntity.withWork(snapshot: WorkTimerSnapshot, updatedAt: Long): WorkoutSessionEntity {
    return copy(
        workPhase = snapshot.phase?.name,
        workSetId = snapshot.setId,
        workTargetMillis = snapshot.targetMillis,
        workElapsedActiveMillis = snapshot.elapsedActiveMillis,
        workAnchorElapsedRealtimeMillis = snapshot.anchorElapsedRealtimeMillis,
        workDeadlineElapsedRealtimeMillis = snapshot.deadlineElapsedRealtimeMillis,
        workDeadlineWallClockMillis = snapshot.deadlineWallClockMillis,
        workGeneration = snapshot.generation,
        restPhase = null,
        restOriginSetId = null,
        restNextSetId = null,
        restAnchorElapsedRealtimeMillis = null,
        restDeadlineElapsedRealtimeMillis = null,
        restDeadlineWallClockMillis = null,
        restDurationMillis = null,
        restGeneration = restGeneration + 1,
        updatedAt = updatedAt
    )
}

private fun WorkoutSessionEntity.clearedRest(updatedAt: Long): WorkoutSessionEntity {
    return copy(
        restPhase = null,
        restOriginSetId = null,
        restNextSetId = null,
        restAnchorElapsedRealtimeMillis = null,
        restDeadlineElapsedRealtimeMillis = null,
        restDeadlineWallClockMillis = null,
        restDurationMillis = null,
        restGeneration = restGeneration + 1,
        workPhase = null,
        workSetId = null,
        workTargetMillis = null,
        workElapsedActiveMillis = null,
        workAnchorElapsedRealtimeMillis = null,
        workDeadlineElapsedRealtimeMillis = null,
        workDeadlineWallClockMillis = null,
        workGeneration = workGeneration + 1,
        updatedAt = updatedAt
    )
}

private fun WorkoutSessionEntity.toWorkSnapshot(): WorkTimerSnapshot {
    return WorkTimerSnapshot(
        phase = WorkPhase.fromStored(workPhase),
        setId = workSetId,
        targetMillis = workTargetMillis,
        elapsedActiveMillis = workElapsedActiveMillis,
        anchorElapsedRealtimeMillis = workAnchorElapsedRealtimeMillis,
        deadlineElapsedRealtimeMillis = workDeadlineElapsedRealtimeMillis,
        deadlineWallClockMillis = workDeadlineWallClockMillis,
        generation = workGeneration
    )
}
