package app.mymusclemap.domain.units

/**
 * How Strict shows and accepts measurements. Stored history stays in canonical
 * metric units regardless of this choice.
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL;

    companion object {
        fun fromStored(value: String?): UnitSystem? {
            return entries.firstOrNull { it.name == value }
        }
    }
}

/**
 * Regions that reliably use US customary units for body weight and distance.
 * This is only an onboarding suggestion. A saved choice is never replaced when
 * the device region changes.
 */
object UnitSystemSuggestion {
    private val IMPERIAL_REGIONS = setOf("US", "LR", "MM")

    fun fromRegion(regionCode: String?): UnitSystem {
        val region = regionCode?.trim()?.uppercase().orEmpty()
        return if (region in IMPERIAL_REGIONS) UnitSystem.IMPERIAL else UnitSystem.METRIC
    }
}
