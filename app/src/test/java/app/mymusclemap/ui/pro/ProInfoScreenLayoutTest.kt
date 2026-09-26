package app.mymusclemap.ui.pro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class ProInfoScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenProInfoThenTitleBodyAndBadgeAreShown() {
        renderScreen()
        composeRule.onNodeWithTag(PRO_INFO_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_BODY).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BADGE).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_DISMISS).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_body)).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_INFO_HIGHLIGHTS).assertDoesNotExist()
    }

    @Test
    fun givenFeatureThenBodyNamesTheCapability() {
        renderScreen(feature = AppFeature.AdvancedMuscleAnalytics)
        composeRule.onNodeWithText(testString(R.string.pro_info_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(
                R.string.pro_info_feature_body,
                testString(R.string.pro_feature_advanced_muscle_analytics)
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_body)).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_INFO_HIGHLIGHTS).assertDoesNotExist()
    }

    @Test
    fun givenAdvancedStatisticsThenTrainingHistoryCopyIsShown() {
        renderScreen(feature = AppFeature.AdvancedStatistics)
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_HIGHLIGHTS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_3m)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_6m)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_1y)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_range_all)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_info_body)).assertDoesNotExist()
    }

    @Test
    fun givenBackWhenTappedThenCallbackRunsOnce() {
        val backs = intArrayOf(0)
        renderScreen(onBack = { backs[0] += 1 })
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        assertEquals(1, backs[0])
    }

    @Test
    fun givenLockedHostWhenGatedRowTappedThenProInfoIsShown() {
        val allowed = intArrayOf(0)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f),
                LocalFeatureEntitlements provides SelectiveFeatureEntitlements(emptySet())
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .height(2000.dp)
                            .fillMaxSize()
                    ) {
                        ProAccessHost { gate ->
                            GatedFeatureRow(
                                title = testString(R.string.pro_feature_advanced_planning),
                                subtitle = "Future calendar depth",
                                feature = AppFeature.AdvancedPlanning,
                                onUnlockedClick = { allowed[0] += 1 },
                                onLockedClick = { feature -> gate(feature) {} }
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_GATED_ROW).performClick()
        composeRule.waitForIdle()
        assertEquals(0, allowed[0])
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            testString(
                R.string.pro_info_feature_body,
                testString(R.string.pro_feature_advanced_planning)
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_body)).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_INFO_HIGHLIGHTS).assertDoesNotExist()
        composeRule.onNodeWithTag(PRO_INFO_DISMISS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertDoesNotExist()
    }

    @Test
    fun givenOpenHostWhenGatedRowTappedThenCapabilityRunsWithoutProInfo() {
        val allowed = intArrayOf(0)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalFeatureEntitlements provides OpenFeatureEntitlements
            ) {
                WeightTrackerThemeForPreview {
                    ProAccessHost { gate ->
                        GatedFeatureRow(
                            title = testString(R.string.pro_feature_unlimited_workout_plans),
                            subtitle = "Future plan limit",
                            feature = AppFeature.UnlimitedWorkoutPlans,
                            onUnlockedClick = { allowed[0] += 1 },
                            onLockedClick = { feature -> gate(feature) {} }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRO_GATED_ROW).performClick()
        composeRule.waitForIdle()
        assertEquals(1, allowed[0])
        composeRule.onNodeWithTag(PRO_INFO_TITLE).assertDoesNotExist()
    }

    @Test
    fun givenLockedStatisticsHostWhenGatedRowTappedThenTrainingHistoryCopyIsShown() {
        val allowed = intArrayOf(0)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f),
                LocalFeatureEntitlements provides SelectiveFeatureEntitlements(emptySet())
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .height(2000.dp)
                            .fillMaxSize()
                    ) {
                        ProAccessHost { gate ->
                            GatedFeatureRow(
                                title = testString(R.string.pro_feature_advanced_statistics),
                                subtitle = "Longer statistics history",
                                feature = AppFeature.AdvancedStatistics,
                                onUnlockedClick = { allowed[0] += 1 },
                                onLockedClick = { feature -> gate(feature) {} }
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRO_GATED_ROW).performClick()
        composeRule.waitForIdle()
        assertEquals(0, allowed[0])
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_statistics_body)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_INFO_HIGHLIGHTS).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_info_title)).assertDoesNotExist()
    }

    private fun renderScreen(
        feature: AppFeature? = null,
        onBack: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f)
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .height(2000.dp)
                            .fillMaxSize()
                    ) {
                        ProInfoScreen(onBack = onBack, feature = feature)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
