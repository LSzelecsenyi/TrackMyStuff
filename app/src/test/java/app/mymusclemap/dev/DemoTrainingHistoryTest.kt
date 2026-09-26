package app.mymusclemap.dev

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupParseResult
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSnapshot
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.domain.exercise.StarterCatalog
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.ThemeMode
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class DemoTrainingHistoryTest {
    @Test
    fun sameReferenceDateIsDeterministic() {
        val again = DemoTrainingHistoryGenerator.generate()
        assertEquals(snapshot, again)
        assertEquals(json, AppBackupJson.encode(again))
    }

    @Test
    fun generatedBackupUsesCurrentSchemaAndPassesValidation() {
        val parsed = AppBackupJson.parse(json)
        assertTrue(parsed.toString(), parsed is AppBackupParseResult.Success)
        val restored = (parsed as AppBackupParseResult.Success).snapshot
        assertEquals(AppBackupFormat.FORMAT_VERSION, restored.formatVersion)
        assertEquals(AppBackupFormat.SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(snapshot.tables, restored.tables)
        assertEquals(StarterCatalog.drafts.size, restored.tables.exercises.size)
    }

    @Test
    fun restoreSucceedsIntoAnEmptySchema7Database() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val preferences = ThemePreferences(context)
        preferences.replaceAppearance(AppearanceSettings.Default.copy(mode = ThemeMode.Dark))
        try {
            val result = AppBackupRepository(database, preferences).restoreJson(json)
            assertEquals(AppBackupRestoreResult.Success, result)
            assertEquals(snapshot.tables, database.appBackupDao().loadTables())
            assertEquals(ThemeMode.System, preferences.current().mode)
        } finally {
            database.close()
        }
    }

    @Test
    fun historyCoversMoreThanOneYearAndRangesDiffer() {
        val analysis = analysis
        val span = ChronoUnit.DAYS.between(analysis.firstCompleted, analysis.lastCompleted)
        assertTrue("span was $span days", span > 365)
        assertEquals(DemoTrainingHistoryGenerator.referenceDate, analysis.referenceDate)
        assertEquals(analysis.referenceDate.minusMonths(18), analysis.historyStart)
        assertFalse(analysis.firstCompleted.isAfter(analysis.historyStart.plusDays(7)))
        assertEquals(analysis.referenceDate, analysis.lastCompleted)
        assertTrue(analysis.bodyWeightEntries in 100..400)
        assertTrue(analysis.firstBodyWeightKg in 70.0..95.0)
        assertTrue(analysis.lastBodyWeightKg in 70.0..95.0)
        assertTrue(analysis.firstBodyWeightKg > analysis.lastBodyWeightKg)
        val weightSpan = ChronoUnit.DAYS.between(
            LocalDate.parse(snapshot.tables.weightMeasurements.minOf { it.date }),
            LocalDate.parse(snapshot.tables.weightMeasurements.maxOf { it.date })
        )
        assertTrue(weightSpan > 365)
        assertTrue(analysis.bodyWeightEntries < weightSpan)

        val counts = analysis.ranges.map { it.workouts }
        assertTrue("workout counts $counts", counts.zipWithNext().all { (earlier, later) -> later > earlier })
        val volume = analysis.ranges.map { it.volumeKg }
        assertTrue(volume.all { it != null && it > 0.0 })
        assertTrue(
            "volume $volume",
            volume.zipWithNext().all { (earlier, later) -> later!! > earlier!! }
        )
        val benchPoints = analysis.ranges.map { it.benchHistoryPoints }
        assertTrue("bench history $benchPoints", benchPoints.zipWithNext().all { (earlier, later) -> later > earlier })
        assertTrue(analysis.ranges.first { it.range == StatisticsRange.Days30 }.muscles >= 6)
        assertTrue(analysis.ranges.first { it.range == StatisticsRange.All }.muscles >= 10)
        println(summary(analysis))
    }

    @Test
    fun scheduleUsesSchema7OccurrenceLinks() {
        val tables = snapshot.tables
        val today = DemoTrainingHistoryGenerator.referenceDate
        val sessionsBySchedule = tables.workoutSessions.associateBy { it.scheduledWorkoutId }
        val scheduledById = tables.scheduledWorkouts.associateBy { it.id }
        assertTrue(analysis.lateCompletions >= 5)
        assertTrue(analysis.missedOccurrences >= 5)
        assertTrue(analysis.cancelledOccurrences >= 8)
        assertTrue(analysis.rescheduledOccurrences >= 3)
        assertTrue(analysis.unplannedWorkouts >= 5)
        assertTrue(analysis.adherencePercent in 80..90)
        assertEquals(analysis.adherenceCompleted, analysis.completedScheduled)
        assertEquals(
            analysis.adherencePercent,
            kotlin.math.round(100.0 * analysis.adherenceCompleted / analysis.adherencePlanned).toInt()
        )

        tables.workoutSessions.forEach { session ->
            assertFalse(LocalDate.parse(session.workoutDate).isAfter(today))
            assertEquals(SessionStatus.COMPLETED.name, session.status)
            assertNull(session.activeLock)
            val scheduledId = session.scheduledWorkoutId ?: return@forEach
            val scheduled = scheduledById.getValue(scheduledId)
            assertNull(scheduled.cancelledAt)
            assertEquals(session.templateId, scheduled.templateId)
            assertEquals(1, tables.workoutSessions.count { it.scheduledWorkoutId == scheduledId })
        }
        tables.scheduledWorkouts.forEach { row ->
            assertFalse(row.templateName.isBlank())
            if (row.cancelledAt != null) {
                assertNull(sessionsBySchedule[row.id])
            }
        }
        val late = analysis.examples.single { it.label == "late" }
        assertEquals(late.originalScheduledDate, late.scheduledDate)
        assertTrue(late.workoutDate!!.isAfter(late.scheduledDate))
        val missed = analysis.examples.single { it.label == "missed" }
        assertNull(missed.workoutDate)
        assertFalse(missed.cancelled)
        assertTrue(missed.scheduledDate!!.isBefore(today))
        val cancelled = analysis.examples.single { it.label == "cancelled" }
        assertTrue(cancelled.cancelled)
        assertNull(cancelled.workoutDate)
        val rescheduled = analysis.examples.single { it.label == "rescheduled" }
        assertFalse(rescheduled.originalScheduledDate == rescheduled.scheduledDate)
        assertEquals(rescheduled.scheduledDate, rescheduled.workoutDate)
        val unplanned = tables.workoutSessions.filter { it.scheduledWorkoutId == null }
        assertEquals(analysis.unplannedWorkouts, unplanned.size)
        unplanned.forEach { session ->
            assertNull(session.scheduledWorkoutId)
            tables.scheduledWorkouts.filter { it.scheduledDate == session.workoutDate }.forEach { row ->
                assertTrue(sessionsBySchedule[row.id]?.id != session.id)
            }
        }
        val futureCompleted = tables.workoutSessions.any { LocalDate.parse(it.workoutDate).isAfter(today) }
        assertFalse(futureCompleted)
        val duringBreak = tables.workoutSessions.any { session ->
            val date = LocalDate.parse(session.workoutDate)
            !date.isBefore(analysis.breakStart) && !date.isAfter(analysis.breakEnd)
        }
        assertFalse(duringBreak)
        val breakDays = ChronoUnit.DAYS.between(analysis.breakStart, analysis.breakEnd) + 1
        assertTrue(breakDays in 12..16)
        assertEquals(0.0, analysis.breakVolumeKg, 0.001)
        assertTrue(
            tables.scheduledWorkouts.any {
                it.cancelledAt != null && LocalDate.parse(it.scheduledDate).isAfter(today)
            }
        )
        assertTrue(
            tables.scheduledWorkouts.any {
                it.cancelledAt == null && LocalDate.parse(it.scheduledDate).isAfter(today)
            }
        )
    }

    @Test
    fun exerciseAndVolumeHistoryAreMeaningful() {
        val analysis = analysis
        assertTrue(analysis.bench.historyPoints >= 30)
        assertTrue(analysis.bench.hasDecrease)
        assertTrue(analysis.bench.latestKg > analysis.bench.firstKg + 10.0)
        assertTrue(analysis.bench.afterBreakKg < analysis.bench.beforeBreakKg)
        assertTrue(analysis.completedSets > 1_000)
        assertTrue(analysis.volumeSets > 400)
        assertTrue(analysis.heaviestWeekKg > analysis.lightestPositiveWeekKg * 1.15)
        assertTrue(analysis.ranges.first { it.range == StatisticsRange.Days30 }.volumeWeeks >= 2)
        assertTrue(analysis.ranges.first { it.range == StatisticsRange.Months3 }.volumeWeeks >= 8)

        val deadliftDates = completedDatesFor("Deadlift")
        val recentDeadlifts = deadliftDates.count { StatisticsRange.Days30.contains(it, analysis.referenceDate) }
        assertTrue(deadliftDates.size >= 8)
        assertTrue("recent=$recentDeadlifts all=${deadliftDates.size}", recentDeadlifts <= 3)
        assertTrue(recentDeadlifts < deadliftDates.size)
        val plankSeconds = completedDurations("Plank")
        assertTrue(plankSeconds.size >= 20)
        assertTrue(plankSeconds.last() > plankSeconds.first())
        assertTrue(plankSeconds.zipWithNext().any { (earlier, later) -> later < earlier })

        val sets = snapshot.tables.workoutSessionSets
        assertTrue(sets.any { it.status == SessionSetStatus.SKIPPED.name && it.actualReps == null })
        assertTrue(sets.any { it.addedDuringWorkout && it.status == SessionSetStatus.COMPLETED.name })
        assertTrue(
            sets.any {
                it.actualLoadKind == PlannedLoadKind.EXTERNAL_WEIGHT.name &&
                    (it.actualWeightKg ?: 0.0) > 0.0 &&
                    (it.actualReps ?: 0) > 0
            }
        )
        assertTrue(
            sets.any {
                it.actualLoadKind == PlannedLoadKind.ADDED_WEIGHT.name &&
                    (it.actualWeightKg ?: 0.0) > 0.0 &&
                    (it.actualReps ?: 0) > 0
            }
        )
        val pushUpReps = completedReps("Push-Up")
        assertTrue(pushUpReps.last() > pushUpReps.first())
        assertTrue(pushUpReps.zipWithNext().any { (earlier, later) -> later < earlier })
        assertEquals(3, snapshot.tables.workoutTemplates.size)
    }

    @Test
    fun checkedInDemoBackupMatchesGenerator() {
        val file = DemoTrainingHistoryPaths.backupFile()
        assertTrue(
            "Missing ${file.absolutePath}. Regenerate with gradlew.bat :app:testDebugUnitTest " +
                "--tests app.mymusclemap.dev.DemoTrainingHistoryTest.writesDemoBackupFileWhenRequested " +
                "-Pdemo.backup.write=true",
            file.isFile
        )
        val text = file.readText().replace("\r\n", "\n")
        assertEquals(json, text)
        val parsed = AppBackupJson.parse(text)
        assertTrue(parsed is AppBackupParseResult.Success)
        assertEquals(AppBackupFormat.SCHEMA_VERSION, (parsed as AppBackupParseResult.Success).snapshot.schemaVersion)
    }

    @Test
    fun writesDemoBackupFileWhenRequested() {
        assumeTrue(System.getProperty("demo.backup.write") == "true")
        val file = DemoTrainingHistoryGenerator.writeBackup()
        assertTrue(file.isFile)
        assertTrue(file.length() > 0L)
    }

    private fun completedDatesFor(exerciseName: String): List<LocalDate> {
        val exerciseIds = snapshot.tables.workoutSessionExercises
            .filter { it.name == exerciseName }
            .map { it.id }
            .toSet()
        val sessionIds = snapshot.tables.workoutSessionSets
            .filter {
                it.sessionExerciseId in exerciseIds &&
                    it.status == SessionSetStatus.COMPLETED.name
            }
            .map { set ->
                snapshot.tables.workoutSessionExercises.first { it.id == set.sessionExerciseId }.sessionId
            }
            .toSet()
        return snapshot.tables.workoutSessions
            .filter { it.id in sessionIds }
            .map { LocalDate.parse(it.workoutDate) }
            .distinct()
            .sorted()
    }

    private fun completedDurations(exerciseName: String): List<Int> {
        return completedValues(exerciseName) { sets ->
            sets.mapNotNull { it.actualDurationSeconds }.maxOrNull()
        }
    }

    private fun completedReps(exerciseName: String): List<Int> {
        return completedValues(exerciseName) { sets ->
            sets.mapNotNull { it.actualReps }.maxOrNull()
        }
    }

    private fun <T : Any> completedValues(
        exerciseName: String,
        value: (List<app.mymusclemap.data.local.WorkoutSessionSetEntity>) -> T?
    ): List<T> {
        val exercises = snapshot.tables.workoutSessionExercises.filter { it.name == exerciseName }
        val setsByExercise = snapshot.tables.workoutSessionSets.groupBy { it.sessionExerciseId }
        val sessionDates = snapshot.tables.workoutSessions.associate { it.id to it.workoutDate }
        return exercises
            .mapNotNull { exercise ->
                val sets = setsByExercise[exercise.id].orEmpty()
                    .filter { it.status == SessionSetStatus.COMPLETED.name }
                val measured = value(sets) ?: return@mapNotNull null
                sessionDates.getValue(exercise.sessionId) to measured
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun summary(analysis: DemoHistoryAnalysis): String {
        val ranges = analysis.ranges.joinToString(separator = "\n") { range ->
            "  ${range.range}: workouts=${range.workouts} volume=${range.volumeKg} " +
                "weeks=${range.volumeWeeks} adherence=${range.adherencePercent} " +
                "benchPoints=${range.benchHistoryPoints} muscles=${range.muscles}"
        }
        val examples = analysis.examples.joinToString(separator = "\n") { example ->
            "  ${example.label}: ${example.templateName} original=${example.originalScheduledDate} " +
                "scheduled=${example.scheduledDate} workout=${example.workoutDate} cancelled=${example.cancelled}"
        }
        return """
            reference=${analysis.referenceDate}
            history=${analysis.historyStart}..${analysis.lastCompleted}
            completed=${analysis.firstCompleted}..${analysis.lastCompleted}
            bodyWeight=${analysis.bodyWeightEntries} (${analysis.firstBodyWeightKg} -> ${analysis.lastBodyWeightKg})
            templates=${analysis.workoutTemplates} exercises=${analysis.exercises}
            scheduled=${analysis.scheduledOccurrences} completedScheduled=${analysis.completedScheduled}
            late=${analysis.lateCompletions} missed=${analysis.missedOccurrences}
            cancelled=${analysis.cancelledOccurrences} rescheduled=${analysis.rescheduledOccurrences}
            unplanned=${analysis.unplannedWorkouts} sessions=${analysis.completedSessions}
            sets=${analysis.completedSets} volumeSets=${analysis.volumeSets}
            adherence=${analysis.adherencePercent}% (${analysis.adherenceCompleted}/${analysis.adherencePlanned})
            break=${analysis.breakStart}..${analysis.breakEnd} volume=${analysis.breakVolumeKg}
            bench=${analysis.bench.firstDate}:${analysis.bench.firstKg} before=${analysis.bench.beforeBreakKg}
            after=${analysis.bench.afterBreakKg} latest=${analysis.bench.latestDate}:${analysis.bench.latestKg}
            weeks=${analysis.lightestPositiveWeekKg}..${analysis.heaviestWeekKg}
            $ranges
            $examples
        """.trimIndent()
    }

    companion object {
        private val snapshot: AppBackupSnapshot by lazy { DemoTrainingHistoryGenerator.generate() }
        private val json: String by lazy { AppBackupJson.encode(snapshot) }
        private val analysis: DemoHistoryAnalysis by lazy { DemoTrainingHistoryGenerator.analyze(snapshot) }
    }
}
