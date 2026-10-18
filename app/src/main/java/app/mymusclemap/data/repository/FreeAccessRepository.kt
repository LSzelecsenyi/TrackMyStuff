package app.mymusclemap.data.repository

import app.mymusclemap.data.local.ExerciseDao
import app.mymusclemap.data.local.FreeAccessDao
import app.mymusclemap.data.local.WorkoutTemplateDao
import app.mymusclemap.domain.entitlement.FreeAccessDecision
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import app.mymusclemap.domain.entitlement.FreeAccessSubject
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
    private val entitlementReady: () -> Boolean = { true }
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

    suspend fun noteCustomExerciseCreated(exerciseId: Long) = noteCreated(customExerciseId = exerciseId)

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
