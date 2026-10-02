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
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.exercise.StarterCatalog
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.ThemeMode
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WeekVerdict
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class DemoTrainingHistoryTest {
    @Test
    fun sameReferenceDateIsDeterministic() {
        assertEquals(LocalDate.of(2026, 9, 26), DemoTrainingHistoryGenerator.referenceDate)
        val again = DemoTrainingHistoryGenerator.generate()
        assertEquals(snapshot, again)
        assertEquals(json, AppBackupJson.encode(again))
        assertEquals(
            DemoTrainingHistoryGenerator.referenceDate,
            snapshot.exportedAt.atZone(ZoneOffset.UTC).toLocalDate()
        )
    }

    @Test
    fun blankReferenceDatePropertyKeepsTheFixedDate() {
        assertEquals(
            DemoTrainingHistoryGenerator.referenceDate,
            DemoTrainingHistoryGenerator.referenceDateFromProperty(null)
        )
        assertEquals(
            DemoTrainingHistoryGenerator.referenceDate,
            DemoTrainingHistoryGenerator.referenceDateFromProperty("  ")
        )
    }

    @Test
    fun customReferenceDateShiftsHistoryAndKeepsDemoShape() {
        val custom = LocalDate.of(2026, 9, 27)
        val customSnapshot = DemoTrainingHistoryGenerator.generate(custom)
        val customAnalysis = DemoTrainingHistoryGenerator.analyze(customSnapshot)
        assertEquals(custom, customAnalysis.referenceDate)
        assertEquals(custom.minusMonths(18), customAnalysis.historyStart)
        assertFalse(customAnalysis.lastCompleted.isAfter(custom))
        assertFalse(customAnalysis.lastCompleted.isBefore(custom.minusDays(6)))
        assertEquals(custom, customSnapshot.exportedAt.atZone(ZoneOffset.UTC).toLocalDate())
        assertTrue(ChronoUnit.DAYS.between(customAnalysis.firstCompleted, customAnalysis.lastCompleted) > 365)
        assertFalse(customAnalysis.firstCompleted.isAfter(customAnalysis.historyStart.plusDays(7)))
        assertTrue(customAnalysis.lateCompletions >= 5)
        assertTrue(customAnalysis.missedOccurrences >= 5)
        assertTrue(customAnalysis.cancelledOccurrences >= 8)
        assertTrue(customAnalysis.rescheduledOccurrences >= 3)
        assertTrue(customAnalysis.unplannedWorkouts >= 5)
        assertTrue(customAnalysis.adherencePercent in 80..90)
        val breakDays = ChronoUnit.DAYS.between(customAnalysis.breakStart, customAnalysis.breakEnd) + 1
        assertTrue(breakDays in 12..16)
        assertEquals(0.0, customAnalysis.breakVolumeKg, 0.001)
        assertTrue(customAnalysis.firstBodyWeightKg > customAnalysis.lastBodyWeightKg)
        assertTrue(customAnalysis.bodyWeightEntries in 100..400)
        assertFalse(json == AppBackupJson.encode(customSnapshot))

        val shifted = LocalDate.of(2026, 1, 15)
        val shiftedAnalysis = DemoTrainingHistoryGenerator.analyze(
            DemoTrainingHistoryGenerator.generate(shifted)
        )
        assertEquals(shifted.minusMonths(18), shiftedAnalysis.historyStart)
        assertFalse(shiftedAnalysis.lastCompleted.isAfter(shifted))
        assertFalse(shiftedAnalysis.lastCompleted.isBefore(shifted.minusDays(6)))
        assertTrue(shiftedAnalysis.breakStart != analysis.breakStart)
        assertTrue(shiftedAnalysis.unplannedWorkouts >= 5)
        assertEquals(0.0, shiftedAnalysis.breakVolumeKg, 0.001)
    }

    @Test
    fun invalidReferenceDateFailsClearly() {
        listOf("2026-09-31", "2026/09/27", "2026-9-27", "yesterday", "2026-09-27T00:00").forEach { raw ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                DemoTrainingHistoryGenerator.referenceDateFromProperty(raw)
            }
            assertTrue(error.message, error.message!!.contains("YYYY-MM-DD"))
            assertTrue(error.message, error.message!!.contains(raw))
        }
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

        val abandoned = tables.workoutSessions.filter { it.status == SessionStatus.ABANDONED.name }
        assertEquals(1, abandoned.size)
        tables.workoutSessions.forEach { session ->
            assertFalse(LocalDate.parse(session.workoutDate).isAfter(today))
            assertNull(session.activeLock)
            if (session.status == SessionStatus.ABANDONED.name) {
                assertNull(session.scheduledWorkoutId)
                assertEquals("Abandoned", session.notes)
                return@forEach
            }
            assertEquals(SessionStatus.COMPLETED.name, session.status)
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
        val unplanned = tables.workoutSessions.filter {
            it.scheduledWorkoutId == null && it.status == SessionStatus.COMPLETED.name
        }
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
        BodyMeasurementType.entries.forEach { type ->
            val rows = snapshot.tables.bodyMeasurements.filter { it.type == type.code }
            assertTrue("${type.code} count ${rows.size}", rows.size >= 8)
            assertTrue(rows.all { it.value in type.minimum..type.maximum })
            assertTrue(rows.all { it.source == "MANUAL" && it.externalId == null })
            val dates = rows.map { LocalDate.parse(it.date) }.sorted()
            assertTrue(java.time.temporal.ChronoUnit.DAYS.between(dates.first(), dates.last()) > 365)
            assertTrue(dates.zipWithNext().any { (earlier, later) ->
                java.time.temporal.ChronoUnit.DAYS.between(earlier, later) >= 14
            })
            assertTrue(dates.any { !it.isBefore(analysis.referenceDate.minusDays(29)) })
        }
    }

    @Test
    fun demoVolumeTrendResolutionMatchesEachStatisticsRange() {
        val today = DemoTrainingHistoryGenerator.referenceDate
        val trends = StatisticsRange.entries.associateWith { range ->
            DemoTrainingHistoryGenerator.statistics(snapshot, range).volume
        }
        val daily = trends.getValue(StatisticsRange.Days30)
        assertEquals(30, daily.trend.size)
        assertEquals(today.minusDays(29), daily.trend.first().date)
        assertEquals(today, daily.trend.last().date)
        assertTrue(daily.trend.zipWithNext().all { (earlier, later) -> later.date == earlier.date.plusDays(1) })
        assertEquals(daily.totalKg!!, daily.trend.sumOf { it.value }, 0.001)
        assertTrue(daily.trend.all { it.value >= 0.0 })

        val three = trends.getValue(StatisticsRange.Months3)
        assertEquals(14, three.trend.size)
        assertTrue(three.trend.all { it.date.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(0.0, three.trend.single { it.date == LocalDate.of(2026, 7, 13) }.value, 0.0)
        assertEquals(0.0, three.trend.single { it.date == LocalDate.of(2026, 7, 20) }.value, 0.0)
        assertEquals(three.totalKg!!, three.trend.sumOf { it.value }, 0.001)
        assertTrue(three.trend.all { it.value >= 0.0 })

        val six = trends.getValue(StatisticsRange.Months6)
        assertEquals(27, six.trend.size)
        assertTrue(six.trend.all { it.date.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(0.0, six.trend.single { it.date == LocalDate.of(2026, 7, 13) }.value, 0.0)
        assertEquals(0.0, six.trend.single { it.date == LocalDate.of(2026, 7, 20) }.value, 0.0)
        assertEquals(six.totalKg!!, six.trend.sumOf { it.value }, 0.001)

        val year = trends.getValue(StatisticsRange.Year1)
        assertEquals(13, year.trend.size)
        assertEquals(today.minusYears(1).withDayOfMonth(1), year.trend.first().date)
        assertEquals(today.withDayOfMonth(1), year.trend.last().date)
        assertTrue(year.trend.all { it.date.dayOfMonth == 1 })
        assertEquals(year.totalKg!!, year.trend.sumOf { it.value }, 0.001)
        assertTrue(year.trend.all { it.value >= 0.0 })

        val all = trends.getValue(StatisticsRange.All)
        assertEquals(19, all.trend.size)
        assertEquals(LocalDate.of(2025, 3, 1), all.trend.first().date)
        assertTrue(all.trend.first().value > 0.0)
        assertEquals(today.withDayOfMonth(1), all.trend.last().date)
        assertTrue(all.trend.all { it.date.dayOfMonth == 1 })
        assertEquals(all.totalKg!!, all.trend.sumOf { it.value }, 0.001)
        assertTrue(all.trend.all { it.value >= 0.0 })
    }

    @Test
    fun octoberReferenceHasTheImportableGoalWindow() {
        val today = LocalDate.of(2026, 10, 2)
        val snapshot = DemoTrainingHistoryGenerator.generate(today)
        val completed = snapshot.tables.workoutSessions
            .filter { it.status == SessionStatus.COMPLETED.name }
            .groupingBy { it.workoutDate }
            .eachCount()
        assertEquals(1, completed["2026-08-17"])
        assertEquals(2, completed["2026-08-20"])
        assertEquals(1, completed["2026-08-21"])
        assertEquals(1, completed["2026-08-24"])
        assertEquals(1, completed["2026-08-28"])
        assertEquals(1, completed["2026-09-14"])
        assertEquals(1, completed["2026-09-15"])
        assertEquals(1, completed["2026-09-29"])
        assertEquals(1, completed["2026-10-02"])
        val open = snapshot.tables.scheduledWorkouts
            .filter { row ->
                row.cancelledAt == null &&
                    row.scheduledDate >= "2026-09-28" &&
                    row.scheduledDate <= "2026-10-04" &&
                    snapshot.tables.workoutSessions.none {
                        it.scheduledWorkoutId == row.id && it.status == SessionStatus.COMPLETED.name
                    }
            }
            .map { it.scheduledDate }
            .sorted()
        assertEquals(listOf("2026-10-03", "2026-10-04"), open)
        assertEquals(
            listOf("2026-09-20"),
            snapshot.tables.workoutSessions
                .filter { it.status == SessionStatus.ABANDONED.name }
                .map { it.workoutDate }
        )
        assertEquals("2026-08-17", snapshot.tables.weeklyWorkoutGoals.single().effectiveWeekStart)
        assertEquals(4, snapshot.tables.weeklyWorkoutGoals.single().workoutsPerWeek)
    }

    @Test
    fun recentWeeksFollowTheWeeklyGoalStreak() {
        val today = DemoTrainingHistoryGenerator.referenceDate
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val revisions = snapshot.tables.weeklyWorkoutGoals.map { row ->
            WeeklyGoalRevision(
                effectiveWeekStart = LocalDate.parse(row.effectiveWeekStart),
                workoutsPerWeek = row.workoutsPerWeek,
                graceWeek = row.graceWeek
            )
        }
        val counts = snapshot.tables.workoutSessions
            .filter { it.status == SessionStatus.COMPLETED.name }
            .groupingBy { LocalDate.parse(it.workoutDate) }
            .eachCount()
        val status = WeeklyGoalLogic.evaluate(revisions, counts, today)
        val expected = listOf(
            -6 to (4 to 1),
            -5 to (5 to 2),
            -4 to (4 to 3),
            -3 to (4 to 4),
            -2 to (2 to 0),
            -1 to (4 to 1)
        )
        expected.forEach { (offset, result) ->
            val week = status.progressOn(monday.plusWeeks(offset.toLong()))
            assertEquals(4, week.goal)
            assertEquals(result.first, week.completed)
            assertEquals(result.second, week.streak)
        }
        assertEquals(WeekVerdict.Missed, status.progressOn(monday.plusWeeks(-2)).verdict)
        assertEquals(2, status.current.completed)
        assertEquals(1, status.current.streak)
        assertEquals(WeekVerdict.InProgress, status.current.verdict)
        val abandoned = snapshot.tables.workoutSessions.filter { it.status == SessionStatus.ABANDONED.name }
        assertTrue(abandoned.isNotEmpty())
        val weekEnd = monday.plusDays(6)
        val openPlans = snapshot.tables.scheduledWorkouts.filter { row ->
            row.cancelledAt == null &&
                !LocalDate.parse(row.scheduledDate).isBefore(monday) &&
                !LocalDate.parse(row.scheduledDate).isAfter(weekEnd) &&
                snapshot.tables.workoutSessions.none {
                    it.scheduledWorkoutId == row.id && it.status == SessionStatus.COMPLETED.name
                }
        }
        assertEquals(2, openPlans.map { it.scheduledDate }.distinct().size)
    }

    @Test
    fun checkedInDemoBackupMatchesOctober22026Reference() {
        val checkedInDate = LocalDate.of(2026, 10, 2)
        val file = DemoTrainingHistoryPaths.backupFile()
        assertTrue(
            "Missing ${file.absolutePath}. Regenerate with gradlew.bat :app:testDebugUnitTest " +
                "--tests app.mymusclemap.dev.DemoTrainingHistoryTest.writesDemoBackupFileWhenRequested " +
                "\"-Pdemo.backup.write=true\" \"-Pdemo.referenceDate=2026-10-02\"",
            file.isFile
        )
        val text = file.readText().replace("\r\n", "\n")
        assertEquals(DemoTrainingHistoryGenerator.encode(checkedInDate), text)
        assertFalse(text == json)
        val parsed = AppBackupJson.parse(text)
        assertTrue(parsed is AppBackupParseResult.Success)
        val restored = (parsed as AppBackupParseResult.Success).snapshot
        assertEquals(AppBackupFormat.SCHEMA_VERSION, restored.schemaVersion)
        assertEquals(checkedInDate, restored.exportedAt.atZone(ZoneOffset.UTC).toLocalDate())
    }

    @Test
    fun writesDemoBackupFileWhenRequested() {
        assumeTrue(System.getProperty("demo.backup.write") == "true")
        val referenceDate = DemoTrainingHistoryGenerator.referenceDateFromProperty(
            System.getProperty(DemoTrainingHistoryGenerator.REFERENCE_DATE_PROPERTY)
        )
        val file = DemoTrainingHistoryGenerator.writeBackup(referenceDate = referenceDate)
        assertTrue(file.isFile)
        assertTrue(file.length() > 0L)
        val parsed = AppBackupJson.parse(file.readText().replace("\r\n", "\n"))
        assertTrue(parsed is AppBackupParseResult.Success)
        assertEquals(
            referenceDate,
            (parsed as AppBackupParseResult.Success).snapshot.exportedAt.atZone(ZoneOffset.UTC).toLocalDate()
        )
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
                "points=${range.volumeWeeks} adherence=${range.adherencePercent} " +
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
