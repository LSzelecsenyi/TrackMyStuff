package app.mymusclemap.domain.locale

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The device's primary language. This reads the system locale list, not the
 * locale Strict has applied for its own UI.
 */
object SystemLanguage {
    fun primary(context: Context): String {
        return primaryLanguage(systemLocales(context))
    }

    fun systemLocales(context: Context): LocaleList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(android.app.LocaleManager::class.java)?.systemLocales
            if (locales != null && !locales.isEmpty) {
                return locales
            }
        }
        val configuration = Resources.getSystem().configuration
        return configuration.locales
    }

    fun primaryLanguage(locales: LocaleList): String {
        if (locales.isEmpty) {
            return AppLanguage.EN.tag
        }
        return locales[0].language.lowercase(Locale.ROOT)
    }
}
