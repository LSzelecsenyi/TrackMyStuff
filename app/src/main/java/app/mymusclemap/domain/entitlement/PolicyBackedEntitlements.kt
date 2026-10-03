package app.mymusclemap.domain.entitlement

import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.statistics.StatisticsRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapts [FeatureAccessPolicy] to the existing capability checks used by screens.
 * Muscle heatmap stays available on Free, matching the product matrix.
 */
class PolicyBackedEntitlements(
    private val policySource: () -> FeatureAccessPolicy,
    private val revisions: StateFlow<Int> = MutableStateFlow(0)
) : FeatureEntitlements {
    constructor(policy: FeatureAccessPolicy) : this({ policy })

    override fun changes(): Flow<Int> = revisions

    override fun hasAccess(feature: AppFeature): Boolean {
        val policy = policySource()
        return when (feature) {
            AppFeature.AdvancedStatistics -> policy.canViewStatistics(StatisticsRange.All)
            AppFeature.AdvancedReports -> policy.canViewReport(ReportKind.Quarterly)
            AppFeature.AdvancedMuscleAnalytics -> true
            AppFeature.UnlimitedWorkoutPlans -> policy.workoutPlans(Int.MAX_VALUE).canCreate
            AppFeature.AdvancedPlanning -> policy.scheduling().canCreate
            AppFeature.AdvancedBodyMeasurements -> policy.advancedMeasurement(fieldAlreadyHasValue = false).canCreateNew
            AppFeature.ProgressPhotos -> policy.progressPhotos().canAdd
            AppFeature.UnlimitedCustomExercises -> policy.customExercises(Int.MAX_VALUE).canCreate
            AppFeature.ExternalWorkoutImport -> policy.healthConnect().canImportExternalWorkouts
        }
    }
}
