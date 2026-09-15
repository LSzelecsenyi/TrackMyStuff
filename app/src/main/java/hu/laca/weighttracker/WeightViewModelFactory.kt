package hu.laca.weighttracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import hu.laca.weighttracker.data.preferences.ThemePreferences
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.ui.dashboard.DashboardViewModel
import hu.laca.weighttracker.ui.exercises.ExerciseEditorViewModel
import hu.laca.weighttracker.ui.exercises.ExerciseListViewModel
import hu.laca.weighttracker.ui.history.HistoryViewModel
import hu.laca.weighttracker.ui.settings.SettingsViewModel
import hu.laca.weighttracker.ui.workout.WorkoutHubViewModel

class WeightViewModelFactory(
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val dateProvider: DateProvider,
    private val themePreferences: ThemePreferences
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(weightRepository, dateProvider)
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(weightRepository, dateProvider)
            }
            modelClass.isAssignableFrom(WorkoutHubViewModel::class.java) -> {
                WorkoutHubViewModel(exerciseRepository)
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
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        } as T
    }
}
