package app.mymusclemap.ui.onboarding

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.mymusclemap.ui.navigation.WeightTrackerNavHost
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class OnboardingNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var coordinator: FirstRunCoordinator
    private lateinit var factory: WeightViewModelFactory
    private val dateProvider = FixedDateProvider(LocalDate.of(2026, 9, 25), LocalTime.of(8, 0))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-09-25T08:00:00Z"), ZoneOffset.UTC)
        val exerciseRepository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = clock,
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao()
        )
        val workoutTemplateRepository = WorkoutTemplateRepository(
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
        val workoutSessionRepository = WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = weightRepository,
            clock = clock,
            dateProvider = dateProvider
        )
        themePreferences = ThemePreferences(context)
        runBlocking { themePreferences.setOnboardingCompleted(false) }
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
            firstRunCoordinator = coordinator
        )
    }

    @After
    fun tearDown() {
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
}
