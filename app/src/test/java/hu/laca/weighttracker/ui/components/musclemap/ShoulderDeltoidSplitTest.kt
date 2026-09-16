package hu.laca.weighttracker.ui.components.musclemap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapAssembler
import hu.laca.weighttracker.domain.musclemap.MuscleRecencyBand
import hu.laca.weighttracker.domain.musclemap.MuscleTrainingExercise
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.ui.components.musclemap.artwork.MuscleMapView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ShoulderDeltoidSplitTest {
    private val midline = 15.844f
    private val parsed by lazy { parseMuscleRegions() }
    private val front by lazy { parsed.filter { it.region.view == MuscleMapView.FRONT } }

    private val innerCap = listOf(
        Offset(22.90f, 17.20f),
        Offset(23.40f, 18.20f),
        Offset(21.50f, 16.40f)
    )
    private val outerCap = listOf(
        Offset(26.40f, 18.50f),
        Offset(27.20f, 19.80f),
        Offset(26.80f, 20.20f)
    )

    @Test
    fun innerCapPointsBelongOnlyToFrontDeltoid() {
        (innerCap + innerCap.map { mirror(it) }).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.FRONT_DELTOID), groupsAt(point))
        }
    }

    @Test
    fun outerCapPointsBelongOnlyToSideDeltoid() {
        (outerCap + outerCap.map { mirror(it) }).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.SIDE_DELTOID), groupsAt(point))
        }
    }

    @Test
    fun frontAndSideDeltoidsHaveMeaningfulFilledArea() {
        val counts = filledCounts()
        assertTrue("front=${counts.front}", counts.front >= 80)
        assertTrue("side=${counts.side}", counts.side >= 80)
        assertTrue(
            "front=${counts.front} side=${counts.side}",
            counts.front.toFloat() / counts.side.toFloat() in 0.45f..2.2f
        )
    }

    @Test
    fun frontAndSideDeltoidFillsDoNotOverlap() {
        val counts = filledCounts()
        val filled = counts.front + counts.side
        assertTrue("overlap=${counts.overlap} filled=$filled", counts.overlap <= 16)
        assertTrue("overlap=${counts.overlap} filled=$filled", counts.overlap.toFloat() / filled.toFloat() < 0.03f)
    }

    @Test
    fun leftAndRightShoulderGeometryIsMirrored() {
        val samples = innerCap + outerCap
        samples.forEach { point ->
            assertEquals(point.toString(), groupsAt(point), groupsAt(mirror(point)))
        }
    }

    @Test
    fun chestAndBicepsPointsRemainUnchanged() {
        val chestLeft = Offset(18.80f, 19.70f)
        val bicepsLeft = Offset(26.20f, 30.20f)
        listOf(chestLeft, mirror(chestLeft)).forEach { point ->
            val groups = groupsAt(point)
            assertTrue(point.toString(), MuscleGroup.CHEST in groups)
            assertTrue(point.toString(), MuscleGroup.FRONT_DELTOID !in groups)
            assertTrue(point.toString(), MuscleGroup.SIDE_DELTOID !in groups)
        }
        listOf(bicepsLeft, mirror(bicepsLeft)).forEach { point ->
            val groups = groupsAt(point)
            assertTrue(point.toString(), MuscleGroup.BICEPS in groups)
            assertTrue(point.toString(), MuscleGroup.FRONT_DELTOID !in groups)
            assertTrue(point.toString(), MuscleGroup.SIDE_DELTOID !in groups)
        }
    }

    @Test
    fun frontAndSideCanRenderDifferentSimultaneousColors() {
        val today = LocalDate.parse("2026-09-16")
        val fills = MuscleMapColors.heatmapFills(
            MuscleHeatmapAssembler.assemble(
                listOf(
                    MuscleTrainingExercise(
                        SessionStatus.COMPLETED,
                        today.minusDays(2),
                        MuscleGroup.FRONT_DELTOID,
                        emptyList(),
                        1
                    ),
                    MuscleTrainingExercise(
                        SessionStatus.COMPLETED,
                        today,
                        MuscleGroup.SIDE_DELTOID,
                        emptyList(),
                        1
                    )
                ),
                today
            )
        )
        assertEquals(MuscleRecencyBand.DAYS_1_2, MuscleHeatmapAssembler.bandFor(2))
        assertEquals(MuscleMapColors.Days1To2, fills.getValue(MuscleGroup.FRONT_DELTOID))
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.SIDE_DELTOID))
        innerCap.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.Days1To2, colorAt(point, fills))
        }
        outerCap.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.Today, colorAt(point, fills))
        }
        assertTrue(fills.getValue(MuscleGroup.FRONT_DELTOID) != fills.getValue(MuscleGroup.SIDE_DELTOID))
    }

    @Test
    fun geometryMembershipUsesExactFillNotInflatedHitTest() {
        val ordered = paintOrderedRegions(front)
        fun indexOf(id: String) = ordered.indexOfFirst { it.region.id == id }
        assertTrue(indexOf("shoulder-front-left") > indexOf("shoulder-side-left"))
        assertTrue(indexOf("shoulder-front-right") > indexOf("shoulder-side-right"))
        val inner = Offset(22.90f, 17.20f)
        val outer = Offset(26.40f, 18.50f)
        assertEquals(setOf(MuscleGroup.FRONT_DELTOID), groupsAt(inner))
        assertEquals(setOf(MuscleGroup.SIDE_DELTOID), groupsAt(outer))
        assertEquals(MuscleGroup.FRONT_DELTOID, hitTest(front, inner, inflate = 0f))
        assertEquals(MuscleGroup.SIDE_DELTOID, hitTest(front, outer, inflate = 0f))
    }

    private fun groupsAt(point: Offset): Set<MuscleGroup> {
        return front.mapNotNull { region ->
            val group = region.region.muscleGroup ?: return@mapNotNull null
            if (pathContains(region.path, point)) group else null
        }.toSet()
    }

    private fun colorAt(point: Offset, fills: Map<MuscleGroup, Color>): Color? {
        val groups = groupsAt(point)
        assertEquals(point.toString(), 1, groups.size)
        return fills.getValue(groups.single())
    }

    private fun mirror(point: Offset): Offset {
        return Offset(2f * midline - point.x, point.y)
    }

    private data class FilledCounts(val front: Int, val side: Int, val overlap: Int)

    private fun filledCounts(): FilledCounts {
        val frontPaths = front.filter { it.region.muscleGroup == MuscleGroup.FRONT_DELTOID }
        val sidePaths = front.filter { it.region.muscleGroup == MuscleGroup.SIDE_DELTOID }
        var frontCount = 0
        var sideCount = 0
        var overlap = 0
        var x = 4.0f
        while (x <= 28.0f) {
            var y = 13.0f
            while (y <= 24.0f) {
                val point = Offset(x, y)
                val inFront = frontPaths.any { pathContains(it.path, point) }
                val inSide = sidePaths.any { pathContains(it.path, point) }
                when {
                    inFront && inSide -> overlap++
                    inFront -> frontCount++
                    inSide -> sideCount++
                }
                y += 0.25f
            }
            x += 0.25f
        }
        return FilledCounts(frontCount, sideCount, overlap)
    }
}
