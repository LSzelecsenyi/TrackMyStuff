package hu.laca.weighttracker.data.repository

import android.database.sqlite.SQLiteConstraintException
import hu.laca.weighttracker.data.local.InsertStartedSessionResult
import hu.laca.weighttracker.data.local.WorkoutSessionDao
import hu.laca.weighttracker.data.local.WorkoutSessionEntity
import hu.laca.weighttracker.data.local.WorkoutSessionExerciseEntity
import hu.laca.weighttracker.data.local.WorkoutSessionExerciseMuscleEntity
import hu.laca.weighttracker.data.local.WorkoutSessionSetEntity
import hu.laca.weighttracker.data.local.WorkoutTemplateDao
import hu.laca.weighttracker.data.local.activeLock
import hu.laca.weighttracker.data.local.toModel
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.exercise.ExerciseEnumCodec
import hu.laca.weighttracker.domain.exercise.MuscleRole
import hu.laca.weighttracker.domain.workout.AbandonWorkoutResult
import hu.laca.weighttracker.domain.workout.DeleteWorkoutResult
import hu.laca.weighttracker.domain.workout.ActiveSessionSummary
import hu.laca.weighttracker.domain.workout.ActualSetDraft
import hu.laca.weighttracker.domain.workout.ActualSetLogic
import hu.laca.weighttracker.domain.workout.BodyWeightProposal
import hu.laca.weighttracker.domain.workout.BodyWeightSnapshotLogic
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.FinishWorkoutResult
import hu.laca.weighttracker.domain.workout.SessionExerciseItem
import hu.laca.weighttracker.domain.workout.SessionMutationResult
import hu.laca.weighttracker.domain.workout.SessionProgressLogic
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.musclemap.MuscleTrainingExercise
import hu.laca.weighttracker.domain.workout.WorkoutSessionAggregate
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import hu.laca.weighttracker.domain.workout.ElapsedTime
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportDatabaseFailure
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportFingerprint
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportPersistenceResult
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportPlan
import hu.laca.weighttracker.domain.workoutimport.WorkoutImportResolvedWorkout
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val exerciseDao: hu.laca.weighttracker.data.local.ExerciseDao,
    private val weightRepository: WeightRepository,
    private val clock: Clock,
    private val dateProvider: DateProvider
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

    suspend fun proposeBodyWeight(workoutDate: LocalDate = LocalDate.now(clock)): BodyWeightProposal {
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
            if (sessionDao.getSessionIdByScheduledWorkoutId(scheduledWorkoutId) != null) {
                return StartWorkoutResult.ScheduleAlreadyStarted
            }
        }
        val relations = templateDao.getExercises(templateId)
        if (relations.isEmpty() || relations.all { templateDao.getSets(it.id).isEmpty() }) {
            return StartWorkoutResult.TemplateEmpty
        }
        val workoutDate = LocalDate.now(clock)
        val resolvedProposal = proposeBodyWeight(workoutDate)
        val now = clock.millis()
        val session = WorkoutSessionEntity(
            templateId = template.id,
            templateName = template.name,
            status = SessionStatus.IN_PROGRESS.name,
            workoutDate = dateProvider.today().toString(),
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
            scheduledWorkoutId = scheduledWorkoutId
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
                notes = relation.notes
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
                    actualReps = planned.minReps,
                    actualLoadKind = planned.loadKind,
                    actualWeightKg = planned.weightKg,
                    actualDurationSeconds = planned.durationSeconds,
                    actualDistanceMeters = planned.distanceMeters,
                    status = SessionSetStatus.PENDING.name,
                    completedAt = null,
                    addedDuringWorkout = false
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
                    templateId
                )
            ) {
                is InsertStartedSessionResult.Inserted -> StartWorkoutResult.Started(inserted.sessionId)
                InsertStartedSessionResult.AlreadyActive -> StartWorkoutResult.AlreadyActive
                InsertStartedSessionResult.TemplateNotFound -> StartWorkoutResult.TemplateNotFound
                InsertStartedSessionResult.TemplateArchived -> StartWorkoutResult.TemplateArchived
                InsertStartedSessionResult.ScheduleNotFound -> StartWorkoutResult.ScheduleNotFound
                InsertStartedSessionResult.ScheduleTemplateMismatch -> StartWorkoutResult.ScheduleTemplateMismatch
                InsertStartedSessionResult.ScheduleAlreadyStarted -> StartWorkoutResult.ScheduleAlreadyStarted
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

    suspend fun completeSet(setId: Long, draft: ActualSetDraft): SessionMutationResult = mutex.withLock {
        mutateActiveSet(setId) { set, exercise, now ->
            val (values, errors) = ActualSetLogic.parse(
                draft,
                ExerciseEnumCodec.measurement(exercise.measurementType),
                ExerciseEnumCodec.resistance(exercise.resistanceBasis)
            )
            if (values == null) {
                return@mutateActiveSet SessionMutationResult.Invalid(errors)
            }
            val completedAt = set.completedAt ?: now
            sessionDao.updateSet(
                set.copy(
                    actualReps = values.reps,
                    actualLoadKind = values.loadKind.name,
                    actualWeightKg = values.weightKg,
                    actualDurationSeconds = values.durationSeconds,
                    actualDistanceMeters = values.distanceMeters,
                    status = SessionSetStatus.COMPLETED.name,
                    completedAt = completedAt
                )
            )
            SessionMutationResult.Updated
        }
    }

    suspend fun skipSet(setId: Long): SessionMutationResult = mutex.withLock {
        mutateActiveSet(setId) { set, _, _ ->
            sessionDao.updateSet(
                set.copy(
                    actualReps = null,
                    actualLoadKind = null,
                    actualWeightKg = null,
                    actualDurationSeconds = null,
                    actualDistanceMeters = null,
                    status = SessionSetStatus.SKIPPED.name,
                    completedAt = null
                )
            )
            SessionMutationResult.Updated
        }
    }

    suspend fun undoSkip(setId: Long): SessionMutationResult = mutex.withLock {
        mutateActiveSet(setId) { set, _, _ ->
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
                    completedAt = null
                )
            )
            SessionMutationResult.Updated
        }
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
        val parsed = ActualSetLogic.parse(actual, measurement, resistance).first
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
        SessionMutationResult.Updated
    }

    suspend fun deleteWorkout(sessionId: Long): DeleteWorkoutResult = mutex.withLock {
        withContext(NonCancellable) {
            try {
                val session = sessionDao.getById(sessionId) ?: return@withContext DeleteWorkoutResult.NotFound
                if (session.status == SessionStatus.IN_PROGRESS.name) {
                    return@withContext DeleteWorkoutResult.ActiveSession
                }
                val deleted = sessionDao.deleteSessionAggregate(sessionId)
                if (deleted <= 0) {
                    DeleteWorkoutResult.NotFound
                } else {
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
        val session = sessionDao.getById(sessionId) ?: return FinishWorkoutResult.NotFound
        if (session.status != SessionStatus.IN_PROGRESS.name) {
            return FinishWorkoutResult.AlreadyTerminal
        }
        val pending = sessionDao.getExercises(sessionId).flatMap { sessionDao.getSets(it.id) }
            .filter { it.status == SessionSetStatus.PENDING.name }
        if (pending.isNotEmpty() && !skipRemaining) {
            return FinishWorkoutResult.PendingRemaining(pending.size)
        }
        val now = clock.millis()
        pending.forEach { set ->
            sessionDao.updateSet(
                set.copy(
                    actualReps = null,
                    actualLoadKind = null,
                    actualWeightKg = null,
                    actualDurationSeconds = null,
                    actualDistanceMeters = null,
                    status = SessionSetStatus.SKIPPED.name,
                    completedAt = null
                )
            )
        }
        sessionDao.updateSession(
            session.copy(
                status = SessionStatus.COMPLETED.name,
                finishedAt = now,
                updatedAt = now,
                activeLock = SessionStatus.COMPLETED.activeLock()
            )
        )
        FinishWorkoutResult.Finished
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
            importFingerprint = fingerprint
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
