package app.mymusclemap.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.unit.ColorProvider
import app.mymusclemap.MainActivity
import app.mymusclemap.R
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import kotlin.math.roundToInt

class MuscleHeatmapWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loaded = try {
            HeatmapWidgetState.load(context)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val chrome = HeatmapWidgetChromeFactory.fromContext(context)
        provideContent {
            MuscleHeatmapWidgetContent(
                state = loaded,
                chrome = chrome,
                openOverview = actionStartActivity(HeatmapWidgetIntents.openOverview(LocalContext.current))
            )
        }
    }
}

object HeatmapWidgetIntents {
    fun openOverview(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_OVERVIEW, true)
        }
    }
}

@Composable
internal fun MuscleHeatmapWidgetContent(
    state: MuscleHeatmapState?,
    chrome: HeatmapWidgetChrome,
    openOverview: androidx.glance.action.Action
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val layout = heatmapWidgetLayout(size.width, size.height)
    val density = context.resources.displayMetrics.density
    val bitmaps = state?.let { heatmap ->
        val requestedWidth = (layout.figureWidth.value * density).roundToInt()
        val requestedHeight = (layout.figureHeight.value * density).roundToInt()
        try {
            MuscleMapBitmapRenderer.render(
                state = heatmap,
                widthPx = requestedWidth,
                heightPx = requestedHeight,
                chrome = chrome.bitmap
            )
        } catch (_: Exception) {
            null
        }
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color.Transparent))
            .semantics {
                contentDescription = context.getString(R.string.widget_heatmap_content_description)
                testTag = HEATMAP_WIDGET_TAG
            }
            .clickable(openOverview)
            .padding(layout.padding),
        contentAlignment = Alignment.Center
    ) {
        if (bitmaps != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BodyImage(
                    bitmap = bitmaps.front,
                    description = context.getString(R.string.widget_heatmap_front),
                    tag = HEATMAP_WIDGET_FRONT_TAG,
                    width = layout.figureWidth,
                    height = layout.figureHeight
                )
                Spacer(modifier = GlanceModifier.width(layout.gap))
                BodyImage(
                    bitmap = bitmaps.back,
                    description = context.getString(R.string.widget_heatmap_back),
                    tag = HEATMAP_WIDGET_BACK_TAG,
                    width = layout.figureWidth,
                    height = layout.figureHeight
                )
            }
        }
    }
}

@Composable
private fun BodyImage(
    bitmap: android.graphics.Bitmap,
    description: String,
    tag: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = description,
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .semantics { testTag = tag },
        contentScale = ContentScale.Fit
    )
}

suspend fun refreshHeatmapWidgets(context: Context) {
    val widget = MuscleHeatmapWidget()
    GlanceAppWidgetManager(context).getGlanceIds(MuscleHeatmapWidget::class.java).forEach { id ->
        widget.update(context, id)
    }
}

internal const val HEATMAP_WIDGET_TAG = "heatmap-widget"
internal const val HEATMAP_WIDGET_FRONT_TAG = "heatmap-widget-front"
internal const val HEATMAP_WIDGET_BACK_TAG = "heatmap-widget-back"
