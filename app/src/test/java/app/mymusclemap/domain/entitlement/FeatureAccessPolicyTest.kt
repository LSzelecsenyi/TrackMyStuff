package app.mymusclemap.domain.entitlement

import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.theme.AppearanceCodec
import app.mymusclemap.domain.theme.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeatureAccessPolicyTest {
    private val today = LocalDate.of(2026, 6, 15)
    private val free = policy(EntitlementTier.Free)
    private val pro = policy(EntitlementTier.Pro)

    @Test
    fun workoutLoggingStaysOpenForFreeAndPro() {
        listOf(free.workoutLogging(), pro.workoutLogging()).forEach { access ->
            assertTrue(access.canView)
            assertTrue(access.canCreate)
            assertTrue(access.canEdit)
            assertTrue(access.canDelete)
            assertTrue(access.canExport)
        }
    }

    @Test
    fun freeCustomExerciseCreationAllowsFewerThanFive() {
        assertTrue(free.customExercises(0).canCreate)
        assertTrue(free.customExercises(4).canCreate)
        assertFalse(free.customExercises(5).canCreate)
    }

    @Test
    fun downgradedCustomExercisesStayUsableUntilCreationDropsUnderFive() {
        val retained = free.customExercises(12)
        assertTrue(retained.canView)
        assertTrue(retained.canUseInWorkout)
        assertTrue(retained.canEdit)
        assertTrue(retained.canDelete)
        assertTrue(retained.canExport)
        assertFalse(retained.canCreate)
        assertTrue(free.customExercises(4).canCreate)
    }

    @Test
    fun proCustomExerciseCreationIsUnlimited() {
        assertTrue(pro.customExercises(12).canCreate)
    }

    @Test
    fun freeWorkoutPlanCreationAllowsFewerThanThreeAndDuplicateCountsAsCreation() {
        assertTrue(free.workoutPlans(0).canCreate)
        assertTrue(free.workoutPlans(2).canCreate)
        assertTrue(free.workoutPlans(2).canDuplicate)
        assertFalse(free.workoutPlans(3).canCreate)
        assertFalse(free.workoutPlans(3).canDuplicate)
    }

    @Test
    fun downgradedPlansStayStartableWhileCreationIsBlocked() {
        val retained = free.workoutPlans(8)
        assertTrue(retained.canView)
        assertTrue(retained.canEdit)
        assertTrue(retained.canDelete)
        assertTrue(retained.canStartWorkout)
        assertFalse(retained.canCreate)
        assertFalse(retained.canDuplicate)
        assertTrue(free.workoutPlans(2).canCreate)
    }

    @Test
    fun proWorkoutPlanCreationIsUnlimited() {
        assertTrue(pro.workoutPlans(8).canCreate)
        assertTrue(pro.workoutPlans(8).canDuplicate)
    }

    @Test
    fun freeStatisticsCoverThirtyDaysAndLongerRangesAreGated() {
        assertTrue(free.canViewStatistics(StatisticsRange.Days30))
        assertTrue(free.canIncludeInStatistics(today.minusDays(29), today))
        assertFalse(free.canIncludeInStatistics(today.minusDays(30), today))
        assertFalse(free.canViewStatistics(StatisticsRange.Months3))
        assertFalse(free.canViewStatistics(StatisticsRange.All))
    }

    @Test
    fun proStatisticsAreUnlimited() {
        assertTrue(pro.canViewStatistics(StatisticsRange.All))
        assertTrue(pro.canIncludeInStatistics(today.minusYears(5), today))
    }

    @Test
    fun monthlyReportsStayFreeAndLongerReportsRequirePro() {
        assertTrue(free.canViewReport(ReportKind.Monthly))
        assertFalse(free.canViewReport(ReportKind.Quarterly))
        assertFalse(free.canViewReport(ReportKind.HalfYear))
        assertFalse(free.canViewReport(ReportKind.Yearly))
        ReportKind.entries.forEach { kind ->
            assertTrue(pro.canViewReport(kind))
        }
    }

    @Test
    fun existingProgressPhotosStayAvailableWhenAddingIsBlocked() {
        val photos = free.progressPhotos()
        assertTrue(photos.canView)
        assertTrue(photos.canDelete)
        assertTrue(photos.canExport)
        assertFalse(photos.canAdd)
        assertTrue(pro.progressPhotos().canAdd)
    }

    @Test
    fun existingAdvancedMeasurementsStayEditableAndEmptyFieldsStayBlocked() {
        val existing = free.advancedMeasurement(fieldAlreadyHasValue = true)
        assertTrue(existing.canViewExisting)
        assertTrue(existing.canEditExisting)
        assertTrue(existing.canDeleteExisting)
        assertTrue(existing.canExport)
        assertFalse(existing.canCreateNew)
        val empty = free.advancedMeasurement(fieldAlreadyHasValue = false)
        assertFalse(empty.canEditExisting)
        assertFalse(empty.canCreateNew)
        val unlocked = pro.advancedMeasurement(fieldAlreadyHasValue = false)
        assertTrue(unlocked.canCreateNew)
        assertTrue(unlocked.canEditExisting)
    }

    @Test
    fun freeSchedulingIsReadOnlyExceptDeleteAndProRestoresIt() {
        val locked = free.scheduling()
        assertTrue(locked.canView)
        assertTrue(locked.canDelete)
        assertFalse(locked.canCreate)
        assertFalse(locked.canEdit)
        assertFalse(locked.canAutomate)
        val unlocked = pro.scheduling()
        assertTrue(unlocked.canCreate)
        assertTrue(unlocked.canEdit)
        assertTrue(unlocked.canDelete)
        assertTrue(unlocked.canAutomate)
    }

    @Test
    fun healthConnectKeepsStepsAndRestingHeartRateAndGatesNewImports() {
        val locked = free.healthConnect()
        assertTrue(locked.canReadDailySteps)
        assertTrue(locked.canReadRestingHeartRate)
        assertFalse(locked.canImportExternalWorkouts)
        assertTrue(locked.canViewImportedWorkouts)
        assertTrue(locked.canDeleteImportedWorkouts)
        assertTrue(locked.canExportImportedWorkouts)
        assertTrue(pro.healthConnect().canImportExternalWorkouts)
    }

    @Test
    fun backupDoesNotGrantEntitlement() {
        assertTrue(free.backup().canBackup)
        assertTrue(free.backup().canRestore)
        assertFalse(free.backup().grantsEntitlement)
        assertFalse(pro.backup().grantsEntitlement)
        val encoded = AppearanceCodec.encode(AppearanceSettings.Default)
        assertTrue(encoded.keys.none { key ->
            key.contains("founder") || key.contains("entitlement") || key.contains("subscription")
        })
    }

    @Test
    fun downgradeRetainsUserContentAndOnlyChangesCapabilities() {
        val content = RetainedUserContent(
            customExerciseIds = List(12) { "exercise-$it" },
            planIds = List(8) { "plan-$it" },
            photoIds = listOf("photo-1"),
            advancedMeasurementValues = mapOf("chest" to 100.0),
            importedWorkoutIds = listOf("imported-1"),
            scheduleIds = listOf("schedule-1")
        )
        val retained = EntitlementDowngrade.retain(content)
        assertSame(content, retained)
        assertEquals(content, retained)
        assertFalse(free.customExercises(content.customExerciseIds.size).canCreate)
        assertTrue(free.workoutPlans(content.planIds.size).canStartWorkout)
        assertTrue(free.progressPhotos().canView)
        assertFalse(free.progressPhotos().canAdd)
        assertTrue(free.advancedMeasurement(fieldAlreadyHasValue = true).canEditExisting)
        assertTrue(free.scheduling().canView)
        assertTrue(free.healthConnect().canViewImportedWorkouts)
        assertTrue(pro.customExercises(content.customExerciseIds.size).canCreate)
        assertTrue(pro.progressPhotos().canAdd)
    }

    @Test
    fun policyBackedEntitlementsFollowTheResolvedTier() {
        val freeEntitlements = PolicyBackedEntitlements(free)
        val proEntitlements = PolicyBackedEntitlements(pro)
        assertFalse(freeEntitlements.hasAccess(AppFeature.ProgressPhotos))
        assertFalse(freeEntitlements.hasAccess(AppFeature.UnlimitedWorkoutPlans))
        assertTrue(freeEntitlements.hasAccess(AppFeature.AdvancedMuscleAnalytics))
        AppFeature.entries.forEach { feature ->
            assertTrue(proEntitlements.hasAccess(feature))
        }
    }

    private fun policy(tier: EntitlementTier): FeatureAccessPolicy {
        val entitlement = EffectiveEntitlement(
            tier = tier,
            subscriptionValid = tier == EntitlementTier.Pro,
            founderLifetime = false,
            temporaryTesterPro = false
        )
        return FeatureAccessPolicy(entitlement)
    }
}
