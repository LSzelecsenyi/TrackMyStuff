package app.mymusclemap.data.repository

import app.mymusclemap.data.local.WeeklyWorkoutGoalDao
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WeeklyGoalRepository(
    private val dao: WeeklyWorkoutGoalDao,
    private val clock: Clock
) {
    fun observe(): Flow<List<WeeklyGoalRevision>> {
        return dao.observeAll().map { rows -> rows.map { it.toRevision() } }
    }

    suspend fun setGoal(today: LocalDate, workoutsPerWeek: Int) {
        save(today, workoutsPerWeek)
    }

    suspend fun disable(today: LocalDate) {
        save(today, null)
    }

    private suspend fun save(today: LocalDate, workoutsPerWeek: Int?) {
        val existing = dao.getAll()
        val history = existing.map { it.toRevision() }
        val proposal = WeeklyGoalLogic.propose(history, today, workoutsPerWeek) ?: return
        val updated = WeeklyGoalLogic.apply(history, proposal)
        if (updated == history) return
        val now = clock.millis()
        val previousByWeek = existing.associateBy { it.effectiveWeekStart }
        dao.replaceAll(
            updated.map { revision ->
                val previous = previousByWeek[revision.effectiveWeekStart.toString()]
                WeeklyWorkoutGoalEntity(
                    effectiveWeekStart = revision.effectiveWeekStart.toString(),
                    workoutsPerWeek = revision.workoutsPerWeek,
                    graceWeek = revision.graceWeek,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = now
                )
            }
        )
    }
}

private fun WeeklyWorkoutGoalEntity.toRevision(): WeeklyGoalRevision {
    return WeeklyGoalRevision(
        effectiveWeekStart = LocalDate.parse(effectiveWeekStart),
        workoutsPerWeek = workoutsPerWeek,
        graceWeek = graceWeek
    )
}
