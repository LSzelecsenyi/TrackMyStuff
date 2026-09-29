package app.mymusclemap.ui.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.mymusclemap.data.health.HealthConnectGateway

@Composable
fun rememberHealthConnectPermissionLaunch(onFinished: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        onFinished()
    }
    val permissions = HealthConnectGateway.readPermissions()
    return { launcher.launch(permissions) }
}

@Composable
fun RefreshHealthConnectOnResume(onRefresh: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, onRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/**
 * Opens Health Connect so the user can review or revoke this app's access.
 *
 * The permission-request contract is only for the first grant. Once every requested permission
 * is already granted, that contract returns immediately and shows no UI.
 *
 * Android 14+ uses `android.health.connect.action.HEALTH_HOME_SETTINGS`. Android 13 and lower
 * use `androidx.health.ACTION_HEALTH_CONNECT_SETTINGS`. Those are the actions Health Connect
 * client 1.1.0 exposes through `HealthConnectClient.getHealthConnectSettingsAction()`.
 *
 * `android.health.connect.action.MANAGE_HEALTH_PERMISSIONS` is not used. On current platform
 * images that action resolves to a controller activity guarded by
 * `android.permission.GRANT_RUNTIME_PERMISSIONS`. A normal app cannot hold that permission, so
 * `startActivity` throws [SecurityException] and finishes the process.
 */
fun healthConnectManageAccessIntent(): Intent {
    return Intent(healthConnectSettingsAction()).addCategory(Intent.CATEGORY_DEFAULT)
}

internal fun startHealthConnectActivity(start: (Intent) -> Unit, intent: Intent): Boolean {
    return try {
        start(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

fun openHealthConnectManageAccess(context: Context): Boolean {
    return startHealthConnectActivity(context::startActivity, healthConnectManageAccessIntent())
}

/**
 * Same action strings as Health Connect client 1.1.0 `getHealthConnectSettingsAction()`.
 * Android 14+ opens Health Connect home. Earlier versions open the provider app settings.
 */
internal fun healthConnectSettingsAction(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        "android.health.connect.action.HEALTH_HOME_SETTINGS"
    } else {
        "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
    }
}

fun openHealthConnectProviderInstall(context: Context): Boolean {
    val intent = HealthConnectGateway.providerInstallIntent(context.packageName)
    return startHealthConnectActivity(context::startActivity, intent)
}
