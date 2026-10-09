package app.mymusclemap.domain.locale

import java.util.Locale

object AppLocale {
    /** The locale Strict is currently displaying. Formatters read this at call time. */
    val UI: Locale
        get() = Locale.getDefault()
}
