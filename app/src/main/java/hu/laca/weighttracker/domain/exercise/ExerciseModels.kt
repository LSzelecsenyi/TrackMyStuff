package hu.laca.weighttracker.domain.exercise

import java.util.Locale

data class Exercise(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val category: ExerciseCategory,
    val movementPattern: MovementPattern,
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val weightInterpretation: WeightInterpretation,
    val primaryMuscle: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val notes: String?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class ExerciseDraft(
    val id: Long? = null,
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.STRENGTH,
    val movementPattern: MovementPattern = MovementPattern.HORIZONTAL_PUSH,
    val measurementType: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
    val resistanceBasis: ResistanceBasis = ResistanceBasis.EXTERNAL,
    val weightInterpretation: WeightInterpretation = WeightInterpretation.TOTAL,
    val primaryMuscle: MuscleGroup = MuscleGroup.CHEST,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val notes: String = "",
    val createdAt: Long? = null
)

enum class ExerciseFieldError {
    NameBlank,
    NameTooLong,
    NotesTooLong,
    PrimaryAlsoSecondary,
    WeightInterpretationRequired
}

sealed class ExerciseSaveResult {
    data class Created(val id: Long) : ExerciseSaveResult()
    data class Updated(val id: Long) : ExerciseSaveResult()
    data class Invalid(val errors: List<ExerciseFieldError>) : ExerciseSaveResult()
    data object DuplicateName : ExerciseSaveResult()
    data object NotFound : ExerciseSaveResult()
}

sealed class ExerciseDeleteResult {
    data object Deleted : ExerciseDeleteResult()
    data object BlockedByReferences : ExerciseDeleteResult()
    data object NotFound : ExerciseDeleteResult()
}

object ExerciseNaming {
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

object ExerciseDefaults {
    fun forCategory(category: ExerciseCategory): Triple<MovementPattern, MeasurementType, ResistanceBasis> {
        return when (category) {
            ExerciseCategory.STRENGTH -> Triple(
                MovementPattern.HORIZONTAL_PUSH,
                MeasurementType.REPETITIONS_AND_WEIGHT,
                ResistanceBasis.EXTERNAL
            )
            ExerciseCategory.CARDIO -> Triple(
                MovementPattern.CARDIO,
                MeasurementType.DISTANCE_AND_DURATION,
                ResistanceBasis.NONE
            )
            ExerciseCategory.STATIC_HOLD -> Triple(
                MovementPattern.CORE,
                MeasurementType.DURATION,
                ResistanceBasis.BODYWEIGHT
            )
            ExerciseCategory.MOBILITY -> Triple(
                MovementPattern.MOBILITY,
                MeasurementType.COMPLETION_ONLY,
                ResistanceBasis.NONE
            )
            ExerciseCategory.SKILL -> Triple(
                MovementPattern.OTHER,
                MeasurementType.COMPLETION_ONLY,
                ResistanceBasis.NONE
            )
        }
    }
}

object ExerciseDraftLogic {
    fun fromExercise(exercise: Exercise): ExerciseDraft {
        return ExerciseDraft(
            id = exercise.id,
            name = exercise.name,
            category = exercise.category,
            movementPattern = exercise.movementPattern,
            measurementType = exercise.measurementType,
            resistanceBasis = exercise.resistanceBasis,
            weightInterpretation = exercise.weightInterpretation,
            primaryMuscle = exercise.primaryMuscle,
            secondaryMuscles = exercise.secondaryMuscles,
            notes = exercise.notes.orEmpty(),
            createdAt = exercise.createdAt
        )
    }

    fun applyCategory(draft: ExerciseDraft, category: ExerciseCategory): ExerciseDraft {
        val suggestion = ExerciseDefaults.forCategory(category)
        val weight = resolvedWeightInterpretation(
            measurementType = suggestion.second,
            resistanceBasis = suggestion.third,
            current = draft.weightInterpretation
        )
        return draft.copy(
            category = category,
            movementPattern = suggestion.first,
            measurementType = suggestion.second,
            resistanceBasis = suggestion.third,
            weightInterpretation = weight
        )
    }

    fun applyPrimaryMuscle(draft: ExerciseDraft, primary: MuscleGroup): ExerciseDraft {
        return draft.copy(
            primaryMuscle = primary,
            secondaryMuscles = draft.secondaryMuscles.filterNot { it == primary }
        )
    }

    fun applySecondaryMuscles(draft: ExerciseDraft, selected: List<MuscleGroup>): ExerciseDraft {
        return draft.copy(secondaryMuscles = normalizeSecondary(draft.primaryMuscle, selected))
    }

    fun toggleSecondary(draft: ExerciseDraft, group: MuscleGroup): ExerciseDraft {
        if (group == draft.primaryMuscle) {
            return draft
        }
        val next = if (group in draft.secondaryMuscles) {
            draft.secondaryMuscles - group
        } else {
            draft.secondaryMuscles + group
        }
        return draft.copy(secondaryMuscles = normalizeSecondary(draft.primaryMuscle, next))
    }

    fun applyMeasurement(draft: ExerciseDraft, measurementType: MeasurementType): ExerciseDraft {
        return draft.copy(
            measurementType = measurementType,
            weightInterpretation = resolvedWeightInterpretation(
                measurementType,
                draft.resistanceBasis,
                draft.weightInterpretation
            )
        )
    }

    fun applyResistance(draft: ExerciseDraft, resistanceBasis: ResistanceBasis): ExerciseDraft {
        return draft.copy(
            resistanceBasis = resistanceBasis,
            weightInterpretation = resolvedWeightInterpretation(
                draft.measurementType,
                resistanceBasis,
                draft.weightInterpretation
            )
        )
    }

    fun isWeightInterpretationVisible(
        measurementType: MeasurementType,
        resistanceBasis: ResistanceBasis
    ): Boolean {
        return resistanceBasis == ResistanceBasis.EXTERNAL &&
            measurementType.recordsExternalWeight()
    }

    fun bodyweightAllowsPerSetLoad(resistanceBasis: ResistanceBasis): Boolean {
        return resistanceBasis == ResistanceBasis.BODYWEIGHT
    }

    fun normalizeSecondary(primary: MuscleGroup, secondary: List<MuscleGroup>): List<MuscleGroup> {
        return secondary
            .filterNot { it == primary }
            .distinct()
    }

    fun validate(draft: ExerciseDraft): List<ExerciseFieldError> {
        val errors = mutableListOf<ExerciseFieldError>()
        val name = ExerciseNaming.displayName(draft.name)
        if (name.isEmpty()) {
            errors += ExerciseFieldError.NameBlank
        } else if (name.length > ExerciseNaming.NAME_MAX_LENGTH) {
            errors += ExerciseFieldError.NameTooLong
        }
        val notes = draft.notes.trim()
        if (notes.length > ExerciseNaming.NOTES_MAX_LENGTH) {
            errors += ExerciseFieldError.NotesTooLong
        }
        if (draft.primaryMuscle in draft.secondaryMuscles) {
            errors += ExerciseFieldError.PrimaryAlsoSecondary
        }
        val visible = isWeightInterpretationVisible(draft.measurementType, draft.resistanceBasis)
        if (visible && draft.weightInterpretation == WeightInterpretation.NOT_APPLICABLE) {
            errors += ExerciseFieldError.WeightInterpretationRequired
        }
        return errors
    }

    fun resolvedWeightInterpretation(
        measurementType: MeasurementType,
        resistanceBasis: ResistanceBasis,
        current: WeightInterpretation
    ): WeightInterpretation {
        return if (isWeightInterpretationVisible(measurementType, resistanceBasis)) {
            if (current == WeightInterpretation.NOT_APPLICABLE) {
                WeightInterpretation.TOTAL
            } else {
                current
            }
        } else {
            WeightInterpretation.NOT_APPLICABLE
        }
    }
}

fun MeasurementType.recordsExternalWeight(): Boolean {
    return this == MeasurementType.REPETITIONS_AND_WEIGHT ||
        this == MeasurementType.DURATION_AND_WEIGHT
}

object ExerciseCatalogLogic {
    fun filter(
        exercises: List<Exercise>,
        query: String,
        category: ExerciseCategory?,
        muscle: MuscleGroup?,
        archiveFilter: ArchiveFilter
    ): List<Exercise> {
        val needle = ExerciseNaming.normalize(query)
        return exercises.filter { exercise ->
            val archiveMatch = when (archiveFilter) {
                ArchiveFilter.ACTIVE -> !exercise.archived
                ArchiveFilter.ARCHIVED -> exercise.archived
            }
            val categoryMatch = category == null || exercise.category == category
            val muscleMatch = muscle == null ||
                exercise.primaryMuscle == muscle ||
                muscle in exercise.secondaryMuscles
            val searchMatch = needle.isEmpty() ||
                exercise.normalizedName.contains(needle) ||
                ExerciseNaming.normalize(exercise.notes.orEmpty()).contains(needle)
            archiveMatch && categoryMatch && muscleMatch && searchMatch
        }
    }

    fun hasActiveFilters(
        query: String,
        category: ExerciseCategory?,
        muscle: MuscleGroup?,
        archiveFilter: ArchiveFilter
    ): Boolean {
        return query.isNotBlank() ||
            category != null ||
            muscle != null ||
            archiveFilter == ArchiveFilter.ARCHIVED
    }
}

object ExerciseEnumCodec {
    fun category(raw: String): ExerciseCategory {
        return parse(raw, ExerciseCategory.STRENGTH)
    }

    fun movement(raw: String): MovementPattern {
        return parse(raw, MovementPattern.OTHER)
    }

    fun measurement(raw: String): MeasurementType {
        return parse(raw, MeasurementType.COMPLETION_ONLY)
    }

    fun resistance(raw: String): ResistanceBasis {
        return parse(raw, ResistanceBasis.NONE)
    }

    fun weight(raw: String): WeightInterpretation {
        return parse(raw, WeightInterpretation.NOT_APPLICABLE)
    }

    fun muscle(raw: String): MuscleGroup? {
        return runCatching { MuscleGroup.valueOf(raw) }.getOrNull()
    }

    fun muscleOrFallback(raw: String): MuscleGroup {
        return muscle(raw) ?: MuscleGroup.FULL_BODY
    }

    fun role(raw: String): MuscleRole {
        return parse(raw, MuscleRole.SECONDARY)
    }

    private inline fun <reified T : Enum<T>> parse(raw: String, fallback: T): T {
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
