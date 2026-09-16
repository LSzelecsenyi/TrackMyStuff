package hu.laca.weighttracker.ui.exercises

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.exercise.ExerciseEnumCodec
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.ui.components.musclemap.artwork.BodyMusclesCatalog
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
}
