package hu.laca.weighttracker.ui.components.musclemap

import hu.laca.weighttracker.ui.components.musclemap.artwork.BodyMusclesArtwork
import hu.laca.weighttracker.ui.theme.AppDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import androidx.compose.ui.unit.dp

@RunWith(JUnit4::class)
class MuscleMapPairLayoutTest {
    @Test
    fun onePlusInnerCardWidthKeepsEqualSideBySideFigures() {
        val screen = 360.dp
        val available = screen - AppDimens.screenPadding * 2 - AppDimens.heroPadding * 2
        assertEquals(280.dp, available)
        val layout = muscleMapPairLayout(available)
        assertTrue(layout.placesSideBySide)
        assertEquals(2, layout.figureCount)
        assertEquals(134.dp, layout.figureWidth)
        assertEquals(134f * BodyMusclesArtwork.VIEW_HEIGHT / BodyMusclesArtwork.VIEW_WIDTH, layout.figureHeight.value, 0.01f)
        assertEquals(available.value, layout.figureWidth.value * 2 + layout.gap.value, 0.01f)
        assertTrue(layout.figureWidth > 0.dp)
        assertTrue(layout.figureHeight > 0.dp)
    }

    @Test
    fun emulatorPhoneWidthAlsoStaysSideBySide() {
        val available = 411.dp - AppDimens.screenPadding * 2 - AppDimens.heroPadding * 2
        val layout = muscleMapPairLayout(available)
        assertTrue(layout.placesSideBySide)
        assertEquals(layout.figureWidth, muscleMapPairLayout(available).figureWidth)
        assertTrue(layout.figureWidth > 0.dp)
        assertEquals(available.value, layout.figureWidth.value * 2 + layout.gap.value, 0.01f)
    }

    @Test
    fun phoneWidthsNeverUseAVerticalStack() {
        listOf(240.dp, 280.dp, 320.dp, 360.dp, 411.dp).forEach { width ->
            val layout = muscleMapPairLayout(width)
            assertTrue(width.toString(), layout.placesSideBySide)
            assertEquals(2, layout.figureCount)
            assertEquals(layout.figureWidth.value, ((width - layout.gap) / 2f).value, 0.01f)
            assertEquals(
                layout.figureWidth.value * BodyMusclesArtwork.VIEW_HEIGHT / BodyMusclesArtwork.VIEW_WIDTH,
                layout.figureHeight.value,
                0.01f
            )
            assertTrue(layout.figureWidth * 2 + layout.gap <= width)
        }
    }

    @Test
    fun fontScaleDoesNotChangePairGeometry() {
        val layout = muscleMapPairLayout(280.dp)
        assertEquals(134.dp, layout.figureWidth)
        assertEquals(2, layout.figureCount)
        assertTrue(layout.placesSideBySide)
    }

    @Test
    fun allocatedFigureFullyContainsArtwork() {
        val layout = muscleMapPairLayout(280.dp)
        val figure = figureLayout(layout.figureWidth.value, layout.figureHeight.value)
        val drawnWidth = BodyMusclesArtwork.VIEW_WIDTH * figure.scale
        val drawnHeight = BodyMusclesArtwork.VIEW_HEIGHT * figure.scale
        assertEquals(layout.figureWidth.value, drawnWidth, 0.01f)
        assertEquals(layout.figureHeight.value, drawnHeight, 0.01f)
        assertEquals(0f, figure.offsetX, 0.01f)
        assertEquals(0f, figure.offsetY, 0.01f)
        assertTrue(drawnWidth <= layout.figureWidth.value + 0.01f)
        assertTrue(drawnHeight <= layout.figureHeight.value + 0.01f)
    }
}
