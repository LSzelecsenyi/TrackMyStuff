package app.mymusclemap.ui.exercises

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.ExerciseEnumCodec
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapArtworkSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
class MuscleGroupLabelsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun upperAndLowerBackUseEnglishLabels() {
        assertEquals("UPPER_BACK", MuscleGroup.UPPER_BACK.name)
        assertEquals("LOWER_BACK", MuscleGroup.LOWER_BACK.name)
        assertEquals(R.string.muscle_upper_back, MuscleGroup.UPPER_BACK.labelRes())
        assertEquals(R.string.muscle_lower_back, MuscleGroup.LOWER_BACK.labelRes())
        assertEquals(
            resources.getString(R.string.muscle_upper_back),
            resources.getString(MuscleGroup.UPPER_BACK.labelRes())
        )
        assertEquals(
            resources.getString(R.string.muscle_lower_back),
            resources.getString(MuscleGroup.LOWER_BACK.labelRes())
        )
        assertEquals("Upper back", resources.getString(MuscleGroup.UPPER_BACK.labelRes()))
        assertEquals("Lower back", resources.getString(MuscleGroup.LOWER_BACK.labelRes()))
        assertEquals("Felső hát", hungarianString("muscle_upper_back"))
    }

    private fun hungarianString(name: String): String {
        val candidates = listOf(
            File("src/main/res/values-hu/strings.xml"),
            File("app/src/main/res/values-hu/strings.xml")
        )
        val file = candidates.first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        for (index in 0 until nodes.length) {
            val node = nodes.item(index) as Element
            if (node.getAttribute("name") == name) return node.textContent
        }
        error("Missing Hungarian string $name")
    }

    @Test
    fun bothGroupsRemainSelectableAsPrimaryAndSecondary() {
        assertTrue(MuscleGroup.UPPER_BACK in MuscleGroup.entries)
        assertTrue(MuscleGroup.LOWER_BACK in MuscleGroup.entries)
        assertEquals(MuscleGroup.UPPER_BACK, ExerciseEnumCodec.muscle("UPPER_BACK"))
        assertEquals(MuscleGroup.LOWER_BACK, ExerciseEnumCodec.muscle("LOWER_BACK"))
        assertEquals("UPPER_BACK", MuscleGroup.UPPER_BACK.name)
        assertEquals("LOWER_BACK", MuscleGroup.LOWER_BACK.name)
    }

    @Test
    fun persistedEnumValuesStillMapToArtworkAndNewLabels() {
        val regions = MuscleMapArtworkSelector.current().regions
        assertTrue(regions.any { it.muscleGroup == MuscleGroup.UPPER_BACK && it.id == "upper_back" })
        assertTrue(regions.any { it.muscleGroup == MuscleGroup.UPPER_BACK && it.id == "traps_front" })
        assertTrue(regions.any { it.muscleGroup == MuscleGroup.LOWER_BACK && it.id == "lower_back" })
        val heatmapSelection = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.UPPER_BACK.labelRes()),
            resources.getQuantityString(R.plurals.heatmap_days_ago, 2, 2)
        )
        assertEquals("Upper back: 2 days ago", heatmapSelection)
        val lower = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.LOWER_BACK.labelRes()),
            resources.getString(R.string.heatmap_band_today)
        )
        assertEquals("Lower back: Today", lower)
    }

    @Test
    fun everyMuscleGroupLabelComesFromTheSharedMapping() {
        MuscleGroup.entries.forEach { group ->
            val label = resources.getString(group.labelRes())
            assertTrue(group.name, label.isNotBlank())
        }
    }

    @Test
    fun neckIsDisplayedAndIsSelectable() {
        assertEquals("NECK", MuscleGroup.NECK.name)
        assertEquals(R.string.muscle_neck, MuscleGroup.NECK.labelRes())
        assertEquals("Neck", resources.getString(MuscleGroup.NECK.labelRes()))
        assertTrue(MuscleGroup.NECK in MuscleGroup.entries)
        assertEquals(MuscleGroup.NECK, ExerciseEnumCodec.muscle("NECK"))
        assertEquals("NECK", MuscleGroup.NECK.name)
        assertTrue(MuscleMapArtworkSelector.current().regions.any { it.muscleGroup == MuscleGroup.NECK })
        val heatmapSelection = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.NECK.labelRes()),
            resources.getQuantityString(R.plurals.heatmap_days_ago, 2, 2)
        )
        assertEquals("Neck: 2 days ago", heatmapSelection)
    }

    @Test
    fun englishOrderPlacesNeckBetweenLowerBackAndObliques() {
        val sorted = LocalizedLabelOrder.sorted(
            MuscleGroup.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        val labels = sorted.map { resources.getString(it.labelRes()) }
        val neckIndex = labels.indexOf("Neck")
        val lowerBackIndex = labels.indexOf("Lower back")
        val obliquesIndex = labels.indexOf("Obliques")
        assertTrue(neckIndex >= 0)
        assertEquals(lowerBackIndex + 1, neckIndex)
        assertEquals(neckIndex + 1, obliquesIndex)
        assertEquals(MuscleGroup.NECK, sorted[neckIndex])
        assertTrue(sorted != MuscleGroup.entries)
    }
}
