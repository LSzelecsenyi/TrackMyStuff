package app.mymusclemap

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApplicationBackupTest {
    @Test
    fun androidBackupUsesTheRestrictedEncryptedAgent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue(info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertTrue(info.flags and ApplicationInfo.FLAG_FULL_BACKUP_ONLY != 0)
        assertTrue(info.backupAgentName.orEmpty().endsWith("StrictBackupAgent"))
    }
}
