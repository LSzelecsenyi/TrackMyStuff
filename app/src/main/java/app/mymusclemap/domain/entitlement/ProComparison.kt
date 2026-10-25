package app.mymusclemap.domain.entitlement

/**
 * User-facing comparison order. Each row matches a gate that already exists.
 * Declaration order is the order shown on the benefits screen.
 */
enum class ProComparisonRow {
    WorkoutPlans,
    CustomExercises,
    TimedSetsAndTimers,
    Statistics,
    Reports,
    WorkoutScheduling,
    Achievements,
    BodyMeasurements,
    ProgressPhotos,
    ExternalWorkouts;

    companion object {
        val order: List<ProComparisonRow> = entries
    }
}
