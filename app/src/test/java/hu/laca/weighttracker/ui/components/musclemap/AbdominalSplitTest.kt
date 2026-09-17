package hu.laca.weighttracker.ui.components.musclemap

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.R
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
import hu.laca.weighttracker.ui.exercises.labelRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

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
    private val lowerAbsInterior = Offset(17.47f, 38.86f)
    private val lowerObliqueInterior = Offset(20.85f, 36.87f)

    @Test
    fun absIncludeSixPackBlocksAndLowerCentralRegions() {
        val abs = BodyMusclesCatalog.regionsFor(MuscleGroup.ABS)
        val obliques = BodyMusclesCatalog.regionsFor(MuscleGroup.OBLIQUES)
        assertEquals(
            setOf(
                "abs-block-left-1", "abs-block-left-2", "abs-block-left-3",
                "abs-block-right-1", "abs-block-right-2", "abs-block-right-3",
                "abs-lower-left", "abs-lower-right"
            ),
            abs.map { it.id }.toSet()
        )
        assertEquals(
            setOf(
                "obliques-block-left-1", "obliques-block-left-2", "obliques-block-left-3", "obliques-block-left-4",
                "obliques-block-right-1", "obliques-block-right-2", "obliques-block-right-3", "obliques-block-right-4",
                "abs-upper-left", "abs-upper-right"
            ),
            obliques.map { it.id }.toSet()
        )
        assertEquals(4, abs.count { it.side == AnatomicalSide.LEFT })
        assertEquals(4, abs.count { it.side == AnatomicalSide.RIGHT })
        assertEquals(5, obliques.count { it.side == AnatomicalSide.LEFT })
        assertEquals(5, obliques.count { it.side == AnatomicalSide.RIGHT })
        assertTrue(abs.none { it.id.startsWith("obliques-") })
        assertTrue(obliques.none { it.id.startsWith("abs-block") || it.id.startsWith("abs-lower") })
    }

    @Test
    fun lowerAbsPathsCoverTheCentralVBelowTheSixPack() {
        val lowerLeft = front.first { it.region.id == "abs-lower-left" }
        val lowerRight = front.first { it.region.id == "abs-lower-right" }
        val upperLeft = front.first { it.region.id == "abs-upper-left" }
        val upperRight = front.first { it.region.id == "abs-upper-right" }
        val lowestBlockLeft = front.first { it.region.id == "abs-block-left-3" }
        val lowestBlockRight = front.first { it.region.id == "abs-block-right-3" }

        assertTrue("lower left should sit under the six-pack", lowerLeft.bounds.top >= lowestBlockLeft.bounds.bottom - 0.6f)
        assertTrue("lower right should sit under the six-pack", lowerRight.bounds.top >= lowestBlockRight.bounds.bottom - 0.6f)
        assertTrue("lower left should stay medial of abs-upper", lowerLeft.bounds.center.x < upperLeft.bounds.center.x)
        assertTrue("lower right should stay medial of abs-upper", lowerRight.bounds.center.x > upperRight.bounds.center.x)
        assertTrue("lower left should sit left of midline in artwork x", lowerLeft.bounds.center.x > midline)
        assertTrue("lower right should sit right of midline in artwork x", lowerRight.bounds.center.x < midline)

        assertTrue(pathContains(lowerLeft.path, lowerAbsInterior))
        assertTrue(pathContains(lowerRight.path, mirror(lowerAbsInterior)))
        assertFalse(pathContains(upperLeft.path, lowerAbsInterior))
        assertFalse(pathContains(upperRight.path, mirror(lowerAbsInterior)))
        assertFalse(pathContains(upperLeft.path, mirror(lowerAbsInterior)))
        assertFalse(pathContains(upperRight.path, lowerAbsInterior))

        val leftIds = idsAt(lowerAbsInterior)
        val rightIds = idsAt(mirror(lowerAbsInterior))
        assertTrue("purple left interior should hit abs-lower-left, was $leftIds", "abs-lower-left" in leftIds)
        assertTrue("purple right interior should hit abs-lower-right, was $rightIds", "abs-lower-right" in rightIds)
        assertTrue(leftIds.none { it.startsWith("obliques-") || it.startsWith("abs-upper") || it.startsWith("abs-block") })
        assertTrue(rightIds.none { it.startsWith("obliques-") || it.startsWith("abs-upper") || it.startsWith("abs-block") })
    }

    @Test
    fun upperLateralPathsCoverTheLowerSideObliqueRegions() {
        val upperLeft = front.first { it.region.id == "abs-upper-left" }
        val upperRight = front.first { it.region.id == "abs-upper-right" }
        val lowerLeft = front.first { it.region.id == "abs-lower-left" }
        val lowestObliqueLeft = front.first { it.region.id == "obliques-block-left-4" }
        val lowestObliqueRight = front.first { it.region.id == "obliques-block-right-4" }

        assertTrue("upper left should sit under the small oblique blocks", upperLeft.bounds.top >= lowestObliqueLeft.bounds.bottom - 1.2f)
        assertTrue("upper right should sit under the small oblique blocks", upperRight.bounds.top >= lowestObliqueRight.bounds.bottom - 1.2f)
        assertTrue("upper left should stay lateral of lower abs", upperLeft.bounds.center.x > lowerLeft.bounds.center.x)
        assertTrue("upper left should sit on the anatomical left", upperLeft.bounds.center.x > midline)
        assertTrue("upper right should sit on the anatomical right", upperRight.bounds.center.x < midline)

        assertTrue(pathContains(upperLeft.path, lowerObliqueInterior))
        assertTrue(pathContains(upperRight.path, mirror(lowerObliqueInterior)))
        assertFalse(pathContains(lowerLeft.path, lowerObliqueInterior))
        assertFalse(pathContains(upperLeft.path, lowerAbsInterior))

        val leftIds = idsAt(lowerObliqueInterior)
        val rightIds = idsAt(mirror(lowerObliqueInterior))
        assertTrue("purple left interior should hit abs-upper-left, was $leftIds", "abs-upper-left" in leftIds)
        assertTrue("purple right interior should hit abs-upper-right, was $rightIds", "abs-upper-right" in rightIds)
        assertTrue(leftIds.none { it.startsWith("abs-block") || it.startsWith("abs-lower") || it.startsWith("obliques-block") })
        assertTrue(rightIds.none { it.startsWith("abs-block") || it.startsWith("abs-lower") || it.startsWith("obliques-block") })
    }

    @Test
    fun centralBlockPointsBelongOnlyToAbs() {
        (centralAbs + centralAbs.map { mirror(it) }).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.ABS), groupsAt(point))
        }
    }

    @Test
    fun lowerAbsInteriorPointsBelongOnlyToAbs() {
        listOf(lowerAbsInterior, mirror(lowerAbsInterior)).forEach { point ->
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
        (centralAbs + sideObliques + listOf(lowerAbsInterior, lowerObliqueInterior)).forEach { point ->
            val groups = groupsAt(point)
            assertTrue(point.toString(), groups.size == 1)
            assertTrue(point.toString(), MuscleGroup.ABS !in groups || MuscleGroup.OBLIQUES !in groups)
        }
    }

    @Test
    fun absAndObliquesHaveNoMeaningfulGeometricOverlap() {
        val absPaths = front.filter { it.region.muscleGroup == MuscleGroup.ABS }
        val obliquePaths = front.filter { it.region.muscleGroup == MuscleGroup.OBLIQUES }
        var overlap = 0
        var x = 8.0f
        while (x <= 24.0f) {
            var y = 24.0f
            while (y <= 46.0f) {
                val point = Offset(x, y)
                val inAbs = absPaths.any { pathContains(it.path, point) }
                val inOblique = obliquePaths.any { pathContains(it.path, point) }
                if (inAbs && inOblique) overlap++
                y += 0.25f
            }
            x += 0.25f
        }
        assertEquals("ABS and OBLIQUES should not share fill samples", 0, overlap)
    }

    @Test
    fun lowerSideObliqueInteriorPointsBelongOnlyToObliques() {
        listOf(lowerObliqueInterior, mirror(lowerObliqueInterior)).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.OBLIQUES), groupsAt(point))
        }
    }

    @Test
    fun leftAndRightExtractedBlocksAreSymmetrical() {
        (centralAbs + sideObliques + listOf(lowerAbsInterior, lowerObliqueInterior)).forEach { point ->
            assertEquals(point.toString(), groupsAt(point), groupsAt(mirror(point)))
        }
        val left = front.first { it.region.id == "abs-upper-left" }
        val right = front.first { it.region.id == "abs-upper-right" }
        assertEquals(left.bounds.width, right.bounds.width, 0.05f)
        assertEquals(left.bounds.height, right.bounds.height, 0.05f)
        assertEquals(left.bounds.center.y, right.bounds.center.y, 0.05f)
        assertEquals(midline - (left.bounds.center.x - midline), right.bounds.center.x, 0.08f)
        val lowerLeft = front.first { it.region.id == "abs-lower-left" }
        val lowerRight = front.first { it.region.id == "abs-lower-right" }
        assertEquals(lowerLeft.bounds.width, lowerRight.bounds.width, 0.05f)
        assertEquals(lowerLeft.bounds.height, lowerRight.bounds.height, 0.05f)
        assertEquals(midline - (lowerLeft.bounds.center.x - midline), lowerRight.bounds.center.x, 0.08f)
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
        listOf(lowerAbsInterior, mirror(lowerAbsInterior)).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.Days1To2, colorAt(point, fills))
        }
        sideObliques.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.Today, colorAt(point, fills))
        }
        listOf(lowerObliqueInterior, mirror(lowerObliqueInterior)).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.Today, colorAt(point, fills))
        }
    }

    @Test
    fun absOnlyFillColorsSixPackAndLowerRegionsTheSame() {
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
                    )
                ),
                today
            )
        )
        val expected = MuscleMapColors.Days1To2
        assertEquals(expected, fills.getValue(MuscleGroup.ABS))
        (centralAbs + listOf(lowerAbsInterior, mirror(lowerAbsInterior))).forEach { point ->
            assertEquals(point.toString(), expected, colorAt(point, fills))
        }
        sideObliques.forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.NeverTrained, colorAt(point, fills))
        }
        listOf(lowerObliqueInterior, mirror(lowerObliqueInterior)).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.NeverTrained, colorAt(point, fills))
        }
    }

    @Test
    fun obliquesOnlyFillColorsSideBlocksAndLowerLateralRegionsTheSame() {
        val today = LocalDate.parse("2026-09-16")
        val fills = MuscleMapColors.heatmapFills(
            MuscleHeatmapAssembler.assemble(
                listOf(
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
        val expected = MuscleMapColors.Today
        assertEquals(expected, fills.getValue(MuscleGroup.OBLIQUES))
        (sideObliques + listOf(lowerObliqueInterior, mirror(lowerObliqueInterior))).forEach { point ->
            assertEquals(point.toString(), expected, colorAt(point, fills))
        }
        (centralAbs + listOf(lowerAbsInterior, mirror(lowerAbsInterior))).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.NeverTrained, colorAt(point, fills))
        }
    }

    @Test
    fun heatmapAndTemplatePreviewUseCorrectedRegions() {
        val parsedRegions = parsed
        val heatmapIds = overlayRegionIds(parsedRegions, setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES))
        assertTrue("abs-block-left-1" in heatmapIds)
        assertTrue("abs-block-left-3" in heatmapIds)
        assertTrue("abs-lower-left" in heatmapIds)
        assertTrue("abs-lower-right" in heatmapIds)
        assertTrue("obliques-block-left-4" in heatmapIds)
        assertTrue("abs-block-right-1" in heatmapIds)
        assertTrue("obliques-block-right-1" in heatmapIds)
        assertTrue("abs-upper-left" in heatmapIds)
        assertTrue("abs-upper-right" in heatmapIds)

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
        assertTrue("abs-lower-left" in templateIds)
        assertTrue("abs-lower-right" in templateIds)
        assertTrue("abs-upper-left" in templateIds)
        assertTrue("abs-upper-right" in templateIds)
        (centralAbs + listOf(lowerAbsInterior)).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.TemplatePrimary, colorAt(point, templateFills))
        }
        (sideObliques + listOf(lowerObliqueInterior)).forEach { point ->
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
        listOf(lowerAbsInterior, mirror(lowerAbsInterior)).forEach { point ->
            assertEquals(MuscleGroup.ABS, hitTest(front, point, inflate = 0f))
        }
        listOf(lowerObliqueInterior, mirror(lowerObliqueInterior)).forEach { point ->
            assertEquals(MuscleGroup.OBLIQUES, hitTest(front, point, inflate = 0f))
        }
    }

    @Test
    fun lowerAbsTapsReportHasWithMatchingRecency() {
        assertEquals(MuscleGroup.ABS, hitTest(front, lowerAbsInterior, inflate = 0f))
        assertEquals(MuscleGroup.ABS, hitTest(front, mirror(lowerAbsInterior), inflate = 0f))
        val today = LocalDate.parse("2026-09-16")
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today.minusDays(2),
                    MuscleGroup.ABS,
                    emptyList(),
                    1
                )
            ),
            today
        )
        val recency = resources.getString(R.string.heatmap_days_ago, 2)
        val description = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.ABS.labelRes()),
            recency
        )
        assertEquals("Has: 2 napja", description)
        assertEquals(2, state.entry(MuscleGroup.ABS).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_1_2, state.entry(MuscleGroup.ABS).band)
    }

    @Test
    fun lowerObliqueTapsReportFerdeHasizomWithMatchingRecency() {
        assertEquals(MuscleGroup.OBLIQUES, hitTest(front, lowerObliqueInterior, inflate = 0f))
        assertEquals(MuscleGroup.OBLIQUES, hitTest(front, mirror(lowerObliqueInterior), inflate = 0f))
        val today = LocalDate.parse("2026-09-16")
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today.minusDays(2),
                    MuscleGroup.OBLIQUES,
                    emptyList(),
                    1
                )
            ),
            today
        )
        val recency = resources.getString(R.string.heatmap_days_ago, 2)
        val description = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.OBLIQUES.labelRes()),
            recency
        )
        assertEquals("Ferde hasizom: 2 napja", description)
        assertEquals(2, state.entry(MuscleGroup.OBLIQUES).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_1_2, state.entry(MuscleGroup.OBLIQUES).band)
    }

    @Test
    fun paintOrderKeepsLowerAbsOnTopOfOverlappingRegions() {
        val ordered = paintOrderedRegions(front)
        fun covering(point: Offset) = ordered.filter { pathContains(it.path, point) }
        val leftCovering = covering(lowerAbsInterior)
        val rightCovering = covering(mirror(lowerAbsInterior))
        assertEquals("abs-lower-left", leftCovering.last().region.id)
        assertEquals("abs-lower-right", rightCovering.last().region.id)
        assertEquals(MuscleGroup.ABS, leftCovering.last().region.muscleGroup)
        assertEquals(MuscleGroup.ABS, rightCovering.last().region.muscleGroup)
        val hipIndex = ordered.indexOfFirst { it.region.id == "hip-flexor-left" }
        val lowerIndex = ordered.indexOfFirst { it.region.id == "abs-lower-left" }
        assertTrue("unmapped hip flexor should be painted under mapped lower abs", hipIndex < lowerIndex)
        val leftObliqueCovering = covering(lowerObliqueInterior)
        val rightObliqueCovering = covering(mirror(lowerObliqueInterior))
        assertEquals("abs-upper-left", leftObliqueCovering.last().region.id)
        assertEquals("abs-upper-right", rightObliqueCovering.last().region.id)
        assertEquals(MuscleGroup.OBLIQUES, leftObliqueCovering.last().region.muscleGroup)
        assertEquals(MuscleGroup.OBLIQUES, rightObliqueCovering.last().region.muscleGroup)
        val upperIndex = ordered.indexOfFirst { it.region.id == "abs-upper-left" }
        assertTrue("mapped lower-side obliques should paint after unmapped hip flexor", hipIndex < upperIndex)
    }

    private fun idsAt(point: Offset): List<String> {
        return front.filter { pathContains(it.path, point) }.map { it.region.id }
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
