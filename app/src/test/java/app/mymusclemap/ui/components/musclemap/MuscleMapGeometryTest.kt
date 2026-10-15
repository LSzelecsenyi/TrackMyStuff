package app.mymusclemap.ui.components.musclemap

import androidx.compose.ui.geometry.Offset
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MuscleMapGeometryTest {
    @Test
    fun allGeneratedPathsParseSuccessfully() {
        val artwork = MuscleMapArtworkSelector.current()
        val parsed = parseMuscleRegions()
        assertEquals(artwork.regions.size, parsed.size)
        parsed.forEach { region ->
            assertTrue(region.region.id, region.bounds.width > 0f || region.bounds.height > 0f)
        }
    }

    @Test
    fun frontAndBackStayInsideTranslatedViewBox() {
        val artwork = MuscleMapArtworkSelector.current()
        val parsed = parseMuscleRegions()
        val epsilon = 2f
        parsed.forEach { region ->
            assertTrue(region.region.id, region.bounds.left >= -epsilon)
            assertTrue(region.region.id, region.bounds.right <= artwork.viewWidth + epsilon)
            assertTrue(region.region.id, region.bounds.top >= -epsilon)
            assertTrue(region.region.id, region.bounds.bottom <= artwork.viewHeight + epsilon)
        }
        assertTrue(parsed.any { it.region.view == MuscleMapView.FRONT })
        assertTrue(parsed.any { it.region.view == MuscleMapView.BACK })
    }

    @Test
    fun figureLayoutCentersAndPreservesAspect() {
        val artwork = MuscleMapArtworkSelector.current()
        val wide = figureLayout(artwork.viewWidth * 2f, artwork.viewHeight)
        assertEquals(1f, wide.scale, 0.001f)
        assertEquals(artwork.viewWidth / 2f, wide.offsetX, 0.001f)
        assertEquals(0f, wide.offsetY, 0.001f)
        val tall = figureLayout(artwork.viewWidth, artwork.viewHeight * 2f)
        assertEquals(1f, tall.scale, 0.001f)
        assertEquals(0f, tall.offsetX, 0.001f)
        assertEquals(artwork.viewHeight / 2f, tall.offsetY, 0.001f)
    }

    @Test
    fun deltoidPathsKeepTheirViewsAndAreNotZeroArea() {
        val parsed = parseMuscleRegions()
        parsed.filter { it.region.muscleGroup == MuscleGroup.FRONT_DELTOID }.forEach { region ->
            assertEquals(region.region.id, MuscleMapView.FRONT, region.region.view)
            assertTrue(region.region.id, regionArea(region) > 1f)
        }
        assertTrue(parsed.any { it.region.muscleGroup == MuscleGroup.SIDE_DELTOID && it.region.view == MuscleMapView.FRONT })
        assertTrue(parsed.any { it.region.muscleGroup == MuscleGroup.SIDE_DELTOID && it.region.view == MuscleMapView.BACK })
        parsed.filter { it.region.muscleGroup == MuscleGroup.REAR_DELTOID }.forEach { region ->
            assertEquals(region.region.id, MuscleMapView.BACK, region.region.view)
            assertTrue(region.region.id, regionArea(region) > 1f)
        }
    }

    @Test
    fun paintOrderDrawsDeltoidsAfterTheLargerOverlappingMuscles() {
        val front = paintOrderedRegions(
            parseMuscleRegions().filter { it.region.view == MuscleMapView.FRONT }
        )
        val back = paintOrderedRegions(
            parseMuscleRegions().filter { it.region.view == MuscleMapView.BACK }
        )
        fun indexOf(regions: List<ParsedMuscleRegion>, id: String) = regions.indexOfFirst { it.region.id == id }
        assertTrue(indexOf(front, "front_delts") > indexOf(front, "pecs"))
        assertTrue(indexOf(front, "front_delts") > indexOf(front, "side_delts_front"))
        assertTrue(indexOf(back, "back_delts_back") > indexOf(back, "lats"))
        assertTrue(indexOf(back, "back_delts_back") > indexOf(back, "upper_back"))
    }

    @Test
    fun chestFrontDeltoidAndTricepsTodayAllHaveVisibleActivePaths() {
        val today = LocalDate.parse("2026-09-15")
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(SessionStatus.COMPLETED, today, MuscleGroup.CHEST, emptyList(), 1),
                MuscleTrainingExercise(SessionStatus.COMPLETED, today, MuscleGroup.FRONT_DELTOID, emptyList(), 1),
                MuscleTrainingExercise(SessionStatus.COMPLETED, today, MuscleGroup.TRICEPS, emptyList(), 1)
            ),
            today
        )
        assertEquals(MuscleRecencyBand.TODAY, state.entry(MuscleGroup.CHEST).band)
        assertEquals(MuscleRecencyBand.TODAY, state.entry(MuscleGroup.FRONT_DELTOID).band)
        assertEquals(MuscleRecencyBand.TODAY, state.entry(MuscleGroup.TRICEPS).band)
        val parsed = parseMuscleRegions()
        val activeIds = overlayRegionIds(
            parsed,
            setOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTOID, MuscleGroup.TRICEPS)
        )
        assertTrue("pecs" in activeIds)
        assertTrue("front_delts" in activeIds)
        assertTrue("triceps_left_front" in activeIds)
        assertTrue("triceps" in activeIds)
        val fills = MuscleMapColors.heatmapFills(state)
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.CHEST))
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.FRONT_DELTOID))
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.TRICEPS))
    }

    @Test
    fun mappedDeltoidRegionsRemainInteractive() {
        val parsed = parseMuscleRegions()
        val front = parsed.filter { it.region.view == MuscleMapView.FRONT }
        val back = parsed.filter { it.region.view == MuscleMapView.BACK }
        assertEquals(MuscleGroup.FRONT_DELTOID, hitTest(front, interiorPoint(front.first { it.region.id == "front_delts" })))
        assertEquals(MuscleGroup.SIDE_DELTOID, hitTest(front, interiorPoint(front.first { it.region.id == "side_delts_front" })))
        assertEquals(MuscleGroup.REAR_DELTOID, hitTest(back, interiorPoint(back.first { it.region.id == "back_delts_back" })))
        assertNull(hitTest(front, Offset(-10f, -10f)))
        assertNotNull(hitTest(front, interiorPoint(front.first { it.region.id == "pecs" })))
    }

    private fun interiorPoint(parsed: ParsedMuscleRegion): Offset {
        val steps = 12
        for (row in 1 until steps) {
            for (col in 1 until steps) {
                val point = Offset(
                    parsed.bounds.left + parsed.bounds.width * col / steps,
                    parsed.bounds.top + parsed.bounds.height * row / steps
                )
                if (pathContains(parsed.path, point)) {
                    return point
                }
            }
        }
        return parsed.bounds.center
    }
}
