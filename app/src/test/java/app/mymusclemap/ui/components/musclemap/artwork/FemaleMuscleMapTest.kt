package app.mymusclemap.ui.components.musclemap.artwork

import androidx.compose.ui.geometry.Offset
import app.mymusclemap.data.preferences.DisplayedMuscleMapBodyModel
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.components.musclemap.hitTest
import app.mymusclemap.ui.components.musclemap.parseMuscleRegions
import app.mymusclemap.ui.components.musclemap.pathContains
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
class FemaleMuscleMapTest {
    @After
    fun restoreMaleArtwork() {
        DisplayedMuscleMapBodyModel.current = MuscleMapBodyModel.MALE
    }

    @Test
    fun everyFemalePathMapsToTheSameMuscleGroupsAsTheMaleDrawing() {
        val female = MuscleMapArtworkSelector.select(MuscleMapBodyModel.FEMALE)
        val male = MuscleMapArtworkSelector.select(MuscleMapBodyModel.MALE)
        assertEquals(MuscleMapBodyModel.FEMALE, female.bodyModel)
        assertEquals(FemaleMuscleMapGeometry.paths.map { it.id }.toSet(), female.regions.map { it.id }.toSet())
        assertEquals(
            FemaleMuscleMapGeometry.paths.map { it.id }.toSet(),
            MuscleMapCatalog.femaleGroupByPathId.keys
        )
        assertEquals(
            MaleMuscleMapGeometry.paths.map { it.id }.toSet(),
            MuscleMapCatalog.maleGroupByPathId.keys
        )
        assertEquals(
            male.regions.map { it.muscleGroup }.toSet(),
            female.regions.map { it.muscleGroup }.toSet()
        )
        assertTrue(female.regions.none { it.muscleGroup == null })
        assertTrue(FemaleMuscleMapGeometry.paths.none { it.id == "path27" })
        assertEquals(MuscleGroup.OBLIQUES, female.regions.first { it.id == "obliques_back" }.muscleGroup)
        assertEquals(MuscleMapView.BACK, female.regions.first { it.id == "obliques_back" }.view)
        assertEquals(MuscleGroup.LOWER_BACK, female.regions.first { it.id == "lower_back" }.muscleGroup)
        assertEquals(MuscleGroup.FRONT_DELTOID, female.regions.first { it.id == "side_delts_front" }.muscleGroup)
        assertEquals(MuscleGroup.SIDE_DELTOID, female.regions.first { it.id == "path3" }.muscleGroup)
        assertEquals(MuscleGroup.SIDE_DELTOID, male.regions.first { it.id == "side_delts_front" }.muscleGroup)
        assertEquals(
            setOf("tigh_extensors_2", "path4"),
            female.regions.filter { it.id == "tigh_extensors_2" || it.id == "path4" }.map { it.id }.toSet()
        )
        female.regions.filter { it.id == "tigh_extensors_2" || it.id == "path4" }.forEach {
            assertEquals(MuscleGroup.QUADRICEPS, it.muscleGroup)
        }
        assertEquals(MuscleGroup.ABS, female.regions.first { it.id == "abs" }.muscleGroup)
        assertEquals(MuscleGroup.TRICEPS, female.regions.first { it.id == "triceps_front" }.muscleGroup)
    }

    @Test
    fun sourceSvgMatchesTheRepositoryOriginal() {
        val copy = moduleFile("src/main/assets/musclemap/female/female_muscle_map.svg")
        val original = moduleFile("../female_muscle_map.svg")
        assertTrue(original.readBytes().contentEquals(copy.readBytes()))
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(copy)
        val paths = document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "path")
        val ids = (0 until paths.length).map { (paths.item(it) as Element).getAttribute("id") }
        assertEquals(ids.toSet().size, ids.size)
        assertEquals(FemaleMuscleMapGeometry.paths.map { it.id }, ids)
        assertFalse(ids.contains("path27"))
        (0 until paths.length).forEach { index ->
            val path = paths.item(index) as Element
            assertFalse(path.hasAttribute("transform"))
            assertEquals("layer2", (path.parentNode as Element).getAttribute("id"))
        }
        assertEquals("0 0 985 1048", document.documentElement.getAttribute("viewBox"))
    }

    @Test
    fun eachLogicalGroupCanBeSelectedOnItsView() {
        val parsed = parseMuscleRegions(MuscleMapArtworkSelector.select(MuscleMapBodyModel.FEMALE))
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
    fun switchingArtworkDoesNotChangeMuscleActivation() {
        val today = LocalDate.parse("2026-10-15")
        val exercises = listOf(
            MuscleTrainingExercise(
                SessionStatus.COMPLETED,
                today.minusDays(1),
                MuscleGroup.CHEST,
                listOf(MuscleGroup.TRICEPS, MuscleGroup.OBLIQUES),
                completedSetCount = 3
            )
        )
        DisplayedMuscleMapBodyModel.current = MuscleMapBodyModel.MALE
        val maleState = MuscleHeatmapAssembler.assemble(exercises, today)
        val maleFills = MuscleMapColors.heatmapFills(maleState)
        DisplayedMuscleMapBodyModel.current = MuscleMapBodyModel.FEMALE
        val femaleState = MuscleHeatmapAssembler.assemble(exercises, today)
        val femaleFills = MuscleMapColors.heatmapFills(femaleState)
        assertEquals(maleState, femaleState)
        assertEquals(maleFills, femaleFills)
        assertEquals(MuscleMapColors.Days1To2, femaleFills.getValue(MuscleGroup.OBLIQUES))
        assertEquals(MuscleMapColors.Days1To2, femaleFills.getValue(MuscleGroup.CHEST))
        val groups = MuscleMapArtworkSelector.select(MuscleMapBodyModel.FEMALE).regions.map { it.muscleGroup }.toSet()
        assertEquals(
            groups,
            MuscleMapArtworkSelector.select(MuscleMapBodyModel.MALE).regions.map { it.muscleGroup }.toSet()
        )
        assertTrue(groups.all { it in femaleFills.keys })
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
    }
}
