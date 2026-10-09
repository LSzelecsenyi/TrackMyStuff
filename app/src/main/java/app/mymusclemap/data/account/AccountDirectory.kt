package app.mymusclemap.data.account

import android.content.Context
import app.mymusclemap.domain.account.LegacyClaim
import app.mymusclemap.domain.account.accountDatabaseName
import app.mymusclemap.domain.account.accountPhotoDirectory
import app.mymusclemap.domain.account.legacyClaim
import org.json.JSONObject
import java.io.File

data class VerifiedAccountProfile(
    val userId: String,
    val email: String?,
    val displayName: String?
)

/**
 * Which Strict account is open on this installation, and the one-time claim of
 * pre-account workout files. Sign-out clears the active pointer and leaves the files.
 */
class AccountDirectory(context: Context) {
    private val appContext = context.applicationContext
    private val root = appContext.noBackupFilesDir

    fun activeUserId(): String? = read(ACTIVE)?.takeIf { it.isNotBlank() }

    fun profile(): VerifiedAccountProfile? {
        val raw = read(PROFILE) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val userId = json.optString("userId")
        if (runCatching { java.util.UUID.fromString(userId) }.isFailure) {
            return null
        }
        return VerifiedAccountProfile(
            userId = userId,
            email = json.optString("email").ifBlank { null },
            displayName = json.optString("displayName").ifBlank { null }
        )
    }

    fun activate(profile: VerifiedAccountProfile) {
        val target = appContext.getDatabasePath(accountDatabaseName(profile.userId))
        if (read(CLAIM) == null && !legacyDatabase().isFile && target.isFile) {
            write(CLAIM, profile.userId)
        }
        write(ACTIVE, profile.userId)
        write(
            PROFILE,
            JSONObject()
                .put("userId", profile.userId)
                .put("email", profile.email.orEmpty())
                .put("displayName", profile.displayName.orEmpty())
                .toString()
        )
    }

    fun clearActive() {
        File(root, ACTIVE).delete()
    }

    fun legacyUnclaimed(): Boolean {
        return legacyDatabase().isFile && read(CLAIM) == null
    }

    fun claimLegacy(userId: String): LegacyClaim {
        val legacyExists = legacyDatabase().isFile
        val decision = legacyClaim(legacyExists, read(CLAIM), userId)
        if (decision == LegacyClaim.ALREADY_MINE || decision == LegacyClaim.OWNED_BY_OTHER) {
            return decision
        }
        val target = appContext.getDatabasePath(accountDatabaseName(userId))
        if (!legacyExists) {
            if (target.isFile) {
                finishClaim(userId)
                return LegacyClaim.ALREADY_MINE
            }
            return LegacyClaim.NOTHING_TO_CLAIM
        }
        if (target.isFile || !moveDatabase(userId)) {
            return LegacyClaim.OWNED_BY_OTHER
        }
        finishClaim(userId)
        return LegacyClaim.MOVE
    }

    private fun finishClaim(userId: String) {
        movePhotos(userId)
        copyPreference("pro_discovery", "pro_discovery_$userId")
        copyPreference("verified_play_subscription", "verified_play_subscription_$userId")
        copyPreference("founder_program", "founder_program_$userId")
        write(CLAIM, userId)
    }

    private fun moveDatabase(userId: String): Boolean {
        val legacy = legacyDatabase()
        val target = appContext.getDatabasePath(accountDatabaseName(userId))
        if (target.isFile) {
            return false
        }
        target.parentFile?.mkdirs()
        if (!legacy.renameTo(target)) {
            return false
        }
        File(legacy.path + "-wal").let { wal ->
            if (wal.isFile) wal.renameTo(File(target.path + "-wal"))
        }
        File(legacy.path + "-shm").let { shm ->
            if (shm.isFile) shm.renameTo(File(target.path + "-shm"))
        }
        return true
    }

    private fun movePhotos(userId: String) {
        val legacy = File(appContext.filesDir, "progress_photos")
        val target = File(appContext.filesDir, accountPhotoDirectory(userId))
        if (legacy.isDirectory && !target.exists()) {
            legacy.renameTo(target)
        }
    }

    private fun copyPreference(from: String, to: String) {
        val directory = File(appContext.filesDir, "datastore")
        val source = File(directory, "$from.preferences_pb")
        val target = File(directory, "$to.preferences_pb")
        if (!source.isFile || target.exists()) {
            return
        }
        directory.mkdirs()
        source.copyTo(target, overwrite = false)
    }

    private fun legacyDatabase(): File = appContext.getDatabasePath("weight_tracker.db")

    private fun read(name: String): String? {
        val file = File(root, name)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    private fun write(name: String, value: String) {
        root.mkdirs()
        val file = File(root, name)
        val temporary = File(root, "$name.tmp")
        temporary.writeText(value)
        if (!temporary.renameTo(file)) {
            file.writeText(value)
            temporary.delete()
        }
    }

    private companion object {
        const val ACTIVE = "active_account.txt"
        const val PROFILE = "verified_account.json"
        const val CLAIM = "legacy_claim.txt"
    }
}
