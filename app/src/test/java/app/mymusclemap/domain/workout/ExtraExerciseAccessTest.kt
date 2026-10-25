package app.mymusclemap.domain.workout

import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import app.mymusclemap.domain.entitlement.FreeAccessSubject
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCatalogLogic
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraExerciseAccessTest {
    private val starter = exercise(1, "Starter Move", custom = false, muscle = MuscleGroup.QUADRICEPS)
    private val open = exercise(2, "Mine Press", custom = true, muscle = MuscleGroup.CHEST)
    private val locked = exercise(3, "Secret Lift", custom = true, muscle = MuscleGroup.CHEST)
    private val archived = exercise(4, "Old Press", custom = true, archived = true)
    private val catalog = listOf(starter, open, locked, archived)
    private val subjects = listOf(FreeAccessSubject(2, 2), FreeAccessSubject(3, 3), FreeAccessSubject(4, 4))

    @Test
    fun freeShowsStartersAndSelectedCustomExercisesOnly() {
        val decision = confirmed(setOf(2L))
        val visible = visible(decision, query = "")
        assertEquals(setOf(starter.id, open.id), visible.map { it.id }.toSet())
        assertTrue(visible.all { ExtraExerciseAccess.addable(it, decision) })
        assertFalse(ExtraExerciseAccess.addable(locked, decision))
        assertFalse(ExtraExerciseAccess.addable(archived, decision))
    }

    @Test
    fun aReleasedCustomExerciseStaysOutOfTheExtraPicker() {
        val decision = FreeAccessLogic.decide(
            state = confirmedState(setOf(2L, 3L)).copy(releasedCustomExerciseIds = setOf(3L)),
            grantsPro = false,
            plans = emptyList(),
            customExercises = subjects
        )
        assertFalse(decision.customExerciseAccessible(3L))
        assertFalse(ExtraExerciseAccess.addable(locked, decision))
        assertTrue(ExtraExerciseAccess.addable(open, decision))
        assertTrue(ExtraExerciseAccess.addable(starter, decision))
    }

    @Test
    fun searchAndFiltersCannotRevealALockedExercise() {
        val decision = confirmed(setOf(2L))
        assertTrue(visible(decision, query = "Secret Lift").isEmpty())
        assertTrue(visible(decision, query = "sec").isEmpty())
        assertEquals(setOf(open.id), visible(decision, query = "press").map { it.id }.toSet())
        assertEquals(
            setOf(open.id),
            visible(decision, query = "", muscle = MuscleGroup.CHEST).map { it.id }.toSet()
        )
        assertEquals(
            setOf(open.id, starter.id),
            visible(decision, query = "", category = ExerciseCategory.STRENGTH).map { it.id }.toSet()
        )
        assertEquals(setOf(starter.id, open.id), visible(decision, query = "").map { it.id }.toSet())
    }

    @Test
    fun proRestoresEligibleCustomExercisesAndStillHidesArchivedOnes() {
        val decision = FreeAccessLogic.decide(
            state = confirmedState(setOf(2L)),
            grantsPro = true,
            plans = emptyList(),
            customExercises = subjects
        )
        assertEquals(setOf(starter.id, open.id, locked.id), visible(decision, query = "").map { it.id }.toSet())
        assertTrue(visible(decision, query = "Old").isEmpty())
    }

    @Test
    fun requiredSelectionDoesNotGrantDraftCustomExerciseAccess() {
        val many = (10L..15L).map { FreeAccessSubject(it, it) }
        val exercises = many.map { exercise(it.id, "Custom ${it.id}", custom = true) } + starter
        val decision = FreeAccessLogic.decide(
            state = FreeAccessState(
                periodId = 2L,
                lastKnownPro = false,
                phase = FreeAccessPhase.Required,
                selectedCustomExerciseIds = setOf(10L, 11L)
            ),
            grantsPro = false,
            plans = emptyList(),
            customExercises = many
        )
        val visible = ExerciseCatalogLogic.filter(
            exercises = exercises.filter { ExtraExerciseAccess.addable(it, decision) },
            query = "",
            category = null,
            muscle = null,
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(setOf(starter.id), visible.map { it.id }.toSet())
        assertTrue(many.all { !decision.customExerciseAccessible(it.id) })
    }

    private fun visible(
        decision: app.mymusclemap.domain.entitlement.FreeAccessDecision,
        query: String,
        category: ExerciseCategory? = null,
        muscle: MuscleGroup? = null
    ): List<Exercise> {
        return ExerciseCatalogLogic.filter(
            exercises = catalog.filter { ExtraExerciseAccess.addable(it, decision) },
            query = query,
            category = category,
            muscle = muscle,
            archiveFilter = ArchiveFilter.ACTIVE
        )
    }

    private fun confirmed(selected: Set<Long>) = FreeAccessLogic.decide(
        state = confirmedState(selected),
        grantsPro = false,
        plans = emptyList(),
        customExercises = subjects
    )

    private fun confirmedState(selected: Set<Long>) = FreeAccessState(
        periodId = 2L,
        lastKnownPro = false,
        phase = FreeAccessPhase.Confirmed,
        selectedCustomExerciseIds = selected
    )

    private fun exercise(
        id: Long,
        name: String,
        custom: Boolean,
        muscle: MuscleGroup = MuscleGroup.CHEST,
        archived: Boolean = false
    ): Exercise {
        return Exercise(
            id = id,
            name = name,
            normalizedName = name.lowercase(),
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.SQUAT,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = muscle,
            secondaryMuscles = emptyList(),
            notes = null,
            archived = archived,
            createdAt = id,
            updatedAt = id,
            custom = custom
        )
    }
}
