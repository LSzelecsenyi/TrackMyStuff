package hu.laca.weighttracker.ui.workout

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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.workout.ActiveSessionSummary
import hu.laca.weighttracker.domain.workout.BodyWeightProposal
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.SessionProgress
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.domain.workout.TemplateMuscleSummary
import hu.laca.weighttracker.domain.workout.WorkoutSession
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import hu.laca.weighttracker.domain.workout.WorkoutTemplate
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview
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
class WorkoutHubScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun givenNoActiveSessionWhenHubOpensThenCompactStartListIsVisible() {
        render(
            state = WorkoutHubUiState(
                loading = false,
                templates = listOf(templateItem(1, "Push – Kondipark", 3, 8)),
                activeTemplateCount = 1,
                activeCount = 4
            )
        )
        composeRule.onNodeWithTag(HUB_START_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Push – Kondipark").assertIsDisplayed()
        composeRule.onNodeWithText("3 gyakorlat · 8 sorozat").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Push – Kondipark edzés indítása").assertIsDisplayed()
        composeRule.onNodeWithTag(HUB_ACTIVE).assertDoesNotExist()
        composeRule.onAllNodesWithText("Edzéstervek kezelése").assertCountEquals(0)
        composeRule.onAllNodesWithText("Gyakorlatok kezelése").assertCountEquals(0)
        composeRule.onAllNodesWithText(
            "Indíts edzést egy tervből, vagy kezeld az edzésterveket és a gyakorlatkatalógust."
        ).assertCountEquals(0)
    }

    @Test
    fun givenActiveSessionWhenHubOpensThenInProgressRowIsFirstWithContinueAction() {
        render(
            state = WorkoutHubUiState(
                loading = false,
                templates = listOf(templateItem(1, "Push – Kondipark", 3, 8)),
                activeSession = activeSummary(completed = 2, total = 8, skipped = 1),
                activeTemplateCount = 1
            )
        )
        val active = composeRule.onNodeWithTag(HUB_ACTIVE).getBoundsInRoot()
        val start = composeRule.onNodeWithTag(HUB_START_SECTION).getBoundsInRoot()
        assertTrue(active.top < start.top)
        composeRule.onNodeWithText("FOLYAMATBAN").assertIsDisplayed()
        composeRule.onNodeWithText("Push – Kondipark").assertIsDisplayed()
        composeRule.onNodeWithText("2 / 8 sorozat", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(HUB_RESUME).assertIsDisplayed()
        composeRule.onNodeWithText("Előbb folytasd vagy zárd le a folyamatban lévő edzést.").assertIsDisplayed()
        composeRule.onNodeWithTag(hubStartPlayTag(1)).assertDoesNotExist()
    }

    @Test
    fun givenATemplateWhenStartIconIsTappedThenExactlyOneStartSheetOpens() {
        var starts = 0
        renderInteractive(
            templates = listOf(templateItem(4, "Push – Kondipark", 3, 8)),
            onStartTemplate = { starts += 1 }
        )
        composeRule.onNodeWithTag(hubStartPlayTag(4)).performClick()
        composeRule.onNodeWithTag(hubStartPlayTag(4)).performClick()
        assertEquals(1, starts)
        composeRule.onNodeWithTag(HUB_START_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("3 gyakorlat · 8 tervezett sorozat").assertIsDisplayed()
        composeRule.onNodeWithTag(HUB_START_CONFIRM).assertIsDisplayed()
    }

    @Test
    fun givenStartInProgressWhenConfirmIsTappedAgainThenCallbackIsNotFired() {
        var confirms = 0
        render(
            state = WorkoutHubUiState(
                loading = false,
                startDraft = startDraft(),
                isStarting = true
            ),
            onConfirmStart = { confirms += 1 }
        )
        composeRule.onNodeWithTag(HUB_START_CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(HUB_START_CONFIRM).performClick()
        assertEquals(0, confirms)
    }

    @Test
    fun givenSameDayWeightWhenSheetOpensThenValueIsPrefilled() {
        render(
            state = WorkoutHubUiState(
                loading = false,
                startDraft = startDraft(weight = "82,4")
            )
        )
        composeRule.onNodeWithTag(HUB_START_WEIGHT).assertIsDisplayed()
        composeRule.onNodeWithText("82,4").assertIsDisplayed()
        composeRule.onNodeWithText("A mai mérésből: 82,4 kg").assertIsDisplayed()
    }

    @Test
    fun givenUserCancelsStartWhenSheetClosesThenDismissIsInvokedWithoutConfirm() {
        var dismissed = 0
        var confirmed = 0
        render(
            state = WorkoutHubUiState(
                loading = false,
                startDraft = startDraft()
            ),
            onDismissStart = { dismissed += 1 },
            onConfirmStart = { confirmed += 1 }
        )
        composeRule.onNodeWithTag(HUB_START_CANCEL).performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
    }

    @Test
    fun givenRecentWorkoutWhenDetailsAreTappedThenDetailOpensOnce() {
        var opened = 0
        render(
            state = WorkoutHubUiState(
                loading = false,
                recentCompleted = recentSummary()
            ),
            onOpenRecent = { opened += 1 }
        )
        composeRule.onNodeWithTag(HUB_RECENT).performClick()
        composeRule.onNodeWithTag(HUB_RECENT).performClick()
        assertEquals(1, opened)
        composeRule.onAllNodesWithText("Részletek").assertCountEquals(0)
    }

    @Test
    fun givenManageSectionWhenRowsAreTappedThenMatchingScreensOpenOnce() {
        var templates = 0
        var catalog = 0
        render(
            state = WorkoutHubUiState(
                loading = false,
                activeTemplateCount = 2,
                activeCount = 5
            ),
            onOpenTemplates = { templates += 1 },
            onOpenCatalog = { catalog += 1 }
        )
        composeRule.onNodeWithText("2 aktív edzésterv").assertIsDisplayed()
        composeRule.onNodeWithText("5 aktív gyakorlat").assertIsDisplayed()
        composeRule.onNodeWithTag(HUB_MANAGE_TEMPLATES).performClick()
        composeRule.onNodeWithTag(HUB_MANAGE_TEMPLATES).performClick()
        composeRule.onNodeWithTag(HUB_MANAGE_EXERCISES).performClick()
        composeRule.onNodeWithTag(HUB_MANAGE_EXERCISES).performClick()
        assertEquals(1, templates)
        assertEquals(1, catalog)
    }

    @Test
    fun givenFontScale13AndNarrowPhoneWhenHubAppearsThenTouchTargetsStay48DpWithoutOverflow() {
        render(
            state = WorkoutHubUiState(
                loading = false,
                templates = listOf(templateItem(9, "Nagyon hosszú edzéstervnév keskeny telefonra", 2, 4)),
                recentCompleted = recentSummary(name = "Hosszú befejezett edzésnév"),
                activeTemplateCount = 1,
                activeCount = 3
            ),
            width = 360.dp,
            fontScale = 1.3f
        )
        val play = composeRule.onNodeWithTag(hubStartPlayTag(9)).getBoundsInRoot()
        val recent = composeRule.onNodeWithTag(HUB_RECENT).performScrollTo().getBoundsInRoot()
        val templates = composeRule.onNodeWithTag(HUB_MANAGE_TEMPLATES).performScrollTo().getBoundsInRoot()
        val exercises = composeRule.onNodeWithTag(HUB_MANAGE_EXERCISES).getBoundsInRoot()
        val settings = composeRule.onNodeWithTag(HUB_SETTINGS).getBoundsInRoot()
        assertTrue("play ${play.right - play.left}", play.right - play.left >= 48.dp)
        assertTrue(play.bottom - play.top >= 48.dp)
        assertTrue(recent.bottom - recent.top >= 48.dp)
        assertTrue(templates.bottom - templates.top >= 48.dp)
        assertTrue(exercises.bottom - exercises.top >= 48.dp)
        assertTrue(settings.right - settings.left >= 48.dp)
        assertTrue("row overflow $play", play.right <= 360.dp + 8.dp)
        assertTrue(recent.right <= 360.dp + 8.dp)
        assertTrue(templates.right <= 360.dp + 8.dp)
        composeRule.onAllNodesWithText("Edzéstervek kezelése").assertCountEquals(0)
        composeRule.onAllNodesWithText("Gyakorlatok kezelése").assertCountEquals(0)
    }

    @Test
    fun givenActiveSessionWhenResumeIsTappedTwiceThenItOpensOnce() {
        var resumes = 0
        render(
            state = WorkoutHubUiState(
                loading = false,
                activeSession = activeSummary()
            ),
            onResume = { resumes += 1 }
        )
        composeRule.onNodeWithTag(HUB_ACTIVE).performClick()
        composeRule.onNodeWithTag(HUB_RESUME).performClick()
        assertEquals(1, resumes)
    }

    private fun renderInteractive(
        templates: List<TemplateListItem>,
        onStartTemplate: (TemplateListItem) -> Unit
    ) {
        composeRule.setContent {
            var draft by remember { mutableStateOf<StartWorkoutDraft?>(null) }
            WeightTrackerThemeForPreview {
                Box(modifier = Modifier.width(360.dp).fillMaxSize()) {
                    WorkoutHubScreen(
                        state = WorkoutHubUiState(
                            loading = false,
                            templates = templates,
                            startDraft = draft,
                            activeTemplateCount = templates.size
                        ),
                        onOpenSettings = {},
                        onOpenTemplates = {},
                        onOpenCatalog = {},
                        onStartTemplate = { item ->
                            onStartTemplate(item)
                            if (draft == null) {
                                draft = startDraft(item)
                            }
                        },
                        onResume = {},
                        onDismissStart = { draft = null },
                        onStartWeightChange = {},
                        onConfirmStart = {},
                        onStartedConsumed = {},
                        onOpenStarted = {},
                        onMessageConsumed = {},
                        onOpenRecent = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun render(
        state: WorkoutHubUiState,
        width: Dp = 360.dp,
        fontScale: Float = 1f,
        onOpenTemplates: () -> Unit = {},
        onOpenCatalog: () -> Unit = {},
        onStartTemplate: (TemplateListItem) -> Unit = {},
        onResume: (Long) -> Unit = {},
        onDismissStart: () -> Unit = {},
        onConfirmStart: () -> Unit = {},
        onOpenRecent: (Long) -> Unit = {}
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                WeightTrackerThemeForPreview {
                    Box(modifier = Modifier.width(width).fillMaxSize()) {
                        WorkoutHubScreen(
                            state = state,
                            onOpenSettings = {},
                            onOpenTemplates = onOpenTemplates,
                            onOpenCatalog = onOpenCatalog,
                            onStartTemplate = onStartTemplate,
                            onResume = onResume,
                            onDismissStart = onDismissStart,
                            onStartWeightChange = {},
                            onConfirmStart = onConfirmStart,
                            onStartedConsumed = {},
                            onOpenStarted = {},
                            onMessageConsumed = {},
                            onOpenRecent = onOpenRecent
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

    private fun startDraft(
        item: TemplateListItem = templateItem(4, "Push – Kondipark", 3, 8),
        weight: String = "82,4"
    ): StartWorkoutDraft {
        return StartWorkoutDraft(
            template = item,
            proposal = BodyWeightProposal(
                kilograms = 82.4,
                source = BodyWeightSource.MEASURED_SAME_DAY,
                sourceDate = LocalDate.of(2026, 9, 15)
            ),
            weightText = weight,
            editedManually = false
        )
    }

    private fun activeSummary(
        completed: Int = 2,
        total: Int = 8,
        skipped: Int = 0
    ): ActiveSessionSummary {
        return ActiveSessionSummary(
            session = session(id = 10, name = "Push – Kondipark", status = SessionStatus.IN_PROGRESS),
            completedSets = completed,
            skippedSets = skipped,
            pendingSets = total - completed - skipped,
            totalSets = total,
            currentExerciseName = "Húzódzkodás",
            currentExercisePosition = 1,
            exerciseCount = 3
        )
    }

    private fun recentSummary(name: String = "Push – Kondipark"): WorkoutSessionSummary {
        return WorkoutSessionSummary(
            session = session(id = 11, name = name, status = SessionStatus.COMPLETED),
            progress = SessionProgress(completed = 6, skipped = 1, pending = 1, total = 8),
            exerciseCount = 3,
            primaryMuscles = emptyList(),
            durationMillis = 42 * 60_000L
        )
    }

    private fun session(
        id: Long,
        name: String,
        status: SessionStatus
    ): WorkoutSession {
        return WorkoutSession(
            id = id,
            templateId = 1L,
            templateName = name,
            status = status,
            workoutDate = LocalDate.of(2026, 9, 15),
            startedAt = System.currentTimeMillis() - 12 * 60_000L,
            finishedAt = if (status == SessionStatus.COMPLETED) System.currentTimeMillis() else null,
            abandonedAt = null,
            notes = null,
            bodyWeightKg = 82.4,
            bodyWeightSource = BodyWeightSource.MEASURED_SAME_DAY,
            bodyWeightSourceDate = LocalDate.of(2026, 9, 15),
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
