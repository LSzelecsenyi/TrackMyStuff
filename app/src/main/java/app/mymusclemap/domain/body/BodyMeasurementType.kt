package app.mymusclemap.domain.body

import app.mymusclemap.domain.entitlement.AppFeature

enum class BodyMeasurementUnit {
    CENTIMETERS,
    PERCENT
}

/**
 * Editable body measurements other than body weight.
 * Weight stays on [app.mymusclemap.domain.model.WeightMeasurement].
 *
 * [code] is the persisted and backed-up text. Unknown codes can still round-trip.
 */
enum class BodyMeasurementType(
    val unit: BodyMeasurementUnit,
    val minimum: Double,
    val maximum: Double,
    val requiredFeature: AppFeature?
) {
    WAIST(
        unit = BodyMeasurementUnit.CENTIMETERS,
        minimum = 20.0,
        maximum = 250.0,
        requiredFeature = null
    ),
    BODY_FAT(
        unit = BodyMeasurementUnit.PERCENT,
        minimum = 2.0,
        maximum = 75.0,
        requiredFeature = AppFeature.AdvancedBodyMeasurements
    ),
    CHEST(
        unit = BodyMeasurementUnit.CENTIMETERS,
        minimum = 20.0,
        maximum = 250.0,
        requiredFeature = AppFeature.AdvancedBodyMeasurements
    ),
    UPPER_ARM(
        unit = BodyMeasurementUnit.CENTIMETERS,
        minimum = 20.0,
        maximum = 250.0,
        requiredFeature = AppFeature.AdvancedBodyMeasurements
    ),
    THIGH(
        unit = BodyMeasurementUnit.CENTIMETERS,
        minimum = 20.0,
        maximum = 250.0,
        requiredFeature = AppFeature.AdvancedBodyMeasurements
    ),
    HIPS(
        unit = BodyMeasurementUnit.CENTIMETERS,
        minimum = 20.0,
        maximum = 250.0,
        requiredFeature = AppFeature.AdvancedBodyMeasurements
    );

    val code: String get() = name

    companion object {
        const val CODE_PATTERN = """[A-Z][A-Z0-9_]{0,63}"""

        fun known(code: String): BodyMeasurementType? {
            return entries.firstOrNull { it.code == code }
        }
    }
}
