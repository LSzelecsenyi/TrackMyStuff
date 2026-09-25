package app.mymusclemap

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApplicationBackupTest {
    @Test
    fun androidBackupIsDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
