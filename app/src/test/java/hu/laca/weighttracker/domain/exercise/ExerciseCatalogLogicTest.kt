package hu.laca.weighttracker.domain.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogLogicTest {
    private val pullUp = sample(
        id = 1,
        name = "Húzódzkodás",
        category = ExerciseCategory.STRENGTH,
        primary = MuscleGroup.LATS,
        secondary = listOf(MuscleGroup.BICEPS)
    )
    private val bike = sample(
        id = 2,
        name = "Kerékpározás",
        category = ExerciseCategory.CARDIO,
        primary = MuscleGroup.CARDIOVASCULAR,
        pattern = MovementPattern.CARDIO,
        measurement = MeasurementType.DISTANCE_AND_DURATION
    )
    private val archivedPlank = sample(
        id = 3,
        name = "Plank",
        category = ExerciseCategory.STATIC_HOLD,
        primary = MuscleGroup.ABS,
        archived = true
    )

    @Test
    fun filtersByCategory() {
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(pullUp, bike, archivedPlank),
            query = "",
            category = ExerciseCategory.CARDIO,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(bike), result)
    }

    @Test
    fun filtersByPrimaryOrSecondaryMuscle() {
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(pullUp, bike),
            query = "",
            category = null,
            muscle = MuscleGroup.BICEPS,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(pullUp), result)
    }

    @Test
    fun combinedSearchAndFiltersAreCaseInsensitive() {
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(pullUp, bike, archivedPlank),
            query = "  HÚZÓ  ",
            category = ExerciseCategory.STRENGTH,
            muscle = MuscleGroup.LATS,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(pullUp), result)
    }

    @Test
    fun archivedFilterHidesActiveExercises() {
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(pullUp, archivedPlank),
            query = "",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ARCHIVED
        )
        assertEquals(listOf(archivedPlank), result)
    }

    @Test
    fun emptySearchWithActiveFiltersIsDetected() {
        assertTrue(
            ExerciseCatalogLogic.hasActiveFilters(
                query = "plank",
                category = null,
                muscle = null,
                archiveFilter = ArchiveFilter.ACTIVE
            )
        )
    }

    private fun sample(
        id: Long,
        name: String,
        category: ExerciseCategory,
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList(),
        pattern: MovementPattern = MovementPattern.VERTICAL_PULL,
        measurement: MeasurementType = MeasurementType.REPETITIONS,
        archived: Boolean = false
    ): Exercise {
        return Exercise(
            id = id,
            name = name,
            normalizedName = ExerciseNaming.normalize(name),
            category = category,
            movementPattern = pattern,
            measurementType = measurement,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = primary,
            secondaryMuscles = secondary,
            notes = null,
            archived = archived,
            createdAt = 1L,
            updatedAt = 1L
        )
    }
}
