package app.mymusclemap.domain.workoutimport

import app.mymusclemap.domain.workout.PlannedLoadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class WorkoutImportCsvTest {
    private val today = LocalDate.parse("2026-09-16")

    @Test
    fun sampleFixtureParsesWithExpectedCounts() {
        val csv = fixture("history-v1-sample.csv")
        val parsed = success(csv)
        assertEquals(4, parsed.workoutCount)
        assertEquals(11, parsed.exerciseCount)
        assertEquals(51, parsed.completedSetCount)
        val pull = parsed.workouts[0]
        val push = parsed.workouts[1]
        val runLater = parsed.workouts[2]
        val runEarlier = parsed.workouts[3]
        assertEquals("history-2026-09-15-pull", pull.workoutId)
        assertEquals("Pull", pull.name)
        assertEquals(5, pull.exercises.size)
        assertEquals(35, pull.completedSetCount)
        assertEquals(listOf(5, 5, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4), pull.exercises[0].sets.map { it.reps })
        assertEquals("pullup", pull.exercises[0].normalizedName)
        assertEquals(BigDecimal("17.5"), pull.exercises[2].sets.first().weightKg)
        assertEquals("Wrist roll", pull.exercises[4].name)
        assertEquals("Push", push.name)
        assertEquals(14, push.completedSetCount)
        assertEquals("gyűrűn tolódzkodás", push.exercises[0].normalizedName)
        assertEquals("kézenállás kitolás", push.exercises[2].normalizedName)
        assertEquals(BigDecimal("2000"), runLater.exercises[0].sets[0].distanceMeters)
        assertEquals(720, runLater.exercises[0].sets[0].durationSeconds)
        assertEquals(PlannedLoadKind.NONE, runLater.exercises[0].sets[0].loadKind)
        assertEquals("Futás", runEarlier.name)
        assertEquals(runLater.exercises[0].normalizedName, runEarlier.exercises[0].normalizedName)
    }

    @Test
    fun acceptsUtf8BomAndCrLf() {
        val body = oneSetCsv()
        val bom = "\uFEFF$body".toByteArray(StandardCharsets.UTF_8)
        val parsedBom = WorkoutImportCsv.parse(bom, today) as WorkoutImportParseResult.Success
        assertEquals(1, parsedBom.document.workoutCount)
        val crlf = success(body.replace("\n", "\r\n"))
        assertEquals(1, crlf.completedSetCount)
    }

    @Test
    fun preservesQuotedCommaEscapedQuoteAndMultilineNotes() {
        val csv = header() +
            row(notes = "hello, \"world\"\nstill here")
        val parsed = success(csv)
        assertEquals("hello, \"world\"\nstill here", parsed.workouts[0].notes)
    }

    @Test
    fun emptyOptionalTrailingFieldsAreAllowed() {
        val csv = header() +
            "1,w1,Pull,2026-09-15,2026-09-15T12:00:00,2026-09-15T13:00:00,,,1,Pullup,1,COMPLETED,5,,,,BODYWEIGHT_ONLY"
        val parsed = success(csv)
        assertEquals(PlannedLoadKind.BODYWEIGHT_ONLY, parsed.workouts[0].exercises[0].sets[0].loadKind)
        assertEquals(null, parsed.workouts[0].exercises[0].sets[0].weightKg)
    }

    @Test
    fun rejectsUnsupportedVersionAndHeaderMismatch() {
        assertCode(
            header().replace("format_version", "version") + row(),
            WorkoutImportErrorCode.InvalidHeader
        )
        assertCode(
            header() + row(version = "2"),
            WorkoutImportErrorCode.UnsupportedVersion
        )
        assertCode(
            header() + row().trimEnd('\n') + ",extra\n",
            WorkoutImportErrorCode.ExtraColumns
        )
    }

    @Test
    fun rejectsMalformedQuotesEmptyAndHeaderOnly() {
        val failedUtf8 = WorkoutImportCsv.parse(byteArrayOf(0xC3.toByte(), 0x28), today)
            as WorkoutImportParseResult.Failure
        assertTrue(failedUtf8.errors.any { it.code == WorkoutImportErrorCode.InvalidUtf8 })
        val duplicateHeader = WorkoutImportCsv.HEADER.replace("workout_id", "format_version")
        assertCode(duplicateHeader + "\n" + row(), WorkoutImportErrorCode.DuplicateHeader)
    }

    @Test
    fun rejectsInconsistentWorkoutFieldsAndFutureDates() {
        val inconsistent = header() +
            row(workoutId = "w1", name = "Pull") +
            row(workoutId = "w1", name = "Push", setIndex = "2")
        assertCode(inconsistent, WorkoutImportErrorCode.InconsistentWorkoutField)
        assertCode(
            header() + row(date = "2026-09-17", started = "2026-09-17T12:00:00", finished = "2026-09-17T13:00:00"),
            WorkoutImportErrorCode.FutureDate
        )
        assertCode(
            header() + row(started = "2026-09-14T12:00:00"),
            WorkoutImportErrorCode.TimestampDateMismatch
        )
        assertCode(
            header() + row(finished = "2026-09-15T11:00:00"),
            WorkoutImportErrorCode.FinishBeforeStart
        )
    }

    @Test
    fun rejectsBrokenIndicesAndWorkoutsWithoutCompletedSets() {
        assertCode(
            header() + row(exerciseIndex = "2"),
            WorkoutImportErrorCode.NonContiguousExerciseIndex
        )
        val conflict = header() +
            row(exerciseName = "Pullup") +
            row(exerciseName = "Chinup", setIndex = "2")
        assertCode(conflict, WorkoutImportErrorCode.ConflictingExerciseName)
        val duplicateSet = header() + row() + row()
        assertCode(duplicateSet, WorkoutImportErrorCode.DuplicateSetIndex)
        assertCode(
            header() + row(setIndex = "2"),
            WorkoutImportErrorCode.NonContiguousSetIndex
        )
        assertCode(
            header() + row(status = "SKIPPED", reps = "", loadKind = "", weight = ""),
            WorkoutImportErrorCode.NoCompletedSets
        )
    }

    @Test
    fun skippedRowsCannotCarryActualsAndCompletedRowsNeedLoadKind() {
        assertCode(
            header() + row(status = "SKIPPED"),
            WorkoutImportErrorCode.SkippedHasActuals
        )
        assertCode(
            header() + row(loadKind = "", weight = ""),
            WorkoutImportErrorCode.CompletedMissingLoadKind
        )
        assertCode(
            header() + row(loadKind = "BODYWEIGHT_ONLY", weight = "10"),
            WorkoutImportErrorCode.WeightIncompatibleWithLoad
        )
        assertCode(
            header() + row(loadKind = "EXTERNAL_WEIGHT", weight = ""),
            WorkoutImportErrorCode.WeightIncompatibleWithLoad
        )
    }

    @Test
    fun normalizesDistanceAndAcceptsCommaOrPeriodDecimals() {
        val km = success(header() + row(reps = "", duration = "720", distance = "2", unit = "km", loadKind = "NONE", weight = ""))
        assertEquals(BigDecimal("2000"), km.workouts[0].exercises[0].sets[0].distanceMeters)
        val meters = success(header() + row(reps = "", duration = "720", distance = "2000.0", unit = "m", loadKind = "NONE", weight = ""))
        assertEquals(BigDecimal("2000.0"), meters.workouts[0].exercises[0].sets[0].distanceMeters)
        val quotedComma = success(
            header() + row(loadKind = "EXTERNAL_WEIGHT", weight = "17,50")
                .replace("17,50", "\"17,50\"")
        )
        assertEquals(BigDecimal("17.50"), quotedComma.workouts[0].exercises[0].sets[0].weightKg)
        assertCode(
            header() + row(weight = "17.555", loadKind = "EXTERNAL_WEIGHT"),
            WorkoutImportErrorCode.InvalidPrecision
        )
        assertCode(
            header() + row(distance = "2", unit = "", loadKind = "NONE", weight = "", reps = "", duration = "30"),
            WorkoutImportErrorCode.MissingDistanceUnit
        )
    }

    @Test
    fun canonicalFormIsStableForEquivalentDecimalsAndChangesWhenContentChanges() {
        val a = success(
            header() + row(loadKind = "EXTERNAL_WEIGHT", weight = "17.50")
        )
        val b = success(
            header() + row(loadKind = "EXTERNAL_WEIGHT", weight = "17.5")
        )
        val km = success(header() + row(reps = "", duration = "720", distance = "2", unit = "km", loadKind = "NONE", weight = ""))
        val meters = success(header() + row(reps = "", duration = "720", distance = "2000", unit = "m", loadKind = "NONE", weight = ""))
        assertEquals(
            WorkoutImportCanonical.workout(a.workouts[0]),
            WorkoutImportCanonical.workout(b.workouts[0])
        )
        assertEquals(
            WorkoutImportCanonical.set(km.workouts[0].exercises[0].sets[0]),
            WorkoutImportCanonical.set(meters.workouts[0].exercises[0].sets[0])
        )
        val changedReps = success(header() + row(reps = "6"))
        assertNotEquals(
            WorkoutImportCanonical.workout(success(header() + row(reps = "5")).workouts[0]),
            WorkoutImportCanonical.workout(changedReps.workouts[0])
        )
        val canonical = WorkoutImportCanonical.workout(a.workouts[0])
        assertTrue(canonical.contains("weight_kg=17.5"))
        assertTrue(!canonical.contains("sourceRow") && !canonical.contains("rowNumber"))
    }

    @Test
    fun enforcesDocumentSafetyLimits() {
        val tooManySets = buildString {
            append(header())
            repeat(WorkoutImportLimits.MAX_SETS_PER_EXERCISE + 1) { index ->
                append(row(setIndex = (index + 1).toString()))
            }
        }
        assertCode(tooManySets, WorkoutImportErrorCode.TooManySets)
        val tooManyExercises = buildString {
            append(header())
            repeat(WorkoutImportLimits.MAX_EXERCISES_PER_WORKOUT + 1) { index ->
                append(row(exerciseIndex = (index + 1).toString(), exerciseName = "Ex${index + 1}"))
            }
        }
        assertCode(tooManyExercises, WorkoutImportErrorCode.TooManyExercises)
        val tooManyWorkouts = buildString {
            append(header())
            repeat(WorkoutImportLimits.MAX_WORKOUTS + 1) { index ->
                append(row(workoutId = "w$index"))
            }
        }
        assertCode(tooManyWorkouts, WorkoutImportErrorCode.TooManyWorkouts)
        val oversized = ByteArray(WorkoutImportLimits.MAX_UTF8_BYTES + 1) { 'a'.code.toByte() }
        val failed = WorkoutImportCsv.parse(oversized, today) as WorkoutImportParseResult.Failure
        assertTrue(failed.errors.any { it.code == WorkoutImportErrorCode.FileTooLarge })
        val tooManyRows = buildString {
            append(header())
            repeat(WorkoutImportLimits.MAX_ROWS + 1) { index ->
                append(row(workoutId = "w$index"))
            }
        }
        assertCode(tooManyRows, WorkoutImportErrorCode.TooManyRows)
        assertCode(header() + row(workoutId = ""), WorkoutImportErrorCode.MissingRequired)
        assertCode(header() + row(reps = "0"), WorkoutImportErrorCode.OutOfRange)
        val duplicate = header() + row() + row()
        assertCode(duplicate, WorkoutImportErrorCode.DuplicateRecord)
    }

    @Test
    fun duplicateExactRecordsAreRejectedEvenWithDistinctSetIndexesMissing() {
        val csv = header() + row() + row()
        val failed = WorkoutImportCsv.parse(csv, today) as WorkoutImportParseResult.Failure
        assertTrue(failed.errors.any { it.code == WorkoutImportErrorCode.DuplicateRecord })
        assertTrue(failed.errors.any { it.code == WorkoutImportErrorCode.DuplicateSetIndex })
    }

    private fun success(csv: String): WorkoutImportDocument {
        val result = WorkoutImportCsv.parse(csv, today)
        assertTrue(result.toString(), result is WorkoutImportParseResult.Success)
        return (result as WorkoutImportParseResult.Success).document
    }

    private fun assertCode(csv: String, code: WorkoutImportErrorCode) {
        val result = WorkoutImportCsv.parse(csv, today)
        assertTrue(result.toString(), result is WorkoutImportParseResult.Failure)
        val errors = (result as WorkoutImportParseResult.Failure).errors
        assertTrue(errors.toString(), errors.any { it.code == code })
    }

    private fun fixture(name: String): String {
        return javaClass.getResource("/app/mymusclemap/domain/workoutimport/$name")!!
            .readText(StandardCharsets.UTF_8)
    }

    private fun oneSetCsv(): String = header() + row()

    private fun header(): String = WorkoutImportCsv.HEADER + "\n"

    private fun row(
        version: String = "1",
        workoutId: String = "w1",
        name: String = "Pull",
        date: String = "2026-09-15",
        started: String = "2026-09-15T12:00:00",
        finished: String = "2026-09-15T13:00:00",
        notes: String = "",
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
        val noteField = if (notes.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${notes.replace("\"", "\"\"")}\""
        } else {
            notes
        }
        return listOf(
            version, workoutId, name, date, started, finished, noteField, body,
            exerciseIndex, exerciseName, setIndex, status, reps, duration, distance, unit, loadKind, weight
        ).joinToString(",") + "\n"
    }
}
