package app.mymusclemap.ui.pro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class ProComponentsLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenBadgeThenProLabelIsShown() {
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProBadge()
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PRO_BADGE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_badge).uppercase(AppLocale.UI)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.pro_badge_description))
            .assertIsDisplayed()
    }

    @Test
    fun givenUnlockedRowWhenTappedThenAllowedActionRuns() {
        val allowed = intArrayOf(0)
        val locked = intArrayOf(0)
        renderRow(
            entitlements = OpenFeatureEntitlements,
            onUnlocked = { allowed[0] += 1 },
            onLocked = { locked[0] += 1 }
        )
        composeRule.onNodeWithTag(PRO_GATED_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BADGE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.pro_feature_advanced_statistics)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_GATED_ROW).performClick()
        assertEquals(1, allowed[0])
        assertEquals(0, locked[0])
    }

    @Test
    fun givenLockedRowWhenTappedThenAccessIsIntercepted() {
        val allowed = intArrayOf(0)
        val locked = intArrayOf(0)
        renderRow(
            entitlements = SelectiveFeatureEntitlements(emptySet()),
            onUnlocked = { allowed[0] += 1 },
            onLocked = { locked[0] += 1 }
        )
        composeRule.onNodeWithTag(PRO_GATED_ROW).performClick()
        assertEquals(0, allowed[0])
        assertEquals(1, locked[0])
    }

    private fun renderRow(
        entitlements: FeatureEntitlements,
        onUnlocked: () -> Unit,
        onLocked: (AppFeature) -> Unit
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 1f),
                LocalFeatureEntitlements provides entitlements
            ) {
                WeightTrackerThemeForPreview {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxSize()
                    ) {
                        GatedFeatureRow(
                            title = testString(R.string.pro_feature_advanced_statistics),
                            subtitle = "Future reporting",
                            feature = AppFeature.AdvancedStatistics,
                            onUnlockedClick = onUnlocked,
                            onLockedClick = onLocked,
                            entitlements = entitlements
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
