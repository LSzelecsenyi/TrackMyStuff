package app.mymusclemap.ui.workout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.provider.Settings
import kotlin.math.pow

internal const val WORKOUT_COMPLETE_MARK = "workout-complete-mark"

internal object WorkoutCompletionMarkTimeline {
    const val CheckDurationMs = 300
    const val CircleDurationMs = 450
    const val SettleDurationMs = 250
    const val PeakScale = 1.12f
    val TotalDurationMs = CheckDurationMs + CircleDurationMs + SettleDurationMs

    fun at(elapsedMs: Long, reducedMotion: Boolean): WorkoutCompletionMarkProgress {
        if (reducedMotion || elapsedMs >= TotalDurationMs) {
            return WorkoutCompletionMarkProgress(1f, 1f, 1f)
        }
        val elapsed = elapsedMs.coerceAtLeast(0L)
        val check = easeOutCubic((elapsed.toFloat() / CheckDurationMs).coerceIn(0f, 1f))
        val afterCheck = (elapsed - CheckDurationMs).coerceAtLeast(0L)
        val circleLinear = (afterCheck.toFloat() / CircleDurationMs).coerceIn(0f, 1f)
        val circle = if (afterCheck <= 0L) 0f else easeOutCubic(circleLinear)
        val scale = when {
            afterCheck <= 0L -> 1f
            afterCheck < CircleDurationMs -> lerp(1f, PeakScale, circleLinear)
            else -> {
                val settle = ((afterCheck - CircleDurationMs).toFloat() / SettleDurationMs).coerceIn(0f, 1f)
                lerp(PeakScale, 1f, easeOutCubic(settle))
            }
        }
        return WorkoutCompletionMarkProgress(
            checkFraction = if (elapsed <= 0L) 0f else check,
            circleFraction = circle,
            scale = scale
        )
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return start + (stop - start) * fraction
    }

    private fun easeOutCubic(t: Float): Float {
        return 1f - (1f - t).pow(3)
    }
}

internal data class WorkoutCompletionMarkProgress(
    val checkFraction: Float,
    val circleFraction: Float,
    val scale: Float
)

@Composable
internal fun WorkoutCompletionMark(
    contentDescription: String,
    modifier: Modifier = Modifier,
    playAnimation: Boolean = true
) {
    val context = LocalContext.current
    val animatorScale = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }
    var settled by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = !playAnimation || animatorScale <= 0f || settled
    val elapsed = remember { Animatable(if (reducedMotion) WorkoutCompletionMarkTimeline.TotalDurationMs.toFloat() else 0f) }
    LaunchedEffect(playAnimation, animatorScale) {
        if (!playAnimation || animatorScale <= 0f || settled) {
            elapsed.snapTo(WorkoutCompletionMarkTimeline.TotalDurationMs.toFloat())
            settled = true
            return@LaunchedEffect
        }
        elapsed.snapTo(0f)
        elapsed.animateTo(
            WorkoutCompletionMarkTimeline.TotalDurationMs.toFloat(),
            tween(WorkoutCompletionMarkTimeline.TotalDurationMs, easing = LinearEasing)
        )
        settled = true
    }
    val progress = WorkoutCompletionMarkTimeline.at(
        elapsed.value.toLong(),
        !playAnimation || animatorScale <= 0f || settled
    )
    val accent = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF81C784)
    } else {
        Color(0xFF2E7D32)
    }
    Canvas(
        modifier = modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = progress.scale
                scaleY = progress.scale
            }
            .semantics { this.contentDescription = contentDescription }
            .testTag(WORKOUT_COMPLETE_MARK)
    ) {
        val stroke = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val inset = stroke.width / 2f
        val bounds = Rect(inset, inset, size.width - inset, size.height - inset)
        if (progress.circleFraction > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.circleFraction,
                useCenter = false,
                topLeft = Offset(bounds.left, bounds.top),
                size = bounds.size,
                style = stroke
            )
        }
        if (progress.checkFraction > 0f) {
            val check = checkPath(bounds)
            val measure = PathMeasure()
            measure.setPath(check, false)
            val segment = Path()
            measure.getSegment(0f, measure.length * progress.checkFraction, segment, true)
            drawPath(segment, accent, style = stroke)
        }
    }
}

internal fun checkPath(bounds: Rect): Path {
    val w = bounds.width
    val h = bounds.height
    return Path().apply {
        moveTo(bounds.left + w * 0.28f, bounds.top + h * 0.52f)
        lineTo(bounds.left + w * 0.42f, bounds.top + h * 0.68f)
        lineTo(bounds.left + w * 0.74f, bounds.top + h * 0.36f)
    }
}
