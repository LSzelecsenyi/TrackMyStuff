package app.mymusclemap.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Green used for completed and in-progress training, shared by the active workout
 * and the calendar. It stays independent of the user-chosen primary color so
 * "today" and selection can use the theme accent without looking like a workout.
 */
object WorkoutColors {
    val lightAccent = Color(0xFF2E7D32)
    val darkAccent = Color(0xFF81C784)
    val onLightAccent = Color.White
    val onDarkAccent = Color(0xFF0C121C)

    fun accent(backgroundLuminance: Float): Color {
        return if (backgroundLuminance < 0.5f) darkAccent else lightAccent
    }

    fun onAccent(backgroundLuminance: Float): Color {
        return if (backgroundLuminance < 0.5f) onDarkAccent else onLightAccent
    }
}
