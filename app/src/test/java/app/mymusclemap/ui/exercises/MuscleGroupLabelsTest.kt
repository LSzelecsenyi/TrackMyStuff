package app.mymusclemap.ui.exercises

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.ExerciseEnumCodec
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MuscleGroupLabelsTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun upperAndLowerBackUseTheNewHungarianLabels() {
        assertEquals("UPPER_BACK", MuscleGroup.UPPER_BACK.name)
        assertEquals("LOWER_BACK", MuscleGroup.LOWER_BACK.name)
        assertEquals(R.string.muscle_upper_back, MuscleGroup.UPPER_BACK.labelRes())
        assertEquals(R.string.muscle_lower_back, MuscleGroup.LOWER_BACK.labelRes())
        assertEquals("Trapézizom", resources.getString(MuscleGroup.UPPER_BACK.labelRes()))
        assertEquals("Derékizmok", resources.getString(MuscleGroup.LOWER_BACK.labelRes()))
        assertFalse(resources.getString(R.string.muscle_upper_back).contains("Felső hát"))
        assertFalse(resources.getString(R.string.muscle_lower_back).contains("Alsó hát"))
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
        assertTrue(BodyMusclesCatalog.hasAnatomicalPaths(MuscleGroup.UPPER_BACK))
        assertTrue(BodyMusclesCatalog.hasAnatomicalPaths(MuscleGroup.LOWER_BACK))
        assertTrue(BodyMusclesCatalog.regionsFor(MuscleGroup.UPPER_BACK).any { it.id.startsWith("traps-") })
        assertTrue(BodyMusclesCatalog.regionsFor(MuscleGroup.LOWER_BACK).any { it.id.startsWith("lower-back-") })
        val heatmapSelection = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.UPPER_BACK.labelRes()),
            "2 napja"
        )
        assertEquals("Trapézizom: 2 napja", heatmapSelection)
        val lower = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.LOWER_BACK.labelRes()),
            "Ma"
        )
        assertEquals("Derékizmok: Ma", lower)
    }

    @Test
    fun everyMuscleGroupLabelComesFromTheSharedMapping() {
        MuscleGroup.entries.forEach { group ->
            val label = resources.getString(group.labelRes())
            assertTrue(group.name, label.isNotBlank())
        }
    }

    @Test
    fun neckIsDisplayedAsNyakAndIsSelectable() {
        assertEquals("NECK", MuscleGroup.NECK.name)
        assertEquals(R.string.muscle_neck, MuscleGroup.NECK.labelRes())
        assertEquals("Nyak", resources.getString(MuscleGroup.NECK.labelRes()))
        assertTrue(MuscleGroup.NECK in MuscleGroup.entries)
        assertEquals(MuscleGroup.NECK, ExerciseEnumCodec.muscle("NECK"))
        assertEquals("NECK", MuscleGroup.NECK.name)
        assertTrue(BodyMusclesCatalog.hasAnatomicalPaths(MuscleGroup.NECK))
        val heatmapSelection = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.NECK.labelRes()),
            "2 napja"
        )
        assertEquals("Nyak: 2 napja", heatmapSelection)
    }

    @Test
    fun hungarianAbcPlacesNyakBetweenMellAndOldalsoVall() {
        val sorted = LocalizedLabelOrder.sorted(
            MuscleGroup.entries,
            label = { resources.getString(it.labelRes()) },
            key = { it.name }
        )
        val labels = sorted.map { resources.getString(it.labelRes()) }
        val neckIndex = labels.indexOf("Nyak")
        val chestIndex = labels.indexOf("Mell")
        val sideDeltIndex = labels.indexOf("Oldalsó váll")
        assertTrue(neckIndex >= 0)
        assertEquals(chestIndex + 1, neckIndex)
        assertEquals(neckIndex + 1, sideDeltIndex)
        assertEquals(MuscleGroup.NECK, sorted[neckIndex])
        assertTrue(sorted != MuscleGroup.entries)
    }
}
