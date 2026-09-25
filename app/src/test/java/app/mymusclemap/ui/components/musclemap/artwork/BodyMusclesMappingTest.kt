package app.mymusclemap.ui.components.musclemap.artwork

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMusclesMappingTest {
    private val expected = mapOf(
        MuscleGroup.CHEST to setOf(
            "chest-upper-left", "chest-upper-right", "chest-lower-left", "chest-lower-right"
        ),
        MuscleGroup.LATS to setOf(
            "lats-upper-left", "lats-upper-right", "lats-mid-left", "lats-mid-right",
            "lats-lower-left", "lats-lower-right"
        ),
        MuscleGroup.UPPER_BACK to setOf(
            "traps-upper-left", "traps-upper-right", "traps-mid-left", "traps-mid-right",
            "traps-lower-left", "traps-lower-right"
        ),
        MuscleGroup.LOWER_BACK to setOf(
            "lower-back-erectors-left", "lower-back-erectors-right",
            "lower-back-ql-left", "lower-back-ql-right", "spine"
        ),
        MuscleGroup.FRONT_DELTOID to setOf("shoulder-front-left", "shoulder-front-right"),
        MuscleGroup.SIDE_DELTOID to setOf("shoulder-side-left", "shoulder-side-right"),
        MuscleGroup.REAR_DELTOID to setOf("deltoid-rear-left", "deltoid-rear-right"),
        MuscleGroup.BICEPS to setOf("biceps-left", "biceps-right"),
        MuscleGroup.TRICEPS to setOf(
            "triceps-long-left", "triceps-long-right", "triceps-lateral-left", "triceps-lateral-right"
        ),
        MuscleGroup.FOREARMS to setOf(
            "forearm-left", "forearm-right", "forearm-flexors-left", "forearm-flexors-right",
            "forearm-extensors-left", "forearm-extensors-right"
        ),
        MuscleGroup.ABS to setOf(
            "abs-block-left-1", "abs-block-left-2", "abs-block-left-3",
            "abs-block-right-1", "abs-block-right-2", "abs-block-right-3",
            "abs-lower-left", "abs-lower-right"
        ),
        MuscleGroup.OBLIQUES to setOf(
            "obliques-block-left-1", "obliques-block-left-2", "obliques-block-left-3", "obliques-block-left-4",
            "obliques-block-right-1", "obliques-block-right-2", "obliques-block-right-3", "obliques-block-right-4",
            "abs-upper-left", "abs-upper-right"
        ),
        MuscleGroup.GLUTES to setOf(
            "gluteus-medius-left", "gluteus-medius-right", "gluteus-maximus-left", "gluteus-maximus-right"
        ),
        MuscleGroup.QUADRICEPS to setOf("quads-left", "quads-right"),
        MuscleGroup.HAMSTRINGS to setOf(
            "hamstrings-medial-left", "hamstrings-medial-right",
            "hamstrings-lateral-left", "hamstrings-lateral-right"
        ),
        MuscleGroup.ADDUCTORS to setOf("adductors-left", "adductors-right"),
        MuscleGroup.CALVES to setOf(
            "calves-gastroc-medial-left", "calves-gastroc-medial-right",
            "calves-gastroc-lateral-left", "calves-gastroc-lateral-right",
            "calves-soleus-left", "calves-soleus-right",
            "tibialis-anterior-left", "tibialis-anterior-right"
        ),
        MuscleGroup.NECK to setOf("neck-left", "neck-right", "nape")
    )

    @Test
    fun everySupportedMuscleGroupMapsToAtLeastOneRegion() {
        expected.keys.forEach { group ->
            assertTrue(group.name, BodyMusclesCatalog.hasAnatomicalPaths(group))
            assertEquals(expected.getValue(group), BodyMusclesCatalog.regionsFor(group).map { it.id }.toSet())
        }
    }

    @Test
    fun fullBodyAndCardiovascularHaveNoPaths() {
        assertFalse(BodyMusclesCatalog.hasAnatomicalPaths(MuscleGroup.FULL_BODY))
        assertFalse(BodyMusclesCatalog.hasAnatomicalPaths(MuscleGroup.CARDIOVASCULAR))
        assertNull(BodyMusclesCatalog.groupFor("head"))
        assertTrue("hip-flexor-left" in BodyMusclesCatalog.unmappedIds)
        assertTrue("abs-upper-left" !in BodyMusclesCatalog.unmappedIds)
        assertTrue("abs-lower-left" !in BodyMusclesCatalog.unmappedIds)
        assertTrue("tibialis-anterior-left" !in BodyMusclesCatalog.unmappedIds)
        assertTrue("neck-left" !in BodyMusclesCatalog.unmappedIds)
        assertTrue("nape" !in BodyMusclesCatalog.unmappedIds)
        assertTrue("knee-left" in BodyMusclesCatalog.unmappedIds)
    }

    @Test
    fun everyReferencedUpstreamIdExists() {
        val ids = BodyMusclesCatalog.regions.map { it.id }.toSet()
        expected.values.flatten().forEach { id ->
            assertTrue(id, id in ids)
        }
    }

    @Test
    fun noRegionIsAssignedToConflictingGroups() {
        val assigned = BodyMusclesCatalog.regions.filter { it.muscleGroup != null }
        val byId = assigned.groupBy { it.id }
        byId.forEach { (id, regions) ->
            assertEquals(id, 1, regions.map { it.muscleGroup }.distinct().size)
        }
        assigned.forEach { region ->
            val expectedGroup = expected.entries.first { region.id in it.value }.key
            assertEquals(region.id, expectedGroup, region.muscleGroup)
        }
    }

    @Test
    fun hipFlexorsKneesAndFeetRemainUnmapped() {
        assertNull(BodyMusclesCatalog.groupFor("hip-flexor-left"))
        assertNull(BodyMusclesCatalog.groupFor("hip-flexor-right"))
        assertEquals(MuscleGroup.OBLIQUES, BodyMusclesCatalog.groupFor("abs-upper-left"))
        assertEquals(MuscleGroup.OBLIQUES, BodyMusclesCatalog.groupFor("abs-upper-right"))
        assertEquals(MuscleGroup.ABS, BodyMusclesCatalog.groupFor("abs-lower-left"))
        assertEquals(MuscleGroup.ABS, BodyMusclesCatalog.groupFor("abs-lower-right"))
        assertEquals(MuscleGroup.CALVES, BodyMusclesCatalog.groupFor("tibialis-anterior-left"))
        assertEquals(MuscleGroup.CALVES, BodyMusclesCatalog.groupFor("tibialis-anterior-right"))
        assertEquals(MuscleGroup.NECK, BodyMusclesCatalog.groupFor("neck-left"))
        assertEquals(MuscleGroup.NECK, BodyMusclesCatalog.groupFor("neck-right"))
        assertEquals(MuscleGroup.NECK, BodyMusclesCatalog.groupFor("nape"))
        assertNull(BodyMusclesCatalog.groupFor("head"))
        assertNull(BodyMusclesCatalog.groupFor("face"))
        assertNull(BodyMusclesCatalog.groupFor("head-back"))
        assertNull(BodyMusclesCatalog.groupFor("knee-left"))
        assertNull(BodyMusclesCatalog.groupFor("knee-right"))
        assertNull(BodyMusclesCatalog.groupFor("foot-left"))
        assertNull(BodyMusclesCatalog.groupFor("foot-right"))
    }

    @Test
    fun absHaveFourRegionsAndObliquesHaveFivePerSide() {
        val abs = BodyMusclesCatalog.regionsFor(MuscleGroup.ABS)
        val obliques = BodyMusclesCatalog.regionsFor(MuscleGroup.OBLIQUES)
        assertEquals(4, abs.count { it.side == AnatomicalSide.LEFT })
        assertEquals(4, abs.count { it.side == AnatomicalSide.RIGHT })
        assertEquals(5, obliques.count { it.side == AnatomicalSide.LEFT })
        assertEquals(5, obliques.count { it.side == AnatomicalSide.RIGHT })
        (abs + obliques).forEach { region ->
            val closed = region.pathData.count { it == 'z' || it == 'Z' }
            assertEquals(region.id, 1, closed)
        }
    }

    @Test
    fun frontDeltoidsMapExactlyToShoulderFrontPaths() {
        assertDeltoidMapping(
            MuscleGroup.FRONT_DELTOID,
            MuscleMapView.FRONT,
            "shoulder-front-left",
            "shoulder-front-right"
        )
    }

    @Test
    fun sideDeltoidsMapExactlyToShoulderSidePaths() {
        assertDeltoidMapping(
            MuscleGroup.SIDE_DELTOID,
            MuscleMapView.FRONT,
            "shoulder-side-left",
            "shoulder-side-right"
        )
    }

    @Test
    fun rearDeltoidsMapExactlyToDeltoidRearPaths() {
        assertDeltoidMapping(
            MuscleGroup.REAR_DELTOID,
            MuscleMapView.BACK,
            "deltoid-rear-left",
            "deltoid-rear-right"
        )
    }

    @Test
    fun requiredDeltoidRegionIdsExist() {
        val ids = BodyMusclesCatalog.regions.map { it.id }.toSet()
        listOf(
            "shoulder-front-left", "shoulder-front-right",
            "shoulder-side-left", "shoulder-side-right",
            "deltoid-rear-left", "deltoid-rear-right"
        ).forEach { id ->
            assertTrue(id, id in ids)
        }
    }

    @Test
    fun unmappedRegionsAreNotAssignedNeverTrainedGroup() {
        BodyMusclesCatalog.regions.filter { it.muscleGroup == null }.forEach { region ->
            assertNull(region.id, region.muscleGroup)
            assertFalse(region.id, region.id in expected.values.flatten())
        }
    }

    private fun assertDeltoidMapping(
        group: MuscleGroup,
        view: MuscleMapView,
        vararg ids: String
    ) {
        val regions = BodyMusclesCatalog.regionsFor(group)
        assertEquals(ids.toSet(), regions.map { it.id }.toSet())
        regions.forEach { region ->
            assertEquals(region.id, view, region.view)
            assertEquals(region.id, group, region.muscleGroup)
        }
        BodyMusclesCatalog.regions.filter { it.id in ids }.forEach { region ->
            assertEquals(region.id, group, region.muscleGroup)
        }
    }
}
