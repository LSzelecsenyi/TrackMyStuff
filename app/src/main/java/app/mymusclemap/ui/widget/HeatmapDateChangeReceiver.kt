package app.mymusclemap.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * DATE_CHANGED, TIME_SET, and TIMEZONE_CHANGED remain exempt from the implicit
 * broadcast limits, so a manifest receiver can refresh the time-relative heatmap
 * without a periodic alarm.
 */
class HeatmapDateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> HeatmapWidgetUpdater.update(context)
        }
    }
}
