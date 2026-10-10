package app.mymusclemap

import app.mymusclemap.domain.entitlement.WelcomeBackDebugConfig

/** Release builds ignore strict.debug.welcomeBack* and always use the real grant. */
object WelcomeBackDebugSelection {
    fun current(): WelcomeBackDebugConfig = WelcomeBackDebugConfig()
}
