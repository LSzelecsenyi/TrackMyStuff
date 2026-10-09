package app.mymusclemap.data.preferences

import android.app.Application
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.locale.AppLanguage
import app.mymusclemap.domain.locale.AppLanguagePolicy
import app.mymusclemap.domain.locale.SystemLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LanguagePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theChoiceSurvivesANewStoreInstance() = runBlocking {
        assertNull(LanguagePreferences(context).read())
        LanguagePreferences(context).write(AppLanguage.EN)
        assertEquals(AppLanguage.EN, LanguagePreferences(context).read())
        LanguagePreferences(context).write(AppLanguage.HU)
        assertEquals(AppLanguage.HU, LanguagePreferences(context).read())
    }

    @Test
    fun systemLanguageEligibilityIgnoresTheAppLocale() {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val language = SystemLanguage.primaryLanguage(LocaleList.forLanguageTags("en"))
        assertEquals("en", language)
        assertFalse(AppLanguagePolicy.systemAllowsChoice(language))
        assertEquals(AppLanguage.EN, AppLanguagePolicy.resolve(language, AppLanguage.HU))
    }
}
