package app.mymusclemap.ui.health

import android.content.ActivityNotFoundException
import android.content.Intent
import android.health.connect.HealthConnectManager
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HealthConnectManageAccessIntentTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun android14OpensHealthConnectHomeSettings() {
        val intent = healthConnectManageAccessIntent()
        assertEquals("android.health.connect.action.HEALTH_HOME_SETTINGS", intent.action)
        assertEquals(healthConnectSettingsAction(), intent.action)
        assertEquals(null, intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
        assertFalse(intent.action == HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun android13OpensProviderHealthConnectSettings() {
        val intent = healthConnectManageAccessIntent()
        assertEquals(healthConnectSettingsAction(), intent.action)
        assertEquals("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS", intent.action)
        assertEquals(null, intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
    }

    @Test
    fun securityExceptionDoesNotEscapeAndReportsFailure() {
        val opened = startHealthConnectActivity(
            start = { throw SecurityException("requires android.permission.GRANT_RUNTIME_PERMISSIONS") },
            intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        )
        assertFalse(opened)
    }

    @Test
    fun missingActivityReportsFailure() {
        val opened = startHealthConnectActivity(
            start = { throw ActivityNotFoundException() },
            intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        )
        assertFalse(opened)
    }

    @Test
    fun successfulLaunchReportsOpened() {
        var started: Intent? = null
        val opened = startHealthConnectActivity(
            start = { started = it },
            intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        )
        assertTrue(opened)
        assertEquals("android.health.connect.action.HEALTH_HOME_SETTINGS", started?.action)
    }
}
