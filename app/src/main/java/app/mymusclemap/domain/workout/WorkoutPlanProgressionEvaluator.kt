package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.WeightInterpretation
import kotlin.math.abs

/**
 * Decides which completed planned sets may replace their saved plan targets.
 * Personal records are a separate concept and are not consulted here.
 */
object WorkoutPlanProgressionEvaluator {
    fun evaluate(
        aggregate: WorkoutSessionAggregate,
        template: WorkoutTemplate?,
        templateExercises: List<WorkoutTemplateExercise>,
        templateSets: List<WorkoutTemplateSet>
    ): PlanProgressionProposal {
        val session = aggregate.session
        val templateId = session.templateId
        if (session.status != SessionStatus.COMPLETED ||
            session.planProgressionApplied ||
            templateId == null ||
            template == null ||
            template.id != templateId
        ) {
            return PlanProgressionProposal.EMPTY
        }
        val exercisesById = templateExercises.filter { it.templateId == templateId }.associateBy { it.id }
        val setsById = templateSets.associateBy { it.id }
        val sessionBySource = aggregate.exercises
            .mapNotNull { item -> item.exercise.sourceTemplateExerciseId?.let { it to item } }
            .toMap()
        val namesInPlanOrder = templateExercises
            .filter { it.templateId == templateId }
            .sortedBy { it.position }
            .map { exercise ->
                exercise.id to sessionBySource[exercise.id]?.exercise?.name.orEmpty()
            }
        val nameCounts = namesInPlanOrder.groupingBy { it.second }.eachCount()
        val candidates = mutableListOf<PlanSetProgression>()
        aggregate.exercises.forEach { item ->
            val exercise = item.exercise
            val sourceExerciseId = exercise.sourceTemplateExerciseId
            val sourceExercise = sourceExerciseId?.let { exercisesById[it] }
            if (exercise.addedDuringWorkout || sourceExercise == null) {
                return@forEach
            }
            item.sets.forEach { set ->
                val sourceSetId = set.sourceTemplateSetId ?: return@forEach
                val sourceSet = setsById[sourceSetId]
                if (set.addedDuringWorkout ||
                    set.status != SessionSetStatus.COMPLETED ||
                    sourceSet == null ||
                    sourceSet.templateExerciseId != sourceExercise.id
                ) {
                    return@forEach
                }
                val update = compare(exercise, set, sourceExercise, sourceSet, namesInPlanOrder, nameCounts)
                if (update != null) {
                    candidates += update
                }
            }
        }
        val unique = candidates
            .groupBy { it.sourceTemplateSetId }
            .values
            .mapNotNull { group -> group.singleOrNull() }
            .sortedWith(compareBy({ it.planExercisePosition }, { it.setNumber }, { it.sourceTemplateSetId }))
        return PlanProgressionProposal(unique)
    }

    private fun compare(
        exercise: SessionExercise,
        set: SessionSet,
        sourceExercise: WorkoutTemplateExercise,
        sourceSet: WorkoutTemplateSet,
        namesInPlanOrder: List<Pair<Long, String>>,
        nameCounts: Map<String, Int>
    ): PlanSetProgression? {
        if (!sameTarget(set, sourceSet)) {
            return null
        }
        val plannedReps = singleRepTarget(sourceSet.minReps, sourceSet.maxReps) ?: return null
        val measurement = exercise.measurementType
        if (measurement != MeasurementType.REPETITIONS &&
            measurement != MeasurementType.REPETITIONS_AND_WEIGHT
        ) {
            return null
        }
        val performedReps = set.actualReps ?: return null
        val weight = weightComparison(exercise, set) ?: return null
        val repsDelta = delta(performedReps.compareTo(plannedReps))
        val weightDelta = weight.delta
        if (repsDelta == Change.WORSE || weightDelta == Change.WORSE) {
            return null
        }
        if (repsDelta != Change.IMPROVED && weightDelta != Change.IMPROVED) {
            return null
        }
        val name = exercise.name
        val displayName = if ((nameCounts[name] ?: 0) > 1) {
            val index = namesInPlanOrder.filter { it.second == name }.indexOfFirst { it.first == sourceExercise.id } + 1
            "$name · $index"
        } else {
            name
        }
        val repsChanged = repsDelta == Change.IMPROVED
        val weightChanged = weightDelta == Change.IMPROVED
        return PlanSetProgression(
            sourceTemplateExerciseId = sourceExercise.id,
            sourceTemplateSetId = sourceSet.id,
            sessionSetId = set.id,
            displayName = displayName,
            planExercisePosition = sourceExercise.position,
            setNumber = sourceSet.position + 1,
            beforeReps = plannedReps,
            afterReps = if (repsChanged) performedReps else plannedReps,
            beforeWeightKg = weight.before,
            afterWeightKg = if (weightChanged) weight.after else weight.before,
            loadKind = sourceSet.loadKind,
            weightInterpretation = exercise.weightInterpretation,
            repsChanged = repsChanged,
            weightChanged = weightChanged,
            newMinReps = if (repsChanged) performedReps else plannedReps,
            newMaxReps = if (repsChanged) performedReps else plannedReps,
            newWeightKg = if (weightChanged) weight.after else sourceSet.weightKg
        )
    }

    /**
     * The plan row must still hold the targets copied onto the session.
     * A later edit, including one that kept the same ids, is a conflict.
     */
    private fun sameTarget(set: SessionSet, source: WorkoutTemplateSet): Boolean {
        return set.plannedMinReps == source.minReps &&
            set.plannedMaxReps == source.maxReps &&
            set.plannedLoadKind == source.loadKind &&
            sameNumber(set.plannedWeightKg, source.weightKg) &&
            set.plannedDurationSeconds == source.durationSeconds &&
            sameNumber(set.plannedDistanceMeters, source.distanceMeters)
    }

    private fun singleRepTarget(minReps: Int?, maxReps: Int?): Int? {
        if (minReps == null || maxReps == null || minReps != maxReps || minReps <= 0) {
            return null
        }
        return minReps
    }

    private fun weightComparison(exercise: SessionExercise, set: SessionSet): WeightChange? {
        if (exercise.measurementType == MeasurementType.REPETITIONS) {
            return WeightChange(Change.SAME, null, null)
        }
        val plannedKind = set.plannedLoadKind
        val actualKind = set.actualLoadKind ?: return null
        if (actualKind != plannedKind) {
            return null
        }
        if (plannedKind == PlannedLoadKind.BODYWEIGHT_ONLY || plannedKind == PlannedLoadKind.NONE) {
            return WeightChange(Change.SAME, null, null)
        }
        val tracksWeight = plannedKind == PlannedLoadKind.EXTERNAL_WEIGHT ||
            plannedKind == PlannedLoadKind.ADDED_WEIGHT ||
            plannedKind == PlannedLoadKind.ASSISTANCE
        if (!tracksWeight) {
            return null
        }
        val planned = set.plannedWeightKg ?: return null
        val actual = set.actualWeightKg ?: return null
        val comparison = compareNumber(actual, planned)
        val delta = when (plannedKind) {
            PlannedLoadKind.ASSISTANCE -> delta(-comparison)
            else -> delta(comparison)
        }
        return WeightChange(delta, planned, actual)
    }

    private fun delta(comparison: Int): Change {
        return when {
            comparison > 0 -> Change.IMPROVED
            comparison < 0 -> Change.WORSE
            else -> Change.SAME
        }
    }

    private fun compareNumber(actual: Double, planned: Double): Int {
        val difference = actual - planned
        if (abs(difference) < NUMBER_TOLERANCE) {
            return 0
        }
        return if (difference > 0) 1 else -1
    }

    private fun sameNumber(left: Double?, right: Double?): Boolean {
        if (left == null || right == null) {
            return left == right
        }
        return abs(left - right) < NUMBER_TOLERANCE
    }

    private enum class Change {
        IMPROVED,
        SAME,
        WORSE
    }

    private data class WeightChange(
        val delta: Change,
        val before: Double?,
        val after: Double?
    )

    private const val NUMBER_TOLERANCE = 0.000_000_1
}

data class PlanProgressionProposal(
    val updates: List<PlanSetProgression> = emptyList()
) {
    companion object {
        val EMPTY = PlanProgressionProposal()
    }
}

data class PlanSetProgression(
    val sourceTemplateExerciseId: Long,
    val sourceTemplateSetId: Long,
    val sessionSetId: Long,
    val displayName: String,
    val planExercisePosition: Int,
    val setNumber: Int,
    val beforeReps: Int,
    val afterReps: Int,
    val beforeWeightKg: Double?,
    val afterWeightKg: Double?,
    val loadKind: PlannedLoadKind,
    val weightInterpretation: WeightInterpretation,
    val repsChanged: Boolean,
    val weightChanged: Boolean,
    val newMinReps: Int,
    val newMaxReps: Int,
    val newWeightKg: Double?
)

sealed interface ApplyPlanProgressionResult {
    data class Applied(val updated: Int, val skipped: Int) : ApplyPlanProgressionResult
    data object AlreadyApplied : ApplyPlanProgressionResult
    data object PlanMissing : ApplyPlanProgressionResult
    data object NothingToApply : ApplyPlanProgressionResult
    data object Failed : ApplyPlanProgressionResult
    data object SessionMissing : ApplyPlanProgressionResult
}
