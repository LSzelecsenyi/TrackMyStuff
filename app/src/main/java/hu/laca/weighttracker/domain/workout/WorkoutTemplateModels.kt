package hu.laca.weighttracker.domain.workout

import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import java.util.Locale

data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val notes: String?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class WorkoutTemplateExercise(
    val id: Long,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val notes: String?,
    val createdAt: Long
)

data class WorkoutTemplateSet(
    val id: Long,
    val templateExerciseId: Long,
    val position: Int,
    val minReps: Int?,
    val maxReps: Int?,
    val loadKind: PlannedLoadKind,
    val weightKg: Double?,
    val durationSeconds: Int?,
    val distanceMeters: Double?
)

data class WorkoutTemplateAggregate(
    val template: WorkoutTemplate,
    val exercises: List<WorkoutTemplateExerciseItem>
)

data class WorkoutTemplateExerciseItem(
    val relation: WorkoutTemplateExercise,
    val exercise: Exercise,
    val sets: List<WorkoutTemplateSet>
)

data class TemplateListItem(
    val template: WorkoutTemplate,
    val exerciseCount: Int,
    val setCount: Int,
    val primaryMuscles: List<MuscleGroup>,
    val muscleSummary: TemplateMuscleSummary,
    val exerciseNames: List<String> = emptyList()
)

data class TemplateMuscleCount(
    val muscle: MuscleGroup,
    val occurrenceCount: Int
)

data class TemplateMuscleSummary(
    val primary: List<TemplateMuscleCount>,
    val secondary: List<TemplateMuscleCount>
)

data class TemplateDraft(
    val id: Long? = null,
    val name: String = "",
    val notes: String = "",
    val createdAt: Long? = null,
    val exercises: List<TemplateExerciseDraft> = emptyList()
)

data class TemplateExerciseDraft(
    val localId: Long,
    val exerciseId: Long,
    val notes: String = "",
    val sets: List<PlannedSetDraft> = emptyList(),
    val expanded: Boolean = true
)

data class PlannedSetDraft(
    val localId: Long,
    val minRepsText: String = "",
    val maxRepsText: String = "",
    val loadKind: PlannedLoadKind,
    val weightText: String = "",
    val minutesText: String = "0",
    val secondsText: String = "0",
    val distanceText: String = "",
    val distanceUnit: DistanceUnit = DistanceUnit.METERS
)

data class PlannedSetValues(
    val minReps: Int? = null,
    val maxReps: Int? = null,
    val loadKind: PlannedLoadKind,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null
)

data class TemplateValidationIssue(
    val error: TemplateFieldError,
    val exerciseIndex: Int? = null,
    val setIndex: Int? = null
)

sealed class TemplateSaveResult {
    data class Created(val id: Long) : TemplateSaveResult()
    data class Updated(val id: Long) : TemplateSaveResult()
    data class Invalid(val issues: List<TemplateValidationIssue>) : TemplateSaveResult()
    data object DuplicateName : TemplateSaveResult()
    data object NotFound : TemplateSaveResult()
}

sealed class TemplateDeleteResult {
    data object Deleted : TemplateDeleteResult()
    data object BlockedByReferences : TemplateDeleteResult()
    data object NotFound : TemplateDeleteResult()
}

object TemplateNaming {
    const val NAME_MAX_LENGTH = 80
    const val NOTES_MAX_LENGTH = 500

    fun displayName(raw: String): String {
        return raw.trim().replace(WHITESPACE, " ")
    }

    fun normalize(raw: String): String {
        return displayName(raw).lowercase(Locale.ROOT)
    }

    private val WHITESPACE = Regex("\\s+")
}
