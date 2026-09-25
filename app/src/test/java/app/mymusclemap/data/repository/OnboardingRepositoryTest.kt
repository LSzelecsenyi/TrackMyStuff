package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.onboarding.OnboardingResumeTarget
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class OnboardingRepositoryTest {
    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var repository: OnboardingRepository
    private lateinit var weightRepository: WeightRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var templateRepository: WorkoutTemplateRepository
    private lateinit var sessionRepository: WorkoutSessionRepository
    private val dateProvider = FixedDateProvider(LocalDate.of(2026, 9, 25), LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-09-25T08:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        themePreferences = ThemePreferences(context)
        weightRepository = WeightRepository(database.weightMeasurementDao(), clock)
        exerciseRepository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = clock,
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao()
        )
        sessionRepository = WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = weightRepository,
            clock = clock,
            dateProvider = dateProvider
        )
        templateRepository = WorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            clock = clock,
            sessionDao = database.workoutSessionDao(),
            scheduledWorkoutDao = database.scheduledWorkoutDao()
        )
        repository = OnboardingRepository(themePreferences, sessionRepository, weightRepository, templateRepository)
        themePreferences.clearOnboardingProgress()
    }

    @After
    fun tearDown() = runTest {
        themePreferences.clearOnboardingProgress()
        database.close()
    }

    @Test
    fun startedStateSurvivesRestartAndAppearanceRestore() = runTest {
        repository.markStarted()
        repository.dismissReminder()
        val first = repository.observe().first { it.flags.started }
        assertTrue(first.flags.started)
        assertTrue(first.flags.reminderDismissed)
        assertFalse(first.flags.completed)
        assertEquals(OnboardingResumeTarget.CreatePlan, first.resumeTarget)

        themePreferences.replaceAppearance(AppearanceSettings.Default)
        val restored = repository.observe().first { it.flags.started && it.flags.reminderDismissed }
        assertTrue(restored.flags.started)
        assertTrue(restored.flags.reminderDismissed)
        assertFalse(restored.reminderVisible)
    }

    @Test
    fun skippingWeightStillAllowsCalendarCompletion() = runTest {
        repository.markStarted()
        repository.markHeatmapSeen()
        repository.markWeightIntroduced()
        val afterSkip = repository.observe().first { it.flags.weightIntroduced }
        assertTrue(afterSkip.showCalendarCoach)
        assertFalse(afterSkip.showWeightChartCoach)
        repository.markCalendarSeen()
        val done = repository.observe().first { it.flags.completed }
        assertTrue(done.flags.completed)
        assertFalse(done.reminderVisible)
    }

    @Test
    fun savingWeightUsesNormalPersistenceThenChartDiscovery() = runTest {
        repository.markStarted()
        repository.markHeatmapSeen()
        weightRepository.save(dateProvider.today(), 82.4)
        repository.markWeightIntroduced()
        val afterSave = repository.observe().first { it.facts.hasWeight && it.flags.weightIntroduced }
        assertEquals(82.4, weightRepository.observeAll().first().single().weightKg, 0.0)
        assertTrue(afterSave.showWeightChartCoach)
        assertEquals(OnboardingResumeTarget.WeightChart, afterSave.resumeTarget)
        repository.markWeightChartSeen()
        val afterChart = repository.observe().first { it.flags.weightChartSeen }
        assertFalse(afterChart.showWeightChartCoach)
        assertTrue(afterChart.showCalendarCoach)
    }

    @Test
    fun firstPlanWithoutCompletedWorkoutResumesStartWorkoutFromRoomState() = runTest {
        repository.markStarted()
        val exerciseId = (exerciseRepository.save(
            ExerciseDraft(
                name = "Pull-up",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created).id
        val saved = templateRepository.save(
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
        assertTrue(saved is TemplateSaveResult.Created)
        val guide = repository.observe().first { it.facts.hasPlan }
        assertEquals(OnboardingResumeTarget.StartWorkout, guide.resumeTarget)
        assertTrue(guide.checklist.planCreated)
        assertFalse(guide.checklist.firstWorkoutDone)
        assertFalse(guide.showHeatmapCoach)
    }

    @Test
    fun completedWorkoutAdvancesToHeatmapDiscovery() = runTest {
        repository.markStarted()
        val exerciseId = (exerciseRepository.save(
            ExerciseDraft(
                name = "Pull-up",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created).id
        val templateId = (templateRepository.save(
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
        ) as TemplateSaveResult.Created).id
        val started = sessionRepository.start(templateId)
        assertTrue(started is StartWorkoutResult.Started)
        sessionRepository.finish((started as StartWorkoutResult.Started).sessionId, skipRemaining = true)
        val guide = repository.observe().first { it.facts.hasCompletedWorkout }
        assertEquals(OnboardingResumeTarget.Heatmap, guide.resumeTarget)
        assertTrue(guide.showHeatmapCoach)
        assertTrue(guide.showHeatmapCompletionCta)
        assertTrue(guide.checklist.planCreated)
        assertTrue(guide.checklist.firstWorkoutDone)
    }
}
