package app.mymusclemap.ui.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mymusclemap.WeightTrackerApplication

internal data class AcceptedSetCompletion(
    val sessionId: Long,
    val setId: Long
)

/**
 * Returns the set named by the intent when the signed-in container is available.
 * A missing container returns null before any asynchronous broadcast work starts.
 */
internal fun acceptedSetCompletion(
    intent: Intent?,
    containerAvailable: Boolean
): AcceptedSetCompletion? {
    if (!containerAvailable) return null
    val ids = ActiveWorkoutNotifications.completionIds(intent) ?: return null
    return AcceptedSetCompletion(ids.first, ids.second)
}

class ActiveWorkoutSetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val container = (context.applicationContext as? WeightTrackerApplication)?.container ?: return
        val accepted = acceptedSetCompletion(intent, containerAvailable = true) ?: return
        val pending = goAsync()
        container.activeWorkoutNotifications.completeFromAction(
            accepted.sessionId,
            accepted.setId,
            pending::finish
        )
    }
}
