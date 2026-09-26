package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.StarterCatalog
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.theme.AppearanceSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class FirstRunCoordinatorTest {
    private lateinit var database: WeightDatabase
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var themePreferences: ThemePreferences
    private lateinit var coordinator: FirstRunCoordinator

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exerciseRepository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        )
        themePreferences = ThemePreferences(context)
        themePreferences.clearOnboardingProgress()
        coordinator = FirstRunCoordinator(database, exerciseRepository, themePreferences)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshDatabaseReceivesStarterExercisesAndShowsOnboarding() = runTest {
        val decision = coordinator.prepare()
        val catalog = exerciseRepository.observeAll().first()
        assertEquals(FirstRunDecision.ShowOnboarding, decision)
        assertEquals(StarterCatalog.drafts.size, catalog.size)
        assertEquals(
            StarterCatalog.drafts.map { it.name }.toSet(),
            catalog.map { it.name }.toSet()
        )
        assertTrue(catalog.all { !it.archived })
        assertFalse(themePreferences.isOnboardingCompleted())
        assertTrue(themePreferences.isWelcomePending())
        catalog.forEach { exercise ->
            val draft = StarterCatalog.drafts.single { it.name == exercise.name }
            assertEquals(draft.measurementType, exercise.measurementType)
            assertEquals(draft.resistanceBasis, exercise.resistanceBasis)
            assertEquals(draft.weightInterpretation, exercise.weightInterpretation)
            assertEquals(draft.primaryMuscle, exercise.primaryMuscle)
            assertEquals(draft.secondaryMuscles, exercise.secondaryMuscles)
            assertEquals(draft.movementPattern, exercise.movementPattern)
        }
    }

    @Test
    fun nonEmptyCatalogIsNotSeededOrModified() = runTest {
        val created = exerciseRepository.save(customDraft()) as ExerciseSaveResult.Created
        val original = exerciseRepository.getById(created.id)!!
        val weightId = database.weightMeasurementDao().insert(
            WeightMeasurementEntity(0, "2026-09-20", 82.4, 5, 5)
        )
        val decision = coordinator.prepare()
        val catalog = exerciseRepository.observeAll().first()
        assertEquals(FirstRunDecision.Ready, decision)
        assertTrue(themePreferences.isOnboardingCompleted())
        assertEquals(1, catalog.size)
        assertEquals(original, exerciseRepository.getById(created.id))
        assertEquals(
            listOf(WeightMeasurementEntity(weightId, "2026-09-20", 82.4, 5, 5)),
            database.weightMeasurementDao().getAllAscending()
        )
        assertFalse(catalog.any { it.name == "Bench Press" })
    }

    @Test
    fun completedOnboardingDoesNotReseedAnEmptyCatalog() = runTest {
        themePreferences.markOnboardingCompleted()
        val decision = coordinator.prepare()
        assertEquals(FirstRunDecision.Ready, decision)
        assertTrue(exerciseRepository.observeAll().first().isEmpty())
        assertEquals(0, database.exerciseDao().countAll())
    }

    @Test
    fun onboardingCompletionPersistsAcrossPrepare() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, coordinator.prepare())
        coordinator.completeOnboarding()
        assertTrue(themePreferences.isOnboardingCompleted())
        assertEquals(FirstRunDecision.Ready, coordinator.prepare())
        assertEquals(StarterCatalog.drafts.size, exerciseRepository.observeAll().first().size)
    }

    @Test
    fun secondPrepareAfterSeedStillShowsOnboarding() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, coordinator.prepare())
        assertEquals(FirstRunDecision.ShowOnboarding, coordinator.prepare())
        assertFalse(themePreferences.isOnboardingCompleted())
        assertFalse(themePreferences.isOnboardingStarted())
        assertTrue(themePreferences.isWelcomePending())
        assertEquals(StarterCatalog.drafts.size, exerciseRepository.observeAll().first().size)
    }

    @Test
    fun startedOnboardingSkipsWelcomeOnRestartWithoutCompleting() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, coordinator.prepare())
        coordinator.markOnboardingStarted()
        assertTrue(themePreferences.isOnboardingStarted())
        assertFalse(themePreferences.isOnboardingCompleted())
        assertFalse(themePreferences.isWelcomePending())
        assertEquals(FirstRunDecision.Ready, coordinator.prepare())
        assertEquals(StarterCatalog.drafts.size, exerciseRepository.observeAll().first().size)
    }

    @Test
    fun appearanceRestoreKeepsWelcomePending() = runTest {
        assertEquals(FirstRunDecision.ShowOnboarding, coordinator.prepare())
        themePreferences.replaceAppearance(AppearanceSettings.Default)
        assertTrue(themePreferences.isWelcomePending())
        assertEquals(FirstRunDecision.ShowOnboarding, coordinator.prepare())
        assertFalse(themePreferences.isOnboardingCompleted())
    }

    @Test
    fun appearanceRestoreKeepsOnboardingCompleted() = runTest {
        coordinator.completeOnboarding()
        themePreferences.replaceAppearance(AppearanceSettings.Default)
        assertTrue(themePreferences.isOnboardingCompleted())
        themePreferences.setOnboardingCompleted(false)
        themePreferences.replaceAppearance(AppearanceSettings.Default)
        assertFalse(themePreferences.isOnboardingCompleted())
    }

    @Test
    fun appearanceRestoreKeepsProgressiveOnboardingFlags() = runTest {
        coordinator.markOnboardingStarted()
        themePreferences.setHeatmapSeen()
        themePreferences.setReminderDismissed()
        themePreferences.replaceAppearance(AppearanceSettings.Default)
        val flags = themePreferences.currentOnboardingFlags()
        assertTrue(flags.started)
        assertTrue(flags.heatmapSeen)
        assertTrue(flags.reminderDismissed)
        assertFalse(flags.completed)
    }

    private fun customDraft(): ExerciseDraft {
        return ExerciseDraft(
            name = "Custom Curl",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.ISOLATION,
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistanceBasis = ResistanceBasis.EXTERNAL,
            weightInterpretation = WeightInterpretation.TOTAL,
            primaryMuscle = MuscleGroup.BICEPS
        )
    }
}
