package app.mymusclemap.data.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.data.local.AccountDatabaseLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AutoBackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val included = listOf(
        "file" to "strict_user_data",
        "file" to "datastore/weight_tracker_settings.preferences_pb",
        "file" to "datastore/app_language.preferences_pb"
    )

    @Test
    fun manifestEnablesEncryptedFileBackupAndNamesTheAgent() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue(info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertTrue(info.flags and ApplicationInfo.FLAG_FULL_BACKUP_ONLY != 0)
        assertTrue(info.backupAgentName.orEmpty().endsWith("StrictBackupAgent"))
    }

    @Test
    fun legacyFullBackupIncludesOnlyUserDataAndRequiresEncryption() {
        val rules = document(R.xml.backup_rules)
        assertEquals(included, rules.includes.map { it.domain to it.path })
        assertTrue(rules.includes.all { it.requireFlags == "clientSideEncryption" })
        assertTrue(rules.excludes.isEmpty())
        assertFalse(rules.raw.contains("path=\"no_backup\""))
        assertFalse(rules.raw.contains("domain=\"no_backup\""))
    }

    @Test
    fun cloudDeviceTransferAndCrossPlatformUseTheSameBoundaries() {
        val rules = document(R.xml.data_extraction_rules)
        assertEquals("true", rules.sectionAttributes.getValue("cloud-backup")["disableIfNoEncryptionCapabilities"])
        assertNull(rules.sectionAttributes["device-transfer"]?.get("disableIfNoEncryptionCapabilities"))
        assertEquals(included, rules.includesFor("cloud-backup"))
        assertEquals(included, rules.includesFor("device-transfer"))
        assertTrue(rules.includesFor("cross-platform-transfer").isEmpty())
        assertEquals(
            listOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
                "device_root",
                "device_file",
                "device_database",
                "device_sharedpref"
            ),
            rules.excludesFor("cross-platform-transfer").map { it.domain }
        )
        assertTrue(rules.excludesFor("cross-platform-transfer").all { it.path == "." })
        assertTrue(rules.excludesFor("cloud-backup").isEmpty())
        assertTrue(rules.excludesFor("device-transfer").isEmpty())
        listOf(
            "progress_photos",
            "weight_tracker.db",
            "androidx.work.workdb",
            "founder_workout_outbox",
            "founder_entitlement_cache",
            "founder_program",
            "verified_play_subscription",
            "welcome_back",
            "active_account",
            "strict_session"
        ).forEach { excluded ->
            assertFalse(excluded, rules.raw.contains("path=\"$excluded"))
        }
    }

    @Test
    fun photoFilesAndTheSessionDirectoryAreOutsideTheIncludedDatabaseFolder() {
        val photos = File(context.filesDir, "progress_photos_${"11111111-1111-1111-1111-111111111111"}")
        val session = File(context.noBackupFilesDir, "strict_session.bin")
        val databases = AccountDatabaseLocation.userDataDirectory(context)
        assertFalse(photos.absolutePath.startsWith(databases.absolutePath))
        assertFalse(session.absolutePath.startsWith(databases.absolutePath))
        assertEquals("no_backup", context.noBackupFilesDir.name)
    }

    private fun document(resourceId: Int): ParsedRules {
        val parser = context.resources.getXml(resourceId)
        val includes = mutableListOf<Rule>()
        val excludes = mutableListOf<Rule>()
        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var section = "document"
        val raw = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            val name = parser.name
            val attributes = mutableMapOf<String, String>()
            for (index in 0 until parser.attributeCount) {
                attributes[parser.getAttributeName(index)] = parser.getAttributeValue(index)
                raw.append(parser.getAttributeName(index)).append("=\"").append(parser.getAttributeValue(index)).append("\" ")
            }
            when (name) {
                "cloud-backup", "device-transfer", "cross-platform-transfer", "full-backup-content" -> {
                    section = name
                    sections[name] = attributes
                }
                "include" -> includes += Rule(section, attributes["domain"].orEmpty(), attributes["path"].orEmpty(), attributes["requireFlags"])
                "exclude" -> excludes += Rule(section, attributes["domain"].orEmpty(), attributes["path"].orEmpty(), attributes["requireFlags"])
            }
        }
        return ParsedRules(includes, excludes, sections, raw.toString())
    }

    private data class Rule(
        val section: String,
        val domain: String,
        val path: String,
        val requireFlags: String?
    )

    private data class ParsedRules(
        val includes: List<Rule>,
        val excludes: List<Rule>,
        val sectionAttributes: Map<String, Map<String, String>>,
        val raw: String
    ) {
        fun includesFor(section: String): List<Pair<String, String>> {
            return includes.filter { it.section == section }.map { it.domain to it.path }
        }

        fun excludesFor(section: String): List<Rule> = excludes.filter { it.section == section }
    }
}
