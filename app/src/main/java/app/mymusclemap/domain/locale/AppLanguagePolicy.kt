package app.mymusclemap.domain.locale

/**
 * Hungarian devices may choose Magyar or English. Every other device language
 * uses English. The stored choice is kept either way and is only applied when
 * the device language is Hungarian.
 */
enum class AppLanguage(val tag: String) {
    HU("hu"),
    EN("en");

    companion object {
        fun fromStored(value: String?): AppLanguage? {
            return when (value) {
                HU.tag -> HU
                EN.tag -> EN
                else -> null
            }
        }
    }
}

object AppLanguagePolicy {
    fun systemAllowsChoice(systemLanguage: String): Boolean {
        return systemLanguage.equals(AppLanguage.HU.tag, ignoreCase = true)
    }

    fun resolve(systemLanguage: String, stored: AppLanguage?): AppLanguage {
        if (!systemAllowsChoice(systemLanguage)) {
            return AppLanguage.EN
        }
        return stored ?: AppLanguage.HU
    }
}
