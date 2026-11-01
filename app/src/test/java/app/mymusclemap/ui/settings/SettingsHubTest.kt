package app.mymusclemap.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.testString
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class SettingsHubTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sixCategoriesAppearInOrderAndOpenTheirDestinations() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                SettingsScreen(
                    onBack = { opened += "back" },
                    onOpenAppearance = { opened += "appearance" },
                    onOpenTraining = { opened += "training" },
                    onOpenHealth = { opened += "health" },
                    onOpenBackup = { opened += "backup" },
                    onOpenAccount = { opened += "account" },
                    onOpenAbout = { opened += "about" }
                )
            }
        }
        val tags = listOf(
            SETTINGS_CATEGORY_APPEARANCE to testString(R.string.settings_category_appearance),
            SETTINGS_CATEGORY_TRAINING to testString(R.string.training_title),
            SETTINGS_CATEGORY_HEALTH to testString(R.string.health_connect_title),
            SETTINGS_CATEGORY_BACKUP to testString(R.string.settings_category_backup),
            SETTINGS_CATEGORY_ACCOUNT to testString(R.string.settings_category_account),
            SETTINGS_CATEGORY_ABOUT to testString(R.string.about_title)
        )
        val subtitles = listOf(
            R.string.settings_category_appearance_subtitle,
            R.string.settings_category_training_subtitle,
            R.string.settings_category_health_subtitle,
            R.string.settings_category_backup_subtitle,
            R.string.settings_category_account_subtitle,
            R.string.settings_category_about_subtitle
        )
        var previousBottom = 0.dp
        tags.forEachIndexed { index, (tag, title) ->
            val node = composeRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            composeRule.onNodeWithText(title).assertIsDisplayed()
            composeRule.onNodeWithText(testString(subtitles[index])).assertIsDisplayed()
            val bounds = node.getBoundsInRoot()
            assertTrue("$tag should sit below the previous row", bounds.top >= previousBottom)
            assertTrue("$tag should be a full-width touch target", bounds.bottom - bounds.top >= 48.dp)
            previousBottom = bounds.bottom
        }
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_APPEARANCE).performClick()
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_TRAINING).performClick()
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_HEALTH).performClick()
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_BACKUP).performClick()
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_ACCOUNT).performClick()
        composeRule.onNodeWithTag(SETTINGS_CATEGORY_ABOUT).performClick()
        composeRule.onNodeWithContentDescription(testString(R.string.action_back)).performClick()
        org.junit.Assert.assertEquals(
            listOf("appearance", "training", "health", "backup", "account", "about", "back"),
            opened
        )
    }
}
