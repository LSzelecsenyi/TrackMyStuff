package app.mymusclemap.domain.musclemap

/**
 * Which anatomical drawing the muscle heatmap uses. This is a visualization
 * preference. It does not describe the person and it does not change how
 * workouts, statistics, or muscle activation are calculated.
 */
enum class MuscleMapBodyModel {
    MALE,
    FEMALE;

    companion object {
        fun fromStored(value: String?): MuscleMapBodyModel? {
            return entries.firstOrNull { it.name == value }
        }
    }
}
