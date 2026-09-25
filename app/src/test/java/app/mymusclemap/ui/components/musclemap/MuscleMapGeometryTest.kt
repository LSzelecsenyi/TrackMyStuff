package app.mymusclemap.ui.components.musclemap

import androidx.compose.ui.geometry.Offset
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesArtwork
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
        val parsed = parseMuscleRegions()
        assertEquals(BodyMusclesArtwork.regions.size, parsed.size)
        parsed.forEach { region ->
            assertTrue(region.region.id, region.bounds.width > 0f || region.bounds.height > 0f)
        }
    }

    @Test
    fun frontAndBackStayInsideTranslatedViewBox() {
        val parsed = parseMuscleRegions()
        val epsilon = 1.5f
        parsed.filter { it.region.view == MuscleMapView.FRONT }.forEach { region ->
            assertTrue(region.region.id, region.bounds.left >= -epsilon)
            assertTrue(region.region.id, region.bounds.right <= BodyMusclesArtwork.VIEW_WIDTH + epsilon)
            assertTrue(region.region.id, region.bounds.top >= -epsilon)
            assertTrue(region.region.id, region.bounds.bottom <= BodyMusclesArtwork.VIEW_HEIGHT + epsilon)
        }
        parsed.filter { it.region.view == MuscleMapView.BACK }.forEach { region ->
            assertTrue(region.region.id + " left", region.bounds.left >= -epsilon)
            assertTrue(region.region.id + " right", region.bounds.right <= BodyMusclesArtwork.VIEW_WIDTH + epsilon)
            assertTrue(region.region.id, region.bounds.top >= -epsilon)
            assertTrue(region.region.id, region.bounds.bottom <= BodyMusclesArtwork.VIEW_HEIGHT + epsilon)
        }
    }

    @Test
    fun figureLayoutCentersAndPreservesAspect() {
        val layout = figureLayout(350f, 930f)
        assertEquals(10f, layout.scale, 0.001f)
        assertEquals(0f, layout.offsetX, 0.001f)
        assertEquals(0f, layout.offsetY, 0.001f)
        val wide = figureLayout(700f, 930f)
        assertEquals(10f, wide.scale, 0.001f)
        assertEquals(175f, wide.offsetX, 0.001f)
    }

    @Test
    fun deltoidPathsKeepViewAndAreNotZeroArea() {
        val parsed = parseMuscleRegions()
        parsed.filter { it.region.muscleGroup == MuscleGroup.FRONT_DELTOID }.forEach { region ->
            assertEquals(region.region.id, MuscleMapView.FRONT, region.region.view)
            assertTrue(region.region.id, regionArea(region) > 1f)
            assertTrue(region.region.id, region.bounds.left > 5f)
        }
        parsed.filter { it.region.muscleGroup == MuscleGroup.SIDE_DELTOID }.forEach { region ->
            assertEquals(region.region.id, MuscleMapView.FRONT, region.region.view)
            assertTrue(region.region.id, regionArea(region) > 1f)
        }
        parsed.filter { it.region.muscleGroup == MuscleGroup.REAR_DELTOID }.forEach { region ->
            assertEquals(region.region.id, MuscleMapView.BACK, region.region.view)
            assertTrue(region.region.id, regionArea(region) > 1f)
            assertTrue(region.region.id, region.bounds.left > -1f)
            assertTrue(region.region.id, region.bounds.right < 36f)
        }
    }

    @Test
    fun paintOrderDrawsFrontDeltoidAfterChestAndSideDeltoid() {
        val front = paintOrderedRegions(
            parseMuscleRegions().filter { it.region.view == MuscleMapView.FRONT }
        )
        fun indexOf(id: String) = front.indexOfFirst { it.region.id == id }
        assertTrue(indexOf("shoulder-front-left") > indexOf("chest-upper-left"))
        assertTrue(indexOf("shoulder-front-right") > indexOf("chest-upper-right"))
        assertTrue(indexOf("shoulder-front-left") > indexOf("shoulder-side-left"))
        assertTrue(indexOf("shoulder-front-right") > indexOf("shoulder-side-right"))
        assertTrue(front.first().region.muscleGroup == null)
    }

    @Test
    fun paintOrderDrawsRearDeltoidAfterOverlappingBackMuscles() {
        val back = paintOrderedRegions(
            parseMuscleRegions().filter { it.region.view == MuscleMapView.BACK }
        )
        fun indexOf(id: String) = back.indexOfFirst { it.region.id == id }
        assertTrue(indexOf("deltoid-rear-left") > indexOf("lats-upper-left"))
        assertTrue(indexOf("deltoid-rear-right") > indexOf("lats-upper-right"))
        assertTrue(indexOf("deltoid-rear-left") > indexOf("traps-upper-left"))
        assertTrue(back.first().region.muscleGroup == null)
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
        val overlay = setOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTOID, MuscleGroup.TRICEPS)
        val parsed = parseMuscleRegions()
        val activeIds = overlayRegionIds(parsed, overlay)
        assertTrue("shoulder-front-left" in activeIds)
        assertTrue("shoulder-front-right" in activeIds)
        assertTrue("chest-upper-left" in activeIds)
        assertTrue("triceps-long-left" in activeIds)
        overlay.forEach { group ->
            parsed.filter { it.region.muscleGroup == group }.forEach { region ->
                assertTrue(region.region.id, regionArea(region) > 1f)
            }
        }
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
        val frontLeft = front.first { it.region.id == "shoulder-front-left" }
        val sideLeft = front.first { it.region.id == "shoulder-side-left" }
        val rearLeft = back.first { it.region.id == "deltoid-rear-left" }
        assertEquals(MuscleGroup.FRONT_DELTOID, hitTest(front, interiorPoint(frontLeft)))
        assertEquals(MuscleGroup.SIDE_DELTOID, hitTest(front, interiorPoint(sideLeft)))
        assertEquals(MuscleGroup.REAR_DELTOID, hitTest(back, interiorPoint(rearLeft)))
        assertNotNull(hitTest(front, interiorPoint(frontLeft)))
        assertNull(hitTest(front, Offset(-10f, -10f)))
    }

    private fun interiorPoint(parsed: ParsedMuscleRegion): Offset {
        val steps = 8
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
