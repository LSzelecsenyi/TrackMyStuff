package hu.laca.weighttracker.ui.components.musclemap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapAssembler
import hu.laca.weighttracker.domain.musclemap.MuscleRecencyBand
import hu.laca.weighttracker.domain.musclemap.MuscleTrainingExercise
import hu.laca.weighttracker.domain.musclemap.TemplateMuscleEmphasis
import hu.laca.weighttracker.domain.musclemap.TemplateMuscleMapAssembler
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.ui.components.musclemap.artwork.AnatomicalSide
import hu.laca.weighttracker.ui.components.musclemap.artwork.BodyMusclesCatalog
import hu.laca.weighttracker.ui.components.musclemap.artwork.MuscleMapView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class AbdominalSplitTest {
    private val midline = 15.844f
    private val parsed by lazy { parseMuscleRegions() }
    private val front by lazy { parsed.filter { it.region.view == MuscleMapView.FRONT } }

    private val centralAbs = listOf(
        Offset(17.70f, 26.60f),
        Offset(17.56f, 29.44f),
        Offset(17.55f, 32.44f)
    )
    private val sideObliques = listOf(
        Offset(20.41f, 27.73f),
        Offset(21.51f, 26.34f),
        Offset(20.70f, 29.46f),
        Offset(20.49f, 31.94f)
    )
    private val formerV = Offset(17.47f, 38.86f)
    private val formerLowerLateral = Offset(20.85f, 36.87f)

    @Test
    fun exactlyThreeAbsBlocksAndFourObliqueBlocksPerSide() {
        val abs = BodyMusclesCatalog.regionsFor(MuscleGroup.ABS)
        val obliques = BodyMusclesCatalog.regionsFor(MuscleGroup.OBLIQUES)
        assertEquals(3, abs.count { it.side == AnatomicalSide.LEFT })
        assertEquals(3, abs.count { it.side == AnatomicalSide.RIGHT })
        assertEquals(4, obliques.count { it.side == AnatomicalSide.LEFT })
        assertEquals(4, obliques.count { it.side == AnatomicalSide.RIGHT })
    }

    @Test
    fun centralBlockPointsBelongOnlyToAbs() {
        (centralAbs + centralAbs.map { mirror(it) }).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.ABS), groupsAt(point))
        }
    }

    @Test
    fun sideBlockPointsBelongOnlyToObliques() {
        (sideObliques + sideObliques.map { mirror(it) }).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.OBLIQUES), groupsAt(point))
        }
    }

    @Test
    fun noRepresentativeBlockBelongsToBothGroups() {
        (centralAbs + sideObliques).forEach { point ->
            val groups = groupsAt(point)
            assertTrue(point.toString(), groups.size == 1)
            assertTrue(point.toString(), MuscleGroup.ABS !in groups || MuscleGroup.OBLIQUES !in groups)
        }
    }

    @Test
    fun formerVAndLowerLateralRegionsAreUnmapped() {
        assertEquals(formerV.toString(), emptySet<MuscleGroup>(), groupsAt(formerV))
        assertEquals(formerLowerLateral.toString(), emptySet<MuscleGroup>(), groupsAt(formerLowerLateral))
        assertEquals(emptySet<MuscleGroup>(), groupsAt(mirror(formerV)))
        assertEquals(emptySet<MuscleGroup>(), groupsAt(mirror(formerLowerLateral)))
        assertEquals(null, BodyMusclesCatalog.groupFor("abs-lower-left"))
        assertEquals(null, BodyMusclesCatalog.groupFor("abs-upper-left"))
    }

    @Test
    fun leftAndRightExtractedBlocksAreSymmetrical() {
        (centralAbs + sideObliques).forEach { point ->
            assertEquals(point.toString(), groupsAt(point), groupsAt(mirror(point)))
        }
    }

    @Test
    fun absAndObliquesCanRenderDifferentSimultaneousColors() {
        val today = LocalDate.parse("2026-09-16")
        val fills = MuscleMapColors.heatmapFills(
            MuscleHeatmapAssembler.assemble(
                listOf(
                    MuscleTrainingExercise(
                        SessionStatus.COMPLETED,
                        today.minusDays(2),
                        MuscleGroup.ABS,
                        emptyList(),
                        1
                    ),
                    MuscleTrainingExercise(
                        SessionStatus.COMPLETED,
                        today,
                        MuscleGroup.OBLIQUES,
                        emptyList(),
                        1
                    )
                ),
                today
            )
        )
        assertEquals(MuscleRecencyBand.DAYS_1_2, MuscleHeatmapAssembler.bandFor(2))
        assertEquals(MuscleMapColors.Days1To2, fills.getValue(MuscleGroup.ABS))
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.OBLIQUES))
        centralAbs.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.Days1To2, colorAt(point, fills))
        }
        sideObliques.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.Today, colorAt(point, fills))
        }
    }

    @Test
    fun heatmapAndTemplatePreviewUseCorrectedRegions() {
        val parsedRegions = parsed
        val heatmapIds = overlayRegionIds(parsedRegions, setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES))
        assertTrue("abs-block-left-1" in heatmapIds)
        assertTrue("abs-block-left-3" in heatmapIds)
        assertTrue("obliques-block-left-4" in heatmapIds)
        assertTrue("abs-block-right-1" in heatmapIds)
        assertTrue("obliques-block-right-1" in heatmapIds)
        assertTrue("abs-upper-left" !in heatmapIds)
        assertTrue("abs-lower-left" !in heatmapIds)

        val template = TemplateMuscleMapAssembler.assemble(
            listOf(
                Exercise(
                    id = 1L,
                    name = "Crunch",
                    normalizedName = "crunch",
                    category = ExerciseCategory.STRENGTH,
                    movementPattern = MovementPattern.OTHER,
                    measurementType = MeasurementType.REPETITIONS,
                    resistanceBasis = ResistanceBasis.BODYWEIGHT,
                    weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                    primaryMuscle = MuscleGroup.ABS,
                    secondaryMuscles = listOf(MuscleGroup.OBLIQUES),
                    notes = null,
                    archived = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )
        assertEquals(TemplateMuscleEmphasis.PRIMARY, template.emphasis[MuscleGroup.ABS])
        assertEquals(TemplateMuscleEmphasis.SECONDARY, template.emphasis[MuscleGroup.OBLIQUES])
        val templateFills = MuscleMapColors.templateFills(template)
        val templateIds = overlayRegionIds(parsedRegions, template.emphasis.keys)
        assertEquals(MuscleMapColors.TemplatePrimary, templateFills.getValue(MuscleGroup.ABS))
        assertEquals(MuscleMapColors.TemplateSecondary, templateFills.getValue(MuscleGroup.OBLIQUES))
        assertTrue(heatmapIds.containsAll(templateIds))
        centralAbs.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.TemplatePrimary, colorAt(point, templateFills))
        }
        sideObliques.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.TemplateSecondary, colorAt(point, templateFills))
        }
    }

    @Test
    fun leftAndRightObliqueTapsReturnTheSameGroup() {
        sideObliques.forEach { point ->
            assertEquals(MuscleGroup.OBLIQUES, hitTest(front, point, inflate = 0f))
            assertEquals(MuscleGroup.OBLIQUES, hitTest(front, mirror(point), inflate = 0f))
        }
        centralAbs.forEach { point ->
            assertEquals(MuscleGroup.ABS, hitTest(front, point, inflate = 0f))
            assertEquals(MuscleGroup.ABS, hitTest(front, mirror(point), inflate = 0f))
        }
    }

    private fun groupsAt(point: Offset): Set<MuscleGroup> {
        return front.mapNotNull { region ->
            val group = region.region.muscleGroup ?: return@mapNotNull null
            if (pathContains(region.path, point)) group else null
        }.toSet()
    }

    private fun colorAt(point: Offset, fills: Map<MuscleGroup, Color>): Color? {
        val groups = groupsAt(point)
        assertEquals(point.toString(), 1, groups.size)
        return fills.getValue(groups.single())
    }

    private fun mirror(point: Offset): Offset {
        return Offset(2f * midline - point.x, point.y)
    }
}
