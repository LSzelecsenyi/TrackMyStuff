package app.mymusclemap.domain.entitlement

import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.statistics.StatisticsRange
import java.time.LocalDate

/**
 * Product rules for the resolved tier. Callers pass current counts; this type does not
 * read or rewrite user data. Losing Pro changes these answers only.
 */
class FeatureAccessPolicy(
    val entitlement: EffectiveEntitlement
) {
    val tier: EntitlementTier get() = entitlement.tier

    fun workoutLogging(): WorkoutPermissions = WorkoutPermissions()

    fun customExercises(existingCount: Int): CustomExercisePermissions {
        return CustomExercisePermissions(
            canCreate = pro || existingCount < FREE_CUSTOM_EXERCISE_LIMIT
        )
    }

    fun workoutPlans(existingCount: Int): WorkoutPlanPermissions {
        val canCreate = pro || existingCount < WorkoutPlanAccess.FREE_PLAN_LIMIT
        return WorkoutPlanPermissions(
            canCreate = canCreate,
            canDuplicate = canCreate
        )
    }

    fun scheduling(): SchedulingPermissions {
        return SchedulingPermissions(
            canCreate = pro,
            canEdit = pro,
            canAutomate = pro
        )
    }

    fun canViewStatistics(range: StatisticsRange): Boolean {
        return pro || range == StatisticsRange.Days30
    }

    fun canIncludeInStatistics(date: LocalDate, today: LocalDate): Boolean {
        if (pro) return true
        return StatisticsRange.Days30.contains(date, today)
    }

    fun canViewReport(kind: ReportKind): Boolean {
        return kind == ReportKind.Monthly || pro
    }

    fun progressPhotos(): ProgressPhotoPermissions {
        return ProgressPhotoPermissions(canAdd = pro)
    }

    fun advancedMeasurement(fieldAlreadyHasValue: Boolean): AdvancedMeasurementPermissions {
        return AdvancedMeasurementPermissions(
            canEditExisting = fieldAlreadyHasValue || pro,
            canCreateNew = pro
        )
    }

    fun healthConnect(): HealthConnectPermissions {
        return HealthConnectPermissions(canImportExternalWorkouts = pro)
    }

    fun backup(): BackupPermissions = BackupPermissions()

    private val pro: Boolean get() = entitlement.grantsPro

    companion object {
        const val FREE_CUSTOM_EXERCISE_LIMIT = 5
    }
}

data class WorkoutPermissions(
    val canView: Boolean = true,
    val canCreate: Boolean = true,
    val canEdit: Boolean = true,
    val canDelete: Boolean = true,
    val canExport: Boolean = true
)

data class CustomExercisePermissions(
    val canView: Boolean = true,
    val canCreate: Boolean,
    val canEdit: Boolean = true,
    val canDelete: Boolean = true,
    val canUseInWorkout: Boolean = true,
    val canExport: Boolean = true
)

data class WorkoutPlanPermissions(
    val canView: Boolean = true,
    val canCreate: Boolean,
    val canDuplicate: Boolean,
    val canEdit: Boolean = true,
    val canDelete: Boolean = true,
    val canStartWorkout: Boolean = true,
    val canExport: Boolean = true
)

data class SchedulingPermissions(
    val canView: Boolean = true,
    val canCreate: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean = true,
    val canAutomate: Boolean
)

data class ProgressPhotoPermissions(
    val canView: Boolean = true,
    val canAdd: Boolean,
    val canDelete: Boolean = true,
    val canExport: Boolean = true
)

data class AdvancedMeasurementPermissions(
    val canViewExisting: Boolean = true,
    val canEditExisting: Boolean,
    val canDeleteExisting: Boolean = true,
    val canExport: Boolean = true,
    val canCreateNew: Boolean
)

data class HealthConnectPermissions(
    val canReadDailySteps: Boolean = true,
    val canReadRestingHeartRate: Boolean = true,
    val canImportExternalWorkouts: Boolean,
    val canViewImportedWorkouts: Boolean = true,
    val canDeleteImportedWorkouts: Boolean = true,
    val canExportImportedWorkouts: Boolean = true
)

data class BackupPermissions(
    val canBackup: Boolean = true,
    val canRestore: Boolean = true,
    val grantsEntitlement: Boolean = false
)

/** User-owned content kept across a tier change. There is no deleting downgrade. */
data class RetainedUserContent(
    val customExerciseIds: List<String>,
    val planIds: List<String>,
    val photoIds: List<String>,
    val advancedMeasurementValues: Map<String, Double>,
    val importedWorkoutIds: List<String>,
    val scheduleIds: List<String>
)

object CustomExerciseAccess {
    fun canCreateAnother(activeCustomCount: Int, entitlements: FeatureEntitlements): Boolean {
        if (entitlements.hasAccess(AppFeature.UnlimitedCustomExercises)) {
            return true
        }
        return activeCustomCount < FeatureAccessPolicy.FREE_CUSTOM_EXERCISE_LIMIT
    }
}

object EntitlementDowngrade {
    fun retain(content: RetainedUserContent): RetainedUserContent = content
}

/** A portable user-data restore never replaces entitlement sources. */
object EntitlementAuthority {
    fun afterPortableUserDataRestore(current: EntitlementSources): EntitlementSources = current
}
