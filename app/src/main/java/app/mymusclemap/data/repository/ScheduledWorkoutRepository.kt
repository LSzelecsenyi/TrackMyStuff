package app.mymusclemap.data.repository

import app.mymusclemap.data.local.ScheduledWorkoutDao
import app.mymusclemap.data.local.ScheduledWorkoutEntity
import app.mymusclemap.data.local.ScheduledWorkoutQueryRow
import app.mymusclemap.data.local.WorkoutSessionDao
import app.mymusclemap.data.local.WorkoutTemplateDao
import app.mymusclemap.domain.workout.RescheduleWorkoutResult
import app.mymusclemap.domain.workout.ScheduleWorkoutResult
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.UnscheduleWorkoutResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

class ScheduledWorkoutRepository(
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
    private val templateDao: WorkoutTemplateDao,
    private val sessionDao: WorkoutSessionDao,
    private val clock: Clock
) {
    private val mutex = Mutex()

    fun observeOnDate(date: LocalDate): Flow<List<ScheduledWorkout>> {
        return scheduledWorkoutDao.observeOnDate(date.toString()).map { rows -> rows.map { it.toModel() } }
    }

    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<ScheduledWorkout>> {
        return scheduledWorkoutDao.observeBetween(start.toString(), end.toString()).map { rows ->
            rows.map { it.toModel() }
        }
    }

    fun observeUpcoming(fromDate: LocalDate): Flow<List<ScheduledWorkout>> {
        return scheduledWorkoutDao.observeUpcoming(fromDate.toString()).map { rows ->
            rows.map { it.toModel() }
        }
    }

    fun observeById(id: Long): Flow<ScheduledWorkout?> {
        return scheduledWorkoutDao.observeById(id).map { it?.toModel() }
    }

    suspend fun getById(id: Long): ScheduledWorkout? {
        return scheduledWorkoutDao.getById(id)?.toModel()
    }

    suspend fun schedule(templateId: Long, date: LocalDate): ScheduleWorkoutResult = mutex.withLock {
        val template = templateDao.getById(templateId) ?: return ScheduleWorkoutResult.TemplateNotFound
        if (template.archived) {
            return ScheduleWorkoutResult.TemplateArchived
        }
        if (scheduledWorkoutDao.findByDateAndTemplate(date.toString(), templateId) != null) {
            return ScheduleWorkoutResult.Duplicate
        }
        return try {
            val id = scheduledWorkoutDao.insert(
                ScheduledWorkoutEntity(
                    scheduledDate = date.toString(),
                    templateId = templateId,
                    createdAt = clock.millis()
                )
            )
            ScheduleWorkoutResult.Scheduled(id)
        } catch (error: Exception) {
            if (isUniqueConstraint(error)) {
                ScheduleWorkoutResult.Duplicate
            } else {
                throw error
            }
        }
    }

    suspend fun reschedule(id: Long, date: LocalDate): RescheduleWorkoutResult = mutex.withLock {
        val existing = scheduledWorkoutDao.getEntity(id) ?: return RescheduleWorkoutResult.NotFound
        if (sessionDao.getSessionIdByScheduledWorkoutId(id) != null) {
            return RescheduleWorkoutResult.LinkedToSession
        }
        if (existing.scheduledDate == date.toString()) {
            return RescheduleWorkoutResult.Moved
        }
        if (scheduledWorkoutDao.findByDateAndTemplate(date.toString(), existing.templateId) != null) {
            return RescheduleWorkoutResult.Duplicate
        }
        return try {
            scheduledWorkoutDao.update(existing.copy(scheduledDate = date.toString()))
            RescheduleWorkoutResult.Moved
        } catch (error: Exception) {
            when {
                isUniqueConstraint(error) -> RescheduleWorkoutResult.Duplicate
                isForeignKeyConstraint(error) -> RescheduleWorkoutResult.LinkedToSession
                else -> throw error
            }
        }
    }

    suspend fun unschedule(id: Long): UnscheduleWorkoutResult = mutex.withLock {
        scheduledWorkoutDao.getEntity(id) ?: return UnscheduleWorkoutResult.NotFound
        if (sessionDao.getSessionIdByScheduledWorkoutId(id) != null) {
            return UnscheduleWorkoutResult.LinkedToSession
        }
        return try {
            val deleted = scheduledWorkoutDao.deleteById(id)
            if (deleted <= 0) {
                UnscheduleWorkoutResult.NotFound
            } else {
                UnscheduleWorkoutResult.Removed
            }
        } catch (error: Exception) {
            if (isForeignKeyConstraint(error)) {
                UnscheduleWorkoutResult.LinkedToSession
            } else {
                throw error
            }
        }
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

    private fun isForeignKeyConstraint(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("FOREIGN KEY", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }
}

internal fun ScheduledWorkoutQueryRow.toModel(): ScheduledWorkout {
    return ScheduledWorkout(
        id = id,
        scheduledDate = LocalDate.parse(scheduledDate),
        templateId = templateId,
        templateName = templateName,
        exerciseCount = exerciseCount,
        plannedSetCount = plannedSetCount,
        templateArchived = templateArchived,
        sessionId = sessionId,
        sessionStatus = sessionStatus?.let { raw ->
            runCatching { SessionStatus.valueOf(raw) }.getOrNull()
        },
        createdAt = createdAt
    )
}
