package app.mymusclemap.ui.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesArtwork
import app.mymusclemap.ui.components.musclemap.figureLayout
import app.mymusclemap.ui.components.musclemap.parseMuscleRegions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MuscleMapBitmapRendererTest {
    private val today = LocalDate.parse("2026-10-02")

    @Test
    fun requestedSizeIsKeptWhenUnderTheCap() {
        val size = MuscleMapBitmapRenderer.pixelSize(90, 200)
        assertEquals(90, size.first)
        assertEquals(200, size.second)
        val bitmaps = MuscleMapBitmapRenderer.render(emptyHeatmap(), 90, 200, lightChrome())
        assertEquals(90, bitmaps.front.width)
        assertEquals(200, bitmaps.front.height)
        assertEquals(90, bitmaps.back.width)
        assertEquals(200, bitmaps.back.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmaps.front.config)
        assertEquals(Bitmap.Config.ARGB_8888, bitmaps.back.config)
    }

    @Test
    fun wideFiguresAreCappedWithoutBreakingTheAspectRatio() {
        val size = MuscleMapBitmapRenderer.pixelSize(800, 2_126)
        assertTrue(size.first <= MuscleMapBitmapRenderer.MAX_FIGURE_WIDTH_PX)
        assertTrue(size.first * size.second * 4L <= MuscleMapBitmapRenderer.MAX_FIGURE_BYTES)
        val expected = 2_126f / 800f
        val actual = size.second.toFloat() / size.first
        assertTrue(abs(actual - expected) < 0.05f)
    }

    @Test
    fun cornersStayTransparentAndBothViewsDrawInk() {
        val bitmaps = MuscleMapBitmapRenderer.render(emptyHeatmap(), 120, 120, lightChrome())
        assertEquals(0, Color.alpha(bitmaps.front.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmaps.back.getPixel(0, 0)))
        assertTrue(hasOpaquePixel(bitmaps.front))
        assertTrue(hasOpaquePixel(bitmaps.back))
    }

    @Test
    fun trainedAndUntrainedMusclesUseTheSharedRecencyColors() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today,
                    MuscleGroup.CHEST,
                    emptyList(),
                    completedSetCount = 1
                )
            ),
            today
        )
        val bitmaps = MuscleMapBitmapRenderer.render(state, 140, 372, lightChrome())
        assertTrue(containsColor(bitmaps.front, MuscleMapColors.Today.toArgb()))
        assertTrue(containsColor(bitmaps.front, MuscleMapColors.NeverTrained.toArgb()))
        assertTrue(containsColor(bitmaps.back, MuscleMapColors.NeverTrained.toArgb()))
    }

    @Test
    fun headFillFollowsTheSystemChromeAndOutlineChangesThePixels() {
        val light = lightChrome()
        val dark = HeatmapWidgetChromeFactory.forSystemDark(isDark = true).bitmap
        val state = emptyHeatmap()
        val lightFront = MuscleMapBitmapRenderer.render(state, 140, 372, light).front
        val darkFront = MuscleMapBitmapRenderer.render(state, 140, 372, dark).front
        val face = facePixel(lightFront.width, lightFront.height)
        assertEquals(light.unmappedFill.toArgb(), lightFront.getPixel(face.first, face.second))
        assertEquals(dark.unmappedFill.toArgb(), darkFront.getPixel(face.first, face.second))
        assertNotEquals(light.unmappedFill.toArgb(), dark.unmappedFill.toArgb())

        val red = light.copy(mappedOutline = androidx.compose.ui.graphics.Color.Red)
        val blue = light.copy(mappedOutline = androidx.compose.ui.graphics.Color.Blue)
        val redFront = MuscleMapBitmapRenderer.render(state, 140, 372, red).front
        val blueFront = MuscleMapBitmapRenderer.render(state, 140, 372, blue).front
        assertFalseSame(redFront, blueFront)
    }

    @Test
    fun emptyBodyKeepsAnEdgeDarkerAndLighterThanTheUntrainedFill() {
        val bitmap = MuscleMapBitmapRenderer.render(emptyHeatmap(), 140, 372, lightChrome()).front
        val untrained = luminance(MuscleMapColors.NeverTrained.toArgb())
        val ink = pixels(bitmap).filter { Color.alpha(it) > 40 }
        assertTrue(ink.any { Color.alpha(it) > 180 && luminance(it) < untrained - 25 })
        assertTrue(ink.any { Color.alpha(it) > 80 && luminance(it) > untrained + 25 })
    }

    @Test
    fun twoByThreePairFitsWithoutOverlapAndKeepsAspect() {
        assertPairFits(heatmapWidgetLayout(110.dp(), 180.dp()), 110.dp(), 180.dp())
    }

    @Test
    fun threeByFourUsesMostOfTheHeight() {
        val layout = heatmapWidgetLayout(180.dp(), 250.dp())
        assertPairFits(layout, 180.dp(), 250.dp())
        val innerHeight = 250f - layout.padding.value * 2
        assertTrue(layout.figureHeight.value / innerHeight >= 0.85f)
    }

    @Test
    fun largerPortraitSlotGrowsTheFiguresWithoutDistortion() {
        val compact = heatmapWidgetLayout(180.dp(), 250.dp())
        val larger = heatmapWidgetLayout(320.dp(), 520.dp())
        assertPairFits(larger, 320.dp(), 520.dp())
        assertTrue(larger.figureHeight.value > compact.figureHeight.value)
        assertTrue(larger.figureWidth.value > compact.figureWidth.value)
    }

    private fun facePixel(width: Int, height: Int): Pair<Int, Int> {
        val face = parseMuscleRegions().first { it.region.id == "face" }
        val layout = figureLayout(width.toFloat(), height.toFloat())
        val x = (layout.offsetX + face.bounds.center.x * layout.scale).roundToInt()
        val y = (layout.offsetY + face.bounds.center.y * layout.scale).roundToInt()
        return x to y
    }

    private fun emptyHeatmap(): MuscleHeatmapState {
        return MuscleHeatmapAssembler.assemble(emptyList(), today)
    }

    private fun lightChrome(): MuscleMapBitmapChrome {
        return HeatmapWidgetChromeFactory.forSystemDark(isDark = false).bitmap
    }

    private fun hasOpaquePixel(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { Color.alpha(it) > 200 }
    }

    private fun containsColor(bitmap: Bitmap, color: Int): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { it == color }
    }

    private fun assertFalseSame(left: Bitmap, right: Bitmap) {
        assertEquals(left.width, right.width)
        assertEquals(left.height, right.height)
        val leftPixels = IntArray(left.width * left.height)
        val rightPixels = IntArray(right.width * right.height)
        left.getPixels(leftPixels, 0, left.width, 0, 0, left.width, left.height)
        right.getPixels(rightPixels, 0, right.width, 0, 0, right.width, right.height)
        assertTrue(leftPixels.indices.any { leftPixels[it] != rightPixels[it] })
    }

    private fun assertPairFits(
        layout: HeatmapWidgetLayout,
        width: androidx.compose.ui.unit.Dp,
        height: androidx.compose.ui.unit.Dp
    ) {
        val aspect = layout.figureHeight.value / layout.figureWidth.value
        val expected = BodyMusclesArtwork.VIEW_HEIGHT / BodyMusclesArtwork.VIEW_WIDTH
        assertTrue(abs(aspect - expected) < 0.01f)
        assertEquals(4f, layout.gap.value)
        assertTrue(layout.gap < layout.figureWidth)
        assertTrue(layout.pairWidth.value + layout.padding.value * 2 <= width.value + 0.5f)
        assertTrue(layout.figureHeight.value + layout.padding.value * 2 <= height.value + 0.5f)
        val horizontalSlack = width.value - layout.padding.value * 2 - layout.pairWidth.value
        val verticalSlack = height.value - layout.padding.value * 2 - layout.figureHeight.value
        assertTrue(horizontalSlack >= -0.5f)
        assertTrue(verticalSlack >= -0.5f)
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    private fun luminance(color: Int): Double {
        return 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
    }

    private fun Int.dp(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(toFloat())
}
