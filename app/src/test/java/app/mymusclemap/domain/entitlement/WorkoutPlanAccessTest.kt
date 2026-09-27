package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanAccessTest {
    private val free = SelectiveFeatureEntitlements(emptySet())
    private val plans = SelectiveFeatureEntitlements(setOf(AppFeature.UnlimitedWorkoutPlans))
    private val scheduling = SelectiveFeatureEntitlements(setOf(AppFeature.AdvancedPlanning))

    @Test
    fun freeCanCreateBelowTheLimitAndNotAtTheLimit() {
        assertTrue(WorkoutPlanAccess.canCreateAnother(0, free))
        assertTrue(WorkoutPlanAccess.canCreateAnother(2, free))
        assertFalse(WorkoutPlanAccess.canCreateAnother(WorkoutPlanAccess.FREE_PLAN_LIMIT, free))
        assertFalse(WorkoutPlanAccess.canCreateAnother(4, free))
    }

    @Test
    fun unlimitedPlansDoesNotDependOnScheduling() {
        assertTrue(WorkoutPlanAccess.canCreateAnother(4, plans))
        assertFalse(WorkoutPlanAccess.canSchedule(plans))
        assertFalse(WorkoutPlanAccess.canCreateAnother(3, scheduling))
        assertTrue(WorkoutPlanAccess.canSchedule(scheduling))
        assertTrue(WorkoutPlanAccess.canCreateAnother(9, OpenFeatureEntitlements))
        assertTrue(WorkoutPlanAccess.canSchedule(OpenFeatureEntitlements))
    }
}
