package app.mymusclemap.ui.components.musclemap

import androidx.compose.ui.graphics.Color
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleRecencyBand
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.musclemap.TemplateMuscleEmphasis
import app.mymusclemap.domain.musclemap.TemplateMuscleMapAssembler
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.ui.components.musclemap.artwork.BodyMusclesCatalog
import app.mymusclemap.ui.theme.toComposeColorScheme
import app.mymusclemap.domain.theme.ColorSchemeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MuscleMapColorsTest {
    private val today = LocalDate.parse("2026-09-15")

    @Test
    fun everyRecencyStateMapsToItsSemanticColor() {
        assertEquals(Color(0xFF1B5E20), MuscleMapColors.recencyFill(MuscleRecencyBand.TODAY))
        assertEquals(Color(0xFF66BB6A), MuscleMapColors.recencyFill(MuscleRecencyBand.DAYS_1_2))
        assertEquals(Color(0xFFFBC02D), MuscleMapColors.recencyFill(MuscleRecencyBand.DAYS_3_4))
        assertEquals(Color(0xFFF57C00), MuscleMapColors.recencyFill(MuscleRecencyBand.DAYS_5_6))
        assertEquals(Color(0xFFD32F2F), MuscleMapColors.recencyFill(MuscleRecencyBand.DAYS_7_13))
        assertEquals(Color(0xFF64B5F6), MuscleMapColors.recencyFill(MuscleRecencyBand.DAYS_14_PLUS))
        assertEquals(Color(0xFFB0BEC5), MuscleMapColors.recencyFill(MuscleRecencyBand.NEVER))
    }

    @Test
    fun applicationColorSchemeDoesNotChangeRecencyFills() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                MuscleTrainingExercise(SessionStatus.COMPLETED, today, MuscleGroup.CHEST, emptyList(), 1),
                MuscleTrainingExercise(
                    SessionStatus.COMPLETED,
                    today.minusDays(4),
                    MuscleGroup.FRONT_DELTOID,
                    emptyList(),
                    1
                )
            ),
            today
        )
        val fills = MuscleMapColors.heatmapFills(state)
        val light = ColorSchemeFactory.derive(ThemeSeeds.DefaultLight, isDark = false).toComposeColorScheme(false)
        val dark = ColorSchemeFactory.derive(ThemeSeeds.DefaultDark, isDark = true).toComposeColorScheme(true)
        val custom = ColorSchemeFactory.derive(
            ThemeSeeds.DefaultLight.copy(primary = 0xFFE91E63.toInt(), secondary = 0xFF00BCD4.toInt()),
            isDark = false
        ).toComposeColorScheme(false)
        assertEquals(MuscleMapColors.Today, fills.getValue(MuscleGroup.CHEST))
        assertEquals(MuscleMapColors.Days3To4, fills.getValue(MuscleGroup.FRONT_DELTOID))
        assertEquals(MuscleMapColors.NeverTrained, fills.getValue(MuscleGroup.BICEPS))
        assertNotEquals(light.primary, MuscleMapColors.Today)
        assertNotEquals(dark.primary, MuscleMapColors.Today)
        assertNotEquals(custom.primary, MuscleMapColors.Today)
        assertEquals(fills, MuscleMapColors.heatmapFills(state))
    }

    @Test
    fun legendContainsSevenCategoriesInOrder() {
        assertEquals(
            listOf(
                MuscleRecencyBand.TODAY,
                MuscleRecencyBand.DAYS_1_2,
                MuscleRecencyBand.DAYS_3_4,
                MuscleRecencyBand.DAYS_5_6,
                MuscleRecencyBand.DAYS_7_13,
                MuscleRecencyBand.DAYS_14_PLUS,
                MuscleRecencyBand.NEVER
            ),
            MuscleRecencyBand.entries.toList()
        )
        assertEquals(7, MuscleRecencyBand.entries.size)
    }

    @Test
    fun neverTrainedSupportedMusclesAreGray() {
        val state = MuscleHeatmapAssembler.assemble(emptyList(), today)
        val fills = MuscleMapColors.heatmapFills(state)
        MuscleHeatmapAssembler.anatomicalGroups.forEach { group ->
            assertEquals(group.name, MuscleMapColors.NeverTrained, fills.getValue(group))
        }
    }

    @Test
    fun unmappedRegionsAreNotAssignedNeverTrainedState() {
        val unmapped = BodyMusclesCatalog.regions.filter { it.muscleGroup == null }
        assertTrue(unmapped.isNotEmpty())
        val fills = MuscleMapColors.heatmapFills(MuscleHeatmapAssembler.assemble(emptyList(), today))
        unmapped.forEach { region ->
            assertEquals(region.id, null, region.muscleGroup)
        }
        assertTrue(fills.keys.all { it in MuscleHeatmapAssembler.anatomicalGroups })
        assertFalse(fills.keys.any { it == MuscleGroup.FULL_BODY || it == MuscleGroup.CARDIOVASCULAR })
    }

    @Test
    fun templateFillsStayIndependentOfRecencyPalette() {
        val exercise = Exercise(
            id = 1L,
            name = "Raise",
            normalizedName = "raise",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.OTHER,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.FRONT_DELTOID,
            secondaryMuscles = listOf(MuscleGroup.SIDE_DELTOID),
            notes = null,
            archived = false,
            createdAt = 1L,
            updatedAt = 1L
        )
        val fills = MuscleMapColors.templateFills(TemplateMuscleMapAssembler.assemble(listOf(exercise)))
        assertEquals(MuscleMapColors.TemplatePrimary, fills.getValue(MuscleGroup.FRONT_DELTOID))
        assertEquals(MuscleMapColors.TemplateSecondary, fills.getValue(MuscleGroup.SIDE_DELTOID))
        assertEquals(TemplateMuscleEmphasis.PRIMARY, TemplateMuscleMapAssembler.assemble(listOf(exercise)).emphasis[MuscleGroup.FRONT_DELTOID])
        MuscleRecencyBand.entries.forEach { band ->
            assertNotEquals(MuscleMapColors.recencyFill(band), MuscleMapColors.TemplatePrimary)
        }
    }
}
