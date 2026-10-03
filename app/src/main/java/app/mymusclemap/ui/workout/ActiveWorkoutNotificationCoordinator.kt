package app.mymusclemap.ui.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.SharedPreferences
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.workout.ActiveWorkoutNotificationLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class ActiveWorkoutNotificationCoordinator(
    private val context: Context,
    private val repository: WorkoutSessionRepository,
    private val scope: CoroutineScope,
    private val prompts: ActiveWorkoutNotificationPrompts = ActiveWorkoutNotificationPrompts(context),
    private val notificationId: Int = ActiveWorkoutNotifications.NOTIFICATION_ID
) {
    private val started = AtomicBoolean(false)
    private val renderLock = Mutex()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            repository.observeActiveAggregate().collect {
                publishFromRoom()
            }
        }
    }

    fun refresh() {
        scope.launch { publishFromRoom() }
    }

    fun hasAskedForPermission(): Boolean = prompts.hasAsked()

    fun markPermissionAsked() {
        prompts.markAsked()
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
            val aggregate = repository.activeAggregate()
            ActiveWorkoutNotifications.publish(
                context,
                ActiveWorkoutNotificationLogic.project(aggregate),
                notificationId
            )
        }
    }
}

class ActiveWorkoutNotificationPrompts(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    fun hasAsked(): Boolean = preferences.getBoolean(KEY_ASKED, false)

    fun markAsked() {
        preferences.edit().putBoolean(KEY_ASKED, true).apply()
    }

    private companion object {
        const val PREFS = "active_workout_notification"
        const val KEY_ASKED = "permission_requested"
    }
}
