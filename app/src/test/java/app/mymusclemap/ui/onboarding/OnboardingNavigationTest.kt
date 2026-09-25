package app.mymusclemap.ui.onboarding

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.WeightViewModelFactory
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.data.workoutimport.ContentWorkoutImportFileReader
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.ui.navigation.BOTTOM_WORKOUT_ACTION
import app.mymusclemap.ui.navigation.WeightTrackerNavHost
import app.mymusclemap.ui.onboarding.ONBOARDING_REMINDER
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import app.mymusclemap.ui.workout.START_PICKER_SHEET
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class OnboardingNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var coordinator: FirstRunCoordinator
    private lateinit var factory: WeightViewModelFactory
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var workoutTemplateRepository: WorkoutTemplateRepository
    private lateinit var workoutSessionRepository: WorkoutSessionRepository
    private val dateProvider = FixedDateProvider(LocalDate.of(2026, 9, 25), LocalTime.of(8, 0))
    private val backDispatcher = AtomicReference<OnBackPressedDispatcher?>(null)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-09-25T08:00:00Z"), ZoneOffset.UTC)
        exerciseRepository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = clock,
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao()
        )
        workoutTemplateRepository = WorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            clock = clock,
            sessionDao = database.workoutSessionDao(),
            scheduledWorkoutDao = database.scheduledWorkoutDao()
        )
        val scheduledWorkoutRepository = ScheduledWorkoutRepository(
            scheduledWorkoutDao = database.scheduledWorkoutDao(),
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao(),
            clock = clock
        )
        val weightRepository = WeightRepository(database.weightMeasurementDao(), clock)
        workoutSessionRepository = WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = weightRepository,
            clock = clock,
            dateProvider = dateProvider
        )
        themePreferences = ThemePreferences(context)
        runBlocking { themePreferences.clearOnboardingProgress() }
        coordinator = FirstRunCoordinator(database, exerciseRepository, themePreferences)
        factory = WeightViewModelFactory(
            weightRepository = weightRepository,
            exerciseRepository = exerciseRepository,
            workoutTemplateRepository = workoutTemplateRepository,
            workoutSessionRepository = workoutSessionRepository,
            scheduledWorkoutRepository = scheduledWorkoutRepository,
            dateProvider = dateProvider,
            themePreferences = themePreferences,
            workoutImportFileReader = ContentWorkoutImportFileReader(context),
            appBackupRepository = AppBackupRepository(database, themePreferences),
            firstRunCoordinator = coordinator,
            onboardingRepository = app.mymusclemap.data.repository.OnboardingRepository(
                themePreferences = themePreferences,
                sessionRepository = workoutSessionRepository,
                weightRepository = weightRepository,
                templateRepository = workoutTemplateRepository
            )
        )
    }

    @After
    fun tearDown() {
        runBlocking { themePreferences.clearOnboardingProgress() }
        database.close()
    }

    @Test
    fun completingOnboardingOpensExistingTemplateEditor() {
        val decision = runBlocking { coordinator.prepare() }
        assertEquals(FirstRunDecision.ShowOnboarding, decision)
        val onboardingViewModel = OnboardingViewModel(coordinator)
        composeRule.setContent {
            val storeOwner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                WeightTrackerThemeForPreview {
                    var showOnboarding by remember { mutableStateOf(true) }
                    var openNewTemplate by remember { mutableStateOf(false) }
                    val step by onboardingViewModel.step.collectAsStateWithLifecycle()
                    val exit by onboardingViewModel.exit.collectAsStateWithLifecycle()
                    LaunchedEffect(exit) {
                        when (exit) {
                            OnboardingExit.OpenTemplateEditor -> {
                                openNewTemplate = true
                                showOnboarding = false
                            }
                            OnboardingExit.Dismiss, null -> Unit
                        }
                    }
                    if (showOnboarding) {
                        OnboardingScreen(
                            step = step,
                            onContinue = onboardingViewModel::onContinue,
                            onCreatePlan = onboardingViewModel::onCreatePlan,
                            onSkip = onboardingViewModel::onSkip
                        )
                    } else {
                        WeightTrackerNavHost(
                            factory = factory,
                            dateProvider = dateProvider,
                            openNewTemplate = openNewTemplate,
                            onOpenedNewTemplate = { openNewTemplate = false }
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.onNodeWithText("Create workout plan").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("New workout plan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("New workout plan").assertIsDisplayed()
        composeRule.onNodeWithTag("template-save").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertDoesNotExist()
    }

    @Test
    fun skippingOnboardingOpensDashboardReminderWithoutBlockingTheApp() {
        val decision = runBlocking { coordinator.prepare() }
        assertEquals(FirstRunDecision.ShowOnboarding, decision)
        val onboardingViewModel = OnboardingViewModel(coordinator)
        composeRule.setContent {
            val storeOwner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                WeightTrackerThemeForPreview {
                    var showOnboarding by remember { mutableStateOf(true) }
                    val step by onboardingViewModel.step.collectAsStateWithLifecycle()
                    val exit by onboardingViewModel.exit.collectAsStateWithLifecycle()
                    LaunchedEffect(exit) {
                        if (exit == OnboardingExit.Dismiss) {
                            showOnboarding = false
                        }
                    }
                    if (showOnboarding) {
                        OnboardingScreen(
                            step = step,
                            onContinue = onboardingViewModel::onContinue,
                            onCreatePlan = onboardingViewModel::onCreatePlan,
                            onSkip = onboardingViewModel::onSkip
                        )
                    } else {
                        WeightTrackerNavHost(
                            factory = factory,
                            dateProvider = dateProvider,
                            openNewTemplate = false,
                            onOpenedNewTemplate = {}
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.onNodeWithTag(ONBOARDING_SKIP).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_REMINDER).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ONBOARDING_REMINDER).assertIsDisplayed()
        composeRule.onNodeWithText("Get started").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
    }

    @Test
    fun continueSetupWithoutPlanOpensPlanCreation() {
        startProgressiveOnboarding()
        composeApp()
        waitForTag(ONBOARDING_REMINDER)
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("New workout plan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("New workout plan").assertIsDisplayed()
        composeRule.onNodeWithTag("template-save").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).assertDoesNotExist()
    }

    @Test
    fun continueSetupWithPlanCoachesExistingWorkoutActionWithoutLeavingDashboard() {
        startProgressiveOnboarding()
        insertPlan()
        composeApp()
        waitForTag(ONBOARDING_REMINDER)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("●  Create your workout plan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("●  Create your workout plan").assertIsDisplayed()
        composeRule.onNodeWithText("○  Start your first workout").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
        composeRule.onNodeWithText("Workout plans").assertDoesNotExist()
        composeRule.onNodeWithText("New workout plan").assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).assertIsDisplayed()
        composeRule.onNodeWithText("Start your first workout").assertIsDisplayed()
        composeRule.onNodeWithText("Tap here when you're ready to train.").assertIsDisplayed()
        composeRule.onAllNodesWithTag(BOTTOM_WORKOUT_ACTION).fetchSemanticsNodes().let { nodes ->
            assertEquals(1, nodes.size)
        }
        composeRule.onNodeWithTag(BOTTOM_WORKOUT_ACTION).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(START_PICKER_SHEET).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(START_PICKER_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
    }

    @Test
    fun continueSetupAfterCompletedWorkoutRevealsHeatmapSpotlightOnDashboard() {
        startProgressiveOnboarding()
        completeFirstWorkout()
        composeApp()
        waitForTag(ONBOARDING_REMINDER)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("●  Start your first workout").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("●  Create your workout plan").assertIsDisplayed()
        composeRule.onNodeWithText("●  Start your first workout").assertIsDisplayed()
        composeRule.onNodeWithText("○  Discover your muscle map").assertIsDisplayed()
        composeRule.onNodeWithText("○  Track your body weight").assertIsDisplayed()
        composeRule.onNodeWithText("○  See your training history").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_COACH).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_COACH).assertIsDisplayed()
        composeRule.onNodeWithText("Discover your muscle map").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Here you can see which muscles you've worked and how recently."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
        composeRule.onNodeWithText("Muscle heatmap").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertDoesNotExist()
    }

    @Test
    fun acknowledgingHeatmapSpotlightPersistsAndDoesNotShowAgain() {
        startProgressiveOnboarding()
        completeFirstWorkout()
        composeApp()
        waitForTag(ONBOARDING_REMINDER)
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Got it").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_WEIGHT_SHEET).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("●  Discover your muscle map").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag(ONBOARDING_WEIGHT_SHEET).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(ONBOARDING_WEIGHT_SKIP).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("●  Discover your muscle map").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("●  Discover your muscle map").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_COACH).assertDoesNotExist()
    }

    @Test
    fun systemBackDismissesHeatmapSpotlightWithoutMarkingItSeen() {
        startProgressiveOnboarding()
        completeFirstWorkout()
        composeApp()
        waitForTag(ONBOARDING_REMINDER)
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            backDispatcher.get()!!.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("○  Discover your muscle map").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertIsDisplayed()
        composeRule.onNodeWithText("○  Discover your muscle map").assertExists()
    }

    @Test
    fun completedWorkoutDoesNotAutoShowHeatmapSpotlight() {
        startProgressiveOnboarding()
        completeFirstWorkout()
        composeApp()
        waitForTag(ONBOARDING_REMINDER)
        composeRule.onNodeWithText("○  Discover your muscle map").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_HEATMAP_COACH).assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_heatmap").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_COACH).assertDoesNotExist()
    }

    @Test
    fun systemBackDismissesWorkoutSpotlightAndStaysOnDashboard() {
        startProgressiveOnboarding()
        insertPlan()
        composeApp()
        waitForTag(ONBOARDING_REMINDER)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("●  Create your workout plan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ONBOARDING_REMINDER_CONTINUE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            backDispatcher.get()!!.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(ONBOARDING_WORKOUT_SPOTLIGHT).assertDoesNotExist()
        composeRule.onNodeWithTag(ONBOARDING_REMINDER).assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
        composeRule.onNodeWithText("New workout plan").assertDoesNotExist()
        composeRule.onNodeWithTag(START_PICKER_SHEET).assertDoesNotExist()
    }

    private fun startProgressiveOnboarding() {
        val decision = runBlocking { coordinator.prepare() }
        assertEquals(FirstRunDecision.ShowOnboarding, decision)
        runBlocking { coordinator.markOnboardingStarted() }
    }

    private fun composeApp() {
        composeRule.setContent {
            val storeOwner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                backDispatcher.set(LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher)
                WeightTrackerThemeForPreview {
                    WeightTrackerNavHost(
                        factory = factory,
                        dateProvider = dateProvider,
                        openNewTemplate = false,
                        onOpenedNewTemplate = {}
                    )
                }
            }
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun insertPlan(): Long = runBlocking {
        val exerciseId = (exerciseRepository.save(
            ExerciseDraft(
                name = "Onboarding Pull",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created).id
        val saved = workoutTemplateRepository.save(
            TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = exerciseId,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -1L,
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        )
                    )
                )
            )
        )
        (saved as TemplateSaveResult.Created).id
    }

    private fun completeFirstWorkout() {
        val templateId = insertPlan()
        val started = runBlocking { workoutSessionRepository.start(templateId) }
        assertTrue(started is StartWorkoutResult.Started)
        val finished = runBlocking {
            workoutSessionRepository.finish(
                (started as StartWorkoutResult.Started).sessionId,
                skipRemaining = true
            )
        }
        assertEquals(FinishWorkoutResult.Finished, finished)
    }
}
