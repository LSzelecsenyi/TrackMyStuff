package app.mymusclemap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mymusclemap.domain.model.ChartPoint
import app.mymusclemap.domain.model.ChartScale
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.hypot
import kotlin.math.roundToInt

internal const val OVERVIEW_WEIGHT_CHART_TOOLTIP = "overview_weight_chart_tooltip"

internal object ChartCanvasMetrics {
    val plotLeft = 52.dp
    val plotRightInset = 16.dp
    val plotTop = 12.dp
    val plotBottomInset = 40.dp
    val tooltipClearance = 18.dp
    val tooltipMargin = 4.dp
    const val hitSlopPx = 28f
}

@Composable
fun WeightChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    contentDescription: String,
    onPointSelected: (ChartPoint?) -> Unit = {},
    subdued: Boolean = false,
    chartHeight: Dp = 240.dp,
    showSelectionTooltip: Boolean = false
) {
    val units = app.mymusclemap.ui.units.LocalUnitSystem.current
    val selectionTooltip: (@Composable (SeriesPoint) -> Unit)? = if (showSelectionTooltip) {
        { series ->
            val stored = points.firstOrNull { it.date == series.date && it.weightKg == series.value }
            val kilograms = stored?.weightKg ?: series.value
            ChartSelectionTooltip(
                date = UiFormatters.compactDate(series.date),
                value = UiFormatters.weightKg(kilograms, units)
            )
        }
    } else {
        null
    }
    SeriesChart(
        points = points.map { it.toSeriesPoint() },
        contentDescription = contentDescription,
        onPointSelected = { selected ->
            onPointSelected(selected?.let { point -> points.firstOrNull { it.date == point.date } })
        },
        subdued = subdued,
        chartHeight = chartHeight,
        yAxisLabel = { value ->
            app.mymusclemap.domain.units.MeasurementText.axisWeight(value, units)
        },
        valueDomain = ChartValueDomain.Padded,
        modifier = modifier,
        selectionTooltip = selectionTooltip
    )
}

@Composable
fun SeriesChart(
    points: List<SeriesPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onPointSelected: (SeriesPoint?) -> Unit = {},
    subdued: Boolean = false,
    chartHeight: Dp = 240.dp,
    valueDomain: ChartValueDomain = ChartValueDomain.Padded,
    xAxisLabel: (LocalDate) -> String = { UiFormatters.compactDate(it) },
    yAxisLabel: (Double) -> String = { value ->
        String.format(app.mymusclemap.domain.locale.AppLocale.UI, "%.1f", value)
    },
    snapXAxisToPoints: Boolean = false,
    selectionTooltip: (@Composable (SeriesPoint) -> Unit)? = null
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = if (subdued) 0.08f else 0.16f)
    val markerColor = MaterialTheme.colorScheme.onSurface
    val markerInner = MaterialTheme.colorScheme.background
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (subdued) 0.55f else 1f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedHalo = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val lineWidth = if (subdued) 2.dp else 3.dp
    val gridWidth = if (subdued) 0.5.dp else 1.dp

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .clipToBounds()
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (points.isEmpty()) return@BoxWithConstraints
        val layout = ChartLayout.from(
            width = constraints.maxWidth.toFloat(),
            height = constraints.maxHeight.toFloat(),
            points = points,
            left = with(density) { ChartCanvasMetrics.plotLeft.toPx() },
            rightInset = with(density) { ChartCanvasMetrics.plotRightInset.toPx() },
            top = with(density) { ChartCanvasMetrics.plotTop.toPx() },
            bottomInset = with(density) { ChartCanvasMetrics.plotBottomInset.toPx() },
            valueDomain = valueDomain,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            snapXAxisToPoints = snapXAxisToPoints
        )
        val layoutState = rememberUpdatedState(layout)
        val selectedMarker = layout.mapped.firstOrNull { it.point.date.toString() == selectedDate }
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(points, valueDomain) {
                    detectTapGestures { tap ->
                        val hit = layoutState.value.hitTest(tap)
                        selectedDate = hit?.date?.toString()
                        onPointSelected(hit)
                    }
                }
        ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            layout.yLabels.forEach { label ->
                drawLine(
                    color = gridColor,
                    start = Offset(layout.plotLeft, label.y),
                    end = Offset(layout.plotRight, label.y),
                    strokeWidth = gridWidth.toPx()
                )
                val measured = textMeasurer.measure(
                    text = label.text,
                    style = TextStyle(color = labelColor, fontSize = 11.sp)
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(4.dp.toPx(), label.y - measured.size.height / 2f)
                )
            }
            layout.xLabels.forEach { label ->
                val measured = textMeasurer.measure(
                    text = label.text,
                    style = TextStyle(color = labelColor, fontSize = 11.sp)
                )
                val x = (label.x - measured.size.width / 2f)
                    .coerceIn(layout.plotLeft, layout.plotRight - measured.size.width)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x, layout.plotBottom + 8.dp.toPx())
                )
            }
            if (layout.mapped.isNotEmpty()) {
                val area = Path().apply {
                    val first = layout.mapped.first()
                    moveTo(first.offset.x, layout.plotBottom)
                    lineTo(first.offset.x, first.offset.y)
                    layout.mapped.drop(1).forEach { point ->
                        lineTo(point.offset.x, point.offset.y)
                    }
                    val last = layout.mapped.last()
                    lineTo(last.offset.x, layout.plotBottom)
                    close()
                }
                drawPath(path = area, color = fillColor)
            }
            if (layout.mapped.size > 1) {
                val path = Path().apply {
                    val first = layout.mapped.first()
                    moveTo(first.offset.x, first.offset.y)
                    layout.mapped.drop(1).forEach { point ->
                        lineTo(point.offset.x, point.offset.y)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = lineWidth.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            layout.mapped.forEach { point ->
                val selected = point.point.date.toString() == selectedDate
                if (selected) {
                    drawCircle(
                        color = selectedHalo,
                        radius = 11.dp.toPx(),
                        center = point.offset
                    )
                }
                drawCircle(
                    color = if (selected) lineColor else markerColor,
                    radius = if (selected) 7.5.dp.toPx() else 5.dp.toPx(),
                    center = point.offset
                )
                drawCircle(
                    color = markerInner,
                    radius = if (selected) 3.dp.toPx() else 2.dp.toPx(),
                    center = point.offset
                )
            }
        }
            val tooltipContent = selectionTooltip
            val marker = selectedMarker
            if (marker != null && tooltipContent != null) {
                val anchor = marker.offset
                val point = marker.point
                ChartTooltipOverlay(
                    anchor = anchor,
                    clearance = with(density) { ChartCanvasMetrics.tooltipClearance.toPx() },
                    margin = with(density) { ChartCanvasMetrics.tooltipMargin.toPx() }
                ) {
                    tooltipContent(point)
                }
            }
        }
    }
}

@Composable
private fun ChartSelectionTooltip(
    date: String,
    value: String
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(8.dp)
    val container = if (dark) MaterialTheme.colorScheme.surfaceContainer else StrictBrand.dark
    val valueColor = if (dark) MaterialTheme.colorScheme.onSurface else StrictBrand.light
    val dateColor = if (dark) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        StrictBrand.light.copy(alpha = 0.72f)
    }
    val borderColor = if (dark) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        StrictBrand.light.copy(alpha = 0.18f)
    }
    Column(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag(OVERVIEW_WEIGHT_CHART_TOOLTIP)
            .background(container, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = date,
            style = AppTypeTokens.statCaption,
            color = dateColor,
            maxLines = 1
        )
        Text(
            text = value,
            style = AppTypeTokens.statValue.copy(fontSize = 15.sp, lineHeight = 18.sp),
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun ChartTooltipOverlay(
    anchor: Offset,
    clearance: Float,
    margin: Float,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { incoming ->
        val placeable = subcompose("tooltip") { content() }
            .first()
            .measure(
                Constraints(
                    maxWidth = incoming.maxWidth,
                    maxHeight = incoming.maxHeight
                )
            )
        val placement = placeChartTooltip(
            anchorX = anchor.x,
            anchorY = anchor.y,
            tooltipWidth = placeable.width.toFloat(),
            tooltipHeight = placeable.height.toFloat(),
            boundsWidth = incoming.maxWidth.toFloat(),
            boundsHeight = incoming.maxHeight.toFloat(),
            clearance = clearance,
            margin = margin
        )
        val maxX = (incoming.maxWidth - placeable.width).coerceAtLeast(0)
        val maxY = (incoming.maxHeight - placeable.height).coerceAtLeast(0)
        layout(incoming.maxWidth, incoming.maxHeight) {
            placeable.place(
                placement.left.roundToInt().coerceIn(0, maxX),
                placement.top.roundToInt().coerceIn(0, maxY)
            )
        }
    }
}

internal data class ChartTooltipPlacement(
    val left: Float,
    val top: Float
)

internal fun placeChartTooltip(
    anchorX: Float,
    anchorY: Float,
    tooltipWidth: Float,
    tooltipHeight: Float,
    boundsWidth: Float,
    boundsHeight: Float,
    clearance: Float,
    margin: Float
): ChartTooltipPlacement {
    val minLeft = margin
    val maxLeft = (boundsWidth - margin - tooltipWidth).coerceAtLeast(minLeft)
    val left = (anchorX - tooltipWidth / 2f).coerceIn(minLeft, maxLeft)
    val aboveTop = anchorY - clearance - tooltipHeight
    val belowTop = anchorY + clearance
    val minTop = margin
    val maxTop = (boundsHeight - margin - tooltipHeight).coerceAtLeast(minTop)
    val aboveFits = aboveTop >= minTop && aboveTop <= maxTop
    val belowFits = belowTop >= minTop && belowTop <= maxTop
    val top = when {
        aboveFits -> aboveTop
        belowFits -> belowTop
        anchorY >= boundsHeight / 2f -> aboveTop.coerceIn(minTop, maxTop)
        else -> belowTop.coerceIn(minTop, maxTop)
    }
    return ChartTooltipPlacement(left = left, top = top)
}

internal data class MappedPoint(
    val point: SeriesPoint,
    val offset: Offset
)

internal data class AxisLabel(
    val text: String,
    val x: Float = 0f,
    val y: Float = 0f
)

internal data class ChartLayout(
    val mapped: List<MappedPoint>,
    val xLabels: List<AxisLabel>,
    val yLabels: List<AxisLabel>,
    val plotLeft: Float,
    val plotRight: Float,
    val plotBottom: Float
) {
    fun hitTest(tap: Offset): SeriesPoint? {
        return mapped.minByOrNull { hypot(it.offset.x - tap.x, it.offset.y - tap.y) }
            ?.takeIf { hypot(it.offset.x - tap.x, it.offset.y - tap.y) <= ChartCanvasMetrics.hitSlopPx }
            ?.point
    }

    companion object {
        fun from(
            width: Float,
            height: Float,
            points: List<SeriesPoint>,
            left: Float,
            rightInset: Float,
            top: Float,
            bottomInset: Float,
            valueDomain: ChartValueDomain,
            xAxisLabel: (LocalDate) -> String = { UiFormatters.compactDate(it) },
            yAxisLabel: (Double) -> String = { value ->
                String.format(app.mymusclemap.domain.locale.AppLocale.UI, "%.1f", value)
            },
            snapXAxisToPoints: Boolean = false
        ): ChartLayout {
            val right = width - rightInset
            val bottom = height - bottomInset
            val bounds = ChartScale.yBounds(points.map { it.value }, valueDomain)
            val yMin = bounds.min
            val yMax = bounds.max
            val yRange = (yMax - yMin).coerceAtLeast(0.001)
            val minDate = points.minOf { it.date }
            val maxDate = points.maxOf { it.date }
            val daySpan = ChronoUnit.DAYS.between(minDate, maxDate).coerceAtLeast(1).toFloat()
            val mapped = points.map { point ->
                val x = if (points.size == 1) {
                    (left + right) / 2f
                } else {
                    val day = ChronoUnit.DAYS.between(minDate, point.date).toFloat()
                    left + (day / daySpan) * (right - left)
                }
                val yRatio = ((point.value - yMin) / yRange).toFloat()
                val y = bottom - yRatio * (bottom - top)
                MappedPoint(point, Offset(x, y))
            }
            val yValues = listOf(yMax, (yMin + yMax) / 2.0, yMin)
            val yLabels = yValues.map { value ->
                val yRatio = ((value - yMin) / yRange).toFloat()
                AxisLabel(
                    text = yAxisLabel(value),
                    y = bottom - yRatio * (bottom - top)
                )
            }
            val xDates = if (snapXAxisToPoints) {
                tickDates(points)
            } else {
                xAxisDates(minDate, maxDate, points.size)
            }
            val xLabels = xDates.map { date ->
                val x = if (points.size == 1 || minDate == maxDate) {
                    (left + right) / 2f
                } else {
                    val day = ChronoUnit.DAYS.between(minDate, date).toFloat()
                    left + (day / daySpan) * (right - left)
                }
                AxisLabel(text = xAxisLabel(date), x = x)
            }
            return ChartLayout(
                mapped = mapped,
                xLabels = xLabels,
                yLabels = yLabels,
                plotLeft = left,
                plotRight = right,
                plotBottom = bottom
            )
        }

        private fun tickDates(points: List<SeriesPoint>): List<LocalDate> {
            val dates = points.map { it.date }.sorted()
            return when {
                dates.size <= 2 -> dates
                else -> listOf(dates.first(), dates[dates.size / 2], dates.last()).distinct()
            }
        }

        private fun xAxisDates(minDate: LocalDate, maxDate: LocalDate, count: Int): List<LocalDate> {
            return when {
                count <= 1 || minDate == maxDate -> listOf(minDate)
                count == 2 -> listOf(minDate, maxDate)
                else -> {
                    val days = ChronoUnit.DAYS.between(minDate, maxDate)
                    listOf(
                        minDate,
                        minDate.plusDays(days / 2),
                        maxDate
                    ).distinct()
                }
            }
        }
    }
}
