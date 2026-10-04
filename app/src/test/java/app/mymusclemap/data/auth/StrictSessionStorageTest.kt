package app.mymusclemap.data.auth

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.BuildConfig
import app.mymusclemap.R
import app.mymusclemap.WeightTrackerApplication
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.emptySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class StrictSessionStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun encryptedSessionFileOmitsTheRawTokenAndLivesOutsideBackup() {
        val token = "backup-excluded-bearer-token"
        val directory = context.noBackupFilesDir
        val store = EncryptedFileStrictSessionStore(
            file = File(directory, EncryptedFileStrictSessionStore.FILE_NAME),
            sealer = TestAesSealer()
        )
        store.write(StoredStrictSession(StrictBearerToken(token), "2026-11-03T00:00:00Z", "user-1"))
        val file = File(directory, EncryptedFileStrictSessionStore.FILE_NAME)
        assertEquals("no_backup", file.parentFile?.name)
        assertEquals(token, store.read()?.accessToken?.value)
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(token))
        assertFalse(AppBackupJson.encode(emptySnapshot()).contains(token))
        assertFalse(AppBackupJson.encode(emptySnapshot()).contains("accessToken"))
    }

    @Test
    fun platformBackupRulesExcludeTheNoBackupDirectory() {
        assertTrue(xmlText(R.xml.backup_rules).contains("no_backup"))
        assertTrue(xmlText(R.xml.data_extraction_rules).contains("no_backup"))
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun blankGoogleServerClientIdFailsClosed() = kotlinx.coroutines.runBlocking {
        val provider = CredentialManagerGoogleIdentityProvider("  ", context)
        assertEquals(GoogleIdTokenRequest.NotConfigured, provider.requestIdToken())
    }

    @Test
    fun unboundGoogleSignInFailsClosed() = kotlinx.coroutines.runBlocking {
        val provider = ActivityBoundGoogleIdentityProvider(BuildConfig.STRICT_GOOGLE_SERVER_CLIENT_ID)
        assertEquals(GoogleIdTokenRequest.NotConfigured, provider.requestIdToken())
    }

    @Test
    fun applicationStartupDoesNotOpenAStrictSession() {
        val app = ApplicationProvider.getApplicationContext<WeightTrackerApplication>()
        assertNull(app.container.strictAuthRepository.storedSession())
        val startup = File("src/main/java/app/mymusclemap/WeightTrackerApplication.kt").readText()
        val main = File("src/main/java/app/mymusclemap/MainActivity.kt").readText()
        assertFalse(startup.contains("signIn("))
        assertFalse(startup.contains("bindStrictSignIn"))
        assertFalse(main.contains("signIn("))
        assertFalse(main.contains("bindStrictSignIn"))
    }

    @Test
    fun debugBackendUrlIsNotPuffAndReleaseUrlIsHttps() {
        val debugUrl = BuildConfig.STRICT_API_BASE_URL.lowercase()
        assertFalse(debugUrl.contains("puff"))
        assertTrue(debugUrl.startsWith("https://") || debugUrl.startsWith("http://"))
        StrictBackendUrls.requireReleaseUrl(StrictBackendUrls.PRODUCTION)
        assertEquals("https://api.strictworkout.eu", StrictBackendUrls.PRODUCTION)
        assertEquals("http://127.0.0.1:8082", StrictBackendUrls.DEBUG_LOOPBACK)
        listOf(
            "http://api.strictworkout.eu",
            "https://localhost:8082",
            "https://127.0.0.1:8082",
            "https://10.0.2.2:8082",
            "https://puff.example"
        ).forEach { unsafe ->
            val rejected = runCatching { StrictBackendUrls.requireReleaseUrl(unsafe) }
            assertTrue(unsafe, rejected.isFailure)
        }
    }

    private fun xmlText(resourceId: Int): String {
        val parser = context.resources.getXml(resourceId)
        val text = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            parser.name?.let { text.append(it).append(' ') }
            for (index in 0 until parser.attributeCount) {
                text.append(parser.getAttributeValue(index)).append(' ')
            }
        }
        return text.toString()
    }
}

private class TestAesSealer : SessionSealer {
    private val key = SecretKeySpec(ByteArray(16) { 9 }, "AES")
    private val iv = ByteArray(12) { 4 }

    override fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(plaintext)
    }

    override fun open(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(sealed)
    }
}
