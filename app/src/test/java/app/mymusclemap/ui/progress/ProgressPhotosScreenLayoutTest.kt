package app.mymusclemap.ui.progress

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mymusclemap.R
import app.mymusclemap.testString
import app.mymusclemap.ui.pro.PRO_BADGE
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ProgressPhotosScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun emptyStateOffersAddAndOverviewStaysCompact() {
        var added = false
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProgressPhotosScreen(
                    state = ProgressPhotosUiState(canAdd = false, showProBadge = true, loaded = true),
                    onBack = {},
                    onAdd = { added = true },
                    onConsumeLaunchPicker = {},
                    onPicked = {},
                    onOpenPhoto = {},
                    onBeginCompare = {},
                    onToggleCompare = {},
                    onCancelCompare = {},
                    onShowCompare = { _, _ -> },
                    onDismissLocked = {},
                    decode = { _, _ -> null }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.progress_photos_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.progress_photos_add)).performClick()
        assertTrue(added)
    }

    @Test
    fun overviewRowStaysCompactAndOpensPhotos() {
        var opened = false
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProgressPhotosOverviewCard(
                    count = 0,
                    latestDate = null,
                    showProBadge = true,
                    thumbnails = emptyList(),
                    latestMissing = false,
                    onOpen = { opened = true }
                )
            }
        }
        composeRule.onNodeWithText(testString(R.string.progress_photos_title)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.progress_photos_overview_support)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.progress_photos_empty)).assertIsDisplayed()
        composeRule.onNodeWithTag(PRO_BADGE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_PHOTOS_ROW).performClick()
        assertTrue(opened)
    }

    @Test
    fun sameDayPhotosOpenViewerAndCompare() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        var opened: Long? = null
        val photos = listOf(
            ProgressPhotoListItem(2, today, "b.jpg", missing = false),
            ProgressPhotoListItem(1, today, "a.jpg", missing = true)
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProgressPhotosScreen(
                    state = ProgressPhotosUiState(photos = photos, canAdd = true, loaded = true),
                    onBack = {},
                    onAdd = {},
                    onConsumeLaunchPicker = {},
                    onPicked = {},
                    onOpenPhoto = { opened = it },
                    onBeginCompare = {},
                    onToggleCompare = {},
                    onCancelCompare = {},
                    onShowCompare = { _, _ -> },
                    onDismissLocked = {},
                    decode = { name, _ -> if (name == "b.jpg") bitmap else null }
                )
            }
        }
        assertEquals(2, composeRule.onAllNodesWithText("Sep 27").fetchSemanticsNodes().size)
        composeRule.onNodeWithText(testString(R.string.progress_photos_missing)).assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_CELL + 2).performClick()
        assertEquals(2L, opened)
    }

    @Test
    fun compareSelectionShowsTheChosenPair() {
        var compared: Pair<Long, Long>? = null
        val photos = listOf(
            ProgressPhotoListItem(2, today, "b.jpg", missing = false),
            ProgressPhotoListItem(1, today, "a.jpg", missing = true)
        )
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProgressPhotosScreen(
                    state = ProgressPhotosUiState(
                        photos = photos,
                        canAdd = true,
                        selectingCompare = true,
                        selectedIds = listOf(2, 1),
                        loaded = true
                    ),
                    onBack = {},
                    onAdd = {},
                    onConsumeLaunchPicker = {},
                    onPicked = {},
                    onOpenPhoto = {},
                    onBeginCompare = {},
                    onToggleCompare = {},
                    onCancelCompare = {},
                    onShowCompare = { first, second -> compared = first to second },
                    onDismissLocked = {}
                )
            }
        }
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_SHOW).performClick()
        assertEquals(2L to 1L, compared)
    }

    @Test
    fun viewerDeletesAndComparisonShowsBothDates() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        var deleted = false
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProgressPhotoViewerScreen(
                    date = today,
                    bitmap = bitmap,
                    missing = false,
                    onBack = {},
                    onDelete = { deleted = true }
                )
            }
        }
        composeRule.onNodeWithTag(PROGRESS_PHOTO_VIEWER).assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_DELETE).performClick()
        composeRule.onAllNodesWithText(testString(R.string.action_delete))[1].performClick()
        assertTrue(deleted)
    }

    @Test
    fun comparisonShowsBothDatesWithoutScoring() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                ProgressPhotoCompareScreen(
                    beforeDate = today.minusDays(10),
                    before = bitmap,
                    beforeMissing = false,
                    afterDate = today,
                    after = null,
                    afterMissing = true,
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithText("Sep 17").assertIsDisplayed()
        composeRule.onNodeWithText("Sep 27").assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.progress_photos_missing)).assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_PHOTO_COMPARE_SCREEN).assertIsDisplayed()
    }
}
