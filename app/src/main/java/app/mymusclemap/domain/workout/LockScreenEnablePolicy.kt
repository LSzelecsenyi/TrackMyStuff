package app.mymusclemap.domain.workout

enum class LockScreenEnableDecision {
    EnableNow,
    ExplainThenRequestPermission,
    ExplainThenOpenSettings
}

object LockScreenEnablePolicy {
    fun decide(
        requiresRuntimePermission: Boolean,
        permissionGranted: Boolean,
        runtimeDialogAvailable: Boolean
    ): LockScreenEnableDecision {
        if (!requiresRuntimePermission || permissionGranted) {
            return LockScreenEnableDecision.EnableNow
        }
        return if (runtimeDialogAvailable) {
            LockScreenEnableDecision.ExplainThenRequestPermission
        } else {
            LockScreenEnableDecision.ExplainThenOpenSettings
        }
    }

    /**
     * Android hides the runtime dialog after "don't ask again".
     * Before the first request, [shouldShowRationale] is also false, so that case still asks.
     */
    fun runtimeDialogAvailable(hasRequestedBefore: Boolean, shouldShowRationale: Boolean): Boolean {
        return shouldShowRationale || !hasRequestedBefore
    }
}
