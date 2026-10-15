package app.mymusclemap.ui.components.musclemap.artwork

import androidx.compose.ui.geometry.Offset
import app.mymusclemap.domain.exercise.ExerciseEnumCodec
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.StarterCatalog
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.components.musclemap.hitTest
import app.mymusclemap.ui.components.musclemap.parseMuscleRegions
import app.mymusclemap.ui.components.musclemap.pathContains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
class MaleMuscleMapTest {
    @Test
    fun everyLogicalMuscleMapsToTheAuthoredPaths() {
        val regions = MuscleMapArtworkSelector.current().regions
        assertEquals(EXPECTED.keys, regions.map { it.id }.toSet())
        EXPECTED.forEach { (id, group) ->
            assertEquals(id, group, regions.first { it.id == id }.muscleGroup)
        }
        val byGroup = regions.groupBy { it.muscleGroup }
        MuscleGroup.entries
            .filter { it != MuscleGroup.FULL_BODY && it != MuscleGroup.CARDIOVASCULAR }
            .forEach { group ->
                assertTrue(group.name, byGroup[group].orEmpty().isNotEmpty())
            }
        assertEquals(
            setOf("forearms_front", "forearms_back"),
            regions.filter { it.muscleGroup == MuscleGroup.FOREARMS }.map { it.id }.toSet()
        )
        assertEquals(
            setOf("tigh_extensors_front", "tigh_extensors_back"),
            regions.filter { it.muscleGroup == MuscleGroup.QUADRICEPS }.map { it.id }.toSet()
        )
        assertEquals(
            setOf("calves_front", "calves_back"),
            regions.filter { it.muscleGroup == MuscleGroup.CALVES }.map { it.id }.toSet()
        )
        val obsolete = setOf(
            "tigh_extensors_1",
            "tigh_extensors_2",
            "calves_left_front",
            "calves_right_front",
            "calves_side_front"
        )
        assertTrue(regions.none { it.id in obsolete })
        assertTrue(MaleMuscleMapGeometry.paths.none { it.id in obsolete })
        assertEquals(setOf("serratus"), regions.filter { it.muscleGroup == MuscleGroup.SERRATUS }.map { it.id }.toSet())
        assertEquals(
            setOf("upper_back", "traps_front"),
            regions.filter { it.muscleGroup == MuscleGroup.UPPER_BACK }.map { it.id }.toSet()
        )
        assertTrue(regions.none { it.muscleGroup == null })
        assertEquals(regions.map { it.id }.distinct().size, regions.size)
    }

    @Test
    fun sourceSvgMatchesTheRepositoryOriginalAndHasNoDuplicateIds() {
        val copy = moduleFile("src/main/assets/musclemap/male/male_msucle_map.svg")
        val original = moduleFile("../male_msucle_map.svg")
        assertEquals(original.readBytes().size, copy.readBytes().size)
        assertTrue(original.readBytes().contentEquals(copy.readBytes()))
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(copy)
        val paths = document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "path")
        val ids = (0 until paths.length).map { (paths.item(it) as Element).getAttribute("id") }
        assertEquals(ids.toSet().size, ids.size)
        assertEquals(MaleMuscleMapGeometry.paths.map { it.id }, ids)
        assertFalse(ids.contains("abs"))
        val sbd = (0 until paths.length).map { paths.item(it) as Element }.first { it.getAttribute("id") == "sbd" }
        assertEquals("abs", sbd.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label"))
        val images = document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "image")
        assertEquals(1, images.length)
        val image = images.item(0) as Element
        val href = image.getAttribute("href").ifBlank {
            image.getAttributeNS("http://www.w3.org/1999/xlink", "href")
        }
        assertTrue(href.startsWith("data:image/png;base64,"))
        (0 until paths.length).forEach { index ->
            val path = paths.item(index) as Element
            assertFalse(path.hasAttribute("transform"))
            assertEquals(path.getAttribute("id"), "layer1", (path.parentNode as Element).getAttribute("id"))
        }
        val svg = document.documentElement
        assertEquals("0 0 1052 1047", svg.getAttribute("viewBox"))
    }

    @Test
    fun femaleArtworkIsNotFabricatedAndMaleRemainsTheOnlyVisualization() {
        assertNull(MuscleMapArtworkSelector.femaleOrNull())
        assertEquals(MuscleMapSex.MALE, MuscleMapArtworkSelector.select(MuscleMapSex.FEMALE).sex)
        assertEquals(MuscleMapSex.MALE, MuscleMapArtworkSelector.select(MuscleMapSex.MALE).sex)
        assertEquals(
            MuscleMapArtworkSelector.current().regions.map { it.id },
            MuscleMapArtworkSelector.select(MuscleMapSex.FEMALE).regions.map { it.id }
        )
    }

    @Test
    fun eachLogicalGroupCanBeSelectedOnItsView() {
        val parsed = parseMuscleRegions()
        selectable.forEach { (group, view) ->
            val visible = parsed.filter { it.region.view == view }
            val owned = visible.filter { it.region.muscleGroup == group }
            assertTrue("$group $view", owned.isNotEmpty())
            val selected = owned.any { region ->
                samples(region).any { point ->
                    pathContains(region.path, point) && hitTest(visible, point) == group
                }
            }
            assertTrue("$group $view is tappable", selected)
        }
    }

    @Test
    fun multiplePathsForOneGroupShareTheRecencyColor() {
        val today = LocalDate.parse("2026-10-15")
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today.minusDays(1),
                    MuscleGroup.CALVES,
                    listOf(MuscleGroup.FOREARMS),
                    completedSetCount = 2
                )
            ),
            today
        )
        val fills = MuscleMapColors.heatmapFills(state)
        assertEquals(MuscleMapColors.Days1To2, fills.getValue(MuscleGroup.CALVES))
        assertEquals(MuscleMapColors.Days1To2, fills.getValue(MuscleGroup.FOREARMS))
        assertEquals(2, MuscleMapArtworkSelector.current().regions.count { it.muscleGroup == MuscleGroup.CALVES })
        assertEquals(2, MuscleMapArtworkSelector.current().regions.count { it.muscleGroup == MuscleGroup.QUADRICEPS })
        assertEquals(2, MuscleMapArtworkSelector.current().regions.count { it.muscleGroup == MuscleGroup.FOREARMS })
    }

    @Test
    fun historicalTrapsRowsRemainUpperBackAndSerratusIsNotInventedForOldWorkouts() {
        val primary = ExerciseEnumCodec.muscleOrFallback("TRAPS")
        val secondary = listOfNotNull(ExerciseEnumCodec.muscle("BICEPS"))
        assertEquals(MuscleGroup.UPPER_BACK, primary)
        assertFalse(MuscleGroup.SERRATUS in secondary)

        val today = LocalDate.parse("2026-10-15")
        val heatmap = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today,
                    primary,
                    secondary,
                    completedSetCount = 1
                )
            ),
            today
        )
        assertEquals(MuscleRecencyBand.TODAY, heatmap.entry(MuscleGroup.UPPER_BACK).band)
        assertEquals(MuscleRecencyBand.NEVER, heatmap.entry(MuscleGroup.SERRATUS).band)
        assertEquals(MuscleMapColors.Today, MuscleMapColors.heatmapFills(heatmap).getValue(MuscleGroup.UPPER_BACK))
        assertTrue(
            StarterCatalog.drafts.none {
                it.primaryMuscle == MuscleGroup.SERRATUS || MuscleGroup.SERRATUS in it.secondaryMuscles
            }
        )
        assertEquals("UPPER_BACK", ExerciseEnumCodec.canonicalMuscleName("TRAPS"))
        assertNotNull(ExerciseEnumCodec.muscle("SERRATUS"))
    }

    @Test
    fun mergedFrontQuadricepsAndCalvesAreSelectableOnBothLegs() {
        val front = parseMuscleRegions().filter { it.region.view == MuscleMapView.FRONT }
        listOf(
            "tigh_extensors_front" to MuscleGroup.QUADRICEPS,
            "calves_front" to MuscleGroup.CALVES
        ).forEach { (id, group) ->
            val region = front.first { it.region.id == id }
            val mid = region.bounds.center.x
            val inside = samples(region).filter { pathContains(region.path, it) }
            val left = inside.filter { it.x < mid }
            val right = inside.filter { it.x > mid }
            assertTrue("$id left", left.any { hitTest(front, it) == group })
            assertTrue("$id right", right.any { hitTest(front, it) == group })
        }
    }

    private fun samples(region: app.mymusclemap.ui.components.musclemap.ParsedMuscleRegion): List<Offset> {
        val steps = 40
        val points = ArrayList<Offset>()
        for (row in 1 until steps) {
            for (col in 1 until steps) {
                points += Offset(
                    region.bounds.left + region.bounds.width * col / steps,
                    region.bounds.top + region.bounds.height * row / steps
                )
            }
        }
        return points
    }

    private fun moduleFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.isFile } ?: error("Missing ${candidates.joinToString { it.path }}")
    }

    private companion object {
        val selectable = listOf(
            MuscleGroup.CHEST to MuscleMapView.FRONT,
            MuscleGroup.FRONT_DELTOID to MuscleMapView.FRONT,
            MuscleGroup.SIDE_DELTOID to MuscleMapView.FRONT,
            MuscleGroup.SIDE_DELTOID to MuscleMapView.BACK,
            MuscleGroup.REAR_DELTOID to MuscleMapView.BACK,
            MuscleGroup.UPPER_BACK to MuscleMapView.FRONT,
            MuscleGroup.UPPER_BACK to MuscleMapView.BACK,
            MuscleGroup.NECK to MuscleMapView.FRONT,
            MuscleGroup.NECK to MuscleMapView.BACK,
            MuscleGroup.BICEPS to MuscleMapView.FRONT,
            MuscleGroup.TRICEPS to MuscleMapView.FRONT,
            MuscleGroup.TRICEPS to MuscleMapView.BACK,
            MuscleGroup.FOREARMS to MuscleMapView.FRONT,
            MuscleGroup.FOREARMS to MuscleMapView.BACK,
            MuscleGroup.LATS to MuscleMapView.FRONT,
            MuscleGroup.LATS to MuscleMapView.BACK,
            MuscleGroup.ABS to MuscleMapView.FRONT,
            MuscleGroup.SERRATUS to MuscleMapView.FRONT,
            MuscleGroup.OBLIQUES to MuscleMapView.FRONT,
            MuscleGroup.OBLIQUES to MuscleMapView.BACK,
            MuscleGroup.LOWER_BACK to MuscleMapView.BACK,
            MuscleGroup.GLUTES to MuscleMapView.BACK,
            MuscleGroup.QUADRICEPS to MuscleMapView.FRONT,
            MuscleGroup.QUADRICEPS to MuscleMapView.BACK,
            MuscleGroup.HAMSTRINGS to MuscleMapView.BACK,
            MuscleGroup.ADDUCTORS to MuscleMapView.FRONT,
            MuscleGroup.ADDUCTORS to MuscleMapView.BACK,
            MuscleGroup.CALVES to MuscleMapView.FRONT,
            MuscleGroup.CALVES to MuscleMapView.BACK
        )

        val EXPECTED = mapOf(
            "pecs" to MuscleGroup.CHEST,
            "front_delts" to MuscleGroup.FRONT_DELTOID,
            "side_delts_front" to MuscleGroup.SIDE_DELTOID,
            "traps_front" to MuscleGroup.UPPER_BACK,
            "neck" to MuscleGroup.NECK,
            "biceps" to MuscleGroup.BICEPS,
            "biceps_right" to MuscleGroup.BICEPS,
            "triceps_left_front" to MuscleGroup.TRICEPS,
            "forearms_front" to MuscleGroup.FOREARMS,
            "lats_front" to MuscleGroup.LATS,
            "sbd" to MuscleGroup.ABS,
            "tigh_extensors_front" to MuscleGroup.QUADRICEPS,
            "tigh_adductors_front" to MuscleGroup.ADDUCTORS,
            "calves_front" to MuscleGroup.CALVES,
            "calves_back" to MuscleGroup.CALVES,
            "upper_back" to MuscleGroup.UPPER_BACK,
            "lats" to MuscleGroup.LATS,
            "side_delts_back" to MuscleGroup.SIDE_DELTOID,
            "back_delts_back" to MuscleGroup.REAR_DELTOID,
            "triceps" to MuscleGroup.TRICEPS,
            "lower_back" to MuscleGroup.LOWER_BACK,
            "glutes" to MuscleGroup.GLUTES,
            "tigh_adductors_back" to MuscleGroup.ADDUCTORS,
            "tigh_extensors_back" to MuscleGroup.QUADRICEPS,
            "tigh_flexors" to MuscleGroup.HAMSTRINGS,
            "neck_back" to MuscleGroup.NECK,
            "serratus" to MuscleGroup.SERRATUS,
            "obliques_front" to MuscleGroup.OBLIQUES,
            "path27" to MuscleGroup.OBLIQUES,
            "forearms_back" to MuscleGroup.FOREARMS
        )
    }
}
