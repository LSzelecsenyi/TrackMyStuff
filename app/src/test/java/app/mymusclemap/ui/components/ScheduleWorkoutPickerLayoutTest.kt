package app.mymusclemap.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.domain.workout.TemplateListItem
import app.mymusclemap.domain.workout.TemplateMuscleSummary
import app.mymusclemap.domain.workout.WorkoutTemplate
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2000dp")
class ScheduleWorkoutPickerLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun givenActiveTemplatesWhenPickerOpensThenRowsAreFullWidthAndDoubleTapSchedulesOnce() {
        var selected = 0L
        render(
            templates = listOf(
                templateItem(2, "Alma"),
                templateItem(1, "Záró")
            ),
            onSelect = { selected = it }
        )
        composeRule.onNodeWithTag(SCHEDULE_PICKER_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("Edzés ütemezése").assertIsDisplayed()
        val alma = composeRule.onNodeWithTag(schedulePickerRowTag(2)).getBoundsInRoot()
        val zaro = composeRule.onNodeWithTag(schedulePickerRowTag(1)).getBoundsInRoot()
        assertTrue(alma.top < zaro.top)
        assertTrue(alma.bottom - alma.top >= 48.dp)
        assertTrue(alma.right - alma.left >= 300.dp)
        composeRule.onNodeWithTag(schedulePickerRowTag(2)).performClick()
        composeRule.onNodeWithTag(schedulePickerRowTag(2)).performClick()
        assertEquals(2L, selected)
    }

    @Test
    fun givenEmptyTemplatesWhenPickerOpensThenCreateActionIsVisible() {
        var created = 0
        render(templates = emptyList(), onCreate = { created += 1 })
        composeRule.onNodeWithTag(SCHEDULE_PICKER_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("Nincs ütemezhető edzésterv.").assertIsDisplayed()
        composeRule.onNodeWithTag(SCHEDULE_PICKER_CREATE).performClick()
        composeRule.onNodeWithTag(SCHEDULE_PICKER_CREATE).performClick()
        assertEquals(1, created)
    }

    @Test
    fun givenFontScale13WhenPickerAppearsThenRowsStay48DpWithoutOverflow() {
        render(
            templates = listOf(templateItem(9, "Nagyon hosszú edzéstervnév keskeny telefonra")),
            fontScale = 1.3f
        )
        val row = composeRule.onNodeWithTag(schedulePickerRowTag(9)).getBoundsInRoot()
        assertTrue(row.bottom - row.top >= 48.dp)
        assertTrue(row.right <= 360.dp + 8.dp)
    }

    private fun render(
        templates: List<TemplateListItem>,
        fontScale: Float = 1f,
        onSelect: (Long) -> Unit = {},
        onCreate: () -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                        ScheduleWorkoutPickerSheet(
                            date = today,
                            templates = templates,
                            busy = false,
                            errorText = null,
                            onDismiss = {},
                            onSelectTemplate = { onSelect(it.template.id) },
                            onCreateTemplate = onCreate
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun templateItem(id: Long, name: String): TemplateListItem {
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
            exerciseCount = 2,
            setCount = 6,
            primaryMuscles = emptyList(),
            muscleSummary = TemplateMuscleSummary(emptyList(), emptyList())
        )
    }
}
