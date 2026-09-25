package app.mymusclemap.ui.templates

import app.mymusclemap.R
import app.mymusclemap.testString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.workout.TemplateCatalogLogic
import app.mymusclemap.domain.workout.TemplateListItem
import app.mymusclemap.domain.workout.TemplateMuscleSummary
import app.mymusclemap.domain.workout.TemplateNaming
import app.mymusclemap.domain.workout.WorkoutTemplate
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class TemplateListScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenUnsortedVisibleTemplatesWhenListAppearsThenNamesFollowHungarianAbc() {
        render(
            items = listOf(
                sample(id = 3, name = "Zárógyakorlat"),
                sample(id = 1, name = "Álló evezés"),
                sample(id = 2, name = "Alma")
            )
        )
        val alma = composeRule.onNodeWithTag(templateRowTag(2)).getBoundsInRoot()
        val allo = composeRule.onNodeWithTag(templateRowTag(1)).getBoundsInRoot()
        val zaro = composeRule.onNodeWithTag(templateRowTag(3)).getBoundsInRoot()
        assertTrue(comesBefore(allo, alma))
        assertTrue(comesBefore(alma, zaro))
        composeRule.onNodeWithText("Alma").assertIsDisplayed()
        composeRule.onAllNodesWithText(testString(R.string.template_row_meta, 2, 6)).assertCountEquals(3)
    }

    @Test
    fun givenSearchQueryWhenChangedThenListFiltersImmediatelyIgnoringCase() {
        renderInteractive(
            source = listOf(
                sample(id = 1, name = "Húzódzkodásos push"),
                sample(id = 2, name = "Kerékpár")
            )
        )
        composeRule.onNodeWithTag(TEMPLATE_SEARCH).assertIsNotFocused()
        composeRule.onNodeWithTag(templateRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(templateRowTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(TEMPLATE_SEARCH).performTextInput("  HÚZÓ")
        composeRule.onNodeWithTag(templateRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(templateRowTag(2)).assertDoesNotExist()
        composeRule.onNodeWithTag(TEMPLATE_SEARCH_CLEAR).assertIsDisplayed()
    }

    @Test
    fun givenNoSearchHitsWhenEmptyStateAppearsThenClearSearchIsShownAndCreateIsNotOffered() {
        renderInteractive(
            source = listOf(sample(id = 1, name = "Húzódzkodásos push")),
            query = "nincsilyen"
        )
        composeRule.onNodeWithText(testString(R.string.template_empty_search_query, "nincsilyen")).assertIsDisplayed()
        composeRule.onNodeWithTag(TEMPLATE_EMPTY_CLEAR_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(TEMPLATE_EMPTY_CREATE).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.template_editor_add)).assertDoesNotExist()
        composeRule.onNodeWithTag(TEMPLATE_EMPTY_CLEAR_SEARCH).performClick()
        composeRule.onNodeWithTag(templateRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(TEMPLATE_EMPTY_CLEAR_SEARCH).assertDoesNotExist()
    }

    @Test
    fun givenARowWhenTappedThenEditorOpensExactlyOnce() {
        var edits = 0
        render(
            items = listOf(sample(id = 4, name = "Push – Kondipark")),
            onEdit = { edits += 1 }
        )
        composeRule.onNodeWithTag(templateRowTag(4)).performClick()
        composeRule.onNodeWithTag(templateRowTag(4)).performClick()
        assertEquals(1, edits)
    }

    @Test
    fun givenOverflowMenuWhenOpenedThenPopupStaysBesideTheIconAndRowDoesNotNavigate() {
        var edits = 0
        render(
            items = listOf(sample(id = 8, name = "Húzódzkodásos push")),
            onEdit = { edits += 1 }
        )
        val row = composeRule.onNodeWithTag(templateRowTag(8)).getBoundsInRoot()
        val anchor = composeRule.onNodeWithTag(
            templateOverflowAnchorTag(8),
            useUnmergedTree = true
        ).getBoundsInRoot()
        val button = composeRule.onNodeWithTag(templateOverflowButtonTag(8)).getBoundsInRoot()
        assertTrue(
            "anchor should wrap the overflow icon, not the row: anchor=$anchor row=$row",
            anchor.right - anchor.left < (row.right - row.left) / 2
        )
        assertTrue(
            "anchor should sit on the right of the row: anchor=$anchor row=$row",
            anchor.left > (row.left + row.right) / 2
        )
        assertTrue(button.right - button.left >= 48.dp)
        assertTrue(button.bottom - button.top >= 48.dp)
        composeRule.onNodeWithContentDescription(testString(R.string.template_more_actions, "Húzódzkodásos push")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(templateOverflowMenuTag(8)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_edit)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.action_archive_template)).assertIsDisplayed()
        composeRule.onAllNodesWithText("Make a copy").assertCountEquals(0)
        val menu = composeRule.onNodeWithTag(templateOverflowMenuTag(8)).getBoundsInRoot()
        val popup = popupWindowLayoutParams().maxByOrNull { params -> params.x }
            ?: error("expected a DropdownMenu popup window")
        val density = composeRule.density.density
        val popupLeft = (popup.x / density).dp
        val popupRight = popupLeft + (menu.right - menu.left)
        assertTrue(
            "menu must not open at the left edge: popupLeft=$popupLeft popupRight=$popupRight button=$button",
            popupLeft > 40.dp
        )
        assertTrue(
            "menu should stay on the right near the overflow icon: popupRight=$popupRight row=$row",
            popupRight > (row.left + row.right) / 2
        )
        assertEquals(0, edits)
    }

    @Test
    fun givenAddIconWhenTappedTwiceThenCreateOpensOnce() {
        var adds = 0
        render(items = listOf(sample(id = 1, name = "Push")), onAdd = { adds += 1 })
        composeRule.onNodeWithTag(TEMPLATE_ADD).performClick()
        composeRule.onNodeWithTag(TEMPLATE_ADD).performClick()
        assertEquals(1, adds)
    }

    @Test
    fun givenFontScale13AndNarrowPhoneWhenListAppearsThenTouchTargetsStay48DpWithoutOverflow() {
        render(
            items = listOf(
                sample(
                    id = 9,
                    name = "Nagyon hosszú edzéstervnév amely két sorba is törhet keskeny telefonon",
                    exerciseNames = listOf("Húzódzkodás", "Fekvőtámasz", "Tolódzkodás")
                )
            ),
            width = 360.dp,
            fontScale = 1.3f
        )
        composeRule.onNodeWithTag(TEMPLATE_ROOT).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(testString(R.string.action_create_template_header)).assertIsDisplayed()
        val add = composeRule.onNodeWithTag(TEMPLATE_ADD).getBoundsInRoot()
        val row = composeRule.onNodeWithTag(templateRowTag(9)).getBoundsInRoot()
        val overflow = composeRule.onNodeWithTag(templateOverflowButtonTag(9)).getBoundsInRoot()
        val active = composeRule.onNodeWithTag(TEMPLATE_FILTER_ACTIVE).getBoundsInRoot()
        assertTrue("add width=${add.right - add.left}", add.right - add.left >= 48.dp)
        assertTrue("add height=${add.bottom - add.top}", add.bottom - add.top >= 48.dp)
        assertTrue("row height=${row.bottom - row.top}", row.bottom - row.top >= 48.dp)
        assertTrue("row should not overflow 360dp: $row", row.right <= 360.dp + 8.dp)
        assertTrue(overflow.right - overflow.left >= 48.dp)
        assertTrue(overflow.bottom - overflow.top >= 48.dp)
        assertTrue(active.bottom - active.top >= 48.dp)
        assertTrue("filter should not overflow: $active", active.right <= 360.dp + 8.dp)
        assertTrue("add and title should not overlap", add.left >= 48.dp)
        composeRule.onAllNodesWithText(testString(R.string.templates_title), substring = false).assertCountEquals(1)
    }

    @Test
    fun givenEmptyActiveListWhenShownThenCompactCreateActionOpensExistingFlow() {
        var adds = 0
        render(items = emptyList(), emptyKind = TemplateEmptyKind.Active, onAdd = { adds += 1 })
        composeRule.onNodeWithText(testString(R.string.template_empty_active_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(TEMPLATE_EMPTY_CREATE).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.template_editor_add)).performClick()
        composeRule.onNodeWithText(testString(R.string.template_editor_add)).performClick()
        assertEquals(1, adds)
        val create = composeRule.onNodeWithTag(TEMPLATE_EMPTY_CREATE).getBoundsInRoot()
        assertTrue(create.bottom - create.top >= 48.dp)
    }

    @Test
    fun givenTemplatesPageWhenShownThenHubStartRecentAndCatalogCardsAreAbsent() {
        render(items = listOf(sample(id = 1, name = "Push – Kondipark")))
        composeRule.onAllNodesWithText(testString(R.string.action_start_workout)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.hub_recent_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.weight_stat_latest_label), substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithText(testString(R.string.action_manage_exercises)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Exercise management card").assertCountEquals(0)
        composeRule.onAllNodesWithText("Start a workout", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("2 active exercises").assertCountEquals(0)
        composeRule.onNodeWithContentDescription(testString(R.string.action_create_template_header)).assertIsDisplayed()
        composeRule.onNodeWithText("Push – Kondipark").assertIsDisplayed()
    }

    private fun comesBefore(first: DpRect, second: DpRect): Boolean {
        return first.top < second.top - 1.dp
    }

    private fun popupWindowLayoutParams(): List<android.view.WindowManager.LayoutParams> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val shadow = org.robolectric.Shadows.shadowOf(wm)
        val viewsMethod = generateSequence(shadow.javaClass as Class<*>?) { type -> type.superclass }
            .mapNotNull { type ->
                type.methods.firstOrNull { method ->
                    method.name == "getViews" && method.parameterCount == 0
                }
            }
            .firstOrNull()
        val views = viewsMethod?.invoke(shadow) as? List<*> ?: emptyList<Any>()
        return views.mapNotNull { view ->
            (view as? android.view.View)?.layoutParams as? android.view.WindowManager.LayoutParams
        }
    }

    private fun renderInteractive(
        source: List<TemplateListItem>,
        query: String = "",
        width: Dp = 360.dp,
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            var currentQuery by remember { mutableStateOf(query) }
            var archiveFilter by remember { mutableStateOf(ArchiveFilter.ACTIVE) }
            val visible = TemplateCatalogLogic.filter(
                items = source,
                query = currentQuery,
                archiveFilter = archiveFilter
            )
            val emptyKind = when {
                visible.isNotEmpty() -> null
                TemplateCatalogLogic.hasSearchQuery(currentQuery) -> TemplateEmptyKind.Search
                archiveFilter == ArchiveFilter.ARCHIVED -> TemplateEmptyKind.Archived
                else -> TemplateEmptyKind.Active
            }
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(width).fillMaxSize()) {
                        TemplateListScreen(
                            state = TemplateListUiState(
                                loading = false,
                                visibleItems = visible,
                                query = currentQuery,
                                archiveFilter = archiveFilter,
                                emptyKind = emptyKind
                            ),
                            onBack = {},
                            onAdd = {},
                            onEdit = {},
                            onQueryChange = { currentQuery = it },
                            onArchiveFilter = { archiveFilter = it },
                            onArchive = {},
                            onRestore = {},
                            onRequestDelete = {},
                            onDismissDelete = {},
                            onConfirmDelete = {},
                            onMessageConsumed = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun render(
        items: List<TemplateListItem>,
        archiveFilter: ArchiveFilter = ArchiveFilter.ACTIVE,
        emptyKind: TemplateEmptyKind? = if (items.isEmpty()) TemplateEmptyKind.Active else null,
        width: Dp = 360.dp,
        fontScale: Float = 1f,
        onEdit: (Long) -> Unit = {},
        onAdd: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(width).fillMaxSize()) {
                        TemplateListScreen(
                            state = TemplateListUiState(
                                loading = false,
                                visibleItems = items,
                                archiveFilter = archiveFilter,
                                emptyKind = emptyKind
                            ),
                            onBack = {},
                            onAdd = onAdd,
                            onEdit = onEdit,
                            onQueryChange = {},
                            onArchiveFilter = {},
                            onArchive = {},
                            onRestore = {},
                            onRequestDelete = {},
                            onDismissDelete = {},
                            onConfirmDelete = {},
                            onMessageConsumed = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun sample(
        id: Long,
        name: String,
        archived: Boolean = false,
        exerciseNames: List<String> = listOf("Húzódzkodás", "Fekvőtámasz")
    ): TemplateListItem {
        return TemplateListItem(
            template = WorkoutTemplate(
                id = id,
                name = name,
                normalizedName = TemplateNaming.normalize(name),
                notes = null,
                archived = archived,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exerciseCount = exerciseNames.size.coerceAtLeast(1),
            setCount = exerciseNames.size.coerceAtLeast(1) * 3,
            primaryMuscles = emptyList(),
            muscleSummary = TemplateMuscleSummary(emptyList(), emptyList()),
            exerciseNames = exerciseNames
        )
    }
}
