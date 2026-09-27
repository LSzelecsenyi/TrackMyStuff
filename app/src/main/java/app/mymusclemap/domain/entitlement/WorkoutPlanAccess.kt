package app.mymusclemap.domain.entitlement

/**
 * Free/Pro rules for workout plans and scheduling.
 *
 * [FREE_PLAN_LIMIT] counts every saved plan, including archived plans. Deleting a plan
 * is what frees a slot. Entitlement changes do not create, hide, or delete plans.
 */
object WorkoutPlanAccess {
    const val FREE_PLAN_LIMIT = 3

    fun canCreateAnother(existingPlanCount: Int, entitlements: FeatureEntitlements): Boolean {
        if (entitlements.hasAccess(AppFeature.UnlimitedWorkoutPlans)) {
            return true
        }
        return existingPlanCount < FREE_PLAN_LIMIT
    }

    fun canSchedule(entitlements: FeatureEntitlements): Boolean {
        return entitlements.hasAccess(AppFeature.AdvancedPlanning)
    }
}
