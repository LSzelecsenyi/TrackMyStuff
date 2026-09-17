package hu.laca.weighttracker.domain.exercise

import hu.laca.weighttracker.domain.locale.LocalizedLabelOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun givenUnsortedActiveExercisesWhenFilteredThenHungarianDisplayedNamesAreAbcOrdered() {
        val zaro = sample(id = 10, name = "Zárógyakorlat", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val allo = sample(id = 11, name = "Álló evezés", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.LATS)
        val alma = sample(id = 12, name = "Alma", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.ABS)
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(zaro, allo, alma),
            query = "",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf("Alma", "Álló evezés", "Zárógyakorlat"), result.map { it.name })
    }

    @Test
    fun givenAccentedHungarianNamesWhenSortedThenHuHuCollatorRulesApply() {
        val oszi = sample(id = 1, name = "őszibarack", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val alma = sample(id = 2, name = "alma", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val aron = sample(id = 3, name = "Áron", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val beka = sample(id = 4, name = "béka", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val ekezet = sample(id = 5, name = "ékezet", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(oszi, alma, aron, beka, ekezet),
            query = "",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(
            listOf("alma", "Áron", "béka", "ékezet", "őszibarack"),
            result.map { it.name }
        )
        assertTrue(LocalizedLabelOrder.compareLabels("alma", "Áron") < 0)
        assertTrue(LocalizedLabelOrder.compareLabels("béka", "ékezet") < 0)
    }

    @Test
    fun identicalDisplayedNamesUseStableSecondaryKey() {
        val later = sample(id = 20, name = "Mellnyomás", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val earlier = sample(id = 4, name = "Mellnyomás", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(later, earlier),
            query = "",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(4L, 20L), result.map { it.id })
    }

    @Test
    fun givenActiveExercisesAndQueryWhenTypedThenListFiltersImmediatelyCaseInsensitively() {
        val result = ExerciseCatalogLogic.filter(
            exercises = listOf(pullUp, bike, archivedPlank),
            query = "  húzó  ",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(pullUp), result)
        val emptyNeedle = ExerciseCatalogLogic.filter(
            exercises = listOf(pullUp, bike),
            query = "   ",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(pullUp, bike), emptyNeedle)
    }

    @Test
    fun searchDoesNotMatchNotesAndDoesNotMutateSource() {
        val noted = pullUp.copy(notes = "Kerékpározás jegyzet")
        val source = mutableListOf(bike, noted)
        val snapshot = source.toList()
        val result = ExerciseCatalogLogic.filter(
            exercises = source,
            query = "kerék",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(bike), result)
        assertEquals(snapshot, source)
        assertEquals(listOf(bike, noted), source)
    }

    @Test
    fun givenArchiveFiltersWhenSwitchedThenOnlyMatchingExercisesAppearInAbcOrder() {
        val zaro = sample(id = 8, name = "Záró", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.CHEST)
        val almaArchived = sample(
            id = 9,
            name = "Alma",
            category = ExerciseCategory.STRENGTH,
            primary = MuscleGroup.ABS,
            archived = true
        )
        val allo = sample(id = 7, name = "Álló evezés", category = ExerciseCategory.STRENGTH, primary = MuscleGroup.LATS)
        val source = listOf(zaro, almaArchived, allo)
        assertEquals(
            listOf("Álló evezés", "Záró"),
            ExerciseCatalogLogic.filter(source, "", null, null, ArchiveFilter.ACTIVE).map { it.name }
        )
        assertEquals(
            listOf("Alma"),
            ExerciseCatalogLogic.filter(source, "", null, null, ArchiveFilter.ARCHIVED).map { it.name }
        )
        assertEquals(
            listOf("Alma", "Álló evezés", "Záró"),
            ExerciseCatalogLogic.filter(source, "", null, null, ArchiveFilter.ALL).map { it.name }
        )
    }

    @Test
    fun hasSearchQueryIgnoresBlankText() {
        assertFalse(ExerciseCatalogLogic.hasSearchQuery("  "))
        assertTrue(ExerciseCatalogLogic.hasSearchQuery("húzó"))
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
