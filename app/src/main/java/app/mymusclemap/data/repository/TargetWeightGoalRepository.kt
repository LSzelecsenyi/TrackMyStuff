package app.mymusclemap.data.repository

import app.mymusclemap.data.local.TargetWeightGoalDao
import app.mymusclemap.data.local.TargetWeightGoalEntity
import app.mymusclemap.domain.achievements.TargetWeightDirection
import app.mymusclemap.domain.achievements.TargetWeightGoalFacts
import app.mymusclemap.domain.achievements.TargetWeightProgressEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

class TargetWeightGoalRepository(
    private val dao: TargetWeightGoalDao,
    private val clock: Clock,
    private val onChanged: suspend () -> Unit = {}
) {
    fun observeActive(): Flow<TargetWeightGoalFacts?> {
        return dao.observeActive().map { it?.toFacts() }
    }

    suspend fun active(): TargetWeightGoalFacts? = dao.active()?.toFacts()

    /**
     * A different target kilogram starts a new goal id.
     * Saving the same target again leaves the current journey in place.
     */
    suspend fun setTarget(targetKg: Double, latestWeightKg: Double?) {
        val target = TargetWeightProgressEvaluator.roundKg(targetKg)
        val current = dao.active()
        if (current != null && TargetWeightProgressEvaluator.sameKg(current.targetKg, target)) {
            return
        }
        val now = clock.millis()
        if (current != null) {
            dao.retire(current.id, retiredAt = now, updatedAt = now)
        }
        val baseline = latestWeightKg?.let { TargetWeightProgressEvaluator.roundKg(it) }
        val direction = baseline?.let { TargetWeightProgressEvaluator.direction(it, target) }
        dao.insert(
            TargetWeightGoalEntity(
                targetKg = target,
                baselineKg = baseline,
                direction = direction?.name,
                createdAt = now,
                updatedAt = now,
                retiredAt = null
            )
        )
        notifyChanged()
    }

    suspend fun clear() {
        val current = dao.active() ?: return
        val now = clock.millis()
        dao.retire(current.id, retiredAt = now, updatedAt = now)
        notifyChanged()
    }

    private suspend fun notifyChanged() {
        try {
            onChanged()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
}

internal fun TargetWeightGoalEntity.toFacts(): TargetWeightGoalFacts {
    return TargetWeightGoalFacts(
        id = id,
        targetKg = targetKg,
        baselineKg = baselineKg,
        direction = TargetWeightDirection.fromStorage(direction),
        active = retiredAt == null,
        createdAt = createdAt
    )
}
