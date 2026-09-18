package hu.laca.weighttracker.domain.workoutimport

import hu.laca.weighttracker.domain.exercise.Exercise
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseNaming
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class WorkoutImportResolverTest {
    private val today = LocalDate.parse("2026-09-16")

    @Test
    fun historicalFixtureResolvesAgainstIntendedCatalog() {
        val document = parseFixture()
        val plan = WorkoutImportResolver.resolve(
            document = document,
            catalog = intendedCatalog(),
            measurements = sampleMeasurements()
        )
        assertTrue(plan.errors.toString(), plan.canConfirm)
        assertEquals(4, plan.workoutCount)
        assertEquals(11, plan.resolvedExerciseCount)
        assertEquals(10, plan.workouts.flatMap { it.exercises }.map { it.snapshot!!.exerciseId }.distinct().size)
        assertEquals(51, plan.completedSetCount)
        assertEquals(0, plan.skippedSetCount)
        assertTrue(plan.unresolvedNames.isEmpty())
        val futasIds = plan.workouts.filter { it.normalizedName == "futás" }.map {
            it.exercises.single().snapshot!!.exerciseId
        }
        assertEquals(2, futasIds.size)
        assertEquals(futasIds[0], futasIds[1])
        val pull = plan.workouts[0]
        assertEquals(WeightInterpretation.PER_SIDE, pull.exercises[2].snapshot!!.weightInterpretation)
        assertEquals(BigDecimal("17.5"), pull.exercises[2].sets.first().weightKg)
        assertEquals(WeightInterpretation.PER_SIDE, pull.exercises[3].snapshot!!.weightInterpretation)
        assertEquals(WeightInterpretation.TOTAL, pull.exercises[4].snapshot!!.weightInterpretation)
        pull.exercises.take(2).plus(plan.workouts[1].exercises).forEach { exercise ->
            if (exercise.snapshot!!.resistanceBasis == ResistanceBasis.BODYWEIGHT) {
                assertTrue(exercise.sets.all { it.loadKind == PlannedLoadKind.BODYWEIGHT_ONLY })
            }
        }
        val run = plan.workouts[2].exercises.single()
        assertEquals(BigDecimal("2000"), run.sets.single().distanceMeters)
        assertEquals(720, run.sets.single().durationSeconds)
        assertEquals(PlannedLoadKind.NONE, run.sets.single().loadKind)
        assertTrue(plan.workouts.all { it.bodyWeight.source == BodyWeightSource.MEASURED_SAME_DAY })
        assertTrue(plan.workouts.none { it.exercises.any { exercise -> exercise.snapshot?.exerciseId == 0L } })
        assertEquals(LocalDate.parse("2026-09-13")..LocalDate.parse("2026-09-15"), plan.dateRange)
    }

    @Test
    fun historicalFixtureDateRangeAndConfirmation() {
        val plan = WorkoutImportResolver.resolve(parseFixture(), intendedCatalog(), measurements = sampleMeasurements())
        assertEquals(LocalDate.parse("2026-09-13"), plan.dateRange!!.start)
        assertEquals(LocalDate.parse("2026-09-15"), plan.dateRange!!.endInclusive)
        assertEquals(10, plan.distinctIncomingExerciseCount)
        assertTrue(plan.canConfirm)
        assertEquals(0, plan.errorCount)
    }

    @Test
    fun exactNormalizedAndAccentedNamesResolve() {
        val document = parse(oneSet("Gyűrűn tolódzkodás"))
        val plan = WorkoutImportResolver.resolve(document, intendedCatalog())
        assertTrue(plan.canConfirm)
        assertEquals("Gyűrűn tolódzkodás", plan.workouts[0].exercises[0].snapshot!!.catalogName)
    }

    @Test
    fun archivedMatchWarnsButAllowsConfirmation() {
        val catalog = listOf(exercise(7, "Pullup", archived = true))
        val plan = WorkoutImportResolver.resolve(parse(oneSet("Pullup")), catalog)
        assertTrue(plan.canConfirm)
        assertTrue(plan.warnings.any { it.code == WorkoutImportWarningCode.ArchivedExercise })
    }

    @Test
    fun unresolvedNameBlocksConfirmation() {
        val plan = WorkoutImportResolver.resolve(parse(oneSet("Unknown")), intendedCatalog())
        assertFalse(plan.canConfirm)
        assertTrue(plan.errors.any { it.code == WorkoutImportErrorCode.UnresolvedExercise })
        assertEquals(listOf("unknown"), plan.unresolvedNames)
    }

    @Test
    fun ambiguousCatalogInputFails() {
        val catalog = listOf(exercise(1, "Pullup"), exercise(2, "Pullup"))
        val plan = WorkoutImportResolver.resolve(parse(oneSet("Pullup")), catalog)
        assertFalse(plan.canConfirm)
        assertTrue(plan.errors.any { it.code == WorkoutImportErrorCode.AmbiguousCatalogMatch })
    }

    @Test
    fun manualAliasMapsPullupsToPullupWithWarning() {
        val csv = oneSet("Pullups")
        val plan = WorkoutImportResolver.resolve(
            document = parse(csv),
            catalog = intendedCatalog(),
            mappings = listOf(WorkoutImportMapping("pullups", 7))
        )
        assertTrue(plan.canConfirm)
        assertEquals(7L, plan.workouts[0].exercises[0].snapshot!!.exerciseId)
        assertEquals("Pullup", plan.workouts[0].exercises[0].snapshot!!.catalogName)
        assertEquals("Pullups", plan.workouts[0].exercises[0].incomingName)
        assertTrue(plan.workouts[0].exercises[0].mappedManually)
        assertTrue(plan.warnings.any { it.code == WorkoutImportWarningCode.ManualAliasMapping })
        assertFalse(csv.contains(",7,"))
    }

    @Test
    fun staleMappingIsAnError() {
        val plan = WorkoutImportResolver.resolve(
            parse(oneSet("Pullups")),
            intendedCatalog(),
            mappings = listOf(WorkoutImportMapping("pullups", 999L))
        )
        assertFalse(plan.canConfirm)
        assertTrue(plan.errors.any { it.code == WorkoutImportErrorCode.StaleManualMapping })
    }

    @Test
    fun twoIncomingAliasesMayMapToOneExercise() {
        val csv = header() +
            row(exerciseIndex = "1", exerciseName = "Pullups") +
            row(exerciseIndex = "2", exerciseName = "Pull-up", setIndex = "1")
        val plan = WorkoutImportResolver.resolve(
            parse(csv),
            intendedCatalog(),
            mappings = listOf(
                WorkoutImportMapping("pullups", 7),
                WorkoutImportMapping("pull-up", 7)
            )
        )
        assertTrue(plan.canConfirm)
        assertEquals(7L, plan.workouts[0].exercises[0].snapshot!!.exerciseId)
        assertEquals(7L, plan.workouts[0].exercises[1].snapshot!!.exerciseId)
        assertTrue(plan.warnings.any { it.code == WorkoutImportWarningCode.MultipleIncomingNamesMapped })
    }

    @Test
    fun loadCompatibilityFollowsCatalogResistance() {
        val bodyweight = parse(oneSet("Pullup", loadKind = "EXTERNAL_WEIGHT", weight = "10"))
        assertFalse(WorkoutImportResolver.resolve(bodyweight, intendedCatalog()).canConfirm)
        assertTrue(
            WorkoutImportResolver.resolve(bodyweight, intendedCatalog()).errors
                .any { it.code == WorkoutImportErrorCode.IncompatibleLoadKind }
        )
        assertTrue(WorkoutImportResolver.resolve(parse(oneSet("Pullup", loadKind = "ADDED_WEIGHT", weight = "5")), intendedCatalog()).canConfirm)
        assertTrue(WorkoutImportResolver.resolve(parse(oneSet("Pullup", loadKind = "ASSISTANCE", weight = "5")), intendedCatalog()).canConfirm)
        val curl = parse(
            oneSet("Biceps curl", loadKind = "BODYWEIGHT_ONLY", reps = "10", weight = "")
        )
        assertTrue(
            WorkoutImportResolver.resolve(curl, intendedCatalog()).errors
                .any { it.code == WorkoutImportErrorCode.IncompatibleLoadKind }
        )
        val none = parse(
            oneSet("Futás", reps = "", duration = "720", distance = "2000", unit = "m", loadKind = "NONE", weight = "")
        )
        assertTrue(WorkoutImportResolver.resolve(none, intendedCatalog()).canConfirm)
        assertTrue(
            WorkoutImportResolver.resolve(
                inMemoryPullup(loadKind = PlannedLoadKind.ADDED_WEIGHT, weightKg = null),
                intendedCatalog()
            ).errors.any { it.code == WorkoutImportErrorCode.MissingLoadWeight }
        )
        assertTrue(
            WorkoutImportResolver.resolve(
                inMemoryPullup(loadKind = PlannedLoadKind.BODYWEIGHT_ONLY, weightKg = BigDecimal("5")),
                intendedCatalog()
            ).errors.any { it.code == WorkoutImportErrorCode.ForbiddenLoadWeight }
        )
        val csvMissingWeight = WorkoutImportCsv.parse(
            oneSet("Pullup", loadKind = "ADDED_WEIGHT", weight = ""),
            today
        )
        assertTrue(csvMissingWeight is WorkoutImportParseResult.Failure)
        assertTrue(
            (csvMissingWeight as WorkoutImportParseResult.Failure).errors
                .any { it.code == WorkoutImportErrorCode.WeightIncompatibleWithLoad }
        )
    }

    @Test
    fun requiredAndForbiddenActualsDependOnMeasurement() {
        val missingReps = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup", reps = "")),
            intendedCatalog()
        )
        assertTrue(missingReps.errors.any { it.code == WorkoutImportErrorCode.MissingRequiredActual })
        val forbiddenDistance = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup", distance = "2", unit = "km")),
            intendedCatalog()
        )
        assertTrue(forbiddenDistance.errors.any { it.code == WorkoutImportErrorCode.ForbiddenActual })
        val missingDuration = WorkoutImportResolver.resolve(
            parse(oneSet("Futás", reps = "", duration = "", distance = "2", unit = "km", loadKind = "NONE", weight = "")),
            intendedCatalog()
        )
        assertTrue(missingDuration.errors.any { it.code == WorkoutImportErrorCode.MissingRequiredActual })
        val durationHold = exercise(
            21,
            "Hang",
            measurement = MeasurementType.DURATION,
            resistance = ResistanceBasis.BODYWEIGHT,
            primary = MuscleGroup.FOREARMS
        )
        val missingHold = WorkoutImportResolver.resolve(
            parse(oneSet("Hang", reps = "", duration = "", loadKind = "BODYWEIGHT_ONLY", weight = "")),
            listOf(durationHold)
        )
        assertTrue(missingHold.errors.any { it.code == WorkoutImportErrorCode.MissingRequiredActual })
        val holdOk = WorkoutImportResolver.resolve(
            parse(oneSet("Hang", reps = "", duration = "30", loadKind = "BODYWEIGHT_ONLY", weight = "")),
            listOf(durationHold)
        )
        assertTrue(holdOk.canConfirm)
        val skipped = parse(oneSet("Pullup") + row(setIndex = "2", status = "SKIPPED", reps = "", loadKind = "", weight = ""))
        val skippedPlan = WorkoutImportResolver.resolve(skipped, intendedCatalog())
        assertTrue(skippedPlan.canConfirm)
        assertEquals(1, skippedPlan.skippedSetCount)
        val completion = exercise(
            20,
            "Farmer carry done",
            measurement = MeasurementType.COMPLETION_ONLY,
            resistance = ResistanceBasis.NONE,
            primary = MuscleGroup.FULL_BODY
        )
        val completionCsv = oneSet(
            "Farmer carry done",
            reps = "",
            loadKind = "NONE",
            weight = ""
        )
        assertTrue(WorkoutImportResolver.resolve(parse(completionCsv), listOf(completion)).canConfirm)
        val completionWithReps = WorkoutImportResolver.resolve(
            parse(oneSet("Farmer carry done", loadKind = "NONE", weight = "")),
            listOf(completion)
        )
        assertTrue(completionWithReps.errors.any { it.code == WorkoutImportErrorCode.ForbiddenActual })
    }

    @Test
    fun perSideWeightIsNotDoubledAndSnapshotIsImmutable() {
        val secondary = mutableListOf(MuscleGroup.FOREARMS)
        val live = exercise(9, "Biceps curl", resistance = ResistanceBasis.EXTERNAL, interpretation = WeightInterpretation.PER_SIDE, primary = MuscleGroup.BICEPS, secondary = secondary)
        val plan = WorkoutImportResolver.resolve(
            parse(oneSet("Biceps curl", loadKind = "EXTERNAL_WEIGHT", reps = "10", weight = "17.5")),
            listOf(live)
        )
        assertEquals(BigDecimal("17.5"), plan.workouts[0].exercises[0].sets[0].weightKg)
        assertEquals(WeightInterpretation.PER_SIDE, plan.workouts[0].exercises[0].snapshot!!.weightInterpretation)
        secondary += MuscleGroup.CHEST
        assertEquals(listOf(MuscleGroup.FOREARMS), plan.workouts[0].exercises[0].snapshot!!.secondaryMuscles)
        assertEquals("Biceps curl", plan.workouts[0].exercises[0].snapshot!!.catalogName)
        assertEquals("Biceps curl", plan.workouts[0].exercises[0].snapshot!!.incomingName)
        assertEquals(MuscleGroup.BICEPS, plan.workouts[0].exercises[0].snapshot!!.primaryMuscle)
        assertEquals(ExerciseCategory.STRENGTH, plan.workouts[0].exercises[0].snapshot!!.category)
        assertEquals(MovementPattern.VERTICAL_PULL, plan.workouts[0].exercises[0].snapshot!!.movementPattern)
        assertEquals(MeasurementType.REPETITIONS_AND_WEIGHT, plan.workouts[0].exercises[0].snapshot!!.measurementType)
        assertEquals(ResistanceBasis.EXTERNAL, plan.workouts[0].exercises[0].snapshot!!.resistanceBasis)
        assertNull(plan.workouts[0].exercises[0].snapshot!!.notes)
    }

    @Test
    fun invalidCatalogMusclesFail() {
        val invalid = exercise(
            1,
            "Pullup",
            primary = MuscleGroup.LATS,
            secondary = listOf(MuscleGroup.LATS, MuscleGroup.BICEPS)
        )
        val plan = WorkoutImportResolver.resolve(parse(oneSet("Pullup")), listOf(invalid))
        assertTrue(plan.errors.any { it.code == WorkoutImportErrorCode.InvalidCatalogMuscle })
        assertFalse(plan.canConfirm)
    }

    @Test
    fun bodyWeightPriorityUsesCsvThenSameDayThenPreviousThenUnknown() {
        val measurements = sampleMeasurements()
        val csvWins = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup", body = "82.5")),
            intendedCatalog(),
            measurements = measurements
        )
        assertEquals(BodyWeightSource.MANUAL, csvWins.workouts[0].bodyWeight.source)
        assertTrue(csvWins.warnings.any { it.code == WorkoutImportWarningCode.BodyWeightMismatch })
        val sameDay = WorkoutImportResolver.resolve(parse(oneSet("Pullup")), intendedCatalog(), measurements = measurements)
        assertEquals(BodyWeightSource.MEASURED_SAME_DAY, sameDay.workouts[0].bodyWeight.source)
        val previous = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup", date = "2026-09-14", started = "2026-09-14T12:00:00", finished = "2026-09-14T13:00:00")),
            intendedCatalog(),
            measurements = listOf(measurement(1, "2026-09-13", 80.0))
        )
        assertEquals(BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT, previous.workouts[0].bodyWeight.source)
        assertTrue(previous.warnings.any { it.code == WorkoutImportWarningCode.PreviousMeasurementFallback })
        val futureIgnored = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup", date = "2026-09-12", started = "2026-09-12T12:00:00", finished = "2026-09-12T13:00:00")),
            intendedCatalog(),
            measurements = listOf(measurement(99, "2026-09-16", 90.0))
        )
        assertEquals(BodyWeightSource.UNKNOWN, futureIgnored.workouts[0].bodyWeight.source)
        val unknown = WorkoutImportResolver.resolve(parse(oneSet("Pullup")), intendedCatalog(), measurements = emptyList())
        assertEquals(BodyWeightSource.UNKNOWN, unknown.workouts[0].bodyWeight.source)
        assertTrue(unknown.warnings.any { it.code == WorkoutImportWarningCode.UnknownBodyWeight })
        assertTrue(unknown.workouts[0].warnings.any { it.code == WorkoutImportWarningCode.UnknownBodyWeight })
        assertTrue(unknown.canConfirm)
    }

    @Test
    fun givenCsvImportWithManualBodyWeightThenManualPriorityRemainsUnchanged() {
        val measurements = sampleMeasurements()
        val csvWins = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup", body = "82.5")),
            intendedCatalog(),
            measurements = measurements
        )
        assertEquals(BodyWeightSource.MANUAL, csvWins.workouts[0].bodyWeight.source)
        assertEquals(82.5, csvWins.workouts[0].bodyWeight.kilograms!!, 0.0)
        assertTrue(csvWins.warnings.any { it.code == WorkoutImportWarningCode.BodyWeightMismatch })
    }

    @Test
    fun csvBodyWeightDifferingFromSameDayWarns() {
        val plan = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup", body = "99.9")),
            intendedCatalog(),
            measurements = listOf(measurement(1, "2026-09-15", 80.0))
        )
        assertEquals(BodyWeightSource.MANUAL, plan.workouts[0].bodyWeight.source)
        assertTrue(plan.warnings.any { it.code == WorkoutImportWarningCode.BodyWeightMismatch })
        assertTrue(plan.canConfirm)
    }

    @Test
    fun warningsDoNotBlockConfirmationWhileErrorsDo() {
        val warned = WorkoutImportResolver.resolve(
            parse(oneSet("Pullup")),
            listOf(exercise(7, "Pullup", archived = true))
        )
        assertTrue(warned.canConfirm)
        assertTrue(warned.warningCount > 0)
        val blocked = WorkoutImportResolver.resolve(parse(oneSet("Missing")), intendedCatalog())
        assertFalse(blocked.canConfirm)
        assertTrue(blocked.errorCount > 0)
    }

    private fun parseFixture(): WorkoutImportDocument {
        val csv = javaClass.getResource("/hu/laca/weighttracker/domain/workoutimport/history-v1-sample.csv")!!
            .readText(StandardCharsets.UTF_8)
        return parse(csv)
    }

    private fun parse(csv: String): WorkoutImportDocument {
        val result = WorkoutImportCsv.parse(csv, today)
        assertTrue(result.toString(), result is WorkoutImportParseResult.Success)
        return (result as WorkoutImportParseResult.Success).document
    }

    private fun inMemoryPullup(
        loadKind: PlannedLoadKind,
        weightKg: BigDecimal?,
        reps: Int? = 5,
        status: SessionSetStatus = SessionSetStatus.COMPLETED
    ): WorkoutImportDocument {
        val set = WorkoutImportSet(
            sourceRowNumber = 2,
            setIndex = 1,
            status = status,
            reps = reps,
            durationSeconds = null,
            distanceMeters = null,
            loadKind = loadKind,
            weightKg = weightKg
        )
        return WorkoutImportDocument(
            workouts = listOf(
                WorkoutImportWorkout(
                    sourceRowNumber = 2,
                    workoutId = "w1",
                    name = "Pull",
                    normalizedName = "pull",
                    workoutDate = LocalDate.parse("2026-09-15"),
                    startedAt = java.time.LocalDateTime.parse("2026-09-15T12:00:00"),
                    finishedAt = java.time.LocalDateTime.parse("2026-09-15T13:00:00"),
                    notes = null,
                    bodyWeightKg = null,
                    exercises = listOf(
                        WorkoutImportExercise(
                            sourceRowNumber = 2,
                            exerciseIndex = 1,
                            name = "Pullup",
                            normalizedName = "pullup",
                            sets = listOf(set)
                        )
                    )
                )
            )
        )
    }

    private fun intendedCatalog(): List<Exercise> {
        return listOf(
            exercise(7, "Pullup", pattern = MovementPattern.VERTICAL_PULL, primary = MuscleGroup.LATS, secondary = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.ABS)),
            exercise(8, "Chinup", pattern = MovementPattern.VERTICAL_PULL, primary = MuscleGroup.LATS, secondary = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.ABS)),
            exercise(9, "Biceps curl", pattern = MovementPattern.ISOLATION, resistance = ResistanceBasis.EXTERNAL, interpretation = WeightInterpretation.PER_SIDE, primary = MuscleGroup.BICEPS, secondary = listOf(MuscleGroup.FOREARMS)),
            exercise(10, "Hammer curl", pattern = MovementPattern.ISOLATION, resistance = ResistanceBasis.EXTERNAL, interpretation = WeightInterpretation.PER_SIDE, primary = MuscleGroup.FOREARMS, secondary = listOf(MuscleGroup.BICEPS)),
            exercise(12, "Wrist roll", pattern = MovementPattern.ISOLATION, resistance = ResistanceBasis.EXTERNAL, interpretation = WeightInterpretation.TOTAL, primary = MuscleGroup.FOREARMS),
            exercise(1, "Gyűrűn tolódzkodás", pattern = MovementPattern.VERTICAL_PUSH, primary = MuscleGroup.CHEST, secondary = listOf(MuscleGroup.TRICEPS)),
            exercise(2, "Tolódzkodás", pattern = MovementPattern.VERTICAL_PUSH, primary = MuscleGroup.TRICEPS, secondary = listOf(MuscleGroup.CHEST)),
            exercise(3, "Kézenállás kitolás", pattern = MovementPattern.VERTICAL_PUSH, primary = MuscleGroup.FRONT_DELTOID, secondary = listOf(MuscleGroup.TRICEPS)),
            exercise(4, "Decline Pushup", pattern = MovementPattern.HORIZONTAL_PUSH, primary = MuscleGroup.CHEST, secondary = listOf(MuscleGroup.FRONT_DELTOID, MuscleGroup.TRICEPS)),
            exercise(
                11,
                "Futás",
                category = ExerciseCategory.CARDIO,
                pattern = MovementPattern.CARDIO,
                measurement = MeasurementType.DISTANCE_AND_DURATION,
                resistance = ResistanceBasis.NONE,
                interpretation = WeightInterpretation.NOT_APPLICABLE,
                primary = MuscleGroup.QUADRICEPS,
                secondary = listOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)
            )
        )
    }

    private fun exercise(
        id: Long,
        name: String,
        archived: Boolean = false,
        category: ExerciseCategory = ExerciseCategory.STRENGTH,
        pattern: MovementPattern = MovementPattern.VERTICAL_PULL,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.BODYWEIGHT,
        interpretation: WeightInterpretation = WeightInterpretation.NOT_APPLICABLE,
        primary: MuscleGroup = MuscleGroup.LATS,
        secondary: List<MuscleGroup> = emptyList()
    ): Exercise {
        return Exercise(
            id = id,
            name = name,
            normalizedName = ExerciseNaming.normalize(name),
            category = category,
            movementPattern = pattern,
            measurementType = measurement,
            resistanceBasis = resistance,
            weightInterpretation = interpretation,
            primaryMuscle = primary,
            secondaryMuscles = secondary,
            notes = null,
            archived = archived,
            createdAt = 1L,
            updatedAt = 1L
        )
    }

    private fun sampleMeasurements(): List<WeightMeasurement> {
        return listOf(
            measurement(1, "2026-09-13", 80.0),
            measurement(2, "2026-09-14", 80.0),
            measurement(3, "2026-09-15", 80.0)
        )
    }

    private fun measurement(id: Long, date: String, kg: Double): WeightMeasurement {
        return WeightMeasurement(id, LocalDate.parse(date), kg, 1L, 1L)
    }

    private fun oneSet(
        exerciseName: String,
        date: String = "2026-09-15",
        started: String = "2026-09-15T12:00:00",
        finished: String = "2026-09-15T13:00:00",
        body: String = "",
        reps: String = "5",
        duration: String = "",
        distance: String = "",
        unit: String = "",
        loadKind: String = "BODYWEIGHT_ONLY",
        weight: String = ""
    ): String {
        return header() + row(
            date = date,
            started = started,
            finished = finished,
            body = body,
            exerciseName = exerciseName,
            reps = reps,
            duration = duration,
            distance = distance,
            unit = unit,
            loadKind = loadKind,
            weight = weight
        )
    }

    private fun header(): String = WorkoutImportCsv.HEADER + "\n"

    private fun row(
        workoutId: String = "w1",
        name: String = "Pull",
        date: String = "2026-09-15",
        started: String = "2026-09-15T12:00:00",
        finished: String = "2026-09-15T13:00:00",
        body: String = "",
        exerciseIndex: String = "1",
        exerciseName: String = "Pullup",
        setIndex: String = "1",
        status: String = "COMPLETED",
        reps: String = "5",
        duration: String = "",
        distance: String = "",
        unit: String = "",
        loadKind: String = "BODYWEIGHT_ONLY",
        weight: String = ""
    ): String {
        return listOf(
            "1", workoutId, name, date, started, finished, "", body,
            exerciseIndex, exerciseName, setIndex, status, reps, duration, distance, unit, loadKind, weight
        ).joinToString(",") + "\n"
    }
}
