package app.mymusclemap

import app.mymusclemap.domain.entitlement.WelcomeBackDebugConfig

/** Debug builds read the values compiled from local.properties. */
object WelcomeBackDebugSelection {
    fun current(): WelcomeBackDebugConfig {
        return WelcomeBackDebugConfig.parse(
            BuildConfig.STRICT_WELCOME_BACK,
            BuildConfig.STRICT_WELCOME_BACK_GAP_DAYS,
            BuildConfig.STRICT_WELCOME_BACK_EXPIRES_IN_SECONDS,
            BuildConfig.STRICT_WELCOME_BACK_WARNING_BEFORE_SECONDS,
            BuildConfig.STRICT_WELCOME_BACK_COOLDOWN_SECONDS
        )
    }
}
