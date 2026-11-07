package app.mymusclemap.data.preferences

import app.mymusclemap.domain.entitlement.PrivacyPolicyNotice
import java.io.File

/**
 * Device-level record of the privacy-policy notice the person dismissed.
 * Reinstalling the app removes this file, so the current notice is shown again.
 * It is not stored per account and it is not a Pro trial or purchase.
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
