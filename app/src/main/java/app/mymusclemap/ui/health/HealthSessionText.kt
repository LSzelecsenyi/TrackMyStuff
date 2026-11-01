package app.mymusclemap.ui.health

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.mymusclemap.R
import app.mymusclemap.domain.health.ExerciseHistoryPreview
import app.mymusclemap.domain.health.ExerciseHistoryPreviewPhrase
import java.time.Duration

internal fun healthSourceLabel(context: Context, packageName: String): String? {
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun healthSessionDuration(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    if (totalSeconds < 60) {
        val shown = if (totalSeconds == 0L && duration > Duration.ZERO) 1L else totalSeconds
        return stringResource(R.string.health_connect_duration_s, shown)
    }
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val minutePart = minutes % 60
    return when {
        hours > 0 -> stringResource(R.string.health_connect_duration_hm, hours, minutePart)
        seconds == 0L -> stringResource(R.string.health_connect_duration_m, minutes)
        else -> stringResource(R.string.health_connect_duration_ms, minutes, seconds)
    }
}

@Composable
internal fun exerciseHistoryPreviewText(
    preview: ExerciseHistoryPreview,
    sourceName: (String) -> String?
): String? {
    if (preview.count == 0 && preview.phrase != ExerciseHistoryPreviewPhrase.Loaded) return null
    return when (preview.phrase) {
        ExerciseHistoryPreviewPhrase.Loaded ->
            pluralStringResource(R.plurals.health_connect_history_activities_loaded, preview.count, preview.count)
        ExerciseHistoryPreviewPhrase.NamedSource -> {
            val name = preview.sourcePackage?.let(sourceName)
            if (name.isNullOrBlank()) {
                pluralStringResource(
                    R.plurals.health_connect_history_activities_connected,
                    preview.count,
                    preview.count
                )
            } else {
                pluralStringResource(
                    R.plurals.health_connect_history_activities_from_source,
                    preview.count,
                    preview.count,
                    name
                )
            }
        }
        ExerciseHistoryPreviewPhrase.ConnectedApps ->
            pluralStringResource(
                R.plurals.health_connect_history_activities_connected,
                preview.count,
                preview.count
            )
        ExerciseHistoryPreviewPhrase.CountOnly ->
            pluralStringResource(R.plurals.health_connect_history_activities, preview.count, preview.count)
    }
}
