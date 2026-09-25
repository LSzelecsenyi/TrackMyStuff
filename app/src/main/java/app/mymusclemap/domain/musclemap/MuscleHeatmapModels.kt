package app.mymusclemap.domain.musclemap

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.workout.SessionStatus
import java.time.LocalDate

data class MuscleTrainingExercise(
    val status: SessionStatus,
    val workoutDate: LocalDate,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val completedSetCount: Int
)

enum class MuscleRecencyBand {
    TODAY,
    DAYS_1_2,
    DAYS_3_4,
    DAYS_5_6,
    DAYS_7_13,
    DAYS_14_PLUS,
    NEVER
}

data class MuscleHeatmapEntry(
    val group: MuscleGroup,
    val lastTrained: LocalDate?,
    val daysAgo: Int?,
    val band: MuscleRecencyBand
)

data class MuscleHeatmapState(
    val entries: Map<MuscleGroup, MuscleHeatmapEntry>,
    val hasCompletedWorkouts: Boolean,
    val fullBody: MuscleHeatmapEntry? = null,
    val cardiovascular: MuscleHeatmapEntry? = null
) {
    fun entry(group: MuscleGroup): MuscleHeatmapEntry {
        return entries.getValue(group)
    }
}

enum class TemplateMuscleEmphasis {
    PRIMARY,
    SECONDARY
}

data class TemplateMuscleMapState(
    val emphasis: Map<MuscleGroup, TemplateMuscleEmphasis>,
    val hasFullBody: Boolean,
    val hasCardiovascular: Boolean
)
