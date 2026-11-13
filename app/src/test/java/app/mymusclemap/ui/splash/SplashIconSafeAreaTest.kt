package app.mymusclemap.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot

/**
 * Android's splash icon is masked to a circle whose diameter is two thirds of the
 * drawable. androidx.core.splashscreen uses that ratio for both icon slots:
 * 160 dp inside the 240 dp icon when a background color is set, and 192 dp inside
 * the 288 dp icon when it is not. The same fraction of this bitmap is visible on
 * API 26–30 and on the Android 12+ platform splash.
 */
class SplashIconSafeAreaTest {
    @Test
    fun splashIconFitsTheMaskAndStaysCentered() {
        val file = resource("drawable-xxxhdpi/splash_icon.png")
        val resDirectory = requireNotNull(file.parentFile?.parentFile)
        val duplicates = resDirectory
            .walkTopDown()
            .filter { it.isFile && it.name == "splash_icon.png" && it.absolutePath != file.absolutePath }
            .toList()
        assertTrue("Unexpected extra splash icons: $duplicates", duplicates.isEmpty())

        val image = ImageIO.read(file)
        assertEquals(960, image.width)
        assertEquals(960, image.height)
        val safeRadius = image.width * MASK_FACTOR / 2.0
        assertEquals(320.0, safeRadius, 0.001)

        var opaque = 0
        var maxRadius = 0.0
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                if (alpha == 0) continue
                opaque += 1
                val radius = hypot(x - image.width / 2.0 + 0.5, y - image.height / 2.0 + 0.5)
                if (radius > maxRadius) maxRadius = radius
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }

        assertTrue("Splash icon has no visible pixels", opaque > 0)
        assertTrue(
            "Opaque pixels reach ${"%.1f".format(maxRadius)} px; the mask radius is $safeRadius",
            maxRadius <= safeRadius
        )
        assertTrue(
            "Splash icon is smaller than the mask allows: radius $maxRadius",
            maxRadius >= safeRadius * 0.90
        )
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        assertEquals(image.width / 2.0 - 0.5, centerX, 1.0)
        assertEquals(image.height / 2.0 - 0.5, centerY, 1.0)
        listOf(
            0 to 0,
            image.width - 1 to 0,
            0 to image.height - 1,
            image.width - 1 to image.height - 1
        ).forEach { (x, y) ->
            assertEquals("Corner ($x,$y) must stay transparent", 0, image.getRGB(x, y) ushr 24)
        }
    }

    @Test
    fun splashThemesKeepTheNavyBackgroundAndDoNotUseTheLauncherIcon() {
        val color = resource("values/colors.xml").readText()
        assertTrue(color.contains("""<color name="splash_background">#0C121C</color>"""))
        listOf(
            "values/themes.xml",
            "values-v27/themes.xml",
            "values-v31/themes.xml"
        ).forEach { path ->
            val theme = resource(path).readText()
            assertTrue(path, theme.contains("""<item name="windowSplashScreenBackground">@color/splash_background</item>"""))
            assertTrue(path, theme.contains("""<item name="windowSplashScreenAnimatedIcon">@drawable/splash_icon</item>"""))
            assertTrue(path, theme.contains("""<item name="windowSplashScreenIconBackgroundColor">@color/splash_background</item>"""))
            assertTrue(path, theme.contains("""<item name="postSplashScreenTheme">@style/Theme.WeightTracker</item>"""))
        }
        listOf(
            "mipmap-anydpi/ic_launcher.xml",
            "mipmap-anydpi/ic_launcher_round.xml"
        ).forEach { path ->
            val launcher = resource(path).readText()
            assertTrue(path, !launcher.contains("splash_icon"))
            assertTrue(path, launcher.contains("@drawable/ic_launcher_foreground"))
        }
    }

    private fun resource(relativePath: String): File {
        val candidates = listOf(
            File("src/main/res", relativePath),
            File("app/src/main/res", relativePath)
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Missing resource $relativePath in $candidates")
    }

    private companion object {
        /** Same ratio as androidx.core.splashscreen.SplashScreen MASK_FACTOR. */
        const val MASK_FACTOR = 2.0 / 3.0
    }
}
