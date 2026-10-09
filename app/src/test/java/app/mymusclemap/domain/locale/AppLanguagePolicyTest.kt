package app.mymusclemap.domain.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppLanguagePolicyTest {
    @Test
    fun hungarianDeviceWithoutAPreferenceUsesHungarian() {
        assertEquals(AppLanguage.HU, AppLanguagePolicy.resolve("hu", null))
        assertTrue(AppLanguagePolicy.systemAllowsChoice("hu"))
    }

    @Test
    fun hungarianDeviceHonorsTheStoredChoice() {
        assertEquals(AppLanguage.EN, AppLanguagePolicy.resolve("hu", AppLanguage.EN))
        assertEquals(AppLanguage.HU, AppLanguagePolicy.resolve("HU", AppLanguage.HU))
    }

    @Test
    fun englishAndOtherDevicesForceEnglishAndHideTheChoice() {
        assertEquals(AppLanguage.EN, AppLanguagePolicy.resolve("en", AppLanguage.HU))
        assertEquals(AppLanguage.EN, AppLanguagePolicy.resolve("de", AppLanguage.HU))
        assertEquals(AppLanguage.EN, AppLanguagePolicy.resolve("de", null))
        assertFalse(AppLanguagePolicy.systemAllowsChoice("en"))
        assertFalse(AppLanguagePolicy.systemAllowsChoice("de"))
    }

    @Test
    fun resolvingALanguageDoesNotReplaceTheStoredChoice() {
        val stored = AppLanguage.HU
        assertEquals(AppLanguage.EN, AppLanguagePolicy.resolve("en", stored))
        assertEquals(AppLanguage.HU, stored)
        assertEquals(AppLanguage.HU, AppLanguagePolicy.resolve("hu", stored))
    }

    @Test
    fun theAppLocaleDoesNotDecideSystemEligibility() {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        assertFalse(AppLanguagePolicy.systemAllowsChoice("en"))
        assertEquals(AppLanguage.EN, AppLanguagePolicy.resolve("en", AppLanguage.HU))
    }
}
