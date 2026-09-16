package hu.laca.weighttracker.domain.workoutimport

enum class WorkoutImportDatabaseFailure {
    Constraint,
    ForeignKey,
    Unknown
}

sealed class WorkoutImportPersistenceResult {
    data class Imported(
        val sessionIds: List<Long>,
        val workoutCount: Int,
        val exerciseCount: Int,
        val completedSetCount: Int,
        val skippedSetCount: Int
    ) : WorkoutImportPersistenceResult()

    data class PlanNotConfirmable(
        val errorCount: Int,
        val unresolvedNames: List<String>
    ) : WorkoutImportPersistenceResult()

    data class DuplicateWorkouts(
        val workoutIds: List<String>
    ) : WorkoutImportPersistenceResult()

    data class DatabaseError(
        val category: WorkoutImportDatabaseFailure
    ) : WorkoutImportPersistenceResult()
}
