package app.mymusclemap.domain.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguageApplicator {
    fun apply(language: AppLanguage) {
        val selected = LocaleListCompat.forLanguageTags(language.tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == selected.toLanguageTags()) {
            return
        }
        AppCompatDelegate.setApplicationLocales(selected)
    }
}
