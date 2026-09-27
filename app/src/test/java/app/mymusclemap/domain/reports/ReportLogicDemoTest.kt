package app.mymusclemap.domain.reports

import app.mymusclemap.data.appbackup.AppBackupTables
import app.mymusclemap.data.local.toModel
import app.mymusclemap.dev.DemoTrainingHistoryGenerator
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportLogicDemoTest {
    @Test
    fun demoMonthlyAndYearlyReportsMatchClosedHistory() {
        val snapshot = DemoTrainingHistoryGenerator.generate()
        val today = DemoTrainingHistoryGenerator.referenceDate
        val tables = snapshot.tables
        val sessions = aggregates(tables)
        val scheduled = tables.scheduledWorkouts.map { row ->
            val session = tables.workoutSessions.firstOrNull { it.scheduledWorkoutId == row.id }
            app.mymusclemap.domain.workout.ScheduledWorkout(
                id = row.id,
                scheduledDate = LocalDate.parse(row.scheduledDate),
                templateId = row.templateId,
                templateName = row.templateName,
                exerciseCount = 0,
                plannedSetCount = 0,
                templateArchived = false,
                sessionId = session?.id,
                sessionStatus = session?.status?.let(SessionStatus::valueOf),
                createdAt = row.createdAt,
                originalScheduledDate = LocalDate.parse(row.originalScheduledDate),
                cancelledAt = row.cancelledAt
            )
        }
        val weights = tables.weightMeasurements.map { it.toModel() }
        val inputs = ReportInputs(sessions, scheduled, weights)
        val historyStart = ReportHistory.earliestDate(
            ReportHistoryEvidence.fromPersisted(
                sessions = sessions.map { it.session },
                scheduled = scheduled,
                bodyWeights = weights
            ),
            today
        )
        assertEquals(LocalDate.of(2025, 3, 26), historyStart)

        val august = ReportLogic.summarize(
            ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 8, 1)),
            inputs,
            today,
            historyStart
        )
        assertEquals(ReportHistoryCoverage.Complete, august.coverage)
        assertEquals(31, august.volume.trend.size)
        assertTrue(august.activity.workoutCount > 0)
        assertEquals(august.volume.totalKg!!, august.volume.trend.sumOf { it.value }, 0.001)
        assertTrue(august.volume.trend.all { it.value >= 0.0 })
        assertTrue(august.adherence.plannedCount > 0)
        val augustPrevious = august.previous
        assertEquals(LocalDate.of(2026, 7, 1), augustPrevious!!.period.startInclusive)
        assertTrue(augustPrevious.workoutCountPercent != null)

        val july = ReportLogic.summarize(
            ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 7, 1)),
            inputs,
            today,
            historyStart
        )
        val breakDays = july.volume.trend.filter {
            !it.date.isBefore(LocalDate.of(2026, 7, 13)) && !it.date.isAfter(LocalDate.of(2026, 7, 26))
        }
        assertEquals(14, breakDays.size)
        assertTrue(breakDays.all { it.value == 0.0 })
        assertTrue(july.volume.trend.any { it.date.isBefore(LocalDate.of(2026, 7, 13)) && it.value > 0.0 })
        assertTrue(july.volume.trend.any { it.date.isAfter(LocalDate.of(2026, 7, 26)) && it.value > 0.0 })

        val year = ReportLogic.summarize(
            ReportPeriod.containing(ReportKind.Yearly, LocalDate.of(2025, 6, 1)),
            inputs,
            today,
            historyStart
        )
        assertEquals(ReportHistoryCoverage.Partial, year.coverage)
        assertEquals(LocalDate.of(2025, 1, 1), year.period.startInclusive)
        assertEquals(LocalDate.of(2025, 12, 31), year.period.endInclusive)
        assertEquals(12, year.volume.trend.size)
        assertEquals(0.0, year.volume.trend.single { it.date == LocalDate.of(2025, 1, 1) }.value, 0.0)
        assertEquals(0.0, year.volume.trend.single { it.date == LocalDate.of(2025, 2, 1) }.value, 0.0)
        assertTrue(year.volume.trend.single { it.date == LocalDate.of(2025, 3, 1) }.value > 0.0)
        assertNull(year.previous)
        assertEquals(year.volume.totalKg!!, year.volume.trend.sumOf { it.value }, 0.001)

        try {
            ReportLogic.summarize(
                ReportPeriod.containing(ReportKind.Yearly, LocalDate.of(2026, 6, 1)),
                inputs,
                today,
                historyStart
            )
            org.junit.Assert.fail("2026 is still open")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("closed"))
        }

        val openMonth = YearMonth.from(today).atDay(1)
        val late = scheduled.mapNotNull { occurrence ->
            if (occurrence.isCancelled || occurrence.sessionStatus != SessionStatus.COMPLETED) {
                return@mapNotNull null
            }
            val session = sessions.firstOrNull { it.session.id == occurrence.sessionId } ?: return@mapNotNull null
            if (!session.session.workoutDate.isBefore(openMonth)) return@mapNotNull null
            if (YearMonth.from(session.session.workoutDate) == YearMonth.from(occurrence.scheduledDate)) {
                null
            } else {
                occurrence to session
            }
        }
        assertTrue(late.isNotEmpty())
        val (occurrence, session) = late.first()
        val plannedMonth = ReportLogic.summarize(
            ReportPeriod.containing(ReportKind.Monthly, occurrence.scheduledDate),
            inputs,
            today,
            historyStart
        )
        val completedMonth = ReportLogic.summarize(
            ReportPeriod.containing(ReportKind.Monthly, session.session.workoutDate),
            inputs,
            today,
            historyStart
        )
        val plannedActivity = sessions.filter {
            it.session.status == SessionStatus.COMPLETED &&
                plannedMonth.period.contains(it.session.workoutDate)
        }
        assertFalse(plannedActivity.any { it.session.id == session.session.id })
        assertEquals(plannedActivity.size, plannedMonth.activity.workoutCount)
        assertTrue(completedMonth.period.contains(session.session.workoutDate))
        assertEquals(
            scheduled.count { item ->
                !item.isCancelled &&
                    plannedMonth.period.contains(item.scheduledDate) &&
                    item.sessionStatus == SessionStatus.COMPLETED
            },
            plannedMonth.adherence.completedCount
        )
    }

    private fun aggregates(
        tables: AppBackupTables
    ): List<WorkoutSessionAggregate> {
        val exercisesBySession = tables.workoutSessionExercises.groupBy { it.sessionId }
        val setsByExercise = tables.workoutSessionSets.groupBy { it.sessionExerciseId }
        val musclesByExercise = tables.workoutSessionExerciseMuscles.groupBy { it.sessionExerciseId }
        return tables.workoutSessions.map { session ->
            WorkoutSessionAggregate(
                session = session.toModel(),
                exercises = exercisesBySession[session.id].orEmpty().sortedBy { it.position }.map { exercise ->
                    SessionExerciseItem(
                        exercise = exercise.toModel(musclesByExercise[exercise.id].orEmpty()),
                        sets = setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.map { it.toModel() }
                    )
                }
            )
        }
    }
}
