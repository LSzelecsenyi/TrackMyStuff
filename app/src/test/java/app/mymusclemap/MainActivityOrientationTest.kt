package app.mymusclemap

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityOrientationTest {
    @Test
    fun mainActivityDeclaresPortrait() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val main = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, main.screenOrientation)
    }
}
