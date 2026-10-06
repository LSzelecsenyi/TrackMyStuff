package app.mymusclemap.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticColorIndependenceTest {
    @Test
    fun workoutHeatmapAndAbandonedColorsStayIndependentOfStrict() {
        assertEquals(0xFF2E7D32.toInt(), WorkoutColors.lightAccent.toArgb())
        assertEquals(0xFF81C784.toInt(), WorkoutColors.darkAccent.toArgb())
        assertEquals(Color.White.toArgb(), WorkoutColors.onLightAccent.toArgb())
        assertEquals(0xFF0C121C.toInt(), WorkoutColors.onDarkAccent.toArgb())
        assertEquals(Color(0xFF1B5E20), MuscleMapColors.Today)
        assertEquals(Color(0xFF66BB6A), MuscleMapColors.Days1To2)
        assertEquals(Color(0xFFFBC02D), MuscleMapColors.Days3To4)
        assertEquals(Color(0xFFF57C00), MuscleMapColors.Days5To6)
        assertEquals(Color(0xFFD32F2F), MuscleMapColors.Days7To13)
        assertEquals(Color(0xFF64B5F6), MuscleMapColors.Days14Plus)
        assertEquals(Color(0xFFB0BEC5), MuscleMapColors.NeverTrained)
        assertEquals(0xFF7A4E12.toInt(), StrictStatus.abandonedLight.toArgb())
        assertEquals(0xFFE2A23A.toInt(), StrictStatus.abandonedDark.toArgb())
    }
}
