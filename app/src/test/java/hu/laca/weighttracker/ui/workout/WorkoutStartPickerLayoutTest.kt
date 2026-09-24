package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.workout.ScheduledWorkout
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.domain.workout.TemplateMuscleSummary
import hu.laca.weighttracker.domain.workout.WorkoutTemplate
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class WorkoutStartPickerLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenActiveTemplatesWhenPickerOpensThenRowsAreFullWidthSortedAndHaveNoPlayButtons() {
        render(
            templates = listOf(
                templateItem(2, "Alma", 2, 6),
                templateItem(1, "Álló evezés", 4, 14),
                templateItem(3, "Záró", 1, 2)
            )
        )
        composeRule.onNodeWithTag(START_PICKER_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(START_PICKER_TITLE).assertIsDisplayed()
        val alma = composeRule.onNodeWithTag(startPickerRowTag(2)).getBoundsInRoot()
        val allo = composeRule.onNodeWithTag(startPickerRowTag(1)).getBoundsInRoot()
        val zaro = composeRule.onNodeWithTag(startPickerRowTag(3)).getBoundsInRoot()
        assertTrue(alma.top < allo.top)
        assertTrue(allo.top < zaro.top)
        composeRule.onNodeWithText("4 gyakorlat · 14 sorozat").assertIsDisplayed()
        assertTrue("row height ${alma.bottom - alma.top}", alma.bottom - alma.top >= 48.dp)
        assertTrue("row width ${alma.right - alma.left}", alma.right - alma.left >= 300.dp)
        composeRule.onNodeWithText("Edzéstervek kezelése").assertIsDisplayed()
    }

    @Test
    fun givenATemplateWhenTheRowIsTappedThenNoBodyWeightFieldOrDialogAppears() {
        render(templates = listOf(templateItem(4, "Push – Kondipark", 3, 8)))
        composeRule.onNodeWithTag(startPickerRowTag(4)).performClick()
        composeRule.onNodeWithTag(START_PICKER_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("Testsúly").assertDoesNotExist()
        composeRule.onNodeWithText("A testsúly nem kötelező. Üresen hagyva az edzés testsúly nélkül indul.")
            .assertDoesNotExist()
        composeRule.onAllNodesWithTag("workout-hub-start-weight").assertCountEquals(0)
        composeRule.onAllNodesWithTag("workout-hub-start-sheet").assertCountEquals(0)
    }

    @Test
    fun givenATemplateWhenTheRowIsTappedTwiceThenStartRunsOnce() {
        var starts = 0
        render(
            templates = listOf(templateItem(4, "Push – Kondipark", 3, 8)),
            onSelect = { starts += 1 }
        )
        composeRule.onNodeWithTag(startPickerRowTag(4)).performClick()
        composeRule.onNodeWithTag(startPickerRowTag(4)).performClick()
        assertEquals(1, starts)
        composeRule.onNodeWithTag(START_PICKER_SHEET).assertIsDisplayed()
    }

    @Test
    fun givenNoTemplatesWhenPickerOpensThenEmptyStateAndCreateActionAreVisible() {
        var created = 0
        render(
            templates = emptyList(),
            onCreate = { created += 1 }
        )
        composeRule.onNodeWithTag(START_PICKER_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Még nincs edzésterved.").assertIsDisplayed()
        composeRule.onNodeWithTag(START_PICKER_CREATE).assertIsDisplayed()
        composeRule.onNodeWithText("Edzésterv létrehozása").performClick()
        composeRule.onNodeWithText("Edzésterv létrehozása").performClick()
        assertEquals(1, created)
        composeRule.onAllNodesWithText("Edzéstervek kezelése").assertCountEquals(0)
    }

    @Test
    fun givenManageActionWhenTappedTwiceThenItFiresOnce() {
        var managed = 0
        render(
            templates = listOf(templateItem(1, "Push", 1, 2)),
            onManage = { managed += 1 }
        )
        composeRule.onNodeWithTag(START_PICKER_MANAGE).performClick()
        composeRule.onNodeWithTag(START_PICKER_MANAGE).performClick()
        assertEquals(1, managed)
    }

    @Test
    fun givenTodaySchedulesWhenPickerOpensThenTheySitAboveTemplatesWithoutDuplication() {
        render(
            templates = listOf(templateItem(3, "Záró", 1, 2)),
            todayPlanned = listOf(scheduled(10, "Push A")),
            todayInProgress = listOf(scheduled(11, "Pull A", inProgress = true))
        )
        composeRule.onNodeWithTag(START_PICKER_TODAY_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("MÁRA TERVEZVE").assertIsDisplayed()
        val planned = composeRule.onNodeWithTag(startPickerScheduledRowTag(10)).getBoundsInRoot()
        val inProgress = composeRule.onNodeWithTag(startPickerScheduledRowTag(11)).getBoundsInRoot()
        val remaining = composeRule.onNodeWithTag(startPickerRowTag(3)).getBoundsInRoot()
        assertTrue(planned.top < inProgress.top)
        assertTrue(inProgress.top < remaining.top)
        composeRule.onNodeWithTag(startPickerScheduledRowTag(10)).assertIsDisplayed()
        composeRule.onNodeWithText("Folyamatban").assertIsDisplayed()
        composeRule.onNodeWithText("Folytatás").assertIsDisplayed()
        composeRule.onAllNodesWithText("Push A").assertCountEquals(1)
        composeRule.onAllNodesWithTag(START_PICKER_TODAY_SECTION).assertCountEquals(1)
        assertTrue(planned.bottom - planned.top >= 48.dp)
    }

    @Test
    fun givenNoTodaySchedulesWhenPickerOpensThenTodayHeaderIsAbsent() {
        render(templates = listOf(templateItem(4, "Push – Kondipark", 3, 8)))
        composeRule.onAllNodesWithTag(START_PICKER_TODAY_SECTION).assertCountEquals(0)
        composeRule.onAllNodesWithText("MÁRA TERVEZVE").assertCountEquals(0)
        composeRule.onNodeWithTag(startPickerRowTag(4)).assertIsDisplayed()
    }

    @Test
    fun givenFontScale13WhenPickerAppearsThenRowsStay48DpWithoutOverflow() {
        render(
            templates = listOf(templateItem(9, "Nagyon hosszú edzéstervnév keskeny telefonra", 2, 4)),
            fontScale = 1.3f
        )
        val row = composeRule.onNodeWithTag(startPickerRowTag(9)).getBoundsInRoot()
        val manage = composeRule.onNodeWithTag(START_PICKER_MANAGE).getBoundsInRoot()
        assertTrue(row.bottom - row.top >= 48.dp)
        assertTrue(manage.bottom - manage.top >= 48.dp)
        assertTrue("row overflow $row", row.right <= 360.dp + 8.dp)
        assertTrue(manage.right <= 360.dp + 8.dp)
    }

    private fun render(
        templates: List<TemplateListItem>,
        fontScale: Float = 1f,
        onManage: () -> Unit = {},
        onCreate: () -> Unit = {},
        onSelect: (TemplateListItem) -> Unit = {},
        todayPlanned: List<ScheduledWorkout> = emptyList(),
        todayInProgress: List<ScheduledWorkout> = emptyList()
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                        WorkoutStartPickerSheet(
                            templates = templates,
                            starting = false,
                            onDismiss = {},
                            onSelectTemplate = onSelect,
                            onManageTemplates = onManage,
                            onCreateTemplate = onCreate,
                            todayPlanned = todayPlanned,
                            todayInProgress = todayInProgress
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun templateItem(
        id: Long,
        name: String,
        exercises: Int,
        sets: Int
    ): TemplateListItem {
        return TemplateListItem(
            template = WorkoutTemplate(
                id = id,
                name = name,
                normalizedName = name.lowercase(),
                notes = null,
                archived = false,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exerciseCount = exercises,
            setCount = sets,
            primaryMuscles = emptyList(),
            muscleSummary = TemplateMuscleSummary(emptyList(), emptyList())
        )
    }

    private fun scheduled(
        id: Long,
        name: String,
        inProgress: Boolean = false
    ): ScheduledWorkout {
        return ScheduledWorkout(
            id = id,
            scheduledDate = java.time.LocalDate.of(2026, 9, 15),
            templateId = id,
            templateName = name,
            exerciseCount = 2,
            plannedSetCount = 6,
            templateArchived = false,
            sessionId = if (inProgress) id + 100 else null,
            sessionStatus = if (inProgress) SessionStatus.IN_PROGRESS else null,
            createdAt = id
        )
    }
}
