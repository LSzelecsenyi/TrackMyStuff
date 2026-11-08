package app.mymusclemap.ui.components

import androidx.compose.ui.geometry.Offset
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartTooltipPlacementTest {
    @Test
    fun tooltipPrefersAboveThePointAndStaysCentered() {
        val placement = placeChartTooltip(
            anchorX = 200f,
            anchorY = 120f,
            tooltipWidth = 40f,
            tooltipHeight = 20f,
            boundsWidth = 400f,
            boundsHeight = 240f,
            clearance = 16f,
            margin = 4f
        )
        assertEquals(180f, placement.left, 0.01f)
        assertEquals(84f, placement.top, 0.01f)
        assertFalse(covers(placement, 40f, 20f, 200f, 120f))
    }

    @Test
    fun tooltipFlipsBelowWhenThePointIsNearTheTop() {
        val placement = placeChartTooltip(
            anchorX = 200f,
            anchorY = 10f,
            tooltipWidth = 40f,
            tooltipHeight = 24f,
            boundsWidth = 400f,
            boundsHeight = 200f,
            clearance = 16f,
            margin = 4f
        )
        assertEquals(26f, placement.top, 0.01f)
        assertTrue(placement.top >= 10f + 16f)
        assertFalse(covers(placement, 40f, 24f, 200f, 10f))
    }

    @Test
    fun edgeAnchorsStayInsideTheChartAndClearThePoint() {
        val width = 400f
        val height = 220f
        val tooltipWidth = 96f
        val tooltipHeight = 44f
        val clearance = 18f
        val margin = 4f
        listOf(12f, width - 12f).forEach { x ->
            listOf(20f, height - 24f).forEach { y ->
                val placement = placeChartTooltip(
                    anchorX = x,
                    anchorY = y,
                    tooltipWidth = tooltipWidth,
                    tooltipHeight = tooltipHeight,
                    boundsWidth = width,
                    boundsHeight = height,
                    clearance = clearance,
                    margin = margin
                )
                assertTrue(placement.left >= margin - 0.01f)
                assertTrue(placement.top >= margin - 0.01f)
                assertTrue(placement.left + tooltipWidth <= width - margin + 0.01f)
                assertTrue(placement.top + tooltipHeight <= height - margin + 0.01f)
                assertFalse(covers(placement, tooltipWidth, tooltipHeight, x, y))
            }
        }
    }

    @Test
    fun hitTestReachesFirstMiddleAndLastPointsAndMissesEmptySpace() {
        val first = SeriesPoint(LocalDate.of(2026, 11, 1), 96.2)
        val middle = SeriesPoint(LocalDate.of(2026, 11, 5), 88.5)
        val last = SeriesPoint(LocalDate.of(2026, 11, 8), 80.0)
        val layout = layoutOf(listOf(first, middle, last))
        assertEquals(first, layout.hitTest(layout.mapped.first().offset))
        assertEquals(middle, layout.hitTest(layout.mapped[1].offset))
        assertEquals(last, layout.hitTest(layout.mapped.last().offset))
        val firstOffset = layout.mapped.first().offset
        assertEquals(first, layout.hitTest(Offset(firstOffset.x + 20f, firstOffset.y)))
        assertNull(layout.hitTest(Offset(firstOffset.x + 40f, firstOffset.y)))
        assertNull(layout.hitTest(Offset(4f, 100f)))
        val single = layoutOf(listOf(middle))
        assertEquals(middle, single.hitTest(single.mapped.single().offset))
    }

    private fun layoutOf(points: List<SeriesPoint>): ChartLayout {
        return ChartLayout.from(
            width = 360f,
            height = 200f,
            points = points,
            left = 52f,
            rightInset = 16f,
            top = 12f,
            bottomInset = 40f,
            valueDomain = ChartValueDomain.Padded
        )
    }

    private fun covers(
        placement: ChartTooltipPlacement,
        width: Float,
        height: Float,
        x: Float,
        y: Float
    ): Boolean {
        return x >= placement.left &&
            x <= placement.left + width &&
            y >= placement.top &&
            y <= placement.top + height
    }
}
