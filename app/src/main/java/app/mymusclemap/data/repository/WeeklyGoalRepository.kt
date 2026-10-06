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
    private val clock: Clock,
    private val onGoalChanged: suspend () -> Unit = {},
    private val earliestCompletedDate: suspend () -> LocalDate? = { null }
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
        val planned = if (workoutsPerWeek != null && history.none { it.workoutsPerWeek != null }) {
            WeeklyGoalLogic.planFirstGoal(today, workoutsPerWeek, earliestCompletedDate())
        } else {
            listOfNotNull(WeeklyGoalLogic.propose(history, today, workoutsPerWeek))
        }
        if (planned.isEmpty()) return
        val updated = planned.fold(history) { current, proposal ->
            WeeklyGoalLogic.apply(current, proposal)
        }
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
        try {
            onGoalChanged()
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }
}

private fun WeeklyWorkoutGoalEntity.toRevision(): WeeklyGoalRevision {
    return WeeklyGoalRevision(
        effectiveWeekStart = LocalDate.parse(effectiveWeekStart),
        workoutsPerWeek = workoutsPerWeek,
        graceWeek = graceWeek
    )
}
