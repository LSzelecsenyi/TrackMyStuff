package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.ScheduledWorkoutRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.AbandonWorkoutResult
import hu.laca.weighttracker.domain.workout.DeleteWorkoutResult
import hu.laca.weighttracker.domain.workout.FinishWorkoutResult
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.RescheduleWorkoutResult
import hu.laca.weighttracker.domain.workout.ScheduleWorkoutResult
import hu.laca.weighttracker.domain.workout.ScheduledWorkoutStatus
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateDeleteResult
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import hu.laca.weighttracker.domain.workout.UnscheduleWorkoutResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ScheduledWorkoutRepositoryRoomTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var scheduled: ScheduledWorkoutRepository
    private val today = LocalDate.parse("2026-09-15")
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock,
            database.workoutSessionDao(),
            database.scheduledWorkoutDao()
        )
        val weights = WeightRepository(database.weightMeasurementDao(), clock)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            weights,
            clock,
            FixedDateProvider(today)
        )
        scheduled = scheduler(1_000L)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenActiveTemplateWhenScheduledThenItIsStoredForThatDay() = runTest {
        val templateId = savePush("Push A")
        val result = scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled
        val stored = scheduled.getById(result.id)!!
        assertEquals(today, stored.scheduledDate)
        assertEquals(templateId, stored.templateId)
        assertEquals("Push A", stored.templateName)
        assertEquals(1, stored.exerciseCount)
        assertEquals(4, stored.plannedSetCount)
        assertFalse(stored.templateArchived)
        assertNull(stored.sessionId)
        assertEquals(ScheduledWorkoutStatus.PLANNED, stored.status)
        assertEquals(listOf(result.id), scheduled.observeOnDate(today).first().map { it.id })
    }

    @Test
    fun givenTwoTemplatesWhenScheduledOnSameDayThenBothAreKept() = runTest {
        val push = savePush("Push A")
        val pull = savePush("Pull A")
        scheduled.schedule(push, today)
        scheduled.schedule(pull, today)
        val day = scheduled.observeOnDate(today).first()
        assertEquals(listOf("Pull A", "Push A").sorted(), day.map { it.templateName }.sorted())
        assertEquals(2, day.size)
    }

    @Test
    fun givenSameTemplateAndDayWhenScheduledAgainThenDuplicateIsRejected() = runTest {
        val templateId = savePush("Push A")
        assertTrue(scheduled.schedule(templateId, today) is ScheduleWorkoutResult.Scheduled)
        assertEquals(ScheduleWorkoutResult.Duplicate, scheduled.schedule(templateId, today))
        assertEquals(1, scheduled.observeOnDate(today).first().size)
    }

    @Test
    fun givenSameTemplateWhenScheduledOnDifferentDaysThenBothExist() = runTest {
        val templateId = savePush("Push A")
        val first = scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled
        val second = scheduled.schedule(templateId, today.plusDays(1)) as ScheduleWorkoutResult.Scheduled
        assertEquals(today, scheduled.getById(first.id)!!.scheduledDate)
        assertEquals(today.plusDays(1), scheduled.getById(second.id)!!.scheduledDate)
    }

    @Test
    fun givenMissingTemplateWhenScheduledThenItIsRejected() = runTest {
        assertEquals(ScheduleWorkoutResult.TemplateNotFound, scheduled.schedule(999L, today))
    }

    @Test
    fun givenArchivedTemplateWhenScheduledThenItIsRejected() = runTest {
        val templateId = savePush("Push A")
        assertTrue(templates.archive(templateId))
        assertEquals(ScheduleWorkoutResult.TemplateArchived, scheduled.schedule(templateId, today))
        assertTrue(scheduled.observeOnDate(today).first().isEmpty())
    }

    @Test
    fun givenExistingScheduleWhenTemplateIsArchivedThenScheduleRemainsReadableAndRemovable() = runTest {
        val templateId = savePush("Push A")
        val id = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        assertTrue(templates.archive(templateId))
        val stored = scheduled.getById(id)!!
        assertTrue(stored.templateArchived)
        assertEquals(ScheduledWorkoutStatus.PLANNED, stored.status)
        assertEquals(UnscheduleWorkoutResult.Removed, scheduled.unschedule(id))
        assertNull(scheduled.getById(id))
    }

    @Test
    fun givenFreeScheduleWhenMovedThenDateUpdates() = runTest {
        val templateId = savePush("Push A")
        val id = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        assertEquals(RescheduleWorkoutResult.Moved, scheduled.reschedule(id, today.minusDays(3)))
        val stored = scheduled.getById(id)!!
        assertEquals(today.minusDays(3), stored.scheduledDate)
        assertTrue(scheduled.observeOnDate(today).first().isEmpty())
        assertEquals(listOf(id), scheduled.observeOnDate(today.minusDays(3)).first().map { it.id })
    }

    @Test
    fun givenMoveToOccupiedDayWhenRescheduledThenDuplicateIsRejected() = runTest {
        val templateId = savePush("Push A")
        val first = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val otherDay = today.plusDays(2)
        scheduled.schedule(templateId, otherDay)
        assertEquals(RescheduleWorkoutResult.Duplicate, scheduled.reschedule(first, otherDay))
        assertEquals(today, scheduled.getById(first)!!.scheduledDate)
    }

    @Test
    fun givenFreeScheduleWhenRemovedThenItDisappears() = runTest {
        val templateId = savePush("Push A")
        val id = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        assertEquals(UnscheduleWorkoutResult.Removed, scheduled.unschedule(id))
        assertNull(scheduled.getById(id))
        assertTrue(scheduled.observeOnDate(today).first().isEmpty())
    }

    @Test
    fun givenScheduleWhenStartedThenSessionReceivesScheduledWorkoutId() = runTest {
        val templateId = savePush("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        assertEquals(StartWorkoutResult.ScheduleNotFound, sessions.start(templateId, 999_999L))
        val started = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        val session = sessions.getAggregate(started.sessionId)!!.session
        assertEquals(scheduleId, session.scheduledWorkoutId)
        val stored = scheduled.getById(scheduleId)!!
        assertEquals(started.sessionId, stored.sessionId)
        assertEquals(SessionStatus.IN_PROGRESS, stored.sessionStatus)
        assertEquals(ScheduledWorkoutStatus.IN_PROGRESS, stored.status)
        assertEquals(StartWorkoutResult.AlreadyActive, sessions.start(savePush("Other")))
    }

    @Test
    fun givenStartedScheduleWhenStartedAgainThenSecondSessionIsRejected() = runTest {
        val templateId = savePush("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val first = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        assertEquals(StartWorkoutResult.AlreadyActive, sessions.start(templateId, scheduleId))
        assertEquals(AbandonWorkoutResult.Abandoned, sessions.abandon(first.sessionId))
        val second = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(second.sessionId, skipRemaining = true))
        assertEquals(StartWorkoutResult.ScheduleAlreadyStarted, sessions.start(templateId, scheduleId))
        assertEquals(1, database.workoutSessionDao().observeAll().first().count { it.scheduledWorkoutId == scheduleId })
    }

    @Test
    fun givenScheduleWhenStartedWithOtherTemplateThenMismatchIsRejected() = runTest {
        val push = savePush("Push A")
        val pull = savePush("Pull A")
        val scheduleId = (scheduled.schedule(push, today) as ScheduleWorkoutResult.Scheduled).id
        assertEquals(StartWorkoutResult.ScheduleTemplateMismatch, sessions.start(pull, scheduleId))
        assertNull(sessions.observeInProgress().first())
        assertEquals(ScheduledWorkoutStatus.PLANNED, scheduled.getById(scheduleId)!!.status)
    }

    @Test
    fun givenSessionDeletedWhenScheduleRemainsThenItCanBeStartedAgain() = runTest {
        val templateId = savePush("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val started = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        assertEquals(AbandonWorkoutResult.Abandoned, sessions.abandon(started.sessionId))
        val afterAbandon = scheduled.getById(scheduleId)!!
        assertNull(afterAbandon.sessionId)
        assertEquals(ScheduledWorkoutStatus.PLANNED, afterAbandon.status)
        val restarted = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(restarted.sessionId, skipRemaining = true))
        assertEquals(ScheduledWorkoutStatus.COMPLETED, scheduled.getById(scheduleId)!!.status)
        assertEquals(DeleteWorkoutResult.Deleted, sessions.deleteWorkout(restarted.sessionId))
        val afterDelete = scheduled.getById(scheduleId)!!
        assertNull(afterDelete.sessionId)
        assertEquals(ScheduledWorkoutStatus.PLANNED, afterDelete.status)
        val third = sessions.start(templateId, scheduleId)
        assertTrue(third is StartWorkoutResult.Started)
    }

    @Test
    fun givenPastOrFutureScheduleWhenStartedThenItIsRejectedWithoutASession() = runTest {
        val templateId = savePush("Push A")
        val pastId = (scheduled.schedule(templateId, today.minusDays(1)) as ScheduleWorkoutResult.Scheduled).id
        val futureTemplate = savePush("Jövő")
        val futureId = (scheduled.schedule(futureTemplate, today.plusDays(1)) as ScheduleWorkoutResult.Scheduled).id
        assertEquals(StartWorkoutResult.ScheduleNotOnToday, sessions.start(templateId, pastId))
        assertEquals(StartWorkoutResult.ScheduleNotOnToday, sessions.start(futureTemplate, futureId))
        assertNull(sessions.observeInProgress().first())
        assertEquals(ScheduledWorkoutStatus.PLANNED, scheduled.getById(pastId)!!.status)
        assertEquals(ScheduledWorkoutStatus.PLANNED, scheduled.getById(futureId)!!.status)
    }

    @Test
    fun givenTwoConcurrentStartsWhenSameScheduleThenOnlyOneSessionExists() = runTest {
        val templateId = savePush("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val results = coroutineScope {
            val first = async { sessions.start(templateId, scheduleId) }
            val second = async { sessions.start(templateId, scheduleId) }
            listOf(first.await(), second.await())
        }
        assertEquals(1, results.count { it is StartWorkoutResult.Started })
        assertTrue(results.any { it is StartWorkoutResult.AlreadyActive || it is StartWorkoutResult.ScheduleAlreadyStarted })
        assertEquals(1, database.workoutSessionDao().observeAll().first().count { it.scheduledWorkoutId == scheduleId })
        assertEquals(scheduleId, sessions.observeInProgress().first()!!.session.scheduledWorkoutId)
    }

    @Test
    fun givenArchivedThenRestoredTemplateWhenScheduleRemainsThenItCanStart() = runTest {
        val templateId = savePush("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        assertTrue(templates.archive(templateId))
        assertEquals(StartWorkoutResult.TemplateArchived, sessions.start(templateId, scheduleId))
        assertTrue(templates.restore(templateId))
        val started = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        assertEquals(scheduleId, sessions.getAggregate(started.sessionId)!!.session.scheduledWorkoutId)
    }

    @Test
    fun givenDstTransitionDateWhenScheduledThenIsoCalendarDayIsStored() = runTest {
        val templateId = savePush("Push A")
        val dstDay = LocalDate.of(2026, 3, 29)
        val id = (scheduled.schedule(templateId, dstDay) as ScheduleWorkoutResult.Scheduled).id
        assertEquals(dstDay, scheduled.getById(id)!!.scheduledDate)
        assertEquals("2026-03-29", database.scheduledWorkoutDao().getEntity(id)!!.scheduledDate)
    }

    @Test
    fun givenNormalStartWhenNoScheduleThenScheduledWorkoutIdStaysNull() = runTest {
        val templateId = savePush("Push A")
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        assertNull(sessions.getAggregate(started.sessionId)!!.session.scheduledWorkoutId)
        assertTrue(scheduled.observeOnDate(today).first().isEmpty())
    }

    @Test
    fun givenLinkedScheduleWhenMovedOrRemovedThenItIsBlocked() = runTest {
        val templateId = savePush("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        assertEquals(RescheduleWorkoutResult.LinkedToSession, scheduled.reschedule(scheduleId, today.plusDays(1)))
        assertEquals(UnscheduleWorkoutResult.LinkedToSession, scheduled.unschedule(scheduleId))
        assertEquals(today, scheduled.getById(scheduleId)!!.scheduledDate)
        assertEquals(TemplateDeleteResult.BlockedByReferences, templates.deletePermanently(templateId))
    }

    @Test
    fun givenFlowsWhenObservedThenItemsAreOrderedByDateThenCreatedAtThenId() = runTest {
        val firstTemplate = savePush("Alpha")
        val secondTemplate = savePush("Beta")
        val thirdTemplate = savePush("Gamma")
        val late = scheduler(3_000L).schedule(thirdTemplate, today.plusDays(1)) as ScheduleWorkoutResult.Scheduled
        val second = scheduler(2_000L).schedule(secondTemplate, today) as ScheduleWorkoutResult.Scheduled
        val first = scheduler(1_000L).schedule(firstTemplate, today) as ScheduleWorkoutResult.Scheduled
        val ordered = scheduled.observeBetween(today, today.plusDays(1)).first()
        assertEquals(listOf(first.id, second.id, late.id), ordered.map { it.id })
        val upcoming = scheduled.observeUpcoming(today).first()
        assertEquals(listOf(first.id, second.id, late.id), upcoming.map { it.id })
        val day = scheduled.observeOnDate(today).first()
        assertEquals(listOf(first.id, second.id), day.map { it.id })
        assertEquals(listOf(1_000L, 2_000L), day.map { it.createdAt })
    }

    @Test
    fun givenCompletedScheduledSessionThenJournalHeatmapAndCountsStillWork() = runTest {
        val templateId = savePush("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val started = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        val summaries = sessions.observeSummariesOnDate(today).first()
        assertEquals(1, summaries.size)
        assertEquals(scheduleId, summaries.single().session.scheduledWorkoutId)
        val counts = sessions.observeCompletedCounts(today, today).first()
        assertEquals(1, counts[today])
        val heatmap = sessions.observeHeatmapExercises().first()
        assertTrue(heatmap.isNotEmpty())
        assertEquals(started.sessionId, sessions.observeLatestCompleted().first()!!.session.id)
        assertNull(sessions.observeInProgress().first())
    }

    private fun scheduler(createdAtMillis: Long): ScheduledWorkoutRepository {
        return ScheduledWorkoutRepository(
            database.scheduledWorkoutDao(),
            database.workoutTemplateDao(),
            database.workoutSessionDao(),
            Clock.fixed(Instant.ofEpochMilli(createdAtMillis), ZoneOffset.UTC)
        )
    }

    private suspend fun savePush(name: String): Long {
        val created = exercises.save(
            ExerciseDraft(
                name = "Húzódzkodás $name",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created
        val draft = TemplateDraft(
            name = name,
            exercises = listOf(
                TemplateExerciseDraft(
                    localId = -1L,
                    exerciseId = created.id,
                    sets = List(4) { index ->
                        PlannedSetDraft(
                            localId = -(index + 1L),
                            minRepsText = "8",
                            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                        )
                    }
                )
            )
        )
        return (templates.save(draft) as TemplateSaveResult.Created).id
    }
}
