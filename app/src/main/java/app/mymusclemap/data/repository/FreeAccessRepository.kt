package app.mymusclemap.data.repository

import app.mymusclemap.data.local.ExerciseDao
import app.mymusclemap.data.local.ExercisePlanReference
import app.mymusclemap.data.local.FreeAccessDao
import app.mymusclemap.data.local.WorkoutSessionDao
import app.mymusclemap.data.local.WorkoutTemplateDao
import app.mymusclemap.domain.entitlement.FreeAccessDecision
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import app.mymusclemap.domain.entitlement.FreeAccessSubject
import app.mymusclemap.domain.entitlement.FreeExerciseReleaseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

class FreeAccessRepository(
    private val dao: FreeAccessDao,
    private val templateDao: WorkoutTemplateDao,
    private val exerciseDao: ExerciseDao,
    private val grantsPro: () -> Boolean,
    private val entitlementReady: () -> Boolean = { true },
    private val sessionDao: WorkoutSessionDao? = null
) {
    private val mutex = Mutex()
    private val latest = AtomicReference(unloaded())
    private val revision = MutableStateFlow(0)

    fun decisions(): Flow<FreeAccessDecision> {
        return combine(
            dao.observeState(),
            templateDao.observeAll(),
            exerciseDao.observeAll(),
            revision
        ) { _, _, _, _ ->
            current()
        }
    }

    fun current(): FreeAccessDecision = latest.get()

    suspend fun refresh(legacyRestoreWithoutSelection: Boolean = false): FreeAccessDecision = mutex.withLock {
        val plans = planSubjects()
        val exercises = customExerciseSubjects()
        val previous = dao.read()
        val pro = grantsPro()
        if (FreeAccessLogic.holdForUnknown(previous, pro, entitlementReady())) {
            val held = if (previous == null) {
                unloaded()
            } else {
                FreeAccessLogic.decide(previous, previous.lastKnownPro == true, plans, exercises)
            }
            latest.set(held)
            revision.value += 1
            return@withLock held
        }
        val next = FreeAccessLogic.reconcile(
            previous = previous,
            grantsPro = pro,
            plans = plans,
            customExercises = exercises,
            legacyRestoreWithoutSelection = legacyRestoreWithoutSelection && previous == null
        )
        if (next != previous) {
            dao.replace(next)
        }
        val decision = FreeAccessLogic.decide(next, grantsPro(), plans, exercises)
        latest.set(decision)
        revision.value += 1
        decision
    }

    suspend fun confirm(planIds: Set<Long>, customExerciseIds: Set<Long>): Boolean = mutex.withLock {
        val plans = planSubjects()
        val exercises = customExerciseSubjects()
        val current = dao.read() ?: return@withLock false
        val confirmed = FreeAccessLogic.confirm(current, planIds, customExerciseIds, plans, exercises)
            ?: return@withLock false
        dao.replace(confirmed)
        latest.set(FreeAccessLogic.decide(confirmed, grantsPro(), plans, exercises))
        revision.value += 1
        true
    }

    suspend fun notePlanCreated(planId: Long) = noteCreated(planId = planId)

    suspend fun noteCustomExerciseCreated(exerciseId: Long) {
        allocateCreatedCustomExercise(exerciseId)
    }

    suspend fun plansReferencing(exerciseId: Long): List<ExercisePlanReference> {
        return templateDao.plansReferencingExercise(exerciseId)
    }

    suspend fun releaseCustomExercise(exerciseId: Long): FreeExerciseReleaseResult = mutex.withLock {
        if ((sessionDao?.pendingWorkCount(exerciseId) ?: 0) > 0) {
            return@withLock FreeExerciseReleaseResult.BlockedByActiveWorkout
        }
        val plans = planSubjects()
        val exercises = customExerciseSubjects()
        val pro = grantsPro()
        if (pro) return@withLock FreeExerciseReleaseResult.NotAllocated
        val current = dao.read() ?: FreeAccessLogic.reconcile(
            previous = null,
            grantsPro = false,
            plans = plans,
            customExercises = exercises
        )
        if (exerciseId in current.releasedCustomExerciseIds) {
            return@withLock FreeExerciseReleaseResult.AlreadyReleased
        }
        val next = FreeAccessLogic.releaseCustomExercise(current, exerciseId, plans, exercises)
            ?: return@withLock FreeExerciseReleaseResult.NotAllocated
        if (next == current) return@withLock FreeExerciseReleaseResult.AlreadyReleased
        dao.replace(next)
        latest.set(FreeAccessLogic.decide(next, false, plans, exercises))
        revision.value += 1
        FreeExerciseReleaseResult.Released
    }

    /**
     * Claims one FREE slot for an exercise id that was not already in this period.
     * Returns false when the id is locked, released, or the allocation is full.
     * PRO does not consume a FREE slot.
     */
    suspend fun allocateCreatedCustomExercise(exerciseId: Long): Boolean = mutex.withLock {
        val plans = planSubjects()
        val exercises = customExerciseSubjects()
        val pro = grantsPro()
        val previous = dao.read()
        if (pro) {
            val remembered = FreeAccessLogic.reconcile(previous, true, plans, exercises)
            if (remembered != previous) dao.replace(remembered)
            latest.set(FreeAccessLogic.decide(remembered, true, plans, exercises))
            revision.value += 1
            return@withLock true
        }
        val current = previous ?: FreeAccessLogic.reconcile(null, false, plans, exercises)
        if (current.phase == FreeAccessPhase.Required) {
            val allowed = exercises.size <= FreeAccessLogic.CUSTOM_EXERCISE_LIMIT
            if (allowed && current != previous) dao.replace(current)
            latest.set(FreeAccessLogic.decide(current, false, plans, exercises))
            revision.value += 1
            return@withLock allowed
        }
        val next = FreeAccessLogic.includeCreated(
            state = current,
            customExerciseId = exerciseId,
            plans = plans,
            customExercises = exercises
        )
        val wasNew = exerciseId !in current.knownCustomExerciseIds &&
            exerciseId !in current.releasedCustomExerciseIds
        val accepted = wasNew &&
            exerciseId in next.knownCustomExerciseIds &&
            (next.phase != FreeAccessPhase.Confirmed || exerciseId in next.selectedCustomExerciseIds)
        if (!accepted) {
            if (previous == null) dao.replace(current)
            latest.set(FreeAccessLogic.decide(if (previous == null) current else previous, false, plans, exercises))
            revision.value += 1
            return@withLock false
        }
        if (next != previous) dao.replace(next)
        latest.set(FreeAccessLogic.decide(next, false, plans, exercises))
        revision.value += 1
        true
    }

    private suspend fun noteCreated(planId: Long? = null, customExerciseId: Long? = null) {
        mutex.withLock {
            val plans = planSubjects()
            val exercises = customExerciseSubjects()
            val current = dao.read() ?: return@withLock
            val next = FreeAccessLogic.includeCreated(current, planId, customExerciseId, plans, exercises)
            if (next != current) {
                dao.replace(next)
            }
            latest.set(FreeAccessLogic.decide(next, grantsPro(), plans, exercises))
            revision.value += 1
        }
    }

    private suspend fun planSubjects(): List<FreeAccessSubject> {
        return templateDao.observeAll().first().map { FreeAccessSubject(it.id, it.createdAt) }
    }

    private suspend fun customExerciseSubjects(): List<FreeAccessSubject> {
        return exerciseDao.getAll()
            .filter { it.custom && !it.archived }
            .map { FreeAccessSubject(it.id, it.createdAt) }
    }

    private fun unloaded(): FreeAccessDecision {
        return FreeAccessDecision(
            state = FreeAccessState(0L, null, FreeAccessPhase.NotRequired),
            selectionRequired = false,
            planLimit = FreeAccessLogic.PLAN_LIMIT,
            customExerciseLimit = FreeAccessLogic.CUSTOM_EXERCISE_LIMIT,
            accessiblePlanIds = emptySet(),
            accessibleCustomExerciseIds = emptySet(),
            loaded = false
        )
    }
}
