package app.mymusclemap.domain.entitlement

/**
 * Gated capabilities. Values are product capabilities, not screens or billing products.
 * Billing later maps subscription SKUs onto these capabilities through [FeatureEntitlements].
 */
enum class AppFeature {
    AdvancedStatistics,
    AdvancedMuscleAnalytics,
    UnlimitedWorkoutPlans,
    AdvancedPlanning
}
