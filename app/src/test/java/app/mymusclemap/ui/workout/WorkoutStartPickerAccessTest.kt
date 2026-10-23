package app.mymusclemap.ui.workout

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import app.mymusclemap.domain.entitlement.FreeAccessSubject
import app.mymusclemap.domain.workout.TemplateListItem
import app.mymusclemap.domain.workout.TemplateMuscleSummary
import app.mymusclemap.domain.workout.WorkoutTemplate
import app.mymusclemap.testString
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
class WorkoutStartPickerAccessTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freeShowsAccessiblePlansBeforeACollapsedLockedSection() {
        val plans = (1L..5L).map { plan(it, "Plan $it", exercises = it.toInt(), sets = it.toInt() * 2) }
        val decision = decide(FreeAccessPhase.Confirmed, plans.map { it.template.id }.take(3).toSet(), plans)
        val available = plans.filter { decision.planAccessible(it.template.id) }
        val locked = plans.filter { !decision.planAccessible(it.template.id) }
        var started: Long? = null
        var explored = 0
        render(available = available, locked = locked, onSelect = { started = it.template.id }, onExplore = { explored += 1 })

        val firstAvailable = composeRule.onNodeWithTag(startPickerRowTag(1)).getBoundsInRoot()
        val header = composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).getBoundsInRoot()
        assertTrue(firstAvailable.top < header.top)
        composeRule.onNodeWithText(quantity(2)).assertIsDisplayed()
        composeRule.onNodeWithText(testString(R.string.start_picker_locked_support)).assertIsDisplayed()
        composeRule.onNodeWithTag(START_PICKER_EXPLORE).assertIsDisplayed()
        composeRule.onNodeWithTag(startPickerLockedRowTag(4)).assertDoesNotExist()
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                testString(R.string.start_picker_locked_collapsed)
            )
        )
        assertTrue(header.bottom - header.top >= 48.dp)
        assertTrue(
            composeRule.onNodeWithTag(START_PICKER_EXPLORE).getBoundsInRoot().bottom -
                composeRule.onNodeWithTag(START_PICKER_EXPLORE).getBoundsInRoot().top >= 48.dp
        )

        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).performClick()
        val lockedRow = composeRule.onNodeWithTag(startPickerLockedRowTag(4))
        lockedRow.assertIsDisplayed().assertHasNoClickAction()
        composeRule.onAllNodesWithContentDescription(testString(R.string.free_locked_title)).assertCountEquals(2)
        val expandedHeader = composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).getBoundsInRoot()
        val fourth = composeRule.onNodeWithTag(startPickerLockedRowTag(4)).getBoundsInRoot()
        val fifth = composeRule.onNodeWithTag(startPickerLockedRowTag(5)).getBoundsInRoot()
        assertTrue(expandedHeader.top < fourth.top)
        assertTrue(fourth.top < fifth.top)
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                testString(R.string.start_picker_locked_expanded)
            )
        )
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).performClick()
        composeRule.onNodeWithTag(startPickerLockedRowTag(4)).assertDoesNotExist()

        composeRule.onNodeWithTag(startPickerRowTag(2)).performClick()
        assertEquals(2L, started)
        composeRule.onNodeWithTag(START_PICKER_EXPLORE).performClick()
        assertEquals(1, explored)
    }

    @Test
    fun proHidesTheLockedSectionWhenEveryPlanIsAccessible() {
        val plans = listOf(plan(1, "Plan 1"), plan(2, "Plan 2"), plan(3, "Plan 3"))
        val decision = FreeAccessLogic.decide(
            state = FreeAccessState(2L, false, FreeAccessPhase.Confirmed, selectedPlanIds = setOf(1L)),
            grantsPro = true,
            plans = plans.map { FreeAccessSubject(it.template.id, it.template.id) },
            customExercises = emptyList()
        )
        render(
            available = plans.filter { decision.planAccessible(it.template.id) },
            locked = plans.filter { !decision.planAccessible(it.template.id) }
        )
        composeRule.onNodeWithTag(startPickerRowTag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).assertDoesNotExist()
        composeRule.onNodeWithText(testString(R.string.start_picker_locked_support)).assertDoesNotExist()
    }

    @Test
    fun requiredSelectionShowsEveryPlanAsLocked() {
        val plans = (1L..4L).map { plan(it, "Plan $it") }
        val decision = decide(FreeAccessPhase.Required, setOf(1L, 2L, 3L), plans)
        var started = 0
        render(
            available = plans.filter { decision.planAccessible(it.template.id) },
            locked = plans.filter { !decision.planAccessible(it.template.id) },
            onSelect = { started += 1 }
        )
        composeRule.onNodeWithTag(START_PICKER_EMPTY).assertDoesNotExist()
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag(startPickerRowTag(1)).assertDoesNotExist()
        assertEquals(0, started)
    }

    @Test
    fun entitlementChangesMovePlansBetweenSectionsAndResetTheLockedSection() {
        val open = plan(1, "Open")
        val closed = plan(2, "Closed")
        var available by mutableStateOf(listOf(open))
        var locked by mutableStateOf(listOf(closed))
        composeRule.setContent {
            WeightTrackerThemeForPreview {
                Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                    WorkoutStartPickerSheet(
                        templates = available,
                        lockedTemplates = locked,
                        starting = false,
                        onDismiss = {},
                        onSelectTemplate = {},
                        onManageTemplates = {},
                        onCreateTemplate = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).performClick()
        composeRule.onNodeWithTag(startPickerLockedRowTag(2)).assertIsDisplayed()
        available = listOf(open, closed)
        locked = emptyList()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).assertDoesNotExist()
        composeRule.onNodeWithTag(startPickerRowTag(2)).assertIsDisplayed().assertHasClickAction()
        available = listOf(open)
        locked = listOf(closed)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(startPickerLockedRowTag(2)).assertDoesNotExist()
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun aLongLockedListStaysScrollable() {
        val locked = (1L..12L).map { plan(it, "Locked $it") }
        render(available = listOf(plan(100, "Ready")), locked = locked)
        composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).performClick()
        composeRule.onNodeWithTag(startPickerLockedRowTag(12)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(startPickerRowTag(100)).assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h2000dp", fontScale = 1.6f)
    fun lockedHeaderStaysAtLeast48DpAtLargeFont() {
        render(available = emptyList(), locked = listOf(plan(1, "A very long locked workout plan name")))
        val header = composeRule.onNodeWithTag(START_PICKER_LOCKED_HEADER).getBoundsInRoot()
        val explore = composeRule.onNodeWithTag(START_PICKER_EXPLORE).getBoundsInRoot()
        assertTrue(header.bottom - header.top >= 48.dp)
        assertTrue(explore.bottom - explore.top >= 48.dp)
        assertTrue(header.right <= 360.dp + 8.dp)
    }

    @Test
    @Config(qualifiers = "hu-w360dp-h2000dp")
    fun hungarianLockedCopyIsShown() {
        render(available = listOf(plan(1, "Nyitó")), locked = List(10) { plan((it + 2).toLong(), "Zárt $it") })
        composeRule.onNodeWithText("10 zárolt edzésterv").assertIsDisplayed()
        composeRule.onNodeWithText("Mentve maradnak, és STRICT PRO-val újra használhatók.").assertIsDisplayed()
        composeRule.onNodeWithText("PRO előnyök megtekintése").assertIsDisplayed()
    }

    private fun render(
        available: List<TemplateListItem>,
        locked: List<TemplateListItem>,
        fontScale: Float = 1f,
        onSelect: (TemplateListItem) -> Unit = {},
        onExplore: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                        WorkoutStartPickerSheet(
                            templates = available,
                            lockedTemplates = locked,
                            starting = false,
                            onDismiss = {},
                            onSelectTemplate = onSelect,
                            onManageTemplates = {},
                            onCreateTemplate = {},
                            onExplorePro = onExplore
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun decide(
        phase: FreeAccessPhase,
        selected: Set<Long>,
        plans: List<TemplateListItem>
    ) = FreeAccessLogic.decide(
        state = FreeAccessState(2L, false, phase, selectedPlanIds = selected),
        grantsPro = false,
        plans = plans.map { FreeAccessSubject(it.template.id, it.template.id) },
        customExercises = emptyList()
    )

    private fun quantity(count: Int): String {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        return resources.getQuantityString(R.plurals.start_picker_locked_count, count, count)
    }

    private fun plan(id: Long, name: String, exercises: Int = 2, sets: Int = 4): TemplateListItem {
        return TemplateListItem(
            template = WorkoutTemplate(
                id = id,
                name = name,
                normalizedName = name.lowercase(),
                notes = null,
                archived = false,
                createdAt = id,
                updatedAt = id
            ),
            exerciseCount = exercises,
            setCount = sets,
            primaryMuscles = emptyList(),
            muscleSummary = TemplateMuscleSummary(emptyList(), emptyList())
        )
    }
}
