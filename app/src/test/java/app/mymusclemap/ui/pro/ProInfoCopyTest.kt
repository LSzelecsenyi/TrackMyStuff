package app.mymusclemap.ui.pro

import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProInfoCopyTest {
    @Test
    fun genericAndNonStatisticsCopyStayIndependentOfTrainingHistory() {
        val generic = (null as AppFeature?).proInfoCopy()
        assertEquals(R.string.pro_info_title, generic.titleRes)
        assertEquals(R.string.pro_info_body, generic.bodyRes)
        assertTrue(generic.highlights.isEmpty())

        val muscle = AppFeature.AdvancedMuscleAnalytics.proInfoCopy()
        assertEquals(R.string.pro_info_title, muscle.titleRes)
        assertEquals(R.string.pro_info_feature_body, muscle.bodyRes)
        assertEquals(R.string.pro_feature_advanced_muscle_analytics, muscle.bodyArgRes)
        assertTrue(muscle.highlights.isEmpty())
    }

    @Test
    fun advancedBodyMeasurementsExplainsExtraMeasurementsWithoutCoaching() {
        val copy = AppFeature.AdvancedBodyMeasurements.proInfoCopy()
        assertEquals(R.string.pro_info_body_measurements_title, copy.titleRes)
        assertEquals(R.string.pro_info_body_measurements_body, copy.bodyRes)
        assertEquals(
            listOf(
                R.string.pro_info_body_measurements_fat,
                R.string.pro_info_body_measurements_circumferences,
                R.string.pro_info_body_measurements_existing_stay
            ),
            copy.highlights
        )
    }

    @Test
    fun advancedReportsUsesLongerReportCopyWithoutStatisticsOrCoachingClaims() {
        val copy = AppFeature.AdvancedReports.proInfoCopy()
        assertEquals(R.string.pro_info_reports_title, copy.titleRes)
        assertEquals(R.string.pro_info_reports_body, copy.bodyRes)
        assertEquals(
            listOf(
                R.string.pro_info_reports_quarterly,
                R.string.pro_info_reports_half_year,
                R.string.pro_info_reports_yearly
            ),
            copy.highlights
        )
        assertTrue(copy.titleRes != R.string.pro_info_statistics_title)
        assertTrue(copy.bodyRes != R.string.pro_info_statistics_body)
        assertTrue(copy.highlights.none { it == R.string.pro_info_statistics_range_3m })
    }

    @Test
    fun unlimitedPlansAndSchedulingUseDedicatedCopy() {
        val plans = AppFeature.UnlimitedWorkoutPlans.proInfoCopy()
        assertEquals(R.string.pro_info_plans_title, plans.titleRes)
        assertEquals(R.string.pro_info_plans_body, plans.bodyRes)
        assertEquals(
            listOf(
                R.string.pro_info_plans_free_limit,
                R.string.pro_info_plans_existing_stay,
                R.string.pro_info_plans_unlimited
            ),
            plans.highlights
        )
        assertTrue(plans.titleRes != R.string.pro_info_title)
        assertTrue(plans.bodyRes != R.string.pro_info_statistics_body)
        assertTrue(plans.bodyRes != R.string.pro_info_reports_body)

        val scheduling = AppFeature.AdvancedPlanning.proInfoCopy()
        assertEquals(R.string.pro_info_scheduling_title, scheduling.titleRes)
        assertEquals(R.string.pro_info_scheduling_body, scheduling.bodyRes)
        assertEquals(
            listOf(
                R.string.pro_info_scheduling_schedule,
                R.string.pro_info_scheduling_reschedule,
                R.string.pro_info_scheduling_history_stays
            ),
            scheduling.highlights
        )
        assertTrue(scheduling.titleRes != plans.titleRes)
        assertTrue(scheduling.bodyRes != R.string.pro_info_feature_body)
    }

    @Test
    fun advancedStatisticsUsesExistingTrainingHistoryCopy() {
        val copy = AppFeature.AdvancedStatistics.proInfoCopy()
        assertEquals(R.string.pro_info_statistics_title, copy.titleRes)
        assertEquals(R.string.pro_info_statistics_body, copy.bodyRes)
        assertEquals(
            listOf(
                R.string.pro_info_statistics_range_3m,
                R.string.pro_info_statistics_range_6m,
                R.string.pro_info_statistics_range_1y,
                R.string.pro_info_statistics_range_all
            ),
            copy.highlights
        )
    }
}
