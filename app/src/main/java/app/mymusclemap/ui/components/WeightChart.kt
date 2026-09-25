package app.mymusclemap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mymusclemap.domain.model.ChartPoint
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.hypot

@Composable
fun WeightChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    contentDescription: String,
    onPointSelected: (ChartPoint?) -> Unit = {},
    subdued: Boolean = false,
    chartHeight: Dp = 240.dp
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = if (subdued) 0.08f else 0.16f)
    val markerColor = MaterialTheme.colorScheme.onSurface
    val markerInner = MaterialTheme.colorScheme.background
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (subdued) 0.55f else 1f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val lineWidth = if (subdued) 2.dp else 3.dp
    val gridWidth = if (subdued) 0.5.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (points.isEmpty()) return@Box
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val layout = ChartLayout.from(
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            points = points,
                            left = 52.dp.toPx(),
                            rightInset = 16.dp.toPx(),
                            top = 12.dp.toPx(),
                            bottomInset = 40.dp.toPx()
                        )
                        val hit = layout.hitTest(tap)
                        selectedDate = hit?.date?.toString()
                        onPointSelected(hit)
                    }
                }
        ) {
            val layout = ChartLayout.from(
                width = size.width,
                height = size.height,
                points = points,
                left = 52.dp.toPx(),
                rightInset = 16.dp.toPx(),
                top = 12.dp.toPx(),
                bottomInset = 40.dp.toPx()
            )
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
                drawCircle(
                    color = if (selected) selectedColor else markerColor,
                    radius = if (selected) 7.dp.toPx() else 5.dp.toPx(),
                    center = point.offset
                )
                drawCircle(
                    color = markerInner,
                    radius = if (selected) 3.dp.toPx() else 2.dp.toPx(),
                    center = point.offset
                )
            }
        }
    }
}

private data class MappedPoint(
    val point: ChartPoint,
    val offset: Offset
)

private data class AxisLabel(
    val text: String,
    val x: Float = 0f,
    val y: Float = 0f
)

private data class ChartLayout(
    val mapped: List<MappedPoint>,
    val xLabels: List<AxisLabel>,
    val yLabels: List<AxisLabel>,
    val plotLeft: Float,
    val plotRight: Float,
    val plotBottom: Float
) {
    fun hitTest(tap: Offset): ChartPoint? {
        val threshold = 28f
        return mapped.minByOrNull { hypot(it.offset.x - tap.x, it.offset.y - tap.y) }
            ?.takeIf { hypot(it.offset.x - tap.x, it.offset.y - tap.y) <= threshold }
            ?.point
    }

    companion object {
        fun from(
            width: Float,
            height: Float,
            points: List<ChartPoint>,
            left: Float,
            rightInset: Float,
            top: Float,
            bottomInset: Float
        ): ChartLayout {
            val right = width - rightInset
            val bottom = height - bottomInset
            val weights = points.map { it.weightKg }
            val minWeight = weights.minOrNull() ?: 0.0
            val maxWeight = weights.maxOrNull() ?: 0.0
            val span = maxOf(maxWeight - minWeight, 0.0)
            val padding = if (span == 0.0) 1.0 else maxOf(span * 0.12, 0.3)
            val yMin = minWeight - padding
            val yMax = maxWeight + padding
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
                val yRatio = ((point.weightKg - yMin) / yRange).toFloat()
                val y = bottom - yRatio * (bottom - top)
                MappedPoint(point, Offset(x, y))
            }
            val yValues = listOf(yMax, (yMin + yMax) / 2.0, yMin)
            val yLabels = yValues.map { value ->
                val yRatio = ((value - yMin) / yRange).toFloat()
                AxisLabel(
                    text = String.format(app.mymusclemap.domain.locale.AppLocale.UI, "%.1f", value),
                    y = bottom - yRatio * (bottom - top)
                )
            }
            val xDates = xAxisDates(minDate, maxDate, points.size)
            val xLabels = xDates.map { date ->
                val x = if (points.size == 1 || minDate == maxDate) {
                    (left + right) / 2f
                } else {
                    val day = ChronoUnit.DAYS.between(minDate, date).toFloat()
                    left + (day / daySpan) * (right - left)
                }
                AxisLabel(text = UiFormatters.compactDate(date), x = x)
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
