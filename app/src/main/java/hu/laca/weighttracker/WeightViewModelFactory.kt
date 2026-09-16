package hu.laca.weighttracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import hu.laca.weighttracker.data.preferences.ThemePreferences
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.ui.dashboard.DashboardViewModel
import hu.laca.weighttracker.ui.dashboard.WeightDetailsViewModel
import hu.laca.weighttracker.ui.exercises.ExerciseEditorViewModel
import hu.laca.weighttracker.ui.exercises.ExerciseListViewModel
import hu.laca.weighttracker.ui.history.HistoryViewModel
import hu.laca.weighttracker.ui.history.WorkoutDetailViewModel
import hu.laca.weighttracker.ui.settings.SettingsViewModel
import hu.laca.weighttracker.ui.templates.TemplateEditorViewModel
import hu.laca.weighttracker.ui.templates.TemplateListViewModel
import hu.laca.weighttracker.ui.workout.ActiveWorkoutViewModel
import hu.laca.weighttracker.ui.workout.WorkoutHubViewModel
import hu.laca.weighttracker.ui.workoutimport.WorkoutImportViewModel
import hu.laca.weighttracker.data.workoutimport.WorkoutImportFileReader

class WeightViewModelFactory(
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val dateProvider: DateProvider,
    private val themePreferences: ThemePreferences,
    private val workoutImportFileReader: WorkoutImportFileReader
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(weightRepository, workoutSessionRepository, dateProvider)
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
                ExerciseEditorViewModel(extras.createSavedStateHandle(), exerciseRepository)
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
