package app.mymusclemap.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException

data class AccountDatabaseMigrationFailure(
    val fileName: String,
    val message: String
)

class AccountDatabaseMigrationException(
    val fileName: String,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Moves databases/account_<userId>.db into files/strict_user_data/.
 * The original file is deleted only after the destination passes integrity_check
 * and its user_version matches. A failed or interrupted attempt leaves the original in place.
 */
class AccountDatabaseMigrator(context: Context) {
    private val appContext = context.applicationContext

    fun migrateAll(): List<AccountDatabaseMigrationFailure> {
        val root = AccountDatabaseLocation.databasesDirectory(appContext)
        val names = root.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && AccountDatabaseLocation.isAccountDatabase(it.name) }
            ?.map { it.name }
            ?.sorted()
            ?.toList()
            .orEmpty()
        return names.mapNotNull { name ->
            try {
                migrateNamed(name)
                null
            } catch (error: AccountDatabaseMigrationException) {
                AccountDatabaseMigrationFailure(name, error.message ?: "migration failed")
            }
        }
    }

    fun migrateNamed(fileName: String) {
        if (!AccountDatabaseLocation.isAccountDatabase(fileName)) {
            throw AccountDatabaseMigrationException(fileName, "Not an account database name")
        }
        val source = AccountDatabaseLocation.legacyAccountFile(appContext, fileName)
        val destination = AccountDatabaseLocation.accountFile(appContext, fileName)
        destination.parentFile?.mkdirs()
        val partial = partialFile(fileName)
        partial.parentFile?.mkdirs()

        if (!source.exists() && !destination.exists()) {
            deleteQuiet(partial)
            return
        }
        if (!source.exists() && destination.exists()) {
            if (!sqliteIntegrityOk(destination)) {
                throw AccountDatabaseMigrationException(
                    fileName,
                    "Account database failed integrity check and the original file is gone"
                )
            }
            deleteQuiet(partial)
            return
        }

        if (destination.exists() && marker(fileName).isFile && sqliteIntegrityOk(destination)) {
            deleteSourceFamily(source)
            deleteQuiet(partial)
            return
        }
        if (destination.exists() && !marker(fileName).isFile) {
            throw AccountDatabaseMigrationException(
                fileName,
                "Account database exists in both the old and new locations without a completed migration marker"
            )
        }
        if (destination.exists() && !sqliteIntegrityOk(destination)) {
            if (!deleteDatabaseFile(destination)) {
                throw AccountDatabaseMigrationException(
                    fileName,
                    "Could not replace an incomplete destination database"
                )
            }
            marker(fileName).delete()
        }

        deleteQuiet(partial)
        if (!looksLikeSqlite(source)) {
            throw AccountDatabaseMigrationException(
                fileName,
                "The original file is not a SQLite database. It was left in place."
            )
        }
        val checkpoint = try {
            checkpointWalTruncating(source)
        } catch (error: Exception) {
            throw AccountDatabaseMigrationException(
                fileName,
                "Could not checkpoint the original database. It was left in place.",
                error
            )
        }
        if (!checkpoint.complete) {
            throw AccountDatabaseMigrationException(
                fileName,
                "WAL checkpoint did not finish (busy=${checkpoint.busy}, log=${checkpoint.log}, " +
                    "checkpointed=${checkpoint.checkpointed}). The original database was left in place."
            )
        }
        val version = sqliteUserVersion(source)
            ?: throw AccountDatabaseMigrationException(
                fileName,
                "Could not read the original database. It was left in place."
            )
        try {
            source.copyTo(partial, overwrite = true)
        } catch (error: Exception) {
            deleteQuiet(partial)
            throw AccountDatabaseMigrationException(
                fileName,
                "Could not copy the original database. It was left in place.",
                error
            )
        }
        val copiedIntegrity = sqliteIntegrityMessage(partial)
        val copiedVersion = sqliteUserVersion(partial)
        if (copiedIntegrity != "ok" || copiedVersion != version) {
            deleteQuiet(partial)
            throw AccountDatabaseMigrationException(
                fileName,
                "The copied database failed verification (integrity=$copiedIntegrity, version=$copiedVersion, expected=$version). The original was left in place."
            )
        }
        deleteSidecars(destination)
        if (!partial.renameTo(destination)) {
            try {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            } catch (error: Exception) {
                deleteQuiet(partial)
                deleteDatabaseFile(destination)
                throw AccountDatabaseMigrationException(
                    fileName,
                    "Could not place the verified copy. The original was left in place.",
                    error
                )
            }
        }
        if (!sqliteIntegrityOk(destination) || sqliteUserVersion(destination) != version) {
            deleteDatabaseFile(destination)
            throw AccountDatabaseMigrationException(
                fileName,
                "The destination database failed verification after it was placed. The original was left in place."
            )
        }
        writeMarker(fileName)
        deleteSourceFamily(source)
        deleteQuiet(partial)
    }

    private fun partialFile(fileName: String): File {
        // Keep the partial beside the legacy databases, outside the backed-up directory.
        // A nested cache path can exceed the SQLite path limit on long test sandboxes.
        return File(AccountDatabaseLocation.databasesDirectory(appContext), "$fileName.partial")
    }

    private fun marker(fileName: String): File {
        return File(appContext.noBackupFilesDir, "$MARKER_PREFIX$fileName")
    }

    private fun writeMarker(fileName: String) {
        val file = marker(fileName)
        file.parentFile?.mkdirs()
        file.writeText("ok")
    }

    private fun deleteSourceFamily(source: File) {
        deleteDatabaseFile(source)
    }

    private fun deleteDatabaseFile(file: File): Boolean {
        deleteSidecars(file)
        if (!file.exists()) {
            return true
        }
        runCatching { SQLiteDatabase.deleteDatabase(file) }
        if (file.exists()) {
            file.delete()
        }
        deleteSidecars(file)
        return !file.exists()
    }

    private fun deleteSidecars(file: File) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            File(file.path + suffix).delete()
        }
    }

    private fun looksLikeSqlite(file: File): Boolean {
        if (!file.isFile || file.length() < 16) {
            return false
        }
        val header = ByteArray(16)
        return try {
            file.inputStream().use { input -> input.read(header) }
            String(header, Charsets.UTF_8) == "SQLite format 3\u0000"
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteQuiet(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    private companion object {
        const val MARKER_PREFIX = "migrated_"
    }
}
