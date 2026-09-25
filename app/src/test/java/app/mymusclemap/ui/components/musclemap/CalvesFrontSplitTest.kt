package app.mymusclemap.ui.components.musclemap

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.musclemap.TemplateMuscleEmphasis
import app.mymusclemap.domain.musclemap.TemplateMuscleMapAssembler
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.artwork.AnatomicalSide
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesCatalog
import app.mymusclemap.ui.components.musclemap.artwork.MuscleMapView
import app.mymusclemap.ui.exercises.labelRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CalvesFrontSplitTest {
    private val midline = 15.844f
    private val parsed by lazy { parseMuscleRegions() }
    private val front by lazy { parsed.filter { it.region.view == MuscleMapView.FRONT } }
    private val back by lazy { parsed.filter { it.region.view == MuscleMapView.BACK } }
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val originalLeftPath =
        "m 18.251375,70.441125 0.29058,0.91486 0.6224,3.8681 0.0829,5.15733 -0.87136,5.03304 0.0412,-6.44714 -0.91242,-2.57848 -0.12561,-2.82837 z m 1.9915,2.32915 -0.20753,7.73637 -1.65949,6.23904 1.80478,-0.853 3.00816,-10.83583 -1.03727,-6.82095 z"
    private val originalRightPath =
        "m 13.437675,70.440945 -0.29058,0.91486 -0.62241,3.86828 -0.0829,5.15733 0.87174,5.03304 -0.0418,-6.44714 0.91298,-2.57848 0.1243,-2.82837 z m -1.99151,2.32914 0.20735,7.73637 1.65968,6.23904 -1.80497,-0.85299 -3.0079799,-10.83584 1.03728,-6.82095 z"

    private val frontLeftInterior = Offset(18.40f, 76.00f)

    @Test
    fun frontShinPathsCoverTheMarkedElongatedLowerLegRegions() {
        val left = front.first { it.region.id == "tibialis-anterior-left" }
        val right = front.first { it.region.id == "tibialis-anterior-right" }
        val knee = front.first { it.region.id == "knee-left" }
        val foot = front.first { it.region.id == "foot-left" }
        val quads = front.first { it.region.id == "quads-left" }

        assertEquals(MuscleMapView.FRONT, left.region.view)
        assertEquals(MuscleMapView.FRONT, right.region.view)
        assertTrue(
            "front calves sit on the shin: top=${left.bounds.top} kneeBottom=${knee.bounds.bottom}",
            left.bounds.center.y > knee.bounds.center.y
        )
        assertTrue(
            "front calves sit above the ankle: bottom=${left.bounds.bottom} footTop=${foot.bounds.top}",
            left.bounds.center.y < foot.bounds.center.y
        )
        assertTrue("front calves sit below the quads bulk", left.bounds.center.y > quads.bounds.center.y)
        assertTrue("left shin is anatomical left of midline", left.bounds.center.x > midline)
        assertTrue("right shin is anatomical right of midline", right.bounds.center.x < midline)
        assertTrue(
            "marked interior is the medial elongated strip, not the outer subpath",
            frontLeftInterior.x < left.bounds.center.x && frontLeftInterior.x > midline
        )

        assertTrue(pathContains(left.path, frontLeftInterior))
        assertTrue(pathContains(right.path, mirror(frontLeftInterior)))
        assertFalse(pathContains(knee.path, frontLeftInterior))
        assertFalse(pathContains(foot.path, frontLeftInterior))

        val leftIds = idsAt(front, frontLeftInterior)
        val rightIds = idsAt(front, mirror(frontLeftInterior))
        assertTrue("marked left interior should hit tibialis-anterior-left, was $leftIds", "tibialis-anterior-left" in leftIds)
        assertTrue("marked right interior should hit tibialis-anterior-right, was $rightIds", "tibialis-anterior-right" in rightIds)
        assertTrue(leftIds.none { it.startsWith("knee") || it.startsWith("foot") || it.startsWith("quads") })
        assertTrue(rightIds.none { it.startsWith("knee") || it.startsWith("foot") || it.startsWith("quads") })
    }

    @Test
    fun markedFrontInteriorsBelongOnlyToCalves() {
        listOf(frontLeftInterior, mirror(frontLeftInterior)).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.CALVES), groupsAt(front, point))
        }
    }

    @Test
    fun frontCalvesAreLeftRightMirrorSymmetric() {
        listOf(frontLeftInterior).forEach { point ->
            assertEquals(point.toString(), groupsAt(front, point), groupsAt(front, mirror(point)))
        }
        val left = front.first { it.region.id == "tibialis-anterior-left" }
        val right = front.first { it.region.id == "tibialis-anterior-right" }
        assertEquals(left.bounds.width, right.bounds.width, 0.08f)
        assertEquals(left.bounds.height, right.bounds.height, 0.08f)
        assertEquals(left.bounds.center.y, right.bounds.center.y, 0.08f)
        assertEquals(midline - (left.bounds.center.x - midline), right.bounds.center.x, 0.12f)
    }

    @Test
    fun frontAndBackCalvesShareTheSameRecencyFill() {
        val today = LocalDate.parse("2026-09-16")
        val fills = MuscleMapColors.heatmapFills(
            MuscleHeatmapAssembler.assemble(
                listOf(
                    MuscleTrainingExercise(
                        SessionStatus.COMPLETED,
                        today.minusDays(2),
                        MuscleGroup.CALVES,
                        emptyList(),
                        1
                    )
                ),
                today
            )
        )
        val expected = MuscleMapColors.Days1To2
        assertEquals(MuscleRecencyBand.DAYS_1_2, MuscleHeatmapAssembler.bandFor(2))
        assertEquals(expected, fills.getValue(MuscleGroup.CALVES))
        listOf(frontLeftInterior, mirror(frontLeftInterior)).forEach { point ->
            assertEquals(point.toString(), expected, colorAt(front, point, fills))
        }
        val backLeft = back.first { it.region.id == "calves-gastroc-medial-left" }
        val backPoint = interiorPoint(backLeft)
        assertEquals(expected, colorAt(back, backPoint, fills))
        val overlay = overlayRegionIds(parsed, setOf(MuscleGroup.CALVES))
        assertTrue("tibialis-anterior-left" in overlay)
        assertTrue("tibialis-anterior-right" in overlay)
        assertTrue("calves-gastroc-medial-left" in overlay)
        assertTrue("calves-soleus-right" in overlay)
        assertTrue("calves-gastroc-lateral-right" in overlay)
    }

    @Test
    fun heatmapAndTemplatePreviewShareTheFrontCalvesMapping() {
        val overlay = overlayRegionIds(parsed, setOf(MuscleGroup.CALVES))
        assertTrue("tibialis-anterior-left" in overlay)
        assertTrue("tibialis-anterior-right" in overlay)
        val template = TemplateMuscleMapAssembler.assemble(
            listOf(
                Exercise(
                    id = 1L,
                    name = "Calf raise",
                    normalizedName = "calf raise",
                    category = ExerciseCategory.STRENGTH,
                    movementPattern = MovementPattern.OTHER,
                    measurementType = MeasurementType.REPETITIONS,
                    resistanceBasis = ResistanceBasis.BODYWEIGHT,
                    weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                    primaryMuscle = MuscleGroup.CALVES,
                    secondaryMuscles = emptyList(),
                    notes = null,
                    archived = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )
        assertEquals(TemplateMuscleEmphasis.PRIMARY, template.emphasis[MuscleGroup.CALVES])
        val templateFills = MuscleMapColors.templateFills(template)
        val templateIds = overlayRegionIds(parsed, template.emphasis.keys)
        assertTrue("tibialis-anterior-left" in templateIds)
        assertTrue("calves-gastroc-medial-left" in templateIds)
        assertEquals(MuscleMapColors.TemplatePrimary, templateFills.getValue(MuscleGroup.CALVES))
        listOf(frontLeftInterior, mirror(frontLeftInterior)).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.TemplatePrimary, colorAt(front, point, templateFills))
        }
        assertTrue(overlay.containsAll(templateIds))
    }

    @Test
    fun frontCalvesHitTestAndLabelAreVadli() {
        assertEquals(MuscleGroup.CALVES, hitTest(front, frontLeftInterior, inflate = 0f))
        assertEquals(MuscleGroup.CALVES, hitTest(front, mirror(frontLeftInterior), inflate = 0f))
        val today = LocalDate.parse("2026-09-16")
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today.minusDays(2),
                    MuscleGroup.CALVES,
                    emptyList(),
                    1
                )
            ),
            today
        )
        val recency = resources.getString(R.string.heatmap_days_ago, 2)
        val description = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.CALVES.labelRes()),
            recency
        )
        assertEquals("Vádli: 2 napja", description)
        assertEquals(2, state.entry(MuscleGroup.CALVES).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_1_2, state.entry(MuscleGroup.CALVES).band)
    }

    @Test
    fun neighboringLowerLegMappingsStayUnchanged() {
        assertNull(BodyMusclesCatalog.groupFor("knee-left"))
        assertNull(BodyMusclesCatalog.groupFor("knee-right"))
        assertNull(BodyMusclesCatalog.groupFor("foot-left"))
        assertNull(BodyMusclesCatalog.groupFor("foot-right"))
        assertEquals(MuscleGroup.QUADRICEPS, BodyMusclesCatalog.groupFor("quads-left"))
        assertEquals(MuscleGroup.QUADRICEPS, BodyMusclesCatalog.groupFor("quads-right"))
        val kneePoint = interiorPoint(front.first { it.region.id == "knee-left" })
        val footPoint = interiorPoint(front.first { it.region.id == "foot-left" })
        val quadsPoint = interiorPoint(front.first { it.region.id == "quads-left" })
        assertEquals(emptySet<MuscleGroup>(), groupsAt(front, kneePoint))
        assertTrue(MuscleGroup.CALVES !in groupsAt(front, kneePoint))
        assertTrue(MuscleGroup.CALVES !in groupsAt(front, footPoint))
        assertEquals(setOf(MuscleGroup.QUADRICEPS), groupsAt(front, quadsPoint))
        assertTrue(pathContains(front.first { it.region.id == "knee-left" }.path, kneePoint))
        assertFalse(pathContains(front.first { it.region.id == "tibialis-anterior-left" }.path, kneePoint))
        assertFalse(pathContains(front.first { it.region.id == "tibialis-anterior-left" }.path, footPoint))
        assertFalse(pathContains(front.first { it.region.id == "tibialis-anterior-left" }.path, quadsPoint))
        val calves = BodyMusclesCatalog.regionsFor(MuscleGroup.CALVES)
        assertTrue(calves.none { it.id.startsWith("knee") || it.id.startsWith("foot") || it.id.startsWith("quads") })
        assertEquals(2, calves.count { it.view == MuscleMapView.FRONT })
        assertEquals(6, calves.count { it.view == MuscleMapView.BACK })
    }

    @Test
    fun frontCalvesHaveNoDualOwnershipWithNeighbors() {
        val calves = front.filter { it.region.muscleGroup == MuscleGroup.CALVES }
        val overlapByGroup = mutableMapOf<MuscleGroup, Int>()
        val knee = front.filter { it.region.id.startsWith("knee") }
        var kneeOverlap = 0
        var x = 6.0f
        while (x <= 26.0f) {
            var y = 62.0f
            while (y <= 92.0f) {
                val point = Offset(x, y)
                val inCalves = calves.any { pathContains(it.path, point) }
                if (inCalves) {
                    groupsAt(front, point).filter { it != MuscleGroup.CALVES }.forEach { group ->
                        overlapByGroup[group] = (overlapByGroup[group] ?: 0) + 1
                    }
                    if (knee.any { pathContains(it.path, point) }) kneeOverlap++
                }
                y += 0.35f
            }
            x += 0.35f
        }
        assertEquals("front calves vs knee overlap=$kneeOverlap", 0, kneeOverlap)
        assertEquals(
            "front calves vs quadriceps overlap=${overlapByGroup[MuscleGroup.QUADRICEPS]}",
            0,
            overlapByGroup[MuscleGroup.QUADRICEPS] ?: 0
        )
        assertTrue(
            "marked interiors remain exclusive despite any artwork seams: $overlapByGroup",
            overlapByGroup[MuscleGroup.QUADRICEPS] ?: 0 == 0
        )
        listOf(frontLeftInterior, mirror(frontLeftInterior)).forEach { point ->
            assertEquals(1, groupsAt(front, point).size)
        }
    }

    @Test
    fun frontCalvesPathGeometryIsUnchanged() {
        val left = BodyMusclesCatalog.regions.first { it.id == "tibialis-anterior-left" }
        val right = BodyMusclesCatalog.regions.first { it.id == "tibialis-anterior-right" }
        assertEquals(originalLeftPath, left.pathData)
        assertEquals(originalRightPath, right.pathData)
        assertEquals(AnatomicalSide.LEFT, left.side)
        assertEquals(AnatomicalSide.RIGHT, right.side)
        assertEquals(MuscleGroup.CALVES, left.muscleGroup)
        assertEquals(MuscleGroup.CALVES, right.muscleGroup)
    }

    @Test
    fun paintOrderKeepsFrontCalvesOnTopOfUnmappedNeighbors() {
        val ordered = paintOrderedRegions(front)
        fun covering(point: Offset) = ordered.filter { pathContains(it.path, point) }
        val leftCovering = covering(frontLeftInterior)
        val rightCovering = covering(mirror(frontLeftInterior))
        assertEquals("tibialis-anterior-left", leftCovering.last().region.id)
        assertEquals("tibialis-anterior-right", rightCovering.last().region.id)
        assertEquals(MuscleGroup.CALVES, leftCovering.last().region.muscleGroup)
        val kneeIndex = ordered.indexOfFirst { it.region.id == "knee-left" }
        val footIndex = ordered.indexOfFirst { it.region.id == "foot-left" }
        val shinIndex = ordered.indexOfFirst { it.region.id == "tibialis-anterior-left" }
        assertTrue(kneeIndex < shinIndex)
        assertTrue(footIndex < shinIndex)
    }

    @Test
    fun backCalvesMappingRemainsTheOriginalSixRegions() {
        val backIds = BodyMusclesCatalog.regionsFor(MuscleGroup.CALVES)
            .filter { it.view == MuscleMapView.BACK }
            .map { it.id }
            .toSet()
        assertEquals(
            setOf(
                "calves-gastroc-medial-left", "calves-gastroc-medial-right",
                "calves-gastroc-lateral-left", "calves-gastroc-lateral-right",
                "calves-soleus-left", "calves-soleus-right"
            ),
            backIds
        )
    }

    private fun idsAt(regions: List<ParsedMuscleRegion>, point: Offset): List<String> {
        return regions.filter { pathContains(it.path, point) }.map { it.region.id }
    }

    private fun groupsAt(regions: List<ParsedMuscleRegion>, point: Offset): Set<MuscleGroup> {
        return regions.mapNotNull { region ->
            val group = region.region.muscleGroup ?: return@mapNotNull null
            if (pathContains(region.path, point)) group else null
        }.toSet()
    }

    private fun colorAt(
        regions: List<ParsedMuscleRegion>,
        point: Offset,
        fills: Map<MuscleGroup, Color>
    ): Color? {
        val groups = groupsAt(regions, point)
        assertEquals(point.toString(), 1, groups.size)
        return fills.getValue(groups.single())
    }

    private fun interiorPoint(parsed: ParsedMuscleRegion): Offset {
        val steps = 8
        for (row in 1 until steps) {
            for (col in 1 until steps) {
                val point = Offset(
                    parsed.bounds.left + parsed.bounds.width * col / steps,
                    parsed.bounds.top + parsed.bounds.height * row / steps
                )
                if (pathContains(parsed.path, point)) {
                    return point
                }
            }
        }
        return parsed.bounds.center
    }

    private fun mirror(point: Offset): Offset {
        return Offset(2f * midline - point.x, point.y)
    }
}
