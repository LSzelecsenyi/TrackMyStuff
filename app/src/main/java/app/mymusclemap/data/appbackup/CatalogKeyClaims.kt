package app.mymusclemap.data.appbackup

import app.mymusclemap.data.local.ExerciseEntity
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.exercise.StarterCatalog

/**
 * A backup may name an official catalog key, but only when that key is a real
 * catalog entry, the row still uses that entry's name, and no other row in the
 * file claims it. Anything else is stored as a user row. Official definitions
 * are never copied over the restored fields.
 */
object CatalogKeyClaims {
    fun sanitize(rows: List<ExerciseEntity>): List<ExerciseEntity> {
        val accepted = mutableSetOf<String>()
        return rows.map { row ->
            val key = row.catalogKey
            val definition = key?.let { StarterCatalog.definition(it) }
            val nameMatches = definition != null &&
                ExerciseNaming.normalize(definition.draft.name) == row.normalizedName
            if (key != null && definition != null && nameMatches && accepted.add(key)) {
                row.copy(catalogKey = key, custom = false, customNormalizedName = null)
            } else {
                row.copy(catalogKey = null, customNormalizedName = row.customNormalizedName)
            }
        }
    }
}
