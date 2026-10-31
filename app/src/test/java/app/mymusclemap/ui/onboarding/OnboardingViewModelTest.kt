package app.mymusclemap.ui.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.FirstRunDecision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var coordinator: FirstRunCoordinator
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        themePreferences = ThemePreferences(context)
        themePreferences.clearOnboardingProgress()
        coordinator = FirstRunCoordinator(
            database,
            ExerciseRepository(
                dao = database.exerciseDao(),
                clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
            ),
            themePreferences
        )
        viewModel = OnboardingViewModel(coordinator)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createPlanMarksOnboardingStartedAndOpensTemplateEditor() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, coordinator.prepare())
        assertEquals(OnboardingStep.Welcome, viewModel.step.value)
        assertNull(viewModel.exit.value)
        viewModel.onContinue()
        assertEquals(OnboardingStep.MeasurementUnits, viewModel.step.value)
        viewModel.onContinue()
        assertEquals(OnboardingStep.BodyModel, viewModel.step.value)
        viewModel.onContinue()
        assertEquals(OnboardingStep.WeeklyGoal, viewModel.step.value)
        viewModel.onWeeklyGoalSkipped()
        assertEquals(OnboardingStep.CreatePlan, viewModel.step.value)
        viewModel.onCreatePlan()
        assertEquals(OnboardingExit.OpenTemplateEditor, viewModel.exit.first { it != null })
        assertTrue(themePreferences.isOnboardingStarted())
        assertFalse(themePreferences.isOnboardingCompleted())
        assertEquals(FirstRunDecision.Ready, coordinator.prepare())
    }

    @Test
    fun skipMarksOnboardingStartedWithoutOpeningTemplateEditor() = runTest {
        viewModel.onContinue()
        viewModel.onContinue()
        viewModel.onContinue()
        viewModel.onWeeklyGoalSkipped()
        viewModel.onSkip()
        assertEquals(OnboardingExit.Dismiss, viewModel.exit.first { it != null })
        assertTrue(themePreferences.isOnboardingStarted())
        assertFalse(themePreferences.isOnboardingCompleted())
        assertEquals(FirstRunDecision.Ready, coordinator.prepare())
    }

    @Test
    fun bodyModelSelectionPersistsAndDefaultsToMale() = runTest {
        val saved = mutableListOf<MuscleMapBodyModel>()
        val choosing = OnboardingViewModel(
            coordinator,
            initialBodyModel = MuscleMapBodyModel.MALE,
            persistBodyModel = { saved += it }
        )
        assertEquals(MuscleMapBodyModel.MALE, choosing.selectedBodyModel.value)
        choosing.onContinue()
        choosing.onContinue()
        assertEquals(OnboardingStep.BodyModel, choosing.step.value)
        choosing.onBodyModelSelected(MuscleMapBodyModel.FEMALE)
        assertEquals(listOf(MuscleMapBodyModel.FEMALE), saved)
        choosing.onContinue()
        assertEquals(listOf(MuscleMapBodyModel.FEMALE, MuscleMapBodyModel.FEMALE), saved)
        assertEquals(OnboardingStep.WeeklyGoal, choosing.step.value)
    }
}
