package app.mymusclemap.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
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
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class OnboardingGuideViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModelStore = ViewModelStore()
    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var repository: OnboardingRepository
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var templateRepository: WorkoutTemplateRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-09-25T08:00:00Z"), ZoneOffset.UTC)
        val dateProvider = FixedDateProvider(LocalDate.of(2026, 9, 25), LocalTime.of(8, 0))
        themePreferences = ThemePreferences(context)
        val weightRepository = WeightRepository(database.weightMeasurementDao(), clock)
        exerciseRepository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = clock,
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao()
        )
        val sessions = WorkoutSessionRepository(
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
        repository = OnboardingRepository(themePreferences, sessions, weightRepository, templateRepository)
        themePreferences.clearOnboardingProgress()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        runBlocking { themePreferences.clearOnboardingProgress() }
        database.close()
    }

    @Test
    fun continueRequestShowsWorkoutCoachOnlyWhileStartWorkoutIsNext() = runTest {
        repository.markStarted()
        val viewModel = ViewModelProvider(
            viewModelStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OnboardingGuideViewModel(repository) as T
                }
            }
        )[OnboardingGuideViewModel::class.java]
        viewModel.guide.first { it.flags.started }
        viewModel.requestStartWorkoutCoach()
        assertFalse(viewModel.guide.value.showWorkoutActionCoach)

        insertPlan()
        viewModel.guide.first { it.resumeTarget == OnboardingResumeTarget.StartWorkout }
        viewModel.requestStartWorkoutCoach()
        assertTrue(viewModel.guide.value.showWorkoutActionCoach)
        assertEquals(OnboardingResumeTarget.StartWorkout, viewModel.guide.value.resumeTarget)

        viewModel.dismissStartWorkoutCoach()
        assertFalse(viewModel.guide.value.showWorkoutActionCoach)
    }

    private suspend fun insertPlan() {
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
    }
}
