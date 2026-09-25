package app.mymusclemap.ui.exercises

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.ExerciseEnumCodec
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
        assertEquals("Traps", resources.getString(MuscleGroup.UPPER_BACK.labelRes()))
        assertEquals("Lower back", resources.getString(MuscleGroup.LOWER_BACK.labelRes()))
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
            resources.getQuantityString(R.plurals.heatmap_days_ago, 2, 2)
        )
        assertEquals("Traps: 2 days ago", heatmapSelection)
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
        assertTrue(BodyMusclesCatalog.hasAnatomicalPaths(MuscleGroup.NECK))
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
