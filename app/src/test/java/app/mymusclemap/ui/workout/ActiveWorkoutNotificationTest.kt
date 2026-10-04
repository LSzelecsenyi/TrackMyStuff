package app.mymusclemap.ui.workout

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainActivity
import app.mymusclemap.R
import app.mymusclemap.data.preferences.LockScreenSetCompletionStore
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.ActiveWorkoutNotificationBody
import app.mymusclemap.domain.workout.ActiveWorkoutNotificationLogic
import app.mymusclemap.domain.workout.ActiveWorkoutNotificationModel
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActiveWorkoutNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private lateinit var database: WeightDatabase
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var scope: CoroutineScope
    private lateinit var preferences: MemoryLockScreenStore
    private lateinit var coordinator: ActiveWorkoutNotificationCoordinator

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notifications).setNotificationsEnabled(true)
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
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
            database.workoutSessionDao()
        )
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(LocalDate.parse("2026-09-16"))
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        preferences = MemoryLockScreenStore()
        coordinator = ActiveWorkoutNotificationCoordinator(
            context = context,
            repository = sessions,
            preferences = preferences,
            scope = scope,
            notificationId = TEST_NOTIFICATION_ID
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun detailedNotificationCarriesTheCurrentSetAndADistinctCompleteAction() {
        val first = current(setId = 3L, reps = 8, weightKg = 15.0)
        val second = current(setId = 4L, reps = 6, weightKg = 15.0)
        val firstNotification = ActiveWorkoutNotifications.build(context, model(first))
        val secondNotification = ActiveWorkoutNotifications.build(context, model(second))
        assertEquals("Dips", firstNotification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(firstNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Set 3 of 4"))
        assertTrue(firstNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("8 reps"))
        assertTrue(firstNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("+15 kg"))
        assertEquals(NotificationCompat.CATEGORY_WORKOUT, firstNotification.category)
        assertEquals(Notification.VISIBILITY_PRIVATE, firstNotification.visibility)
        assertTrue(firstNotification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, firstNotification.actions.size)
        assertEquals(context.getString(R.string.notification_complete_set), firstNotification.actions[0].title)
        val firstAction = shadowOf(firstNotification.actions[0].actionIntent).savedIntent
        val secondAction = shadowOf(secondNotification.actions[0].actionIntent).savedIntent
        assertEquals(7L to 3L, ActiveWorkoutNotifications.completionIds(firstAction))
        assertEquals(7L to 4L, ActiveWorkoutNotifications.completionIds(secondAction))
        assertNotEquals(firstAction.data, secondAction.data)
        val content = shadowOf(firstNotification.contentIntent).savedIntent
        assertEquals(7L, content.getLongExtra(MainActivity.EXTRA_OPEN_ACTIVE_WORKOUT, -1L))
        val redacted = firstNotification.publicVersion
        assertFalse(redacted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Dips"))
        assertFalse(redacted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("15"))
        assertEquals(context.getString(R.string.notification_workout_in_progress), redacted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(1, redacted.actions.size)
        assertFalse(NotificationCompat.isRequestPromotedOngoing(firstNotification))
        assertFalse(NotificationCompat.isRequestPromotedOngoing(redacted))
        assertFalse(ActiveWorkoutNotifications.promotionAvailable(context))
    }

    @Test
    fun invalidSetAndFinishedLoggingOmitTheCompleteAction() {
        val needsInput = ActiveWorkoutNotifications.build(
            context,
            model(current(setId = 3L, reps = null, weightKg = null, completable = false))
        )
        assertNull(needsInput.actions)
        assertTrue(
            needsInput.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                .contains(context.getString(R.string.notification_open_to_enter_set))
        )
        val ready = ActiveWorkoutNotifications.build(
            context,
            ActiveWorkoutNotificationModel(7L, "Push", ActiveWorkoutNotificationBody.ReadyToFinish)
        )
        assertNull(ready.actions)
        assertEquals(
            context.getString(R.string.notification_all_sets_logged),
            ready.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            context.getString(R.string.notification_finish_in_app),
            ready.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        )
        assertFalse(NotificationCompat.isRequestPromotedOngoing(needsInput))
        assertFalse(NotificationCompat.isRequestPromotedOngoing(ready))
    }

    @Test
    fun requestingPromotionKeepsTheWorkoutNotificationAndDoesNotLeakThePublicVersion() {
        val detailed = ActiveWorkoutNotifications.build(
            context,
            model(current(setId = 3L, reps = 8, weightKg = 15.0)),
            requestPromotion = true
        )
        assertTrue(NotificationCompat.isRequestPromotedOngoing(detailed))
        assertEquals(NotificationCompat.CATEGORY_WORKOUT, detailed.category)
        assertEquals(Notification.VISIBILITY_PRIVATE, detailed.visibility)
        assertTrue(detailed.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, detailed.actions.size)
        assertEquals(context.getString(R.string.notification_complete_set), detailed.actions[0].title)
        val redacted = detailed.publicVersion
        assertFalse(NotificationCompat.isRequestPromotedOngoing(redacted))
        assertFalse(redacted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Dips"))
        assertFalse(redacted.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("15"))
        assertFalse(redacted.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("Dips"))

        val needsInput = ActiveWorkoutNotifications.build(
            context,
            model(current(setId = 3L, reps = null, weightKg = null, completable = false)),
            requestPromotion = true
        )
        assertTrue(NotificationCompat.isRequestPromotedOngoing(needsInput))
        assertNull(needsInput.actions)
        assertTrue(
            needsInput.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                .contains(context.getString(R.string.notification_open_to_enter_set))
        )

        val ready = ActiveWorkoutNotifications.build(
            context,
            ActiveWorkoutNotificationModel(7L, "Push", ActiveWorkoutNotificationBody.ReadyToFinish),
            requestPromotion = true
        )
        assertTrue(NotificationCompat.isRequestPromotedOngoing(ready))
        assertNull(ready.actions)
        assertEquals(
            context.getString(R.string.notification_all_sets_logged),
            ready.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        )
    }

    @Test
    fun unavailablePromotionStillPostsTheCompletableNotification() {
        val notification = ActiveWorkoutNotifications.build(
            context,
            model(current(setId = 3L, reps = 8, weightKg = 15.0)),
            requestPromotion = false
        )
        assertFalse(NotificationCompat.isRequestPromotedOngoing(notification))
        assertEquals(1, notification.actions.size)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun malformedActionIdsAreIgnored() {
        assertNull(ActiveWorkoutNotifications.completionIds(null))
        assertNull(ActiveWorkoutNotifications.completionIds(Intent("other")))
        assertNull(
            ActiveWorkoutNotifications.completionIds(
                Intent(ActiveWorkoutNotifications.ACTION_COMPLETE)
            )
        )
    }

    @Test
    fun coordinatorProjectsRoomAndAStaleActionDoesNotCompleteTheNextSet() {
        runBlocking {
            preferences.setEnabled(true)
            coordinator.start()
            val sessionId = startWorkout()
            val first = awaitNotification()
            val aggregate = sessions.getAggregate(sessionId)!!
            val model = ActiveWorkoutNotificationLogic.project(aggregate)!!
            val current = model.body as ActiveWorkoutNotificationBody.CurrentSet
            assertEquals(current.exerciseName, first.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(1, first.actions.size)
            coordinator.performAction(sessionId, current.setId)
            awaitText { text -> text.contains("Set ${current.setNumber + 1} of") }
            assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
            assertEquals(SessionSetStatus.COMPLETED, sessions.getAggregate(sessionId)!!.exercises.single().sets[0].status)
            coordinator.performAction(sessionId, current.setId)
            assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(sessionId)!!.exercises.single().sets[1].status)
            sessions.finish(sessionId, skipRemaining = true)
            awaitCleared()
            assertNull(shadowOf(notifications).getNotification(TEST_NOTIFICATION_ID))
        }
    }

    @Test
    fun deniedNotificationPermissionLeavesTheWorkoutUntouched() {
        runBlocking {
            preferences.setEnabled(true)
            shadowOf(notifications).setNotificationsEnabled(false)
            coordinator.start()
            val sessionId = startWorkout()
            withContext(Dispatchers.Default) {
                delay(100)
            }
            assertNull(shadowOf(notifications).getNotification(TEST_NOTIFICATION_ID))
            assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
            assertEquals(FinishWorkoutResult.Finished, sessions.finish(sessionId, skipRemaining = true))
        }
    }

    @Test
    fun featureOffSuppressesTheNotificationUntilTheUserEnablesIt() {
        runBlocking {
            coordinator.start()
            val sessionId = startWorkout()
            withContext(Dispatchers.Default) { delay(150) }
            assertNull(shadowOf(notifications).getNotification(TEST_NOTIFICATION_ID))
            assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
            preferences.setEnabled(true)
            val posted = awaitNotification()
            assertEquals(1, posted.actions.size)
            preferences.setEnabled(false)
            awaitCleared()
            assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
        }
    }

    @Test
    fun disabledWorkoutChannelDoesNotPostWhenTheFeatureIsOn() {
        runBlocking {
            preferences.setEnabled(true)
            val blocked = android.app.NotificationChannel(
                ActiveWorkoutNotifications.CHANNEL_ID,
                "Active workout",
                NotificationManager.IMPORTANCE_NONE
            )
            notifications.deleteNotificationChannel(ActiveWorkoutNotifications.CHANNEL_ID)
            notifications.createNotificationChannel(blocked)
            coordinator.start()
            val sessionId = startWorkout()
            withContext(Dispatchers.Default) { delay(150) }
            assertNull(shadowOf(notifications).getNotification(TEST_NOTIFICATION_ID))
            assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
            assertEquals(NotificationManager.IMPORTANCE_NONE, notifications.getNotificationChannel(ActiveWorkoutNotifications.CHANNEL_ID).importance)
        }
    }

    private suspend fun startWorkout(): Long {
        val exerciseId = (exercises.save(
            ExerciseDraft(
                name = "Dips",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.TRICEPS
            )
        ) as ExerciseSaveResult.Created).id
        val templateId = (templates.save(
            TemplateDraft(
                name = "Push",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = exerciseId,
                        sets = List(2) { index ->
                            PlannedSetDraft(
                                localId = -(index + 1L),
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        }
                    )
                )
            )
        ) as TemplateSaveResult.Created).id
        return (sessions.start(templateId) as StartWorkoutResult.Started).sessionId
    }

    private suspend fun awaitNotification(): Notification {
        return withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (true) {
                    val posted = shadowOf(notifications).getNotification(TEST_NOTIFICATION_ID)
                    if (posted != null) return@withTimeout posted
                    delay(20)
                }
                error("notification was not posted")
            }
        }
    }

    private suspend fun awaitText(match: (String) -> Boolean): Notification {
        return withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (true) {
                    val posted = shadowOf(notifications).getNotification(TEST_NOTIFICATION_ID)
                    val text = posted?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                    if (posted != null && match(text)) return@withTimeout posted
                    delay(20)
                }
                error("notification text did not update")
            }
        }
    }

    private suspend fun awaitCleared() {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (shadowOf(notifications).getNotification(TEST_NOTIFICATION_ID) != null) {
                    delay(20)
                }
            }
        }
    }

    private fun model(body: ActiveWorkoutNotificationBody.CurrentSet): ActiveWorkoutNotificationModel {
        return ActiveWorkoutNotificationModel(7L, "Push", body)
    }

    private fun current(
        setId: Long,
        reps: Int?,
        weightKg: Double?,
        completable: Boolean = true
    ): ActiveWorkoutNotificationBody.CurrentSet {
        return ActiveWorkoutNotificationBody.CurrentSet(
            setId = setId,
            exerciseName = "Dips",
            setNumber = 3,
            setCount = 4,
            reps = reps,
            loadKind = if (weightKg != null) PlannedLoadKind.ADDED_WEIGHT else null,
            weightKg = weightKg,
            durationSeconds = null,
            distanceMeters = null,
            completable = completable
        )
    }

    private companion object {
        const val TEST_NOTIFICATION_ID = 41002
    }
}

private class MemoryLockScreenStore : LockScreenSetCompletionStore {
    private val enabledState = MutableStateFlow(false)
    private val requestedState = MutableStateFlow(false)
    override val enabled = enabledState.asStateFlow()
    override val runtimePermissionRequested = requestedState.asStateFlow()
    override suspend fun setEnabled(enabled: Boolean) {
        enabledState.value = enabled
    }
    override suspend fun markRuntimePermissionRequested() {
        requestedState.value = true
    }
}
