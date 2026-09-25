package app.mymusclemap.ui.components.musclemap

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.domain.musclemap.TemplateMuscleEmphasis
import app.mymusclemap.domain.musclemap.TemplateMuscleMapState

object MuscleMapColors {
    val Today = Color(0xFF1B5E20)
    val Days1To2 = Color(0xFF66BB6A)
    val Days3To4 = Color(0xFFFBC02D)
    val Days5To6 = Color(0xFFF57C00)
    val Days7To13 = Color(0xFFD32F2F)
    val Days14Plus = Color(0xFF64B5F6)
    val NeverTrained = Color(0xFFB0BEC5)

    val TemplatePrimary = Color(0xFF3949AB)
    val TemplateSecondary = Color(0xFF9FA8DA)

    fun recencyFill(band: MuscleRecencyBand): Color {
        return when (band) {
            MuscleRecencyBand.TODAY -> Today
            MuscleRecencyBand.DAYS_1_2 -> Days1To2
            MuscleRecencyBand.DAYS_3_4 -> Days3To4
            MuscleRecencyBand.DAYS_5_6 -> Days5To6
            MuscleRecencyBand.DAYS_7_13 -> Days7To13
            MuscleRecencyBand.DAYS_14_PLUS -> Days14Plus
            MuscleRecencyBand.NEVER -> NeverTrained
        }
    }

    fun heatmapFills(state: MuscleHeatmapState): Map<MuscleGroup, Color> {
        return state.entries.mapValues { (_, entry) -> recencyFill(entry.band) }
    }

    fun templateFills(state: TemplateMuscleMapState): Map<MuscleGroup, Color> {
        return state.emphasis.mapValues { (_, emphasis) ->
            when (emphasis) {
                TemplateMuscleEmphasis.PRIMARY -> TemplatePrimary
                TemplateMuscleEmphasis.SECONDARY -> TemplateSecondary
            }
        }
    }

    fun unmappedFill(scheme: ColorScheme): Color = scheme.surfaceContainerHigh

    fun outline(scheme: ColorScheme): Color = scheme.outline

    fun mappedOutline(scheme: ColorScheme): Color = scheme.onSurface.copy(alpha = 0.55f)

    fun selectedOutline(scheme: ColorScheme): Color = scheme.onSurface
}
