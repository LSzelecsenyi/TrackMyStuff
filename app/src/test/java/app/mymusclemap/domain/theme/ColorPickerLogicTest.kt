package app.mymusclemap.domain.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPickerLogicTest {
    private val blue = parse("#2457C5")
    private val green = parse("#00FF00")

    @Test
    fun validHexUpdatesHsvState() {
        val opened = ColorPickerLogic.open(ColorScience.WHITE)
        val updated = ColorPickerLogic.applyHex(opened, "#00FF00")
        assertTrue(updated.hexValid)
        assertEquals("#00FF00", updated.hexDraft)
        assertEquals(green, updated.rgb)
        assertEquals(120f, updated.hsv.hue, 0.05f)
        assertEquals(1f, updated.hsv.saturation, 0.001f)
        assertEquals(1f, updated.hsv.value, 0.001f)
    }

    @Test
    fun visualHsvSelectionUpdatesCanonicalHex() {
        val opened = ColorPickerLogic.open(blue)
        val red = ColorPickerLogic.applyHsv(
            opened,
            ColorScience.Hsv(hue = 0f, saturation = 1f, value = 1f)
        )
        assertEquals("#FF0000", red.hexDraft)
        assertEquals(parse("#FF0000"), red.rgb)
        assertEquals(0xFF, ColorScience.alpha(red.rgb))
    }

    @Test
    fun lowercaseHexNormalizesToUppercase() {
        val opened = ColorPickerLogic.open(blue)
        val updated = ColorPickerLogic.applyHex(opened, "#8a2be2")
        assertEquals("#8A2BE2", updated.hexDraft)
        assertTrue(updated.hexValid)
    }

    @Test
    fun missingHashIsAcceptedAndNormalized() {
        val opened = ColorPickerLogic.open(blue)
        val updated = ColorPickerLogic.applyHex(opened, "8a2be2")
        assertEquals("#8A2BE2", updated.hexDraft)
        assertEquals(parse("#8A2BE2"), updated.rgb)
    }

    @Test
    fun malformedAndIncompleteHexPreserveLastValidColor() {
        val opened = ColorPickerLogic.open(blue)
        val valid = ColorPickerLogic.applyHex(opened, "#8A2BE2")
        val incomplete = ColorPickerLogic.applyHex(valid, "#8A2BE")
        val malformed = ColorPickerLogic.applyHex(incomplete, "not-a-color")
        val empty = ColorPickerLogic.applyHex(malformed, "")
        listOf(incomplete, malformed, empty).forEach { state ->
            assertFalse(state.hexValid)
            assertFalse(state.canConfirm)
            assertEquals(parse("#8A2BE2"), state.rgb)
            assertEquals("#8A2BE2", HexColor.format(state.previewRgb))
            assertNull(ColorPickerLogic.confirm(state))
        }
        assertEquals("#8A2BE", incomplete.hexDraft)
        assertEquals("not-a-color", malformed.hexDraft)
        assertEquals("", empty.hexDraft)
    }

    @Test
    fun hueBoundariesAtZeroAndThreeSixtyMatch() {
        val atZero = ColorScience.fromHsv(ColorScience.Hsv(0f, 1f, 1f))
        val atThreeSixty = ColorScience.fromHsv(ColorScience.Hsv(360f, 1f, 1f))
        val atSevenTwenty = ColorScience.fromHsv(ColorScience.Hsv(720f, 1f, 1f))
        assertEquals(atZero, atThreeSixty)
        assertEquals(atZero, atSevenTwenty)
        assertEquals("#FF0000", HexColor.format(atZero))
        assertEquals(0f, ColorPickerLogic.hueFromPointer(0f, 200f), 0.001f)
        assertEquals(360f, ColorPickerLogic.hueFromPointer(200f, 200f), 0.001f)
        assertEquals(0f, ColorPickerLogic.hueFromPointer(-20f, 200f), 0.001f)
        assertEquals(360f, ColorPickerLogic.hueFromPointer(260f, 200f), 0.001f)
        val opened = ColorPickerLogic.open(blue)
        val wrapped = ColorPickerLogic.applyHue(opened, 360f)
        val zero = ColorPickerLogic.applyHue(opened, 0f)
        assertEquals(zero.rgb, wrapped.rgb)
        assertEquals("#FF0000", HexColor.format(wrapped.rgb).let {
            HexColor.format(ColorScience.fromHsv(ColorScience.Hsv(wrapped.hsv.hue, 1f, 1f)))
        })
    }

    @Test
    fun saturationValueCoordinatesAreClamped() {
        assertEquals(0f to 1f, ColorPickerLogic.svFromPointer(-40f, -12f, 100f, 80f))
        assertEquals(1f to 0f, ColorPickerLogic.svFromPointer(400f, 300f, 100f, 80f))
        assertEquals(0f to 1f, ColorPickerLogic.svFromPointer(0f, 0f, 120f, 90f))
        assertEquals(1f to 0f, ColorPickerLogic.svFromPointer(120f, 90f, 120f, 90f))
        assertEquals(0.25f to 0.5f, ColorPickerLogic.svFromPointer(30f, 45f, 120f, 90f))
        val opened = ColorPickerLogic.open(blue)
        val topLeft = ColorPickerLogic.applySaturationValue(opened, -1f, 2f)
        assertEquals(0f, topLeft.hsv.saturation, 0.001f)
        assertEquals(1f, topLeft.hsv.value, 0.001f)
        val bottomRight = ColorPickerLogic.applySaturationValue(opened, 4f, -3f)
        assertEquals(1f, bottomRight.hsv.saturation, 0.001f)
        assertEquals(0f, bottomRight.hsv.value, 0.001f)
        assertEquals("#000000", bottomRight.hexDraft)
    }

    @Test
    fun canonicalColorsRoundTripThroughHsvAndHex() {
        val colors = listOf(
            "#FF0000",
            "#00FF00",
            "#0000FF",
            "#FFFFFF",
            "#000000",
            ThemeSeeds.DefaultLight.canonical().background,
            ThemeSeeds.DefaultLight.canonical().primary,
            ThemeSeeds.DefaultLight.canonical().secondary,
            ThemeSeeds.DefaultLight.canonical().tertiary,
            ThemeSeeds.DefaultDark.canonical().background,
            ThemeSeeds.DefaultDark.canonical().primary,
            ThemeSeeds.DefaultDark.canonical().secondary,
            ThemeSeeds.DefaultDark.canonical().tertiary
        )
        colors.forEach { hex ->
            val rgb = parse(hex)
            val roundTrip = ColorScience.fromHsv(ColorScience.toHsv(rgb))
            assertEquals("hsv round-trip $hex", hex, HexColor.format(roundTrip))
            assertEquals(0xFF, ColorScience.alpha(roundTrip))
            assertEquals(0xFF, ColorScience.alpha(rgb))
            val viaPicker = ColorPickerLogic.applyHex(ColorPickerLogic.open(blue), hex)
            assertEquals(hex, viaPicker.hexDraft)
            assertEquals(rgb, viaPicker.rgb)
        }
    }

    @Test
    fun cancelReturnsOriginalValue() {
        val opened = ColorPickerLogic.open(blue)
        val changed = ColorPickerLogic.applyHex(opened, "#00FF00")
        assertEquals(blue, ColorPickerLogic.cancel(changed))
        assertNotEquals(changed.rgb, ColorPickerLogic.cancel(changed))
    }

    @Test
    fun confirmReturnsFinalValidSelectedValue() {
        val opened = ColorPickerLogic.open(blue)
        val changed = ColorPickerLogic.applyHsv(
            opened,
            ColorScience.Hsv(hue = 300f, saturation = 1f, value = 1f)
        )
        assertEquals(changed.rgb, ColorPickerLogic.confirm(changed))
        assertEquals("#FF00FF", HexColor.format(ColorPickerLogic.confirm(changed)!!))
        val typed = ColorPickerLogic.applyHex(opened, "#8A2BE2")
        assertEquals(parse("#8A2BE2"), ColorPickerLogic.confirm(typed))
    }

    @Test
    fun applyingSelectedSeedKeepsContrastValidation() {
        val draft = PaletteDraftLogic.fromSeeds(ThemeSeeds.DefaultLight, ThemeSeeds.DefaultDark)
        val updated = PaletteDraftLogic.applyParsedColor(draft, SeedField.Primary, parse("#8A2BE2"))
        assertEquals("#8A2BE2", updated.lightPrimary)
        val result = PaletteDraftLogic.validateForSave(updated)
        assertTrue(result is PaletteSaveResult.Success || result is PaletteSaveResult.Contrast)
        val whitePrimary = PaletteDraftLogic.applyParsedColor(
            draft,
            SeedField.Primary,
            ColorScience.WHITE
        )
        val rejected = PaletteDraftLogic.validateForSave(whitePrimary)
        assertTrue(rejected is PaletteSaveResult.Contrast)
    }

    private fun parse(hex: String): Int {
        return (HexColor.parse(hex) as HexParseResult.Valid).rgb
    }
}
