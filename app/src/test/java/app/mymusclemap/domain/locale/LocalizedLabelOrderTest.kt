package app.mymusclemap.domain.locale

import app.mymusclemap.EnglishDisplayLocaleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class LocalizedLabelOrderTest {
    @get:Rule
    val locale = EnglishDisplayLocaleRule()

    @Test
    fun followsTheCurrentDisplayLocale() {
        assertEquals(Locale.ENGLISH, LocalizedLabelOrder.locale)
        assertEquals(AppLocale.UI, LocalizedLabelOrder.locale)
        Locale.setDefault(Locale.forLanguageTag("hu-HU"))
        try {
            assertEquals(Locale.forLanguageTag("hu-HU"), LocalizedLabelOrder.locale)
            assertEquals(AppLocale.UI, LocalizedLabelOrder.locale)
        } finally {
            Locale.setDefault(Locale.ENGLISH)
        }
    }

    @Test
    fun sortsByEnglishCollation() {
        val items = listOf("Zebra", "apple", "Mango")
        val sorted = LocalizedLabelOrder.sorted(items, label = { it }, key = { it })
        assertEquals(listOf("apple", "Mango", "Zebra"), sorted)
    }

    @Test
    fun usesStableSecondaryKeyWhenLabelsMatch() {
        data class Item(val label: String, val key: String)
        val sorted = LocalizedLabelOrder.sorted(
            items = listOf(Item("Chest", "CHEST"), Item("Chest", "ABS")),
            label = { it.label },
            key = { it.key }
        )
        assertEquals(listOf("ABS", "CHEST"), sorted.map { it.key })
    }

    @Test
    fun englishOrderPlacesBaseLettersWithAccentsNearby() {
        assertTrue(LocalizedLabelOrder.compareLabels("Apple", "Banana") < 0)
        assertTrue(LocalizedLabelOrder.compareLabels("Chest", "Neck") < 0)
    }
}
