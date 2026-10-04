package app.mymusclemap.ui.workout

import android.content.BroadcastReceiver
import android.content.Context
import app.mymusclemap.data.preferences.LockScreenSetCompletionStore
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.workout.ActiveWorkoutNotificationLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class ActiveWorkoutNotificationCoordinator(
    private val context: Context,
    private val repository: WorkoutSessionRepository,
    private val preferences: LockScreenSetCompletionStore,
    private val scope: CoroutineScope,
    private val notificationId: Int = ActiveWorkoutNotifications.NOTIFICATION_ID
) {
    private val started = AtomicBoolean(false)
    private val renderLock = Mutex()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(repository.observeActiveAggregate(), preferences.enabled) { _, _ ->
                Unit
            }.collect {
                publishFromRoom()
            }
        }
    }

    fun refresh() {
        scope.launch { publishFromRoom() }
    }

    fun completeFromAction(
        sessionId: Long,
        setId: Long,
        pending: BroadcastReceiver.PendingResult
    ) {
        scope.launch {
            try {
                performAction(sessionId, setId)
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun performAction(sessionId: Long, setId: Long) {
        try {
            if (sessionId > 0L && setId > 0L) {
                repository.completeCurrentPendingSetFromNotification(sessionId, setId)
            }
            publishFromRoom()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publishFromRoom()
        }
    }

    private suspend fun publishFromRoom() {
        renderLock.withLock {
            if (!preferences.enabled.first()) {
                ActiveWorkoutNotifications.cancel(context, notificationId)
                return@withLock
            }
            val aggregate = repository.activeAggregate()
            ActiveWorkoutNotifications.publish(
                context,
                ActiveWorkoutNotificationLogic.project(aggregate),
                notificationId
            )
        }
    }
}
