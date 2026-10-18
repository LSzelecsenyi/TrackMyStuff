package app.mymusclemap.domain.entitlement

/**
 * Which saved plans and custom exercises a FREE user may use after PRO access ends.
 *
 * Plan limit is [WorkoutPlanAccess.FREE_PLAN_LIMIT] and counts every saved plan.
 * Exercise limit is [FeatureAccessPolicy.FREE_CUSTOM_EXERCISE_LIMIT] and counts
 * active custom exercises only. Starter exercises are not part of this selection.
 * PRO access ignores a stored selection. A confirmed selection does not change
 * when the app restarts or when items are renamed or reordered.
 */
enum class FreeAccessPhase {
    NotRequired,
    Required,
    Confirmed
}

data class FreeAccessSubject(
    val id: Long,
    val createdAt: Long
)

data class FreeAccessState(
    val periodId: Long,
    val lastKnownPro: Boolean?,
    val phase: FreeAccessPhase,
    val selectedPlanIds: Set<Long> = emptySet(),
    val selectedCustomExerciseIds: Set<Long> = emptySet()
)

data class FreeAccessDecision(
    val state: FreeAccessState,
    val selectionRequired: Boolean,
    val planLimit: Int,
    val customExerciseLimit: Int,
    val accessiblePlanIds: Set<Long>,
    val accessibleCustomExerciseIds: Set<Long>,
    val lockedPlanIds: Set<Long> = emptySet(),
    val lockedCustomExerciseIds: Set<Long> = emptySet(),
    val loaded: Boolean = true
) {
    fun planAccessible(id: Long): Boolean = !loaded || id in accessiblePlanIds

    fun customExerciseAccessible(id: Long): Boolean = !loaded || id in accessibleCustomExerciseIds
}

object FreeAccessLogic {
    const val PLAN_LIMIT = WorkoutPlanAccess.FREE_PLAN_LIMIT
    const val CUSTOM_EXERCISE_LIMIT = FeatureAccessPolicy.FREE_CUSTOM_EXERCISE_LIMIT

    fun reconcile(
        previous: FreeAccessState?,
        grantsPro: Boolean,
        plans: List<FreeAccessSubject>,
        customExercises: List<FreeAccessSubject>,
        legacyRestoreWithoutSelection: Boolean = false
    ): FreeAccessState {
        if (grantsPro) {
            return (previous ?: emptyState(periodId = 0L)).copy(lastKnownPro = true)
        }
        if (previous == null) {
            return if (legacyRestoreWithoutSelection && exceedsLimits(plans, customExercises)) {
                confirmedOldest(periodId = 1L, plans, customExercises)
            } else {
                openPeriod(periodId = 1L, plans, customExercises)
            }
        }
        if (previous.lastKnownPro == true) {
            return openPeriod(periodId = previous.periodId + 1L, plans, customExercises)
        }
        if (previous.lastKnownPro == null) {
            return previous.copy(lastKnownPro = false)
        }
        return previous.copy(
            selectedPlanIds = previous.selectedPlanIds.intersect(plans.map { it.id }.toSet()),
            selectedCustomExerciseIds = previous.selectedCustomExerciseIds.intersect(
                customExercises.map { it.id }.toSet()
            )
        )
    }

    fun decide(
        state: FreeAccessState,
        grantsPro: Boolean,
        plans: List<FreeAccessSubject>,
        customExercises: List<FreeAccessSubject>
    ): FreeAccessDecision {
        val planIds = plans.map { it.id }.toSet()
        val exerciseIds = customExercises.map { it.id }.toSet()
        val plansOver = plans.size > PLAN_LIMIT
        val exercisesOver = customExercises.size > CUSTOM_EXERCISE_LIMIT
        val accessiblePlans: Set<Long>
        val accessibleExercises: Set<Long>
        val required: Boolean
        if (grantsPro || state.phase == FreeAccessPhase.NotRequired) {
            accessiblePlans = planIds
            accessibleExercises = exerciseIds
            required = false
        } else if (state.phase == FreeAccessPhase.Required) {
            accessiblePlans = if (plansOver) emptySet() else planIds
            accessibleExercises = if (exercisesOver) emptySet() else exerciseIds
            required = plansOver || exercisesOver
        } else {
            accessiblePlans = state.selectedPlanIds.intersect(planIds)
            accessibleExercises = state.selectedCustomExerciseIds.intersect(exerciseIds)
            required = false
        }
        return FreeAccessDecision(
            state = state,
            selectionRequired = required && !grantsPro,
            planLimit = PLAN_LIMIT,
            customExerciseLimit = CUSTOM_EXERCISE_LIMIT,
            accessiblePlanIds = accessiblePlans,
            accessibleCustomExerciseIds = accessibleExercises,
            lockedPlanIds = planIds - accessiblePlans,
            lockedCustomExerciseIds = exerciseIds - accessibleExercises
        )
    }

    fun unknown(): FreeAccessDecision {
        return FreeAccessDecision(
            state = FreeAccessState(0L, null, FreeAccessPhase.NotRequired),
            selectionRequired = false,
            planLimit = PLAN_LIMIT,
            customExerciseLimit = CUSTOM_EXERCISE_LIMIT,
            accessiblePlanIds = emptySet(),
            accessibleCustomExerciseIds = emptySet(),
            loaded = false
        )
    }

    /**
     * A FREE reading before entitlement stores have loaded must not open a new period.
     * A user who was last known to be PRO stays on that period until the reading is ready.
     */
    fun holdForUnknown(
        previous: FreeAccessState?,
        grantsPro: Boolean,
        entitlementReady: Boolean
    ): Boolean {
        if (entitlementReady || grantsPro) return false
        return previous == null || previous.lastKnownPro != false
    }

    fun confirm(
        state: FreeAccessState,
        planIds: Set<Long>,
        customExerciseIds: Set<Long>,
        plans: List<FreeAccessSubject>,
        customExercises: List<FreeAccessSubject>
    ): FreeAccessState? {
        if (state.phase != FreeAccessPhase.Required || state.lastKnownPro == true) {
            return null
        }
        if (!selectionFits(plans, PLAN_LIMIT, planIds)) return null
        if (!selectionFits(customExercises, CUSTOM_EXERCISE_LIMIT, customExerciseIds)) return null
        return state.copy(
            phase = FreeAccessPhase.Confirmed,
            selectedPlanIds = storedSelection(plans, PLAN_LIMIT, planIds),
            selectedCustomExerciseIds = storedSelection(customExercises, CUSTOM_EXERCISE_LIMIT, customExerciseIds)
        )
    }

    /**
     * A plan or custom exercise created while FREE is usable only when creation
     * itself is still inside the existing count limit. It never replaces a locked item.
     */
    fun includeCreated(
        state: FreeAccessState,
        planId: Long? = null,
        customExerciseId: Long? = null,
        plans: List<FreeAccessSubject>,
        customExercises: List<FreeAccessSubject>
    ): FreeAccessState {
        if (state.phase != FreeAccessPhase.Confirmed || state.lastKnownPro == true) {
            return state
        }
        var selectedPlans = state.selectedPlanIds
        var selectedExercises = state.selectedCustomExerciseIds
        if (planId != null && plans.any { it.id == planId }) {
            val usable = selectedPlans.intersect(plans.map { it.id }.toSet()).size
            if (usable < PLAN_LIMIT) {
                selectedPlans = selectedPlans + planId
            }
        }
        if (customExerciseId != null && customExercises.any { it.id == customExerciseId }) {
            val usable = selectedExercises.intersect(customExercises.map { it.id }.toSet()).size
            if (usable < CUSTOM_EXERCISE_LIMIT) {
                selectedExercises = selectedExercises + customExerciseId
            }
        }
        return state.copy(
            selectedPlanIds = selectedPlans,
            selectedCustomExerciseIds = selectedExercises
        )
    }

    private fun openPeriod(
        periodId: Long,
        plans: List<FreeAccessSubject>,
        customExercises: List<FreeAccessSubject>
    ): FreeAccessState {
        if (!exceedsLimits(plans, customExercises)) {
            return FreeAccessState(
                periodId = periodId,
                lastKnownPro = false,
                phase = FreeAccessPhase.NotRequired,
                selectedPlanIds = plans.map { it.id }.toSet(),
                selectedCustomExerciseIds = customExercises.map { it.id }.toSet()
            )
        }
        return FreeAccessState(
            periodId = periodId,
            lastKnownPro = false,
            phase = FreeAccessPhase.Required
        )
    }

    private fun confirmedOldest(
        periodId: Long,
        plans: List<FreeAccessSubject>,
        customExercises: List<FreeAccessSubject>
    ): FreeAccessState {
        return FreeAccessState(
            periodId = periodId,
            lastKnownPro = false,
            phase = FreeAccessPhase.Confirmed,
            selectedPlanIds = oldest(plans, PLAN_LIMIT),
            selectedCustomExerciseIds = oldest(customExercises, CUSTOM_EXERCISE_LIMIT)
        )
    }

    private fun exceedsLimits(
        plans: List<FreeAccessSubject>,
        customExercises: List<FreeAccessSubject>
    ): Boolean {
        return plans.size > PLAN_LIMIT || customExercises.size > CUSTOM_EXERCISE_LIMIT
    }

    private fun selectionFits(items: List<FreeAccessSubject>, limit: Int, chosen: Set<Long>): Boolean {
        val ids = items.map { it.id }.toSet()
        if (!chosen.all { it in ids }) return false
        return if (items.size <= limit) {
            chosen.isEmpty() || chosen == ids
        } else {
            chosen.size <= limit
        }
    }

    private fun storedSelection(
        items: List<FreeAccessSubject>,
        limit: Int,
        chosen: Set<Long>
    ): Set<Long> {
        return if (items.size <= limit) items.map { it.id }.toSet() else chosen
    }

    private fun oldest(items: List<FreeAccessSubject>, limit: Int): Set<Long> {
        return items.sortedWith(compareBy<FreeAccessSubject> { it.createdAt }.thenBy { it.id })
            .take(limit)
            .map { it.id }
            .toSet()
    }

    private fun emptyState(periodId: Long): FreeAccessState {
        return FreeAccessState(
            periodId = periodId,
            lastKnownPro = null,
            phase = FreeAccessPhase.NotRequired
        )
    }
}
