package app.mymusclemap.domain.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourceTest {
    private val placeholder = Regex("""%(?:\d+\$)?[sdf]|%%""")

    @Test
    fun hungarianResourcesCoverEveryEnglishStringAndPlural() {
        val english = load("src/main/res/values/strings.xml")
        val hungarian = load("src/main/res/values-hu/strings.xml")
        val missing = english.keys - hungarian.keys
        val extra = hungarian.keys - english.keys
        assertTrue("Missing Hungarian strings: $missing", missing.isEmpty())
        assertTrue("Unexpected Hungarian strings: $extra", extra.isEmpty())
        english.forEach { (name, value) ->
            assertEquals(name, placeholders(value), placeholders(hungarian.getValue(name)))
        }
        val englishPlurals = loadPlurals("src/main/res/values/plurals.xml") +
            loadPlurals("src/main/res/values/strings.xml")
        val hungarianPlurals = loadPlurals("src/main/res/values-hu/plurals.xml") +
            loadPlurals("src/main/res/values-hu/strings.xml")
        assertEquals(englishPlurals.keys, hungarianPlurals.keys)
        listOf(english, hungarian).forEach { resources ->
            val calculation = resources.getValue("muscle_insights_explain_load")
            assertTrue(calculation, calculation.contains("15%"))
            assertTrue(calculation, !calculation.contains("%%"))
            assertTrue(resources.getValue("muscle_insights_explain_chart").contains("28"))
        }
        assertEquals("Elsődleges sorozatok", hungarian.getValue("muscle_insights_primary_sets"))
        assertEquals("Másodlagos érintettség", hungarian.getValue("muscle_insights_secondary_sets"))
        assertEquals(
            "A másodlagos érintettséget külön mutatjuk, és nem számítjuk bele az elsődleges sorozatok számába.",
            hungarian.getValue("muscle_insights_secondary_limit")
        )
        assertEquals("Kiválasztott időszak: %1\$s", hungarian.getValue("muscle_insights_selected_period"))
        assertEquals("Elmúlt 7 nap vs. előző 4 hét", hungarian.getValue("muscle_insights_comparison_window"))
        assertEquals("4 hét", hungarian.getValue("muscle_insights_period_name_4w"))
        assertEquals("3 hónap", hungarian.getValue("muscle_insights_period_name_3m"))
        assertEquals("6 hónap", hungarian.getValue("muscle_insights_period_name_6m"))
        assertEquals("1 év", hungarian.getValue("muscle_insights_period_name_1y"))
        assertEquals("Teljes időszak", hungarian.getValue("muscle_insights_period_name_all"))
        englishPlurals.forEach { (name, items) ->
            assertEquals(name, items.keys, hungarianPlurals.getValue(name).keys)
            items.forEach { (quantity, value) ->
                assertEquals(
                    "$name:$quantity",
                    placeholders(value),
                    placeholders(hungarianPlurals.getValue(name).getValue(quantity))
                )
            }
        }
    }

    private fun load(path: String): Map<String, String> {
        val document = document(path)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                val name = node.attributes.getNamedItem("name").nodeValue
                put(name, node.textContent)
            }
        }
    }

    private fun loadPlurals(path: String): Map<String, Map<String, String>> {
        val file = File(path)
        if (!file.isFile) {
            return emptyMap()
        }
        val nodes = document(path).getElementsByTagName("plurals")
        return buildMap {
            for (index in 0 until nodes.length) {
                val plural = nodes.item(index)
                val name = plural.attributes.getNamedItem("name").nodeValue
                val items = buildMap {
                    val children = plural.childNodes
                    for (childIndex in 0 until children.length) {
                        val child = children.item(childIndex)
                        if (child.nodeName != "item") continue
                        put(child.attributes.getNamedItem("quantity").nodeValue, child.textContent)
                    }
                }
                put(name, items)
            }
        }
    }

    private fun document(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))

    private fun placeholders(value: String): List<String> {
        return placeholder.findAll(value).map { it.value }.sorted().toList()
    }
}
