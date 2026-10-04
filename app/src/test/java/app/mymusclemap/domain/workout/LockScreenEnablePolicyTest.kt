package app.mymusclemap.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class LockScreenEnablePolicyTest {
    @Test
    fun belowRuntimePermissionApiEnablesImmediately() {
        assertEquals(
            LockScreenEnableDecision.EnableNow,
            LockScreenEnablePolicy.decide(
                requiresRuntimePermission = false,
                permissionGranted = false,
                runtimeDialogAvailable = false
            )
        )
    }

    @Test
    fun alreadyGrantedPermissionEnablesImmediately() {
        assertEquals(
            LockScreenEnableDecision.EnableNow,
            LockScreenEnablePolicy.decide(
                requiresRuntimePermission = true,
                permissionGranted = true,
                runtimeDialogAvailable = false
            )
        )
    }

    @Test
    fun firstEnableExplainsBeforeTheSystemDialog() {
        assertEquals(
            LockScreenEnableDecision.ExplainThenRequestPermission,
            LockScreenEnablePolicy.decide(
                requiresRuntimePermission = true,
                permissionGranted = false,
                runtimeDialogAvailable = LockScreenEnablePolicy.runtimeDialogAvailable(
                    hasRequestedBefore = false,
                    shouldShowRationale = false
                )
            )
        )
    }

    @Test
    fun aPreviousDenialStillUsesTheSystemDialog() {
        assertEquals(
            LockScreenEnableDecision.ExplainThenRequestPermission,
            LockScreenEnablePolicy.decide(
                requiresRuntimePermission = true,
                permissionGranted = false,
                runtimeDialogAvailable = LockScreenEnablePolicy.runtimeDialogAvailable(
                    hasRequestedBefore = true,
                    shouldShowRationale = true
                )
            )
        )
    }

    @Test
    fun permanentDenialOpensSystemSettingsInsteadOfRequestingAgain() {
        assertEquals(
            LockScreenEnableDecision.ExplainThenOpenSettings,
            LockScreenEnablePolicy.decide(
                requiresRuntimePermission = true,
                permissionGranted = false,
                runtimeDialogAvailable = LockScreenEnablePolicy.runtimeDialogAvailable(
                    hasRequestedBefore = true,
                    shouldShowRationale = false
                )
            )
        )
    }
}
