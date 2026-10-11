package app.mymusclemap.data.local

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException

data class WalCheckpoint(
    val busy: Int,
    val log: Int,
    val checkpointed: Int
) {
    /** A checkpoint request is complete only when no frames were left behind. */
    val complete: Boolean get() = busy == 0 && log == checkpointed
}

fun checkpointWalTruncating(file: File): WalCheckpoint {
    val database = SQLiteDatabase.openDatabase(
        file.absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE
    )
    try {
        database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.columnCount < 3) {
                throw IOException("SQLite checkpoint returned no result for ${file.name}")
            }
            return WalCheckpoint(
                busy = cursor.getInt(0),
                log = cursor.getInt(1),
                checkpointed = cursor.getInt(2)
            )
        }
    } finally {
        database.close()
    }
}

fun requireCompleteCheckpoint(file: File, checkpoint: WalCheckpoint) {
    if (!checkpoint.complete) {
        throw IOException(
            "SQLite checkpoint for ${file.name} did not commit the WAL " +
                "(busy=${checkpoint.busy}, log=${checkpoint.log}, checkpointed=${checkpoint.checkpointed})"
        )
    }
}

fun sqliteIntegrityOk(file: File): Boolean = sqliteIntegrityMessage(file) == "ok"

fun sqliteIntegrityMessage(file: File): String {
    if (!file.isFile || file.length() == 0L) {
        return "missing"
    }
    return try {
        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    "empty"
                } else {
                    val message = cursor.getString(0) ?: "null"
                    if (cursor.moveToNext()) "multiple" else message
                }
            }
        } finally {
            database.close()
        }
    } catch (error: Exception) {
        error.javaClass.simpleName + ":" + error.message
    }
}

private val ACCOUNT_SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

/**
 * Drops WAL and rollback-journal files that belonged to an account database
 * replaced by this restore and were not themselves restored.
 * Journal mode is not consulted: a sidecar restored with its database stays,
 * and every account database absent from [restoredFiles] stays untouched.
 * This does not open a database connection.
 */
fun discardUnrestoredAccountSidecars(restoredFiles: Collection<File>) {
    val restoredPaths = restoredFiles.mapTo(HashSet()) { it.absolutePath }
    restoredFiles
        .filter { AccountDatabaseLocation.isAccountDatabase(it.name) }
        .forEach { database ->
            val parent = database.parentFile ?: return@forEach
            ACCOUNT_SIDECAR_SUFFIXES.forEach { suffix ->
                val sidecar = File(parent, database.name + suffix)
                if (sidecar.absolutePath !in restoredPaths) {
                    sidecar.delete()
                }
            }
        }
}

fun sqliteUserVersion(file: File): Int? {
    return try {
        val database = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            database.version
        } finally {
            database.close()
        }
    } catch (_: Exception) {
        null
    }
}
