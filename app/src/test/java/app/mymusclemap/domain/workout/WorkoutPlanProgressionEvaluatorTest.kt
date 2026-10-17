package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutPlanProgressionEvaluatorTest {
    @Test
    fun moreRepsAtTheSameWeightIsEligible() {
        val update = single(plannedReps = 8, plannedWeight = 15.0, actualReps = 10, actualWeight = 15.0)
        assertEquals(10, update.newMinReps)
        assertEquals(10, update.newMaxReps)
        assertEquals(15.0, update.newWeightKg!!, 0.0)
        assertTrue(update.repsChanged)
        assertTrue(!update.weightChanged)
    }

    @Test
    fun sameRepsAtMoreWeightIsEligible() {
        val update = single(plannedReps = 8, plannedWeight = 15.0, actualReps = 8, actualWeight = 17.5)
        assertEquals(8, update.newMinReps)
        assertEquals(17.5, update.newWeightKg!!, 0.0)
        assertTrue(!update.repsChanged)
        assertTrue(update.weightChanged)
    }

    @Test
    fun moreRepsAndMoreWeightAreBothEligible() {
        val update = single(plannedReps = 8, plannedWeight = 15.0, actualReps = 10, actualWeight = 17.5)
        assertEquals(10, update.newMinReps)
        assertEquals(17.5, update.newWeightKg!!, 0.0)
        assertTrue(update.repsChanged)
        assertTrue(update.weightChanged)
    }

    @Test
    fun moreWeightWithFewerRepsIsNotEligible() {
        assertTrue(updates(plannedReps = 8, plannedWeight = 15.0, actualReps = 6, actualWeight = 20.0).isEmpty())
    }

    @Test
    fun moreRepsWithLessWeightIsNotEligible() {
        assertTrue(updates(plannedReps = 8, plannedWeight = 15.0, actualReps = 12, actualWeight = 10.0).isEmpty())
    }

    @Test
    fun identicalPerformanceIsNotEligible() {
        assertTrue(updates(plannedReps = 8, plannedWeight = 15.0, actualReps = 8, actualWeight = 15.0).isEmpty())
    }

    @Test
    fun lowerPerformanceIsNotEligible() {
        assertTrue(updates(plannedReps = 8, plannedWeight = 15.0, actualReps = 7, actualWeight = 15.0).isEmpty())
    }

    @Test
    fun repetitionOnlyImprovementIsEligible() {
        val update = single(
            plannedReps = 8,
            plannedWeight = null,
            actualReps = 12,
            actualWeight = null,
            measurement = MeasurementType.REPETITIONS,
            load = PlannedLoadKind.BODYWEIGHT_ONLY
        )
        assertEquals(12, update.afterReps)
        assertNull(update.newWeightKg)
    }

    @Test
    fun missingPerformanceIsSkipped() {
        assertTrue(
            updates(plannedReps = 8, plannedWeight = 15.0, actualReps = null, actualWeight = 15.0).isEmpty()
        )
        assertTrue(
            updates(plannedReps = 8, plannedWeight = 15.0, actualReps = 10, actualWeight = null).isEmpty()
        )
    }

    @Test
    fun durationAndDistanceMeasurementsAreSkipped() {
        assertTrue(
            updates(
                plannedReps = 8,
                plannedWeight = null,
                actualReps = null,
                actualWeight = null,
                measurement = MeasurementType.DURATION,
                load = PlannedLoadKind.NONE,
                actualDuration = 90
            ).isEmpty()
        )
        assertTrue(
            updates(
                plannedReps = 8,
                plannedWeight = null,
                actualReps = null,
                actualWeight = null,
                measurement = MeasurementType.DISTANCE_AND_DURATION,
                load = PlannedLoadKind.NONE,
                actualDistance = 1_000.0
            ).isEmpty()
        )
    }

    @Test
    fun extraSetsAndExtraExercisesAreExcluded() {
        val templateExercises = listOf(templateExercise(10, position = 0))
        val templateSets = listOf(templateSet(100, 10, 0, 8, 15.0))
        val planned = item(
            exercise(1, sourceExercise = 10, added = false),
            listOf(
                performed(1, sourceSet = 100, reps = 10, weight = 15.0, added = false),
                performed(2, sourceSet = null, reps = 6, weight = 15.0, added = true)
            )
        )
        val extra = item(
            exercise(2, sourceExercise = null, added = true, name = "Curl"),
            listOf(performed(3, sourceSet = null, reps = 20, weight = 40.0, added = true))
        )
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(listOf(planned, extra)),
            template(),
            templateExercises,
            templateSets
        )
        assertEquals(listOf(100L), proposal.updates.map { it.sourceTemplateSetId })
    }

    @Test
    fun duplicateCatalogExercisesMapToTheirOwnPlanSets() {
        val exercises = listOf(
            templateExercise(10, position = 0),
            templateExercise(11, position = 1)
        )
        val sets = listOf(
            templateSet(100, 10, 0, 8, 15.0),
            templateSet(101, 11, 0, 8, 15.0)
        )
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(1, sourceExercise = 10, name = "Curl"),
                        listOf(performed(1, sourceSet = 100, reps = 8, weight = 15.0))
                    ),
                    item(
                        exercise(2, sourceExercise = 11, name = "Curl", position = 1),
                        listOf(performed(2, sourceSet = 101, reps = 10, weight = 15.0))
                    )
                )
            ),
            template(),
            exercises,
            sets
        )
        assertEquals(listOf(101L), proposal.updates.map { it.sourceTemplateSetId })
        assertEquals("Curl · 2", proposal.updates.single().displayName)
    }

    @Test
    fun reorderedSessionContentStillUsesSourceIds() {
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(2, sourceExercise = 11, position = 0),
                        listOf(performed(2, sourceSet = 101, reps = 8, weight = 17.5, position = 0, plannedWeight = 15.0))
                    ),
                    item(
                        exercise(1, sourceExercise = 10, position = 1),
                        listOf(performed(1, sourceSet = 100, reps = 10, weight = 15.0, position = 0))
                    )
                )
            ),
            template(),
            listOf(templateExercise(10, 0), templateExercise(11, 1)),
            listOf(templateSet(100, 10, 0, 8, 15.0), templateSet(101, 11, 0, 8, 15.0))
        )
        assertEquals(listOf(100L, 101L), proposal.updates.map { it.sourceTemplateSetId })
        assertEquals(listOf(1, 1), proposal.updates.map { it.setNumber })
    }

    @Test
    fun aRemovedPlannedSetDoesNotStealAnotherTarget() {
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(1, sourceExercise = 10),
                        listOf(performed(2, sourceSet = 102, reps = 10, weight = 15.0, position = 0))
                    )
                )
            ),
            template(),
            listOf(templateExercise(10, 0)),
            listOf(
                templateSet(101, 10, 0, 8, 15.0),
                templateSet(102, 10, 1, 8, 15.0)
            )
        )
        assertEquals(102L, proposal.updates.single().sourceTemplateSetId)
        assertEquals(2, proposal.updates.single().setNumber)
    }

    @Test
    fun historicalSessionsWithoutSourceIdsProduceNoUpdate() {
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(1, sourceExercise = null),
                        listOf(performed(1, sourceSet = null, reps = 12, weight = 20.0))
                    )
                )
            ),
            template(),
            listOf(templateExercise(10, 0)),
            listOf(templateSet(100, 10, 0, 8, 15.0))
        )
        assertTrue(proposal.updates.isEmpty())
    }

    @Test
    fun anEditedPlanTargetIsAConflict() {
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(1, sourceExercise = 10),
                        listOf(performed(1, sourceSet = 100, reps = 10, weight = 17.5, plannedWeight = 15.0))
                    )
                )
            ),
            template(),
            listOf(templateExercise(10, 0)),
            listOf(templateSet(100, 10, 0, 8, 20.0))
        )
        assertTrue(proposal.updates.isEmpty())
    }

    @Test
    fun anAlreadyAppliedWorkoutProducesNoUpdate() {
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(1, sourceExercise = 10),
                        listOf(performed(1, sourceSet = 100, reps = 10, weight = 15.0))
                    )
                ),
                applied = true
            ),
            template(),
            listOf(templateExercise(10, 0)),
            listOf(templateSet(100, 10, 0, 8, 15.0))
        )
        assertTrue(proposal.updates.isEmpty())
    }

    @Test
    fun aRepRangeIsNotCompared() {
        val proposal = WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(1, sourceExercise = 10),
                        listOf(
                            performed(
                                1,
                                sourceSet = 100,
                                reps = 12,
                                weight = 15.0,
                                plannedMin = 8,
                                plannedMax = 12
                            )
                        )
                    )
                )
            ),
            template(),
            listOf(templateExercise(10, 0)),
            listOf(templateSet(100, 10, 0, 8, 15.0, maxReps = 12))
        )
        assertTrue(proposal.updates.isEmpty())
    }

    private fun single(
        plannedReps: Int,
        plannedWeight: Double?,
        actualReps: Int?,
        actualWeight: Double?,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        load: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        actualDuration: Int? = null,
        actualDistance: Double? = null
    ): PlanSetProgression {
        return updates(
            plannedReps,
            plannedWeight,
            actualReps,
            actualWeight,
            measurement,
            load,
            actualDuration,
            actualDistance
        ).single()
    }

    private fun updates(
        plannedReps: Int,
        plannedWeight: Double?,
        actualReps: Int?,
        actualWeight: Double?,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        load: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        actualDuration: Int? = null,
        actualDistance: Double? = null
    ): List<PlanSetProgression> {
        return WorkoutPlanProgressionEvaluator.evaluate(
            aggregate(
                listOf(
                    item(
                        exercise(1, sourceExercise = 10, measurement = measurement),
                        listOf(
                            performed(
                                id = 1,
                                sourceSet = 100,
                                reps = actualReps,
                                weight = actualWeight,
                                load = load,
                                plannedWeight = plannedWeight,
                                plannedMin = plannedReps,
                                plannedMax = plannedReps,
                                duration = actualDuration,
                                distance = actualDistance
                            )
                        )
                    )
                )
            ),
            template(),
            listOf(templateExercise(10, 0)),
            listOf(templateSet(100, 10, 0, plannedReps, plannedWeight, load, plannedReps))
        ).updates
    }

    private fun aggregate(
        items: List<SessionExerciseItem>,
        applied: Boolean = false
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = 1,
                templateId = 7,
                templateName = "Push",
                status = SessionStatus.COMPLETED,
                workoutDate = LocalDate.parse("2026-10-16"),
                startedAt = 1,
                finishedAt = 2,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1,
                updatedAt = 2,
                clientWorkoutId = "cw",
                planProgressionApplied = applied
            ),
            exercises = items
        )
    }

    private fun template() = WorkoutTemplate(7, "Push", "push", null, false, 1, 1)

    private fun templateExercise(id: Long, position: Int) = WorkoutTemplateExercise(id, 7, 5, position, null, 1)

    private fun templateSet(
        id: Long,
        exerciseId: Long,
        position: Int,
        reps: Int,
        weight: Double?,
        load: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        maxReps: Int = reps
    ) = WorkoutTemplateSet(id, exerciseId, position, reps, maxReps, load, weight, null, null)

    private fun item(exercise: SessionExercise, sets: List<SessionSet>) = SessionExerciseItem(exercise, sets)

    private fun exercise(
        id: Long,
        sourceExercise: Long?,
        added: Boolean = false,
        name: String = "Curl",
        position: Int = 0,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT
    ) = SessionExercise(
        id = id,
        sessionId = 1,
        exerciseId = 5,
        position = position,
        name = name,
        category = ExerciseCategory.STRENGTH,
        movementPattern = MovementPattern.ISOLATION,
        measurementType = measurement,
        resistanceBasis = ResistanceBasis.EXTERNAL,
        weightInterpretation = WeightInterpretation.TOTAL,
        primaryMuscle = MuscleGroup.BICEPS,
        secondaryMuscles = emptyList(),
        notes = null,
        addedDuringWorkout = added,
        sourceTemplateExerciseId = sourceExercise
    )

    private fun performed(
        id: Long,
        sourceSet: Long?,
        reps: Int?,
        weight: Double?,
        added: Boolean = false,
        position: Int = 0,
        load: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        plannedWeight: Double? = weight,
        plannedMin: Int = 8,
        plannedMax: Int = plannedMin,
        duration: Int? = null,
        distance: Double? = null
    ) = SessionSet(
        id = id,
        sessionExerciseId = 1,
        position = position,
        plannedMinReps = plannedMin,
        plannedMaxReps = plannedMax,
        plannedLoadKind = load,
        plannedWeightKg = plannedWeight,
        plannedDurationSeconds = null,
        plannedDistanceMeters = null,
        actualReps = reps,
        actualLoadKind = load,
        actualWeightKg = weight,
        actualDurationSeconds = duration,
        actualDistanceMeters = distance,
        status = SessionSetStatus.COMPLETED,
        completedAt = 2,
        addedDuringWorkout = added,
        sourceTemplateSetId = sourceSet
    )
}
