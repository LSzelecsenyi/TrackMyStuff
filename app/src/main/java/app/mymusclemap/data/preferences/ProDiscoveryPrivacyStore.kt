package app.mymusclemap.data.preferences

import app.mymusclemap.domain.entitlement.PrivacyPolicyNotice
import app.mymusclemap.domain.entitlement.ProDiscoverySyncChoice
import app.mymusclemap.domain.entitlement.ProDiscoverySyncDeletion
import app.mymusclemap.domain.entitlement.ProDiscoverySyncPermission
import java.io.File
import java.util.UUID

/**
 * Device-level record of the privacy-policy notice the person dismissed.
 * Reinstalling the app removes this file, so the current notice is shown again.
 * It is not stored per account and it is not an activity-sync permission.
 */
class PrivacyNoticeStore(private val file: File) {
    fun acknowledgedVersion(): Int {
        val text = if (file.isFile) file.readText() else return 0
        return text.trim().toIntOrNull() ?: 0
    }

    fun acknowledge(version: Int = PrivacyPolicyNotice.CURRENT_VERSION) {
        file.parentFile?.mkdirs()
        file.writeText(version.toString())
    }
}

/**
 * Stable random installation id. It names one app install's activity snapshot.
 * It is not an account credential. A reinstall creates a new id and leaves the
 * previous installation's server snapshot in place.
 */
class InstallationIdStore(private val file: File) {
    fun getOrCreate(): String {
        val existing = if (file.isFile) file.readText().trim() else ""
        if (existing.isNotEmpty()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        file.parentFile?.mkdirs()
        file.writeText(created)
        return created
    }
}

/**
 * Account-scoped Pro Discovery sync choice. The file name includes the account id,
 * so a second account on the same install does not read the first account's choice.
 */
class ProDiscoverySyncPermissionStore(private val file: File) {
    fun read(): ProDiscoverySyncPermission {
        if (!file.isFile) {
            return ProDiscoverySyncPermission()
        }
        val values = file.readLines()
            .mapNotNull { line ->
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
        val choice = runCatching { ProDiscoverySyncChoice.valueOf(values["choice"].orEmpty()) }
            .getOrDefault(ProDiscoverySyncChoice.UNSET)
        return ProDiscoverySyncPermission(
            choice = choice,
            generation = values["generation"]?.toLongOrNull() ?: 0L,
            scopeVersion = values["scope"]?.toIntOrNull() ?: ProDiscoverySyncPermission.SCOPE_VERSION,
            deletion = runCatching { ProDiscoverySyncDeletion.valueOf(values["deletion"].orEmpty()) }
                .getOrDefault(ProDiscoverySyncDeletion.NONE)
        )
    }

    fun write(permission: ProDiscoverySyncPermission) {
        file.parentFile?.mkdirs()
        file.writeText(
            listOf(
                "choice=${permission.choice.name}",
                "generation=${permission.generation}",
                "scope=${permission.scopeVersion}",
                "deletion=${permission.deletion.name}"
            ).joinToString("\n")
        )
    }
}
