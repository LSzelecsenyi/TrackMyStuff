package app.mymusclemap.domain.workoutimport

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.MuscleRole
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightProposal
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class WorkoutImportFingerprintTest {
    @Test
    fun sameResolvedWorkoutAlwaysProducesTheSameFingerprint() {
        val first = hash(workout())
        val second = hash(workout())
        assertEquals(64, first.length)
        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertTrue(WorkoutImportFingerprint.canonical(workout()).startsWith("workout-import-fingerprint-v1"))
    }

    @Test
    fun aliasesMappedToTheSameCatalogExerciseShareAFingerprint() {
        val pullup = workout(incomingName = "Pullup")
        val pullups = workout(incomingName = "Pullups")
        assertEquals(hash(pullup), hash(pullups))
        assertEquals(
            WorkoutImportFingerprint.canonical(pullup),
            WorkoutImportFingerprint.canonical(pullups)
        )
    }

    @Test
    fun trailingZerosAndKilometerDistanceDoNotChangeTheFingerprint() {
        val compact = workout(
            weight = BigDecimal("17.5"),
            distance = BigDecimal("2000")
        )
        val trailing = workout(
            weight = BigDecimal("17.50"),
            distance = BigDecimal("2000.000")
        )
        assertEquals(hash(compact), hash(trailing))
    }

    @Test
    fun exerciseAndSetOrderIsCanonical() {
        val canonical = WorkoutImportFingerprint.canonical(workout())
        val second = WorkoutImportFingerprint.canonical(workout())
        assertEquals(canonical, second)
        assertTrue(canonical.indexOf("index=1") < canonical.indexOf("index=2"))
    }

    @Test
    fun changingMaterialFieldsChangesTheFingerprint() {
        val baseline = hash(workout())
        assertNotEquals(baseline, hash(workout(date = LocalDate.parse("2026-09-14"))))
        assertNotEquals(baseline, hash(workout(catalogId = 8L, catalogName = "Chinup")))
        assertNotEquals(baseline, hash(workout(status = SessionSetStatus.SKIPPED, reps = null)))
        assertNotEquals(baseline, hash(workout(reps = 6)))
        assertNotEquals(baseline, hash(workout(loadKind = PlannedLoadKind.ADDED_WEIGHT, weight = BigDecimal("5"))))
        assertNotEquals(baseline, hash(workout(duration = 30)))
        assertNotEquals(baseline, hash(workout(distance = BigDecimal("1000"))))
        assertNotEquals(baseline, hash(workout(secondary = listOf(MuscleGroup.BICEPS, MuscleGroup.CHEST))))
        assertNotEquals(baseline, hash(workout(primary = MuscleGroup.NECK)))
        assertNotEquals(
            baseline,
            hash(
                workout(
                    bodyWeight = BodyWeightProposal(
                        82.5,
                        BodyWeightSource.MANUAL,
                        null
                    )
                )
            )
        )
    }

    @Test
    fun neckMuscleIsEncodedInTheImportFingerprint() {
        val snapshot = WorkoutImportFingerprint.canonical(workout(primary = MuscleGroup.NECK))
        assertTrue(snapshot.contains("primary=NECK"))
        assertTrue(snapshot.contains("NECK"))
        assertEquals(MuscleGroup.NECK, workout(primary = MuscleGroup.NECK).exercises.single().snapshot!!.primaryMuscle)
        assertEquals(
            MuscleGroup.NECK,
            workout(primary = MuscleGroup.NECK).exercises.single().snapshot!!.muscles.single { it.role == MuscleRole.PRIMARY }.muscleGroup
        )
    }

    @Test
    fun sameDateAndNameWithDifferentContentGetDifferentFingerprints() {
        val first = hash(workout(workoutId = "a", reps = 5))
        val second = hash(workout(workoutId = "b", reps = 4))
        assertNotEquals(first, second)
        assertEquals(
            hash(workout(workoutId = "a", reps = 5)),
            hash(workout(workoutId = "other-id", reps = 5))
        )
    }

    private fun hash(workout: WorkoutImportResolvedWorkout): String {
        return WorkoutImportFingerprint.hash(workout)
    }

    private fun workout(
        workoutId: String = "w1",
        incomingName: String = "Pullup",
        catalogId: Long = 7L,
        catalogName: String = "Pullup",
        date: LocalDate = LocalDate.parse("2026-09-15"),
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        reps: Int? = 5,
        duration: Int? = null,
        distance: BigDecimal? = null,
        loadKind: PlannedLoadKind? = PlannedLoadKind.BODYWEIGHT_ONLY,
        weight: BigDecimal? = null,
        secondary: List<MuscleGroup> = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
        primary: MuscleGroup = MuscleGroup.LATS,
        bodyWeight: BodyWeightProposal = BodyWeightProposal(
            80.0,
            BodyWeightSource.MEASURED_SAME_DAY,
            date
        )
    ): WorkoutImportResolvedWorkout {
        val snapshot = WorkoutImportExerciseSnapshot(
            exerciseId = catalogId,
            catalogName = catalogName,
            incomingName = incomingName,
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = primary,
            secondaryMuscles = secondary,
            muscles = listOf(WorkoutImportMuscleSnapshot(primary, MuscleRole.PRIMARY)) +
                secondary.map { WorkoutImportMuscleSnapshot(it, MuscleRole.SECONDARY) },
            notes = null,
            archived = false
        )
        val first = WorkoutImportResolvedSet(
            sourceRowNumber = 2,
            setIndex = 1,
            status = status,
            reps = reps,
            durationSeconds = duration,
            distanceMeters = distance,
            loadKind = loadKind,
            weightKg = weight
        )
        val second = first.copy(sourceRowNumber = 3, setIndex = 2)
        return WorkoutImportResolvedWorkout(
            sourceRowNumber = 2,
            workoutId = workoutId,
            name = "Pull",
            normalizedName = "pull",
            workoutDate = date,
            startedAt = LocalDateTime.parse("${date}T12:00:00"),
            finishedAt = LocalDateTime.parse("${date}T13:00:00"),
            durationMillis = 3_600_000L,
            notes = null,
            bodyWeight = bodyWeight,
            exercises = listOf(
                WorkoutImportResolvedExercise(
                    sourceRowNumber = 2,
                    exerciseIndex = 1,
                    incomingName = incomingName,
                    normalizedIncomingName = incomingName.lowercase(),
                    snapshot = snapshot,
                    mappedManually = incomingName != catalogName,
                    sets = listOf(first, second),
                    errors = emptyList(),
                    warnings = emptyList()
                )
            ),
            errors = emptyList(),
            warnings = emptyList()
        )
    }
}
