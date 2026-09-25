package app.mymusclemap.domain.theme

sealed class HexParseResult {
    data class Valid(val rgb: Int, val canonical: String) : HexParseResult()
    data object Invalid : HexParseResult()
}

object HexColor {
    private val pattern = Regex("^#?[0-9A-Fa-f]{6}$")

    fun parse(raw: String): HexParseResult {
        val trimmed = raw.trim()
        if (!trimmed.matches(pattern)) {
            return HexParseResult.Invalid
        }
        val digits = trimmed.removePrefix("#")
        val rgb = 0xFF000000.toInt() or digits.toInt(16)
        return HexParseResult.Valid(rgb = rgb, canonical = format(rgb))
    }

    fun format(rgb: Int): String {
        return "#%06X".format(rgb and 0xFFFFFF)
    }
}
