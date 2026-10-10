package app.mymusclemap.data.account

import android.content.Context
import app.mymusclemap.domain.account.accountDatabaseName
import app.mymusclemap.domain.account.accountPhotoDirectory
import java.io.File
import java.util.UUID

/**
 * Deletes files that belong to one Strict account.
 *
 * Exported ZIP backups, Android shared storage, and another account's database
 * are outside this eraser. Theme, language, and lock-screen preferences stay.
 * The founder workout queue is not keyed by account, so it is cleared: leaving
 * it would let the next sign-in upload the deleted account's queued events.
 */
class AccountLocalDataEraser(context: Context) {
    private val appContext = context.applicationContext

    fun pendingUserId(): String? {
        val raw = pendingFile().takeIf { it.isFile }?.readText()?.trim().orEmpty()
        return raw.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
    }

    fun markPending(userId: String): Boolean {
        if (runCatching { UUID.fromString(userId) }.isFailure) {
            return false
        }
        return runCatching {
            pendingFile().parentFile?.mkdirs()
            pendingFile().writeText(userId)
        }.isSuccess && pendingUserId() == userId
    }

    fun clearPending() {
        pendingFile().delete()
    }

    fun erase(userId: String): Boolean {
        val id = runCatching { UUID.fromString(userId).toString() }.getOrNull() ?: return false
        val failures = mutableListOf<File>()
        deleteDatabase(accountDatabaseName(id), failures)
        deleteRecursively(File(appContext.filesDir, accountPhotoDirectory(id)), failures)
        val datastore = File(appContext.filesDir, "datastore")
        datastore.listFiles()?.forEach { file ->
            if (file.name.contains(id) || file.name in DEVICE_SCOPED_FOUNDER_FILES) {
                deleteRecursively(file, failures)
            }
        }
        return failures.isEmpty()
    }

    private fun deleteDatabase(name: String, failures: MutableList<File>) {
        val database = appContext.getDatabasePath(name)
        listOf(database, File(database.path + "-wal"), File(database.path + "-shm")).forEach { file ->
            if (file.exists() && !file.delete()) {
                failures += file
            }
        }
    }

    private fun deleteRecursively(file: File, failures: MutableList<File>) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteRecursively(child, failures) }
        }
        if (file.exists() && !file.delete()) {
            failures += file
        }
    }

    private fun pendingFile(): File = File(appContext.noBackupFilesDir, PENDING)

    private companion object {
        const val PENDING = "pending_account_erasure.txt"
        val DEVICE_SCOPED_FOUNDER_FILES = setOf(
            "founder_workout_outbox.preferences_pb",
            "founder_milestone_acknowledgements.preferences_pb",
            "founder_entitlement_cache.preferences_pb"
        )
    }
}
