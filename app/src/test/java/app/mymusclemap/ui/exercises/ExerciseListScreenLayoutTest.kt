package app.mymusclemap.ui.exercises

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
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCatalogLogic
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
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
class ExerciseListScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenUnsortedVisibleExercisesWhenCatalogAppearsThenNamesFollowHungarianAbc() {
        render(
            exercises = listOf(
                sample(id = 3, name = "Zárógyakorlat"),
                sample(id = 1, name = "Álló evezés"),
                sample(id = 2, name = "Alma")
            )
        )
        val alma = composeRule.onNodeWithTag(catalogRowTag(2)).getBoundsInRoot()
        val allo = composeRule.onNodeWithTag(catalogRowTag(1)).getBoundsInRoot()
        val zaro = composeRule.onNodeWithTag(catalogRowTag(3)).getBoundsInRoot()
        assertTrue(comesBefore(alma, allo))
        assertTrue(comesBefore(allo, zaro))
        composeRule.onNodeWithText("Alma").assertIsDisplayed()
        composeRule.onAllNodesWithText("Széles hátizom").assertCountEquals(3)
        composeRule.onAllNodesWithText("Bicepsz").assertCountEquals(3)
    }

    @Test
    fun givenAccentedHungarianNamesWhenListRendersThenHuHuOrderIsVisible() {
        render(
            exercises = listOf(
                sample(id = 5, name = "őszibarack"),
                sample(id = 1, name = "alma"),
                sample(id = 2, name = "Áron"),
                sample(id = 3, name = "béka"),
                sample(id = 4, name = "ékezet")
            )
        )
        val alma = composeRule.onNodeWithTag(catalogRowTag(1)).getBoundsInRoot()
        val aron = composeRule.onNodeWithTag(catalogRowTag(2)).getBoundsInRoot()
        val beka = composeRule.onNodeWithTag(catalogRowTag(3)).getBoundsInRoot()
        val ekezet = composeRule.onNodeWithTag(catalogRowTag(4)).getBoundsInRoot()
        val oszi = composeRule.onNodeWithTag(catalogRowTag(5)).getBoundsInRoot()
        assertTrue(comesBefore(alma, aron))
        assertTrue(comesBefore(aron, beka))
        assertTrue(comesBefore(beka, ekezet))
        assertTrue(comesBefore(ekezet, oszi))
    }

    @Test
    fun givenActiveExercisesWhenUserTypesThenListFiltersImmediatelyIgnoringCase() {
        renderInteractive(
            source = listOf(
                sample(id = 1, name = "Húzódzkodás"),
                sample(id = 2, name = "Kerékpározás", category = ExerciseCategory.CARDIO, primary = MuscleGroup.CARDIOVASCULAR)
            )
        )
        composeRule.onNodeWithTag(CATALOG_SEARCH).assertIsNotFocused()
        composeRule.onNodeWithTag(catalogRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(catalogRowTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(CATALOG_SEARCH).performTextInput("  HÚZÓ")
        composeRule.onNodeWithTag(catalogRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(catalogRowTag(2)).assertDoesNotExist()
        composeRule.onNodeWithTag(CATALOG_SEARCH_CLEAR).assertIsDisplayed()
    }

    @Test
    fun givenNoSearchHitsWhenEmptyStateAppearsThenClearSearchIsShownAndCreateIsNotOffered() {
        renderInteractive(
            source = listOf(sample(id = 1, name = "Húzódzkodás")),
            query = "nincsilyen"
        )
        composeRule.onNodeWithText("Nincs találat a(z) „nincsilyen” keresésre.").assertIsDisplayed()
        composeRule.onNodeWithTag(CATALOG_EMPTY_CLEAR_SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(CATALOG_EMPTY_CREATE).assertDoesNotExist()
        composeRule.onNodeWithText("Új gyakorlat").assertDoesNotExist()
        composeRule.onNodeWithTag(CATALOG_EMPTY_CLEAR_SEARCH).performClick()
        composeRule.onNodeWithTag(catalogRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(CATALOG_EMPTY_CLEAR_SEARCH).assertDoesNotExist()
    }

    @Test
    fun givenArchiveFiltersWhenUserSwitchesThenOnlyMatchingRowsRemainInAbcOrder() {
        renderInteractive(
            source = listOf(
                sample(id = 3, name = "Záró"),
                sample(id = 1, name = "Alma", archived = true),
                sample(id = 2, name = "Álló evezés")
            )
        )
        composeRule.onNodeWithTag(catalogRowTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(catalogRowTag(3)).assertIsDisplayed()
        composeRule.onNodeWithTag(catalogRowTag(1)).assertDoesNotExist()
        val allo = composeRule.onNodeWithTag(catalogRowTag(2)).getBoundsInRoot()
        val zaro = composeRule.onNodeWithTag(catalogRowTag(3)).getBoundsInRoot()
        assertTrue(comesBefore(allo, zaro))
        composeRule.onNodeWithTag(CATALOG_FILTER_ARCHIVED).performClick()
        composeRule.onNodeWithTag(catalogRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(catalogRowTag(2)).assertDoesNotExist()
        composeRule.onAllNodesWithText("Archivált").assertCountEquals(2)
        composeRule.onNodeWithTag(CATALOG_FILTER_ALL).performClick()
        composeRule.onNodeWithTag(catalogRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(catalogRowTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(catalogRowTag(3)).assertIsDisplayed()
        val almaAll = composeRule.onNodeWithTag(catalogRowTag(1)).getBoundsInRoot()
        val alloAll = composeRule.onNodeWithTag(catalogRowTag(2)).getBoundsInRoot()
        val zaroAll = composeRule.onNodeWithTag(catalogRowTag(3)).getBoundsInRoot()
        assertTrue(comesBefore(almaAll, alloAll))
        assertTrue(comesBefore(alloAll, zaroAll))
    }

    @Test
    fun givenARowWhenTappedThenEditorOpensExactlyOnce() {
        var edits = 0
        render(
            exercises = listOf(sample(id = 4, name = "Húzódzkodás")),
            onEdit = { edits += 1 }
        )
        composeRule.onNodeWithTag(catalogRowTag(4)).performClick()
        composeRule.onNodeWithTag(catalogRowTag(4)).performClick()
        assertEquals(1, edits)
    }

    @Test
    fun givenOverflowMenuWhenOpenedThenPopupStaysBesideTheIconNotAtTheLeftEdge() {
        render(exercises = listOf(sample(id = 8, name = "Húzódzkodás")), width = 360.dp)
        val row = composeRule.onNodeWithTag(catalogRowTag(8)).getBoundsInRoot()
        val anchor = composeRule.onNodeWithTag(
            catalogOverflowAnchorTag(8),
            useUnmergedTree = true
        ).getBoundsInRoot()
        val button = composeRule.onNodeWithTag(catalogOverflowButtonTag(8)).getBoundsInRoot()
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
        composeRule.onNodeWithContentDescription("További műveletek: Húzódzkodás").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(catalogOverflowMenuTag(8)).assertIsDisplayed()
        composeRule.onNodeWithText("Szerkesztés").assertIsDisplayed()
        composeRule.onNodeWithText("Archiválás").assertIsDisplayed()
        val menu = composeRule.onNodeWithTag(catalogOverflowMenuTag(8)).getBoundsInRoot()
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
    }

    @Test
    fun givenEmptyArchivedFilterWhenShownThenArchivedEmptyCopyIsUsed() {
        render(
            exercises = emptyList(),
            archiveFilter = ArchiveFilter.ARCHIVED,
            emptyKind = CatalogEmptyKind.Archived
        )
        composeRule.onNodeWithText("Nincs archivált gyakorlat.").assertIsDisplayed()
        composeRule.onNodeWithTag(CATALOG_EMPTY_CREATE).assertDoesNotExist()
        composeRule.onNodeWithTag(CATALOG_EMPTY_CLEAR_SEARCH).assertDoesNotExist()
    }

    @Test
    fun givenEmptyActiveCatalogWhenShownThenCompactCreateActionIsAvailable() {
        render(exercises = emptyList(), emptyKind = CatalogEmptyKind.Active)
        composeRule.onNodeWithText("Még nincs gyakorlat").assertIsDisplayed()
        composeRule.onNodeWithTag(CATALOG_EMPTY_CREATE).assertIsDisplayed()
        val create = composeRule.onNodeWithTag(CATALOG_EMPTY_CREATE).getBoundsInRoot()
        assertTrue(create.bottom - create.top >= 48.dp)
    }

    @Test
    fun givenFontScale13AndNarrowPhoneWhenListAppearsThenTouchTargetsStay48DpWithoutOverflow() {
        render(
            exercises = listOf(
                sample(
                    id = 9,
                    name = "Nagyon hosszú gyakorlatnév amely két sorba is törhet keskeny telefonon"
                )
            ),
            width = 360.dp,
            fontScale = 1.3f
        )
        composeRule.onNodeWithTag(CATALOG_ROOT).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Új gyakorlat létrehozása").assertIsDisplayed()
        val add = composeRule.onNodeWithTag(CATALOG_ADD).getBoundsInRoot()
        val row = composeRule.onNodeWithTag(catalogRowTag(9)).getBoundsInRoot()
        val overflow = composeRule.onNodeWithTag(catalogOverflowButtonTag(9)).getBoundsInRoot()
        val active = composeRule.onNodeWithTag(CATALOG_FILTER_ACTIVE).getBoundsInRoot()
        assertTrue("add width=${add.right - add.left}", add.right - add.left >= 48.dp)
        assertTrue("add height=${add.bottom - add.top}", add.bottom - add.top >= 48.dp)
        assertTrue("row height=${row.bottom - row.top}", row.bottom - row.top >= 48.dp)
        assertTrue("row should not overflow 360dp: $row", row.right <= 360.dp + 8.dp)
        assertTrue(overflow.right - overflow.left >= 48.dp)
        assertTrue(overflow.bottom - overflow.top >= 48.dp)
        assertTrue(active.bottom - active.top >= 48.dp)
        assertTrue("filter should not overflow: $active", active.right <= 360.dp + 8.dp)
        composeRule.onAllNodesWithText("Gyakorlatok", substring = false).assertCountEquals(1)
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
        source: List<Exercise>,
        query: String = "",
        width: Dp = 360.dp,
        fontScale: Float = 1f
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            var currentQuery by remember { mutableStateOf(query) }
            var archiveFilter by remember { mutableStateOf(ArchiveFilter.ACTIVE) }
            val visible = ExerciseCatalogLogic.filter(
                exercises = source,
                query = currentQuery,
                category = null,
                muscle = null,
                archiveFilter = archiveFilter
            )
            val emptyKind = when {
                visible.isNotEmpty() -> null
                ExerciseCatalogLogic.hasSearchQuery(currentQuery) -> CatalogEmptyKind.Search
                archiveFilter == ArchiveFilter.ARCHIVED -> CatalogEmptyKind.Archived
                else -> CatalogEmptyKind.Active
            }
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(width).fillMaxSize()) {
                        ExerciseListScreen(
                            state = ExerciseListUiState(
                                loading = false,
                                visibleExercises = visible,
                                query = currentQuery,
                                archiveFilter = archiveFilter,
                                emptyKind = emptyKind
                            ),
                            onBack = {},
                            onAdd = {},
                            onEdit = {},
                            onQueryChange = { currentQuery = it },
                            onCategoryFilter = {},
                            onMuscleFilter = {},
                            onArchiveFilter = { archiveFilter = it },
                            onClearFilters = {
                                currentQuery = ""
                                archiveFilter = ArchiveFilter.ACTIVE
                            },
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
        exercises: List<Exercise>,
        archiveFilter: ArchiveFilter = ArchiveFilter.ACTIVE,
        emptyKind: CatalogEmptyKind? = if (exercises.isEmpty()) CatalogEmptyKind.Active else null,
        width: Dp = 360.dp,
        fontScale: Float = 1f,
        onEdit: (Long) -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(width).fillMaxSize()) {
                        ExerciseListScreen(
                            state = ExerciseListUiState(
                                loading = false,
                                visibleExercises = exercises,
                                archiveFilter = archiveFilter,
                                emptyKind = emptyKind
                            ),
                            onBack = {},
                            onAdd = {},
                            onEdit = onEdit,
                            onQueryChange = {},
                            onCategoryFilter = {},
                            onMuscleFilter = {},
                            onArchiveFilter = {},
                            onClearFilters = {},
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
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        primary: MuscleGroup = MuscleGroup.LATS,
        secondary: List<MuscleGroup> = listOf(MuscleGroup.BICEPS),
        archived: Boolean = false
    ): Exercise {
        return Exercise(
            id = id,
            name = name,
            normalizedName = ExerciseNaming.normalize(name),
            category = category,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = primary,
            secondaryMuscles = secondary,
            notes = null,
            archived = archived,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
