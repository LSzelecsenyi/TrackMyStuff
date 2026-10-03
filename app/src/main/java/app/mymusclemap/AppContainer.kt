package app.mymusclemap

import android.content.Context
import app.mymusclemap.data.health.HealthConnectGateway
import app.mymusclemap.data.health.HealthRepository
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.BodyMeasurementRepository
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.data.repository.ProgressPhotoRepository
import java.io.File
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WeeklyGoalRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.data.workoutimport.ContentWorkoutImportFileReader
import app.mymusclemap.ui.widget.HeatmapWidgetUpdater
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.SystemDateProvider
import app.mymusclemap.domain.entitlement.DevelopmentSubscriptionProvider
import app.mymusclemap.domain.entitlement.EntitlementComposer
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.InactiveFounderLifetimeProvider
import app.mymusclemap.domain.entitlement.PolicyBackedEntitlements
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: Clock = Clock.systemDefaultZone()
    val dateProvider: DateProvider = SystemDateProvider(clock)
    private val database = WeightDatabase.create(appContext)
    val weightRepository = WeightRepository(
        dao = database.weightMeasurementDao(),
        clock = clock
    )
    val bodyMeasurementRepository = BodyMeasurementRepository(
        dao = database.bodyMeasurementDao(),
        clock = clock
    )
    private val customExerciseCreate = AtomicReference<(Int) -> Boolean> { true }
    val exerciseRepository = ExerciseRepository(
        dao = database.exerciseDao(),
        clock = clock,
        templateDao = database.workoutTemplateDao(),
        sessionDao = database.workoutSessionDao(),
        allowCustomCreate = { count -> customExerciseCreate.get().invoke(count) }
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
    val workoutSessionRepository = WorkoutSessionRepository(
        sessionDao = database.workoutSessionDao(),
        templateDao = database.workoutTemplateDao(),
        exerciseDao = database.exerciseDao(),
        weightRepository = weightRepository,
        clock = clock,
        dateProvider = dateProvider,
        onHeatmapDataChanged = { HeatmapWidgetUpdater.update(appContext) }
    )
    val weeklyGoalRepository = WeeklyGoalRepository(
        dao = database.weeklyWorkoutGoalDao(),
        clock = clock,
        earliestCompletedDate = {
            database.workoutSessionDao().earliestCompletedWorkoutDate()?.let(LocalDate::parse)
        }
    )
    val themePreferences = ThemePreferences(appContext)
    val progressPhotoStore = ProgressPhotoStore(
        File(appContext.filesDir, ProgressPhotoStore.DIRECTORY_NAME)
    )
    val appBackupRepository = AppBackupRepository(
        database,
        themePreferences,
        progressPhotoStore = progressPhotoStore,
        onHeatmapDataChanged = { HeatmapWidgetUpdater.update(appContext) }
    )
    val firstRunCoordinator = FirstRunCoordinator(database, exerciseRepository, themePreferences)
    val founderProgramRules: FounderProgramRules = FounderProgramRuleSelection.rules
    val founderProgramStore = FounderProgramStore(appContext)
    private val founderProgramState = AtomicReference(FounderProgramState())
    val subscriptionProvider = DevelopmentSubscriptionProvider
    val founderLifetimeProvider = InactiveFounderLifetimeProvider
    val entitlementComposer = EntitlementComposer(
        subscriptionProvider = subscriptionProvider,
        founderLifetimeProvider = founderLifetimeProvider,
        clock = clock,
        founderProgram = founderProgramState::get
    )
    val featureEntitlements: FeatureEntitlements = PolicyBackedEntitlements {
        entitlementComposer.policy()
    }

    init {
        customExerciseCreate.set { count ->
            entitlementComposer.policy().customExercises(count).canCreate
        }
    }

    suspend fun refreshFounderProgramFromStore() {
        founderProgramState.set(founderProgramStore.load())
    }
    val progressPhotoRepository = ProgressPhotoRepository(
        dao = database.progressPhotoDao(),
        store = progressPhotoStore,
        clock = clock,
        dateProvider = dateProvider
    )
    val healthRepository = HealthRepository(
        source = HealthConnectGateway(appContext),
        dateProvider = dateProvider,
        canImportExternalWorkouts = {
            entitlementComposer.policy().healthConnect().canImportExternalWorkouts
        }
    )
    val onboardingRepository = OnboardingRepository(
        themePreferences = themePreferences,
        sessionRepository = workoutSessionRepository,
        weightRepository = weightRepository,
        templateRepository = workoutTemplateRepository
    )
    val viewModelFactory = WeightViewModelFactory(
        weightRepository = weightRepository,
        bodyMeasurementRepository = bodyMeasurementRepository,
        exerciseRepository = exerciseRepository,
        workoutTemplateRepository = workoutTemplateRepository,
        workoutSessionRepository = workoutSessionRepository,
        scheduledWorkoutRepository = scheduledWorkoutRepository,
        dateProvider = dateProvider,
        themePreferences = themePreferences,
        workoutImportFileReader = ContentWorkoutImportFileReader(appContext),
        appBackupRepository = appBackupRepository,
        firstRunCoordinator = firstRunCoordinator,
        onboardingRepository = onboardingRepository,
        progressPhotoRepository = progressPhotoRepository,
        healthRepository = healthRepository,
        featureEntitlements = featureEntitlements,
        weeklyGoalRepository = weeklyGoalRepository
    )
}
