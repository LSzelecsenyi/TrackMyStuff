package app.mymusclemap.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object HeatmapWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Test hook. Production leaves this null. Invoked synchronously before the
     * background Glance refresh so unit tests can observe the request.
     */
    internal var refreshListener: (() -> Unit)? = null

    fun update(context: Context) {
        val listener = refreshListener
        if (listener != null) {
            listener()
            return
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                MuscleHeatmapWidget().updateAll(appContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A missing widget host must not fail the workout or restore that requested this.
            }
        }
    }
}
