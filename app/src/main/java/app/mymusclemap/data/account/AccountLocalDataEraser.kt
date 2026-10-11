package app.mymusclemap.data.account

import android.content.Context
import app.mymusclemap.data.local.AccountDatabaseLocation
import app.mymusclemap.domain.account.accountDatabaseName
import app.mymusclemap.domain.account.accountPhotoDirectory
import java.io.File
import java.util.UUID

/**
 * Deletes files that belong to one Strict account.
 *
 * Exported ZIP backups, Android shared storage, and another account's database
 * are outside this eraser. Theme, language, and lock-screen preferences stay.
 * Founder upload events are removed for this user only. Another account's queued
 * events stay in the outbox, which is outside Auto Backup.
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
        val locations = buildList {
            add(appContext.getDatabasePath(name))
            if (AccountDatabaseLocation.isAccountDatabase(name)) {
                add(AccountDatabaseLocation.accountFile(appContext, name))
                add(File(appContext.getDatabasePath(name).parentFile, "$name.partial"))
            }
        }
        locations.forEach { database ->
            listOf(
                database,
                File(database.path + "-wal"),
                File(database.path + "-shm"),
                File(database.path + "-journal")
            ).forEach { file ->
                if (file.exists() && !file.delete()) {
                    failures += file
                }
            }
        }
        if (AccountDatabaseLocation.isAccountDatabase(name)) {
            File(appContext.noBackupFilesDir, "migrated_$name").delete()
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
            "founder_milestone_acknowledgements.preferences_pb",
            "founder_entitlement_cache.preferences_pb"
        )
    }
}
