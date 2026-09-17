package hu.laca.weighttracker.domain.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedLabelOrderTest {
    @Test
    fun sortsByHungarianAlphabetNotAscii() {
        val items = listOf("őszibarack", "alma", "Áron", "béka", "ékezet")
        val sorted = LocalizedLabelOrder.sorted(items, label = { it }, key = { it })
        assertEquals(listOf("alma", "Áron", "béka", "ékezet", "őszibarack"), sorted)
    }

    @Test
    fun usesStableSecondaryKeyWhenLabelsMatch() {
        data class Item(val label: String, val key: String)
        val sorted = LocalizedLabelOrder.sorted(
            items = listOf(Item("Mell", "CHEST"), Item("Mell", "ABS")),
            label = { it.label },
            key = { it.key }
        )
        assertEquals(listOf("ABS", "CHEST"), sorted.map { it.key })
    }

    @Test
    fun hungarianAccentedLettersSortAfterTheirBaseLetters() {
        assertTrue(LocalizedLabelOrder.compareLabels("a", "á") < 0)
        assertTrue(LocalizedLabelOrder.compareLabels("e", "é") < 0)
        assertTrue(LocalizedLabelOrder.compareLabels("o", "ö") < 0)
        assertTrue(LocalizedLabelOrder.compareLabels("ö", "ő") < 0)
        assertTrue(LocalizedLabelOrder.compareLabels("Alma", "Álló evezés") < 0)
    }
}
