package app.mymusclemap.ui.founder

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FounderProgramRules
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
class FounderInvitationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fastRules = FounderProgramRules(
        temporaryProWorkoutCount = 1,
        founderWorkoutCount = 2,
        requiredDistinctWorkoutDays = 1,
        qualificationWindowDays = 45,
        feedbackRequired = true,
        testerAnalyticsReportRequired = true
    )

    @Test
    fun invitationReusesWelcomeCopyAndTheRulesItIsGiven() {
        render(fastRules)
        composeRule.onNodeWithTag(FOUNDER_MARK).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.founder_mark_content_description)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_lead)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_unlock_early_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_native)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_upload)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.founder_welcome_review)).assertIsDisplayed()
        show(bullet(R.plurals.founder_challenge_workouts, fastRules.founderWorkoutCount))
        show(bullet(R.plurals.founder_challenge_days, fastRules.requiredDistinctWorkoutDays))
        show(bullet(R.plurals.founder_challenge_window, fastRules.qualificationWindowDays))
        show(testString(R.string.founder_bullet, testString(R.string.founder_challenge_feedback)))
        show(testString(R.string.founder_bullet, testString(R.string.founder_challenge_report)))
        show(offer(fastRules.temporaryProWorkoutCount))
        composeRule.onNodeWithText(bullet(R.plurals.founder_challenge_workouts, FounderProgramRules.Production.founderWorkoutCount)).assertDoesNotExist()
        composeRule.onNodeWithText(offer(FounderProgramRules.Production.temporaryProWorkoutCount)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_debug_approve)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_invitation_not_now)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun invitationCanShowProductionRules() {
        render(FounderProgramRules.Production)
        show(bullet(R.plurals.founder_challenge_workouts, FounderProgramRules.Production.founderWorkoutCount))
        show(bullet(R.plurals.founder_challenge_days, FounderProgramRules.Production.requiredDistinctWorkoutDays))
        show(offer(FounderProgramRules.Production.temporaryProWorkoutCount))
        composeRule.onNodeWithText(bullet(R.plurals.founder_challenge_workouts, fastRules.founderWorkoutCount)).assertDoesNotExist()
        composeRule.onNodeWithText(offer(fastRules.temporaryProWorkoutCount)).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.founder_debug_approve)).assertDoesNotExist()
    }

    @Test
    fun joinStaysDisabledWhileItIsRunningAndCanBeRetriedAfterItStops() {
        var joins = 0
        var declines = 0
        var joining by mutableStateOf(false)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 1f)) {
                WeightTrackerThemeForPreview {
                    Box(Modifier.width(360.dp).height(2000.dp).fillMaxSize()) {
                        FounderInvitationScreen(
                            rules = fastRules,
                            onJoin = {
                                joins += 1
                                joining = true
                            },
                            onNotNow = { declines += 1 },
                            joining = joining
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FOUNDER_ENROLL).performScrollTo().performClick()
        assertEquals(1, joins)
        assertEquals(0, declines)
        composeRule.onNodeWithTag(FOUNDER_ENROLL).assertIsNotEnabled()
        composeRule.onNodeWithTag(FOUNDER_INVITATION_NOT_NOW).assertIsNotEnabled()
        composeRule.onNodeWithTag(FOUNDER_ENROLL).performClick()
        assertEquals(1, joins)
        joining = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FOUNDER_ENROLL).performScrollTo().performClick()
        assertEquals(2, joins)
    }

    @Test
    fun notNowDoesNotJoin() {
        var joins = 0
        var declines = 0
        render(fastRules, onJoin = { joins += 1 }, onNotNow = { declines += 1 })
        composeRule.onNodeWithTag(FOUNDER_INVITATION_NOT_NOW).performScrollTo().performClick()
        assertEquals(0, joins)
        assertEquals(1, declines)
        composeRule.onNodeWithTag(FOUNDER_ENROLL).assertIsNotEnabled()
    }

    @Test
    fun recompositionKeepsASingleInvitation() {
        var revision by mutableStateOf(0)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 1f)) {
                WeightTrackerThemeForPreview {
                    Box(Modifier.width(360.dp).height(2000.dp).fillMaxSize()) {
                        revision
                        FounderInvitationScreen(rules = fastRules, onJoin = {}, onNotNow = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        revision = 1
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(FOUNDER_INVITATION).assertCountEquals(1)
    }

    private fun render(
        rules: FounderProgramRules,
        onJoin: () -> Unit = {},
        onNotNow: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 1f)) {
                WeightTrackerThemeForPreview {
                    Box(Modifier.width(360.dp).height(2000.dp).fillMaxSize()) {
                        FounderInvitationScreen(rules = rules, onJoin = onJoin, onNotNow = onNotNow)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun show(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun bullet(@PluralsRes id: Int, count: Int): String {
        return testString(R.string.founder_bullet, quantity(id, count))
    }

    private fun offer(count: Int): String = quantity(R.plurals.founder_temporary_pro_offer, count)

    private fun quantity(@PluralsRes id: Int, count: Int): String {
        return ApplicationProvider.getApplicationContext<Context>()
            .resources
            .getQuantityString(id, count, count)
    }
}
