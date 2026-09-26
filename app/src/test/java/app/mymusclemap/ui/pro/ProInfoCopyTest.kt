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

        val planning = AppFeature.AdvancedPlanning.proInfoCopy()
        assertEquals(R.string.pro_info_title, planning.titleRes)
        assertEquals(R.string.pro_info_feature_body, planning.bodyRes)
        assertEquals(R.string.pro_feature_advanced_planning, planning.bodyArgRes)
        assertTrue(planning.highlights.isEmpty())

        val muscle = AppFeature.AdvancedMuscleAnalytics.proInfoCopy()
        assertEquals(R.string.pro_info_title, muscle.titleRes)
        assertEquals(R.string.pro_info_feature_body, muscle.bodyRes)
        assertEquals(R.string.pro_feature_advanced_muscle_analytics, muscle.bodyArgRes)
        assertTrue(muscle.highlights.isEmpty())
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
