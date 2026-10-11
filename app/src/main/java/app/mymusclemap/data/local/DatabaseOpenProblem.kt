package app.mymusclemap.data.local

import android.database.sqlite.SQLiteException

enum class DatabaseOpenProblem {
    MigrationFailed,
    NewerSchema,
    Unreadable
}

fun databaseOpenProblemFor(error: Throwable): DatabaseOpenProblem? {
    return when (error) {
        is AccountDatabaseMigrationException -> DatabaseOpenProblem.MigrationFailed
        is SQLiteException -> DatabaseOpenProblem.Unreadable
        is IllegalStateException -> {
            val message = error.message.orEmpty()
            if (message.contains("migration", ignoreCase = true) ||
                message.contains("version", ignoreCase = true)
            ) {
                DatabaseOpenProblem.NewerSchema
            } else {
                null
            }
        }
        else -> null
    }
}
