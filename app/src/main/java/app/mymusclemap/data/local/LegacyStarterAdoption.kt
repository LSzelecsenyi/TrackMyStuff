package app.mymusclemap.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.exercise.StarterCatalog

/**
 * Assigns an official catalog key to one recognizable legacy starter.
 * A row qualifies only when its normalized name matches one catalog entry,
 * custom is false, and it does not already have a catalog key.
 * The row's id, name, and measurement fields are left as they are.
 * A custom exercise is never claimed, and a key is never given to two rows.
 */
object LegacyStarterAdoption {
    fun adopt(db: SupportSQLiteDatabase) {
        StarterCatalog.entries.forEach { entry ->
            db.execSQL(
                """
                UPDATE exercises
                SET catalogKey = ?, customNormalizedName = NULL
                WHERE id = (
                    SELECT id FROM exercises
                    WHERE custom = 0
                      AND catalogKey IS NULL
                      AND normalizedName = ?
                      AND NOT EXISTS (
                          SELECT 1 FROM exercises AS claimed WHERE claimed.catalogKey = ?
                      )
                    ORDER BY id
                    LIMIT 1
                )
                """.trimIndent(),
                arrayOf(
                    entry.catalogKey,
                    ExerciseNaming.normalize(entry.draft.name),
                    entry.catalogKey
                )
            )
        }
    }
}
