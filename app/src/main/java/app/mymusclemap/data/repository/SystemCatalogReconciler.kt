package app.mymusclemap.data.repository

import androidx.room.withTransaction
import app.mymusclemap.data.local.LegacyStarterAdoption
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.StarterCatalog
import app.mymusclemap.domain.exercise.SystemExerciseDefinition

/**
 * Claims recognizable legacy starters, then inserts official catalog rows
 * that this account still does not have. Existing rows are never deleted
 * or given a new id, and their names and measurement settings are not replaced.
 */
class SystemCatalogReconciler(
    private val database: WeightDatabase,
    private val exerciseRepository: ExerciseRepository,
    private val entries: List<SystemExerciseDefinition> = StarterCatalog.entries
) {
    suspend fun reconcile() {
        database.withTransaction {
            database.exerciseDao().backfillCustomNormalizedNames()
            LegacyStarterAdoption.adopt(database.openHelper.writableDatabase)
            val present = database.exerciseDao().existingCatalogKeys().toSet()
            entries.forEach { entry ->
                if (entry.catalogKey in present) {
                    return@forEach
                }
                val result = exerciseRepository.save(entry.draft, catalogKey = entry.catalogKey)
                check(result is ExerciseSaveResult.Created) {
                    "System catalog insert failed for ${entry.catalogKey}: $result"
                }
            }
        }
    }
}
