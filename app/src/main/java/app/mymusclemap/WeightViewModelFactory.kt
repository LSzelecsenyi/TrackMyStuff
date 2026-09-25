package app.mymusclemap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.data.workoutimport.WorkoutImportFileReader
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.ui.dashboard.DashboardViewModel
import app.mymusclemap.ui.dashboard.WeightDetailsViewModel
import app.mymusclemap.ui.exercises.ExerciseEditorViewModel
import app.mymusclemap.ui.exercises.ExerciseListViewModel
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.history.HistoryViewModel
import app.mymusclemap.ui.history.WorkoutDetailViewModel
import app.mymusclemap.ui.settings.SettingsViewModel
import app.mymusclemap.ui.templates.TemplateEditorViewModel
import app.mymusclemap.ui.templates.TemplateListViewModel
import app.mymusclemap.ui.workout.ActiveWorkoutViewModel
import app.mymusclemap.ui.workout.WorkoutHubViewModel
import app.mymusclemap.ui.workoutimport.WorkoutImportViewModel

class WeightViewModelFactory(
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val scheduledWorkoutRepository: ScheduledWorkoutRepository,
    private val dateProvider: DateProvider,
    private val themePreferences: ThemePreferences,
    private val workoutImportFileReader: WorkoutImportFileReader
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    weightRepository,
                    workoutSessionRepository,
                    dateProvider,
                    scheduledWorkoutRepository,
                    workoutTemplateRepository
                )
            }
            modelClass.isAssignableFrom(WeightDetailsViewModel::class.java) -> {
                WeightDetailsViewModel(weightRepository, dateProvider)
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
                    dateProvider
                )
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(weightRepository, themePreferences, dateProvider)
            }
            modelClass.isAssignableFrom(ExerciseListViewModel::class.java) -> {
                ExerciseListViewModel(exerciseRepository)
            }
            modelClass.isAssignableFrom(ExerciseEditorViewModel::class.java) -> {
                val application = extras[APPLICATION_KEY]
                ExerciseEditorViewModel(
                    extras.createSavedStateHandle(),
                    exerciseRepository
                ) { group: MuscleGroup ->
                    application?.getString(group.labelRes()) ?: group.name
                }
            }
            modelClass.isAssignableFrom(TemplateListViewModel::class.java) -> {
                TemplateListViewModel(workoutTemplateRepository)
            }
            modelClass.isAssignableFrom(TemplateEditorViewModel::class.java) -> {
                TemplateEditorViewModel(
                    extras.createSavedStateHandle(),
                    workoutTemplateRepository,
                    exerciseRepository
                )
            }
            modelClass.isAssignableFrom(ActiveWorkoutViewModel::class.java) -> {
                ActiveWorkoutViewModel(
                    extras.createSavedStateHandle(),
                    workoutSessionRepository
                )
            }
            modelClass.isAssignableFrom(WorkoutDetailViewModel::class.java) -> {
                WorkoutDetailViewModel(
                    extras.createSavedStateHandle(),
                    workoutSessionRepository
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
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        } as T
    }
}
