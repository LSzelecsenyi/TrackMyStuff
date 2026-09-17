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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class NeckSplitTest {
    private val midline = 15.844f
    private val parsed by lazy { parseMuscleRegions() }
    private val front by lazy { parsed.filter { it.region.view == MuscleMapView.FRONT } }
    private val back by lazy { parsed.filter { it.region.view == MuscleMapView.BACK } }
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val originalNeckLeftPath =
        "m 18.385135,11.910505 -1.64975,2.35202 -0.74538,2.62234 1.73486,-1.38354 0.86649,-2.97104 z"
    private val originalNeckRightPath =
        "m 13.304665,11.910505 1.64975,2.35202 0.74426,2.62159 -1.73486,-1.38354 -0.86649,-2.97104 z"
    private val originalNapePath =
        "m 52.369695,12.105075 -2.35767,-1.55045 -1.47119,-3.9514301 -0.60741,0.0403 0.27409,1.82447 0.97635,0.33932 0.7613,2.2157201 0.33017,1.06849 0.0895,2.14894 1.16448,0.008 0.10563,-0.70833 0.54716,-0.0606 z m 1.01793,1.47595 0.23768,0.64982 1.38107,-0.004 0.01,-2.38784 0.25971,-0.79061 0.57215,-2.1698001 0.76359,-0.41018 0.25158,-1.78416 -0.62859,0.0193 -1.08488,3.8998101 -2.39725,1.46684 0.2768,1.48507 z"

    private val frontLeftInterior = Offset(17.50f, 13.80f)
    private val napeLeftInterior = Offset(13.40f, 11.20f)
    private val trapsUpperInterior = Offset(8.80f, 15.60f)
    private val trapsMidInterior = Offset(12.40f, 19.50f)
    private val nape by lazy { back.first { it.region.id == "nape" } }
    private val leftEarInterior by lazy { representativeInterior(nape, 10.90f, 12.80f, 6.65f, 9.20f) }
    private val rightEarInterior by lazy { representativeInterior(nape, 18.00f, 20.10f, 6.65f, 9.20f) }

    @Test
    fun markedFrontNeckInteriorsBelongOnlyToNeck() {
        listOf(frontLeftInterior, mirror(frontLeftInterior)).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.NECK), groupsAt(front, point))
        }
        val leftIds = idsAt(front, frontLeftInterior)
        val rightIds = idsAt(front, mirror(frontLeftInterior))
        assertTrue("front left should hit neck-left, was $leftIds", "neck-left" in leftIds)
        assertTrue("front right should hit neck-right, was $rightIds", "neck-right" in rightIds)
        assertTrue(leftIds.none { it.startsWith("head") || it.startsWith("face") || it.startsWith("shoulder") })
        assertTrue(rightIds.none { it.startsWith("head") || it.startsWith("face") || it.startsWith("shoulder") })
        assertTrue(pathContains(front.first { it.region.id == "neck-left" }.path, frontLeftInterior))
        assertTrue(pathContains(front.first { it.region.id == "neck-right" }.path, mirror(frontLeftInterior)))
    }

    @Test
    fun markedBackNapeInteriorsBelongOnlyToNeck() {
        listOf(napeLeftInterior, mirror(napeLeftInterior)).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.NECK), groupsAt(back, point))
        }
        val leftIds = idsAt(back, napeLeftInterior)
        val rightIds = idsAt(back, mirror(napeLeftInterior))
        assertTrue("back left nape should hit nape, was $leftIds", "nape" in leftIds)
        assertTrue("back right nape should hit nape, was $rightIds", "nape" in rightIds)
        assertTrue(leftIds.none { it.startsWith("traps") || it.startsWith("head") })
        assertTrue(rightIds.none { it.startsWith("traps") || it.startsWith("head") })
        val nape = back.first { it.region.id == "nape" }
        assertTrue(pathContains(nape.path, napeLeftInterior))
        assertTrue(pathContains(nape.path, mirror(napeLeftInterior)))
    }

    @Test
    fun markedEarRegionsBelongOnlyToNeck() {
        listOf(leftEarInterior, rightEarInterior).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.NECK), groupsAt(back, point))
            assertTrue(point.toString(), "nape" in idsAt(back, point))
            assertTrue(point.toString(), pathContains(nape.path, point))
            assertFalse(point.toString(), pathContains(back.first { it.region.id == "head-back" }.path, point))
            assertTrue(point.toString(), MuscleGroup.UPPER_BACK !in groupsAt(back, point))
        }
        assertTrue("left ear sits in the upward nape spike", leftEarInterior.y < napeLeftInterior.y)
        assertTrue("right ear sits in the upward nape spike", rightEarInterior.y < napeLeftInterior.y)
        assertTrue("ears are above the trap bulk", leftEarInterior.y < trapsUpperInterior.y)
        assertTrue(leftEarInterior.x < midline)
        assertTrue(rightEarInterior.x > midline)
        assertEquals(midline - leftEarInterior.x, rightEarInterior.x - midline, 1.20f)
        assertEquals(leftEarInterior.y, rightEarInterior.y, 1.00f)
    }

    @Test
    fun trapeziusInteriorPointsStayUpperBackNotNeck() {
        listOf(trapsUpperInterior, mirror(trapsUpperInterior), trapsMidInterior, mirror(trapsMidInterior)).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.UPPER_BACK), groupsAt(back, point))
            assertTrue(point.toString(), MuscleGroup.NECK !in groupsAt(back, point))
        }
        assertTrue("traps-upper-left" in idsAt(back, trapsUpperInterior))
        assertTrue("traps-upper-right" in idsAt(back, mirror(trapsUpperInterior)))
        assertTrue("traps-mid-left" in idsAt(back, trapsMidInterior))
        assertTrue("traps-mid-right" in idsAt(back, mirror(trapsMidInterior)))
        assertEquals(
            setOf(
                "traps-upper-left", "traps-upper-right", "traps-mid-left", "traps-mid-right",
                "traps-lower-left", "traps-lower-right"
            ),
            BodyMusclesCatalog.regionsFor(MuscleGroup.UPPER_BACK).map { it.id }.toSet()
        )
        assertTrue(BodyMusclesCatalog.regionsFor(MuscleGroup.UPPER_BACK).none { it.id == "nape" })
        assertTrue(BodyMusclesCatalog.regionsFor(MuscleGroup.NECK).none { it.id.startsWith("traps") })
    }

    @Test
    fun leftAndRightNeckRegionsAreMirrorSymmetric() {
        listOf(frontLeftInterior).forEach { point ->
            assertEquals(point.toString(), groupsAt(front, point), groupsAt(front, mirror(point)))
        }
        listOf(napeLeftInterior).forEach { point ->
            assertEquals(point.toString(), groupsAt(back, point), groupsAt(back, mirror(point)))
        }
        assertEquals(groupsAt(back, leftEarInterior), groupsAt(back, rightEarInterior))
        val left = front.first { it.region.id == "neck-left" }
        val right = front.first { it.region.id == "neck-right" }
        assertEquals(left.bounds.width, right.bounds.width, 0.08f)
        assertEquals(left.bounds.height, right.bounds.height, 0.08f)
        assertEquals(left.bounds.center.y, right.bounds.center.y, 0.08f)
        assertEquals(midline - (left.bounds.center.x - midline), right.bounds.center.x, 0.12f)
        val napeRegion = back.first { it.region.id == "nape" }
        assertTrue("nape covers anatomical left of midline", napeRegion.bounds.right > midline)
        assertTrue("nape covers anatomical right of midline", napeRegion.bounds.left < midline)
        assertTrue("nape includes the upward ear spikes", napeRegion.bounds.top < 7.5f)
    }

    @Test
    fun completedNeckWorkoutPaintsEveryFrontAndBackNeckRegionTheSameRecencyColor() {
        val today = LocalDate.parse("2026-09-16")
        val primaryFills = MuscleMapColors.heatmapFills(
            MuscleHeatmapAssembler.assemble(
                listOf(
                    MuscleTrainingExercise(
                        SessionStatus.COMPLETED,
                        today.minusDays(2),
                        MuscleGroup.NECK,
                        emptyList(),
                        1
                    )
                ),
                today
            )
        )
        val secondaryFills = MuscleMapColors.heatmapFills(
            MuscleHeatmapAssembler.assemble(
                listOf(
                    MuscleTrainingExercise(
                        SessionStatus.COMPLETED,
                        today.minusDays(2),
                        MuscleGroup.UPPER_BACK,
                        listOf(MuscleGroup.NECK),
                        1
                    )
                ),
                today
            )
        )
        val expected = MuscleMapColors.Days1To2
        assertEquals(MuscleRecencyBand.DAYS_1_2, MuscleHeatmapAssembler.bandFor(2))
        assertEquals(expected, primaryFills.getValue(MuscleGroup.NECK))
        assertEquals(expected, secondaryFills.getValue(MuscleGroup.NECK))
        val overlay = overlayRegionIds(parsed, setOf(MuscleGroup.NECK))
        assertEquals(setOf("neck-left", "neck-right", "nape"), overlay)
        val neckPoints = listOf(
            frontLeftInterior to front,
            mirror(frontLeftInterior) to front,
            napeLeftInterior to back,
            mirror(napeLeftInterior) to back,
            leftEarInterior to back,
            rightEarInterior to back
        )
        neckPoints.forEach { (point, regions) ->
            assertEquals(point.toString(), expected, colorAt(regions, point, primaryFills))
            assertEquals(point.toString(), expected, colorAt(regions, point, secondaryFills))
        }
        assertEquals(MuscleMapColors.Days1To2, secondaryFills.getValue(MuscleGroup.UPPER_BACK))
        assertEquals(MuscleMapColors.NeverTrained, primaryFills.getValue(MuscleGroup.UPPER_BACK))
    }

    @Test
    fun heatmapAndTemplatePreviewShareTheNeckMapping() {
        val overlay = overlayRegionIds(parsed, setOf(MuscleGroup.NECK))
        assertTrue("neck-left" in overlay)
        assertTrue("neck-right" in overlay)
        assertTrue("nape" in overlay)
        val template = TemplateMuscleMapAssembler.assemble(listOf(neckExercise()))
        assertEquals(TemplateMuscleEmphasis.PRIMARY, template.emphasis[MuscleGroup.NECK])
        val templateFills = MuscleMapColors.templateFills(template)
        val templateIds = overlayRegionIds(parsed, template.emphasis.keys)
        assertTrue("neck-left" in templateIds)
        assertTrue("nape" in templateIds)
        assertEquals(MuscleMapColors.TemplatePrimary, templateFills.getValue(MuscleGroup.NECK))
        listOf(frontLeftInterior, mirror(frontLeftInterior)).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.TemplatePrimary, colorAt(front, point, templateFills))
        }
        listOf(napeLeftInterior, leftEarInterior, rightEarInterior).forEach { point ->
            assertEquals(point.toString(), MuscleMapColors.TemplatePrimary, colorAt(back, point, templateFills))
        }
        assertTrue(overlay.containsAll(templateIds))
    }

    @Test
    fun neckHitTestAndLabelAreNyak() {
        assertEquals(MuscleGroup.NECK, hitTest(front, frontLeftInterior, inflate = 0f))
        assertEquals(MuscleGroup.NECK, hitTest(front, mirror(frontLeftInterior), inflate = 0f))
        assertEquals(MuscleGroup.NECK, hitTest(back, napeLeftInterior, inflate = 0f))
        assertEquals(MuscleGroup.NECK, hitTest(back, mirror(napeLeftInterior), inflate = 0f))
        assertEquals(MuscleGroup.NECK, hitTest(back, leftEarInterior, inflate = 0f))
        assertEquals(MuscleGroup.NECK, hitTest(back, rightEarInterior, inflate = 0f))
        val today = LocalDate.parse("2026-09-16")
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today.minusDays(2),
                    MuscleGroup.NECK,
                    emptyList(),
                    1
                )
            ),
            today
        )
        val recency = resources.getString(R.string.heatmap_days_ago, 2)
        val description = resources.getString(
            R.string.heatmap_selection,
            resources.getString(MuscleGroup.NECK.labelRes()),
            recency
        )
        assertEquals("Nyak: 2 napja", description)
        assertEquals(2, state.entry(MuscleGroup.NECK).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_1_2, state.entry(MuscleGroup.NECK).band)
        assertTrue(MuscleGroup.NECK in MuscleHeatmapAssembler.anatomicalGroups)
    }

    @Test
    fun neckHasNoDualOwnershipWithTrapsOrNeighbors() {
        val neckFront = front.filter { it.region.muscleGroup == MuscleGroup.NECK }
        val neckBack = back.filter { it.region.muscleGroup == MuscleGroup.NECK }
        val overlapByGroup = mutableMapOf<MuscleGroup, Int>()
        val trapSeam = mutableListOf<Offset>()
        var x = 8.0f
        while (x <= 24.0f) {
            var y = 5.0f
            while (y <= 20.0f) {
                val point = Offset(x, y)
                val inFrontNeck = neckFront.any { pathContains(it.path, point) }
                val inBackNeck = neckBack.any { pathContains(it.path, point) }
                if (inFrontNeck) {
                    groupsAt(front, point).filter { it != MuscleGroup.NECK }.forEach { group ->
                        overlapByGroup[group] = (overlapByGroup[group] ?: 0) + 1
                    }
                }
                if (inBackNeck) {
                    val others = groupsAt(back, point).filter { it != MuscleGroup.NECK }
                    others.forEach { group ->
                        overlapByGroup[group] = (overlapByGroup[group] ?: 0) + 1
                    }
                    if (MuscleGroup.UPPER_BACK in others) {
                        trapSeam += point
                    }
                }
                y += 0.25f
            }
            x += 0.25f
        }
        overlapByGroup.keys.filter { it != MuscleGroup.UPPER_BACK && it != MuscleGroup.LOWER_BACK }.forEach { group ->
            assertEquals("neck vs $group overlap=${overlapByGroup[group]}", 0, overlapByGroup[group] ?: 0)
        }
        trapSeam.forEach { point ->
            val ids = idsAt(back, point)
            assertTrue(point.toString(), "nape" in ids)
            assertTrue(point.toString(), ids.any { it.startsWith("traps-upper") })
            assertTrue(point.toString(), ids.none { it.startsWith("traps-mid") || it.startsWith("traps-lower") })
            assertTrue("trap seam stays at the nape/trap junction: $point", point.y in 12.0f..15.5f)
        }
        listOf(frontLeftInterior, mirror(frontLeftInterior)).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.NECK), groupsAt(front, point))
        }
        listOf(napeLeftInterior, mirror(napeLeftInterior), leftEarInterior, rightEarInterior).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.NECK), groupsAt(back, point))
        }
        listOf(trapsUpperInterior, mirror(trapsUpperInterior), trapsMidInterior, mirror(trapsMidInterior)).forEach { point ->
            assertEquals(point.toString(), setOf(MuscleGroup.UPPER_BACK), groupsAt(back, point))
        }
        val spine = back.first { it.region.id == "spine" }
        assertFalse(pathContains(spine.path, napeLeftInterior))
        assertFalse(pathContains(spine.path, leftEarInterior))
        assertFalse(pathContains(spine.path, rightEarInterior))
        assertEquals(MuscleGroup.LOWER_BACK, spine.region.muscleGroup)
    }

    @Test
    fun neckPathGeometryIsUnchanged() {
        val left = BodyMusclesCatalog.regions.first { it.id == "neck-left" }
        val right = BodyMusclesCatalog.regions.first { it.id == "neck-right" }
        val nape = BodyMusclesCatalog.regions.first { it.id == "nape" }
        assertEquals(originalNeckLeftPath, left.pathData)
        assertEquals(originalNeckRightPath, right.pathData)
        assertEquals(originalNapePath, nape.pathData)
        assertEquals(AnatomicalSide.LEFT, left.side)
        assertEquals(AnatomicalSide.RIGHT, right.side)
        assertEquals(AnatomicalSide.CENTRAL, nape.side)
        assertEquals(MuscleMapView.FRONT, left.view)
        assertEquals(MuscleMapView.FRONT, right.view)
        assertEquals(MuscleMapView.BACK, nape.view)
        assertEquals(MuscleGroup.NECK, left.muscleGroup)
        assertEquals(MuscleGroup.NECK, right.muscleGroup)
        assertEquals(MuscleGroup.NECK, nape.muscleGroup)
        assertNull(BodyMusclesCatalog.groupFor("head"))
        assertNull(BodyMusclesCatalog.groupFor("face"))
        assertNull(BodyMusclesCatalog.groupFor("head-back"))
    }

    @Test
    fun paintOrderKeepsMappedNeckOnTopOfUnmappedHead() {
        val frontOrdered = paintOrderedRegions(front)
        val backOrdered = paintOrderedRegions(back)
        fun covering(regions: List<ParsedMuscleRegion>, point: Offset) =
            regions.filter { pathContains(it.path, point) }
        val leftCovering = covering(frontOrdered, frontLeftInterior)
        val napeCovering = covering(backOrdered, napeLeftInterior)
        val earCovering = covering(backOrdered, leftEarInterior)
        val rightEarCovering = covering(backOrdered, rightEarInterior)
        assertEquals("neck-left", leftCovering.last().region.id)
        assertEquals("nape", napeCovering.last().region.id)
        assertEquals("nape", earCovering.last().region.id)
        assertEquals("nape", rightEarCovering.last().region.id)
        val headIndex = frontOrdered.indexOfFirst { it.region.id == "head" }
        val faceIndex = frontOrdered.indexOfFirst { it.region.id == "face" }
        val neckIndex = frontOrdered.indexOfFirst { it.region.id == "neck-left" }
        val headBackIndex = backOrdered.indexOfFirst { it.region.id == "head-back" }
        val napeIndex = backOrdered.indexOfFirst { it.region.id == "nape" }
        assertTrue(headIndex < neckIndex)
        assertTrue(faceIndex < neckIndex)
        assertTrue(headBackIndex < napeIndex)
    }

    private fun neckExercise(): Exercise {
        return Exercise(
            id = 1L,
            name = "Nyakhajlítás",
            normalizedName = "nyakhajlítás",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.OTHER,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.NECK,
            secondaryMuscles = emptyList(),
            notes = null,
            archived = false,
            createdAt = 1L,
            updatedAt = 1L
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

    private fun representativeInterior(
        parsed: ParsedMuscleRegion,
        xMin: Float,
        xMax: Float,
        yMin: Float,
        yMax: Float
    ): Offset {
        val hits = mutableListOf<Offset>()
        var x = xMin
        while (x <= xMax) {
            var y = yMin
            while (y <= yMax) {
                val point = Offset(x, y)
                if (pathContains(parsed.path, point)) {
                    hits += point
                }
                y += 0.05f
            }
            x += 0.05f
        }
        assertTrue("no NECK interior in x=$xMin..$xMax y=$yMin..$yMax bounds=${parsed.bounds}", hits.isNotEmpty())
        val cx = hits.map { it.x }.average().toFloat()
        val cy = hits.map { it.y }.average().toFloat()
        return hits.minBy { point ->
            val dx = point.x - cx
            val dy = point.y - cy
            dx * dx + dy * dy
        }
    }

    private fun mirror(point: Offset): Offset {
        return Offset(2f * midline - point.x, point.y)
    }
}
