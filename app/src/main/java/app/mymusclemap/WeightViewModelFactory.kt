package app.mymusclemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import app.mymusclemap.data.auth.FounderReportSubmission
import app.mymusclemap.data.founder.FounderJoinResult
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.health.HealthRepository
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.LockScreenSetCompletionStore
import app.mymusclemap.data.preferences.TimerSoundStore
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AchievementRepository
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.BodyMeasurementRepository
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FreeAccessRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.data.repository.ProgressPhotoRepository
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.TargetWeightGoalRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WeeklyGoalRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.data.workoutimport.WorkoutImportFileReader
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.ui.achievements.AchievementsViewModel
import app.mymusclemap.ui.dashboard.DashboardViewModel
import app.mymusclemap.ui.dashboard.WeightDetailsViewModel
import app.mymusclemap.ui.founder.FounderProgramViewModel
import app.mymusclemap.ui.exercises.ExerciseEditorViewModel
import app.mymusclemap.ui.exercises.ExerciseListViewModel
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.history.HistoryViewModel
import app.mymusclemap.ui.history.WorkoutDetailViewModel
import app.mymusclemap.ui.onboarding.OnboardingGuideViewModel
import app.mymusclemap.ui.onboarding.OnboardingViewModel
import app.mymusclemap.ui.progress.ProgressPhotosViewModel
import app.mymusclemap.ui.reports.ReportsViewModel
import app.mymusclemap.ui.health.HealthConnectViewModel
import app.mymusclemap.ui.settings.SettingsViewModel
import app.mymusclemap.ui.statistics.StatisticsViewModel
import app.mymusclemap.ui.templates.TemplateEditorViewModel
import app.mymusclemap.ui.templates.TemplateListViewModel
import app.mymusclemap.ui.workout.ActiveWorkoutViewModel
import app.mymusclemap.ui.workout.AndroidRestTimerEffects
import app.mymusclemap.ui.workout.AndroidTimerCuePlayer
import app.mymusclemap.ui.workout.WorkoutCompletionViewModel
import app.mymusclemap.ui.workout.WorkoutHubViewModel
import app.mymusclemap.ui.workoutimport.WorkoutImportViewModel
import app.mymusclemap.ui.entitlement.FreeAccessSelectionViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class WeightViewModelFactory(
    private val weightRepository: WeightRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val scheduledWorkoutRepository: ScheduledWorkoutRepository,
    private val dateProvider: DateProvider,
    private val themePreferences: ThemePreferences,
    private val workoutImportFileReader: WorkoutImportFileReader,
    private val appBackupRepository: AppBackupRepository,
    private val firstRunCoordinator: FirstRunCoordinator,
    private val onboardingRepository: OnboardingRepository,
    private val progressPhotoRepository: ProgressPhotoRepository,
    private val healthRepository: HealthRepository,
    private val featureEntitlements: FeatureEntitlements = OpenFeatureEntitlements,
    private val weeklyGoalRepository: WeeklyGoalRepository? = null,
    private val founderProgram: FounderProgramCoordinator? = null,
    private val founderRules: FounderProgramRules? = null,
    private val founderMilestoneAcknowledgements: FounderMilestoneAcknowledgementStore? = null,
    private val founderAvailability: FounderProgramAvailability = FounderProgramAvailability.Open,
    private val lockScreenSetCompletion: LockScreenSetCompletionStore = LockScreenSetCompletionStore.Off,
    private val timerSounds: TimerSoundStore = TimerSoundStore.On,
    private val founderJoin: suspend () -> FounderJoinResult = { FounderJoinResult.Rejected },
    private val founderSessionRevision: Flow<Int> = flowOf(0),
    private val founderSessionPresent: () -> Boolean = { true },
    private val founderSubmitReport: suspend (submissionId: String, feedback: String, appVersion: String) -> FounderReportSubmission =
        { _, _, _ -> FounderReportSubmission.Unavailable },
    private val founderRefreshAuthority: suspend () -> Unit = {},
    private val achievementRepository: AchievementRepository? = null,
    private val targetWeightGoalRepository: TargetWeightGoalRepository? = null,
    private val freeAccessRepository: FreeAccessRepository? = null
) : ViewModelProvider.Factory {
    private val accessDecisions = freeAccessRepository?.decisions()
        ?: flowOf(app.mymusclemap.domain.entitlement.FreeAccessLogic.unknown())

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    weightRepository,
                    workoutSessionRepository,
                    dateProvider,
                    scheduledWorkoutRepository,
                    workoutTemplateRepository,
                    onboardingRepository,
                    featureEntitlements,
                    weeklyGoalRepository
                )
            }
            modelClass.isAssignableFrom(WeightDetailsViewModel::class.java) -> {
                WeightDetailsViewModel(
                    weightRepository,
                    dateProvider,
                    onboardingRepository,
                    bodyMeasurementRepository,
                    featureEntitlements,
                    targetWeightGoalRepository
                )
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(
                    weightRepository,
                    workoutSessionRepository,
                    dateProvider,
                    extras.createSavedStateHandle()
                )
            }
            modelClass.isAssignableFrom(WorkoutHubViewModel::class.java) -> {
                WorkoutHubViewModel(
                    exerciseRepository,
                    workoutTemplateRepository,
                    workoutSessionRepository,
                    scheduledWorkoutRepository,
                    dateProvider,
                    featureEntitlements
                )
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    themePreferences,
                    dateProvider,
                    appBackupRepository,
                    weeklyGoalRepository,
                    lockScreenSetCompletion = lockScreenSetCompletion,
                    timerSounds = timerSounds
                )
            }
            modelClass.isAssignableFrom(OnboardingViewModel::class.java) -> {
                OnboardingViewModel(
                    firstRunCoordinator,
                    weeklyGoalRepository,
                    dateProvider,
                    founderMilestoneAcknowledgements,
                    founderProgram,
                    founderAvailability,
                    founderJoin
                )
            }
            modelClass.isAssignableFrom(OnboardingGuideViewModel::class.java) -> {
                OnboardingGuideViewModel(onboardingRepository)
            }
            modelClass.isAssignableFrom(ExerciseListViewModel::class.java) -> {
                ExerciseListViewModel(exerciseRepository, featureEntitlements, accessDecisions)
            }
            modelClass.isAssignableFrom(ExerciseEditorViewModel::class.java) -> {
                val application = extras[APPLICATION_KEY]
                ExerciseEditorViewModel(
                    extras.createSavedStateHandle(),
                    exerciseRepository,
                    muscleLabel = { group: MuscleGroup ->
                        application?.getString(group.labelRes()) ?: group.name
                    },
                    access = accessDecisions
                )
            }
            modelClass.isAssignableFrom(TemplateListViewModel::class.java) -> {
                TemplateListViewModel(
                    workoutTemplateRepository,
                    featureEntitlements,
                    accessDecisions
                )
            }
            modelClass.isAssignableFrom(TemplateEditorViewModel::class.java) -> {
                TemplateEditorViewModel(
                    extras.createSavedStateHandle(),
                    workoutTemplateRepository,
                    exerciseRepository,
                    featureEntitlements,
                    accessDecisions
                )
            }
            modelClass.isAssignableFrom(ActiveWorkoutViewModel::class.java) -> {
                val application = extras[APPLICATION_KEY]
                    ?: throw IllegalStateException("Application is required")
                ActiveWorkoutViewModel(
                    extras.createSavedStateHandle(),
                    workoutSessionRepository,
                    observeCatalog = exerciseRepository.observeActive(),
                    restEffects = AndroidRestTimerEffects(application),
                    entitlements = featureEntitlements,
                    timerSounds = timerSounds,
                    timerCuePlayer = AndroidTimerCuePlayer(application),
                    lockedCatalogExerciseIds = accessDecisions.map { it.lockedCustomExerciseIds }
                )
            }
            modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java) -> {
                val application = extras[APPLICATION_KEY]
                    ?: throw IllegalStateException("Application is required")
                WorkoutDetailViewModel(
                    extras.createSavedStateHandle(),
                    workoutSessionRepository,
                    application.resources
                )
            }
            modelClass.isAssignableFrom(WorkoutImportViewModel::class.java) -> {
                WorkoutImportViewModel(
                    fileReader = workoutImportFileReader,
                    exerciseRepository = exerciseRepository,
                    weightRepository = weightRepository,
                    sessionRepository = workoutSessionRepository,
                    dateProvider = dateProvider
                )
            }
            modelClass.isAssignableFrom(StatisticsViewModel::class.java) -> {
                StatisticsViewModel(
                    workoutSessionRepository,
                    scheduledWorkoutRepository,
                    dateProvider,
                    featureEntitlements,
                    extras.createSavedStateHandle()
                )
            }
            modelClass.isAssignableFrom(app.mymusclemap.ui.muscleinsights.MuscleInsightsViewModel::class.java) -> {
                app.mymusclemap.ui.muscleinsights.MuscleInsightsViewModel(
                    workoutSessionRepository,
                    dateProvider,
                    featureEntitlements,
                    extras.createSavedStateHandle()
                )
            }
            modelClass.isAssignableFrom(ProgressPhotosViewModel::class.java) -> {
                ProgressPhotosViewModel(progressPhotoRepository, featureEntitlements)
            }
            modelClass.isAssignableFrom(FounderProgramViewModel::class.java) -> {
                FounderProgramViewModel(
                    coordinator = checkNotNull(founderProgram),
                    rules = checkNotNull(founderRules),
                    versionName = BuildConfig.VERSION_NAME,
                    milestoneAcknowledgements = checkNotNull(founderMilestoneAcknowledgements),
                    availability = founderAvailability,
                    joinFounder = founderJoin,
                    sessionRevision = founderSessionRevision,
                    sessionPresent = founderSessionPresent,
                    submitReport = founderSubmitReport,
                    refreshAuthority = founderRefreshAuthority
                )
            }
            modelClass.isAssignableFrom(HealthConnectViewModel::class.java) -> {
                HealthConnectViewModel(healthRepository, dateProvider)
            }
            modelClass.isAssignableFrom(WorkoutCompletionViewModel::class.java) -> {
                WorkoutCompletionViewModel(workoutSessionRepository)
            }
            modelClass.isAssignableFrom(AchievementsViewModel::class.java) -> {
                AchievementsViewModel(checkNotNull(achievementRepository))
            }
            modelClass.isAssignableFrom(ReportsViewModel::class.java) -> {
                ReportsViewModel(
                    workoutSessionRepository,
                    scheduledWorkoutRepository,
                    weightRepository,
                    dateProvider,
                    featureEntitlements,
                    extras.createSavedStateHandle(),
                    onMonthlyReportGenerated = {
                        checkNotNull(achievementRepository).recordMonthlyReportGenerated()
                    }
                )
            }
            modelClass.isAssignableFrom(FreeAccessSelectionViewModel::class.java) -> {
                FreeAccessSelectionViewModel(
                    templates = workoutTemplateRepository,
                    exercises = exerciseRepository,
                    access = checkNotNull(freeAccessRepository)
                )
            }
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        } as T
    }
}
