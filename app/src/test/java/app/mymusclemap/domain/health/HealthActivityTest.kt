package app.mymusclemap.domain.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HealthActivityTest {
    private val today = LocalDate.of(2026, 9, 29)
    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun classifierMatchesOfficialExerciseConstants() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            HealthExerciseClassifier.STRENGTH_TRAINING
        )
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING, HealthExerciseClassifier.WEIGHTLIFTING)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS, HealthExerciseClassifier.CALISTHENICS)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, HealthExerciseClassifier.BIKING)
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            HealthExerciseClassifier.BIKING_STATIONARY
        )
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, HealthExerciseClassifier.RUNNING)
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            HealthExerciseClassifier.RUNNING_TREADMILL
        )
        assertEquals(70, HealthExerciseClassifier.STRENGTH_TRAINING)
        assertEquals(81, HealthExerciseClassifier.WEIGHTLIFTING)
        assertEquals(13, HealthExerciseClassifier.CALISTHENICS)
        assertEquals(8, HealthExerciseClassifier.BIKING)
        assertEquals(9, HealthExerciseClassifier.BIKING_STATIONARY)
        assertEquals(56, HealthExerciseClassifier.RUNNING)
        assertEquals(57, HealthExerciseClassifier.RUNNING_TREADMILL)
    }

    @Test
    fun includedExerciseTypesClassifyAndExcludedTypesStayOther() {
        assertEquals(HealthExerciseKind.STRENGTH, HealthExerciseClassifier.classify(70))
        assertEquals(HealthExerciseKind.STRENGTH, HealthExerciseClassifier.classify(81))
        assertEquals(HealthExerciseKind.STRENGTH, HealthExerciseClassifier.classify(13))
        assertEquals(HealthExerciseKind.CYCLING, HealthExerciseClassifier.classify(8))
        assertEquals(HealthExerciseKind.CYCLING, HealthExerciseClassifier.classify(9))
        assertEquals(HealthExerciseKind.RUNNING, HealthExerciseClassifier.classify(56))
        assertEquals(HealthExerciseKind.RUNNING, HealthExerciseClassifier.classify(57))
        listOf(0, 10, 11, 36, 48, 71, 79, 82, 83).forEach { type ->
            assertEquals(HealthExerciseKind.OTHER, HealthExerciseClassifier.classify(type))
        }
    }

    @Test
    fun sessionsAggregateByStartDateAndIgnoreOtherAndFuture() {
        val day = LocalDate.of(2026, 9, 28)
        val sessions = listOf(
            session(day, 18, 0, 45, HealthExerciseKind.STRENGTH),
            session(day, 19, 0, 15, HealthExerciseKind.STRENGTH),
            session(day, 7, 40, 32, HealthExerciseKind.RUNNING),
            session(day, 12, 0, 15, HealthExerciseKind.OTHER),
            session(day.plusDays(2), 8, 0, 30, HealthExerciseKind.CYCLING),
            session(day.minusDays(40), 8, 0, 30, HealthExerciseKind.CYCLING)
        )
        val daily = HealthExerciseAggregator.daily(sessions, today)
        val row = daily.single()
        assertEquals(day, row.date)
        assertEquals(2, row.strength.sessions)
        assertEquals(Duration.ofMinutes(60), row.strength.duration)
        assertEquals(1, row.running.sessions)
        assertEquals(Duration.ofMinutes(32), row.running.duration)
        assertEquals(0, row.cycling.sessions)
    }

    @Test
    fun exerciseThatCrossesMidnightStaysOnTheStartDate() {
        val start = LocalDate.of(2026, 9, 28).atTime(23, 30).toInstant(ZoneOffset.UTC)
        val daily = HealthExerciseAggregator.daily(
            listOf(
                HealthExerciseSession(
                    start,
                    start.plus(Duration.ofHours(1)),
                    utc,
                    HealthExerciseKind.RUNNING
                )
            ),
            today
        )
        val row = daily.single()
        assertEquals(LocalDate.of(2026, 9, 28), row.date)
        assertEquals(Duration.ofHours(1), row.running.duration)
    }

    @Test
    fun exerciseUsesTheSessionZoneForTheDate() {
        val instant = Instant.parse("2026-09-28T23:30:00Z")
        val daily = HealthExerciseAggregator.daily(
            listOf(
                HealthExerciseSession(
                    instant,
                    instant.plus(Duration.ofMinutes(20)),
                    ZoneId.of("Pacific/Kiritimati"),
                    HealthExerciseKind.CYCLING
                )
            ),
            today
        )
        assertEquals(LocalDate.of(2026, 9, 29), daily.single().date)
    }

    @Test
    fun hrvAveragesMultipleSamplesAndOmitsMissingDays() {
        val day = LocalDate.of(2026, 9, 26)
        val daily = HealthHrvAggregator.daily(
            listOf(
                sample(day, 7, 0, 40.0),
                sample(day, 8, 0, 50.0),
                sample(day, 9, 0, 0.5),
                sample(day.plusDays(4), 8, 0, 47.0)
            ),
            today
        )
        assertEquals(1, daily.size)
        assertEquals(day, daily.single().date)
        assertEquals(45.0, daily.single().millis, 0.0)
    }

    @Test
    fun hrvUsesTheSampleZoneAndDropsFutureValues() {
        val instant = Instant.parse("2026-09-28T23:30:00Z")
        val daily = HealthHrvAggregator.daily(
            listOf(
                HealthHrvSample(instant, ZoneId.of("Pacific/Kiritimati"), 47.0),
                HealthHrvSample(Instant.parse("2026-09-30T01:00:00Z"), utc, 41.0)
            ),
            today
        )
        assertEquals(LocalDate.of(2026, 9, 29), daily.single().date)
        assertEquals(47.0, daily.single().millis, 0.0)
    }

    @Test
    fun sleepCrossingMidnightSplitsAcrossLocalDates() {
        val start = LocalDate.of(2026, 9, 28).atTime(22, 0).toInstant(ZoneOffset.UTC)
        val daily = HealthSleepAggregator.daily(
            listOf(HealthSleepSpan(start, start.plus(Duration.ofHours(8)), utc)),
            today
        )
        assertEquals(Duration.ofHours(2), daily.first { it.date == LocalDate.of(2026, 9, 28) }.duration)
        assertEquals(Duration.ofHours(6), daily.first { it.date == LocalDate.of(2026, 9, 29) }.duration)
    }

    @Test
    fun sleepEndingAtMidnightDoesNotAddTheNextDay() {
        val start = LocalDate.of(2026, 9, 28).atTime(22, 0).toInstant(ZoneOffset.UTC)
        val end = LocalDate.of(2026, 9, 29).atStartOfDay().toInstant(ZoneOffset.UTC)
        val daily = HealthSleepAggregator.daily(listOf(HealthSleepSpan(start, end, utc)), today)
        assertEquals(listOf(LocalDate.of(2026, 9, 28)), daily.map { it.date })
        assertEquals(Duration.ofHours(2), daily.single().duration)
    }

    @Test
    fun multipleSleepSpansOnOneDaySumAndAMissingDayStaysAbsent() {
        val day = LocalDate.of(2026, 9, 27)
        val first = day.atTime(0, 20).toInstant(ZoneOffset.UTC)
        val second = day.atTime(13, 0).toInstant(ZoneOffset.UTC)
        val daily = HealthSleepAggregator.daily(
            listOf(
                HealthSleepSpan(first, first.plus(Duration.ofHours(6)), utc),
                HealthSleepSpan(second, second.plus(Duration.ofMinutes(30)), utc)
            ),
            today
        )
        assertEquals(Duration.ofMinutes(390), daily.single().duration)
        assertTrue(daily.none { it.date == LocalDate.of(2026, 9, 26) })
    }

    @Test
    fun futureSleepBeyondTodayIsDropped() {
        val start = LocalDate.of(2026, 9, 28).atTime(22, 0).toInstant(ZoneOffset.UTC)
        val daily = HealthSleepAggregator.daily(
            listOf(HealthSleepSpan(start, start.plus(Duration.ofHours(8)), utc)),
            LocalDate.of(2026, 9, 28)
        )
        assertEquals(listOf(LocalDate.of(2026, 9, 28)), daily.map { it.date })
        assertEquals(Duration.ofHours(2), daily.single().duration)
    }

    @Test
    fun sleepUsesTheSpanZoneAtATimezoneBoundary() {
        val instant = Instant.parse("2026-09-28T23:30:00Z")
        val daily = HealthSleepAggregator.daily(
            listOf(
                HealthSleepSpan(
                    instant,
                    instant.plus(Duration.ofHours(2)),
                    ZoneId.of("Pacific/Kiritimati")
                )
            ),
            today
        )
        assertEquals(LocalDate.of(2026, 9, 29), daily.single().date)
        assertEquals(Duration.ofHours(2), daily.single().duration)
    }

    private fun session(
        date: LocalDate,
        hour: Int,
        minute: Int,
        minutes: Long,
        kind: HealthExerciseKind
    ): HealthExerciseSession {
        val start = date.atTime(hour, minute).toInstant(ZoneOffset.UTC)
        return HealthExerciseSession(start, start.plus(Duration.ofMinutes(minutes)), utc, kind)
    }

    private fun sample(date: LocalDate, hour: Int, minute: Int, millis: Double): HealthHrvSample {
        return HealthHrvSample(date.atTime(hour, minute).toInstant(ZoneOffset.UTC), utc, millis)
    }
}
