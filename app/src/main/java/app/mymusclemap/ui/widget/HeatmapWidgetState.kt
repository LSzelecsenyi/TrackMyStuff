package app.mymusclemap.ui.widget

import android.content.Context
import app.mymusclemap.WeightTrackerApplication
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import kotlinx.coroutines.flow.first
import java.time.LocalDate

internal object HeatmapWidgetState {
    fun from(exercises: List<MuscleTrainingExercise>, today: LocalDate): MuscleHeatmapState {
        return MuscleHeatmapAssembler.assemble(exercises, today)
    }

    suspend fun load(context: Context): MuscleHeatmapState {
        val container = (context.applicationContext as WeightTrackerApplication).container
        val exercises = container.workoutSessionRepository.observeHeatmapExercises().first()
        return from(exercises, container.dateProvider.today())
    }
}
