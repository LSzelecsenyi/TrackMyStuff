package app.mymusclemap.ui.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mymusclemap.WeightTrackerApplication

class ActiveWorkoutSetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val ids = ActiveWorkoutNotifications.completionIds(intent) ?: return
        val app = context.applicationContext as? WeightTrackerApplication ?: return
        val pending = goAsync()
        app.container.activeWorkoutNotifications.completeFromAction(ids.first, ids.second, pending)
    }
}
