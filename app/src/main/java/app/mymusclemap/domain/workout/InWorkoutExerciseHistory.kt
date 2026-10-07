package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import java.time.LocalDate

/**
 * Previous performance for the exercise occurrence that is current in an active workout.
 *
 * Occurrence identity is not a template-exercise id. Session rows do not store one.
 * The same catalog exercise may appear more than once in one workout; each row stays
 * its own occurrence. The ordinal is the 0-based index of that row among rows with the
 * same [SessionExercise.exerciseId] in that session, ordered by position then row id.
 * History never merges those set lists.
 *
 * The selected occurrence is one completed session's matching ordinal. Sets keep the
 * position they were stored with. A skipped or missing position is not filled by a
 * later completed set.
 */
data class ExerciseHistoryRequest(
    val currentSessionId: Long,
    val currentStartedAt: Long,
    val currentTemplateId: Long?,
    val currentMeasurement: MeasurementType,
    val occurrenceOrdinal: Int
)

data class ExerciseHistoryCandidate(
    val sessionId: Long,
    val templateId: Long?,
    val templateName: String,
    val workoutDate: LocalDate,
    val startedAt: Long,
    val status: SessionStatus,
    val occurrences: List<ExerciseHistoryOccurrence>
)

data class ExerciseHistoryOccurrence(
    val sessionExerciseId: Long,
    val position: Int,
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val weightInterpretation: WeightInterpretation,
    val sets: List<ExerciseHistorySet>
)

data class ExerciseHistorySet(
    val position: Int,
    val status: SessionSetStatus,
    val reps: Int? = null,
    val loadKind: PlannedLoadKind? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null
)

data class ExerciseHistorySelection(
    val sessionId: Long,
    val templateId: Long?,
    val templateName: String,
    val workoutDate: LocalDate,
    val startedAt: Long,
    val fromOtherPlan: Boolean,
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val weightInterpretation: WeightInterpretation,
    val sets: List<ExerciseHistorySet>
)

sealed interface InWorkoutExerciseHistory {
    data object Loading : InWorkoutExerciseHistory
    data object None : InWorkoutExerciseHistory
    data class Found(
        val sessionExerciseId: Long,
        val selection: ExerciseHistorySelection
    ) : InWorkoutExerciseHistory
}

sealed interface CurrentSetHistoryLine {
    data object Hidden : CurrentSetHistoryLine
    data object NoPreviousData : CurrentSetHistoryLine
    data class NoMatchingSet(val selection: ExerciseHistorySelection) : CurrentSetHistoryLine
    data class Recorded(
        val set: ExerciseHistorySet,
        val selection: ExerciseHistorySelection
    ) : CurrentSetHistoryLine
}

/**
 * Rejects a history result that finished after the active exercise changed.
 * [begin] and [accepts] are called on the main thread.
 */
class ExerciseHistoryGate {
    private var generation = 0L
    private var exerciseId: Long? = null

    fun begin(exerciseId: Long?): Long {
        this.exerciseId = exerciseId
        generation += 1L
        return generation
    }

    fun accepts(requestGeneration: Long, requestExerciseId: Long?): Boolean {
        return requestGeneration == generation && requestExerciseId == exerciseId
    }
}

object ExerciseHistoryLogic {
    fun occurrenceOrdinal(exercises: List<SessionExercise>, sessionExerciseId: Long): Int? {
        val target = exercises.firstOrNull { it.id == sessionExerciseId } ?: return null
        return exercises
            .asSequence()
            .filter { it.exerciseId == target.exerciseId }
            .sortedWith(compareBy<SessionExercise> { it.position }.thenBy { it.id })
            .indexOfFirst { it.id == sessionExerciseId }
            .takeIf { it >= 0 }
    }

    fun select(
        request: ExerciseHistoryRequest,
        candidates: List<ExerciseHistoryCandidate>
    ): ExerciseHistorySelection? {
        val eligible = candidates.mapNotNull { candidate ->
            toSelection(request, candidate)?.let { candidate to it }
        }
        if (eligible.isEmpty()) return null
        val samePlan = if (request.currentTemplateId == null) {
            emptyList()
        } else {
            eligible.filter { (candidate, _) -> candidate.templateId == request.currentTemplateId }
        }
        val pool = if (samePlan.isNotEmpty()) samePlan else eligible
        return pool.maxWith(compareBy<Pair<ExerciseHistoryCandidate, ExerciseHistorySelection>> { it.first.startedAt }
            .thenBy { it.first.sessionId }).second
    }

    fun matchingSet(selection: ExerciseHistorySelection, setPosition: Int): ExerciseHistorySet? {
        return selection.sets.firstOrNull { set ->
            set.position == setPosition && set.status == SessionSetStatus.COMPLETED
        }
    }

    fun currentLine(
        history: InWorkoutExerciseHistory,
        sessionExerciseId: Long?,
        setPosition: Int
    ): CurrentSetHistoryLine {
        return when (history) {
            InWorkoutExerciseHistory.Loading -> CurrentSetHistoryLine.Hidden
            InWorkoutExerciseHistory.None -> CurrentSetHistoryLine.NoPreviousData
            is InWorkoutExerciseHistory.Found -> {
                if (history.sessionExerciseId != sessionExerciseId) {
                    CurrentSetHistoryLine.Hidden
                } else {
                    val match = matchingSet(history.selection, setPosition)
                    if (match == null) {
                        CurrentSetHistoryLine.NoMatchingSet(history.selection)
                    } else {
                        CurrentSetHistoryLine.Recorded(match, history.selection)
                    }
                }
            }
        }
    }

    private fun toSelection(
        request: ExerciseHistoryRequest,
        candidate: ExerciseHistoryCandidate
    ): ExerciseHistorySelection? {
        if (!isEligibleSession(request, candidate)) return null
        val occurrence = candidate.occurrences
            .sortedWith(compareBy<ExerciseHistoryOccurrence> { it.position }.thenBy { it.sessionExerciseId })
            .getOrNull(request.occurrenceOrdinal)
            ?: return null
        if (occurrence.measurementType != request.currentMeasurement) return null
        val completed = occurrence.sets
            .filter { it.status == SessionSetStatus.COMPLETED }
            .sortedWith(compareBy<ExerciseHistorySet> { it.position })
        if (completed.isEmpty()) return null
        val samePlan = request.currentTemplateId != null &&
            candidate.templateId == request.currentTemplateId
        return ExerciseHistorySelection(
            sessionId = candidate.sessionId,
            templateId = candidate.templateId,
            templateName = candidate.templateName,
            workoutDate = candidate.workoutDate,
            startedAt = candidate.startedAt,
            fromOtherPlan = request.currentTemplateId != null && !samePlan,
            measurementType = occurrence.measurementType,
            resistanceBasis = occurrence.resistanceBasis,
            weightInterpretation = occurrence.weightInterpretation,
            sets = completed
        )
    }

    private fun isEligibleSession(
        request: ExerciseHistoryRequest,
        candidate: ExerciseHistoryCandidate
    ): Boolean {
        if (candidate.sessionId == request.currentSessionId) return false
        if (candidate.status != SessionStatus.COMPLETED) return false
        return candidate.startedAt < request.currentStartedAt ||
            (candidate.startedAt == request.currentStartedAt && candidate.sessionId < request.currentSessionId)
    }
}
