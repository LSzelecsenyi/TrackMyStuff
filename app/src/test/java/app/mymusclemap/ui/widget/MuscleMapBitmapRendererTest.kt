package app.mymusclemap.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.MuscleMapAnatomy
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.components.musclemap.MuscleMapCompositing
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
import app.mymusclemap.ui.components.musclemap.figureLayout
import app.mymusclemap.ui.components.musclemap.parseMuscleRegions
import app.mymusclemap.ui.components.musclemap.pathContains
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
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun requestedSizeIsKeptWhenUnderTheCap() {
        val size = MuscleMapBitmapRenderer.pixelSize(90, 200)
        assertEquals(90, size.first)
        assertEquals(200, size.second)
        val bitmaps = MuscleMapBitmapRenderer.render(context, emptyHeatmap(), 90, 200, lightChrome())
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
        val bitmaps = MuscleMapBitmapRenderer.render(context, emptyHeatmap(), 120, 120, lightChrome())
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
        val artwork = MuscleMapArtworkSelector.current()
        val anatomy = MuscleMapAnatomy.load(context)
        val parsed = parseMuscleRegions().filter {
            it.region.view == app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView.FRONT
        }
        val bitmap = Bitmap.createBitmap(
            artwork.viewWidth.roundToInt(),
            artwork.viewHeight.roundToInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        app.mymusclemap.ui.components.musclemap.MuscleMapFigurePainter.draw(
            canvas = canvas,
            anatomy = anatomy.front,
            lighting = anatomy.frontLighting,
            regions = app.mymusclemap.ui.components.musclemap.paintOrderedRegions(parsed),
            fills = MuscleMapColors.heatmapFills(state),
            unmappedFill = lightChrome().unmappedFill,
            outline = lightChrome().outline,
            mappedOutline = lightChrome().mappedOutline,
            selectedOutline = lightChrome().mappedOutline,
            selected = null,
            viewWidth = artwork.viewWidth,
            viewHeight = artwork.viewHeight
        )
        val chestPoint = interior("pecs")
        val bicepsPoint = interior("biceps")
        val chest = bitmap.getPixel(chestPoint.x.roundToInt(), chestPoint.y.roundToInt())
        val biceps = bitmap.getPixel(bicepsPoint.x.roundToInt(), bicepsPoint.y.roundToInt())
        val anatomyFront = MuscleMapAnatomy.load(context).front
        val expectedChest = expectedTint(anatomyFront, MuscleMapColors.Today.toArgb(), chestPoint)
        val expectedBiceps = expectedTint(anatomyFront, MuscleMapColors.NeverTrained.toArgb(), bicepsPoint)
        assertTrue(
            "chest=${Color.red(chest)},${Color.green(chest)},${Color.blue(chest)} " +
                "expected=${Color.red(expectedChest)},${Color.green(expectedChest)},${Color.blue(expectedChest)}",
            kotlin.math.abs(Color.green(chest) - Color.green(expectedChest)) <= 2
        )
        assertTrue(
            "biceps=${Color.red(biceps)},${Color.green(biceps)},${Color.blue(biceps)} " +
                "expected=${Color.red(expectedBiceps)},${Color.green(expectedBiceps)},${Color.blue(expectedBiceps)}",
            kotlin.math.abs(Color.red(biceps) - Color.red(expectedBiceps)) <= 2
        )
        assertTrue(chest != biceps)
    }

    @Test
    fun mergedQuadricepsAndCalvesKeepTheRecencyTintOnBothLegs() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today,
                    MuscleGroup.QUADRICEPS,
                    listOf(MuscleGroup.CALVES),
                    completedSetCount = 1
                )
            ),
            today
        )
        val fills = MuscleMapColors.heatmapFills(state)
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.QUADRICEPS))
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.CALVES))
        val anatomy = MuscleMapAnatomy.load(context)
        val front = paintView(
            app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView.FRONT,
            anatomy.front,
            anatomy.frontLighting,
            fills
        )
        val back = paintView(
            app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView.BACK,
            anatomy.back,
            anatomy.backLighting,
            fills
        )
        listOf(
            "tigh_extensors_front" to front,
            "calves_front" to front
        ).forEach { (id, bitmap) ->
            assertBothLegsMatch(bitmap, anatomy.front, id, MuscleMapColors.Today.toArgb())
        }
        listOf(
            "tigh_extensors_back" to back,
            "calves_back" to back
        ).forEach { (id, bitmap) ->
            val point = interior(id)
            val pixel = bitmap.getPixel(point.x.roundToInt(), point.y.roundToInt())
            val expected = expectedTint(anatomy.back, MuscleMapColors.Today.toArgb(), point)
            assertClose(id, pixel, expected)
        }
    }

    @Test
    fun headKeepsTheAnatomicalArtworkAndOutlineChangesThePixels() {
        val light = lightChrome()
        val dark = HeatmapWidgetChromeFactory.forSystemDark(isDark = true).bitmap
        val state = emptyHeatmap()
        val lightFront = MuscleMapBitmapRenderer.render(context, state, 140, 372, light).front
        val darkFront = MuscleMapBitmapRenderer.render(context, state, 140, 372, dark).front
        val face = facePixel(lightFront.width, lightFront.height)
        assertTrue(Color.alpha(lightFront.getPixel(face.first, face.second)) > 200)
        assertEquals(lightFront.getPixel(face.first, face.second), darkFront.getPixel(face.first, face.second))
        assertNotEquals(light.unmappedFill.toArgb(), dark.unmappedFill.toArgb())

        val red = light.copy(mappedOutline = androidx.compose.ui.graphics.Color.Red)
        val blue = light.copy(mappedOutline = androidx.compose.ui.graphics.Color.Blue)
        val redFront = MuscleMapBitmapRenderer.render(context, state, 140, 372, red).front
        val blueFront = MuscleMapBitmapRenderer.render(context, state, 140, 372, blue).front
        assertFalseSame(redFront, blueFront)
    }

    @Test
    fun emptyBodyKeepsAnEdgeDarkerAndLighterThanTheUntrainedFill() {
        val bitmap = MuscleMapBitmapRenderer.render(context, emptyHeatmap(), 140, 372, lightChrome()).front
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
        val innerWidth = 180f - layout.padding.value * 2
        val widthUsed = layout.pairWidth.value / innerWidth
        val heightUsed = layout.figureHeight.value / innerHeight
        assertTrue("width=$widthUsed height=$heightUsed", widthUsed >= 0.95f || heightUsed >= 0.85f)
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
        val layout = figureLayout(width.toFloat(), height.toFloat())
        val x = (layout.offsetX + 266f * layout.scale).roundToInt()
        val y = (layout.offsetY + 80f * layout.scale).roundToInt()
        return x to y
    }

    private fun paintView(
        view: app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView,
        anatomy: Bitmap,
        lighting: Bitmap,
        fills: Map<MuscleGroup, androidx.compose.ui.graphics.Color>
    ): Bitmap {
        val artwork = MuscleMapArtworkSelector.current()
        val bitmap = Bitmap.createBitmap(
            artwork.viewWidth.roundToInt(),
            artwork.viewHeight.roundToInt(),
            Bitmap.Config.ARGB_8888
        )
        app.mymusclemap.ui.components.musclemap.MuscleMapFigurePainter.draw(
            canvas = android.graphics.Canvas(bitmap),
            anatomy = anatomy,
            lighting = lighting,
            regions = app.mymusclemap.ui.components.musclemap.paintOrderedRegions(
                parseMuscleRegions().filter { it.region.view == view }
            ),
            fills = fills,
            unmappedFill = lightChrome().unmappedFill,
            outline = lightChrome().outline,
            mappedOutline = lightChrome().mappedOutline,
            selectedOutline = lightChrome().mappedOutline,
            selected = null,
            viewWidth = artwork.viewWidth,
            viewHeight = artwork.viewHeight
        )
        return bitmap
    }

    private fun assertBothLegsMatch(bitmap: Bitmap, anatomy: Bitmap, pathId: String, color: Int) {
        val region = parseMuscleRegions().first { it.region.id == pathId }
        val mid = region.bounds.center.x
        listOf("left" to { point: Offset -> point.x < mid }, "right" to { point: Offset -> point.x > mid })
            .forEach { (side, accept) ->
                val point = interior(pathId, accept)
                val pixel = bitmap.getPixel(point.x.roundToInt(), point.y.roundToInt())
                assertClose("$pathId $side", pixel, expectedTint(anatomy, color, point))
            }
    }

    private fun assertClose(label: String, pixel: Int, expected: Int) {
        assertTrue(
            "$label pixel=${Color.red(pixel)},${Color.green(pixel)},${Color.blue(pixel)} " +
                "expected=${Color.red(expected)},${Color.green(expected)},${Color.blue(expected)}",
            kotlin.math.abs(Color.red(pixel) - Color.red(expected)) <= 2 &&
                kotlin.math.abs(Color.green(pixel) - Color.green(expected)) <= 2 &&
                kotlin.math.abs(Color.blue(pixel) - Color.blue(expected)) <= 2
        )
    }

    private fun expectedTint(anatomy: Bitmap, color: Int, point: Offset): Int {
        val x = point.x.roundToInt().coerceIn(0, anatomy.width - 1)
        val y = point.y.roundToInt().coerceIn(0, anatomy.height - 1)
        val source = anatomy.getPixel(x, y)
        val factor = MuscleMapCompositing.shadeFactor(Color.red(source), Color.green(source), Color.blue(source))
        return Color.argb(
            255,
            (Color.red(color) * factor).roundToInt(),
            (Color.green(color) * factor).roundToInt(),
            (Color.blue(color) * factor).roundToInt()
        )
    }

    private fun interior(pathId: String, accept: (Offset) -> Boolean = { true }): Offset {
        val parsed = parseMuscleRegions().first { it.region.id == pathId }
        val steps = 28
        val margin = 16f
        val probes = listOf(
            Offset(margin, 0f),
            Offset(-margin, 0f),
            Offset(0f, margin),
            Offset(0f, -margin)
        )
        var best: Offset? = null
        var bestScore = -1
        for (row in 1 until steps) {
            for (col in 1 until steps) {
                val point = Offset(
                    parsed.bounds.left + parsed.bounds.width * col / steps,
                    parsed.bounds.top + parsed.bounds.height * row / steps
                )
                if (!pathContains(parsed.path, point) || !accept(point)) continue
                val score = probes.count { probe -> pathContains(parsed.path, point + probe) }
                if (score > bestScore) {
                    bestScore = score
                    best = point
                }
            }
        }
        return best ?: parsed.bounds.center
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
        val artwork = MuscleMapArtworkSelector.current()
        val expected = artwork.viewHeight / artwork.viewWidth
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
