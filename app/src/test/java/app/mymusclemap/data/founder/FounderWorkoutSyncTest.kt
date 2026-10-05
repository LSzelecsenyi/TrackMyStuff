package app.mymusclemap.data.founder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.auth.OkHttpStrictBackendApi
import app.mymusclemap.data.auth.StoredStrictSession
import app.mymusclemap.data.auth.StrictBearerToken
import app.mymusclemap.data.auth.StrictSessionStore
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class FounderWorkoutSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workoutId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val roomSessionId = 424242L
    private lateinit var outbox: FounderWorkoutOutbox

    @Before
    fun setUp() {
        outbox = FounderWorkoutOutbox(context)
        runBlocking { clearOutbox() }
    }

    @After
    fun tearDown() {
        runBlocking { clearOutbox() }
    }

    @Test
    fun freeCompletionDoesNotCallTheBackend() = runBlocking {
        var submissions = 0
        var signInRequests = 0
        val sync = sync(
            accepting = false,
            submit = {
                submissions += 1
                FounderWorkoutSubmission.Unavailable
            }
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(0, submissions)
        assertEquals(0, signInRequests)
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun enrolledQualifyingWorkoutStoresTheStableUuidAndNotTheRoomId() = runBlocking {
        val submitted = mutableListOf<PendingFounderWorkout>()
        var scheduled = 0
        val sync = sync(
            accepting = true,
            submit = {
                submitted += it
                FounderWorkoutSubmission.Accepted(snapshot(workouts = 1))
            },
            schedule = { scheduled += 1 }
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(1, scheduled)
        assertEquals(listOf(workoutId.toString()), outbox.pending().map { it.clientWorkoutId })
        assertFalse(outbox.pending().first().toString().contains(roomSessionId.toString()))
        assertEquals(FounderWorkoutFlush.Done, sync.flush())
        assertEquals(workoutId.toString(), submitted.single().clientWorkoutId)
        assertFalse(submitted.single().toString().contains(roomSessionId.toString()))
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun importedWorkoutIsNotQueued() = runBlocking {
        var submissions = 0
        val sync = sync(
            accepting = true,
            imported = true,
            submit = {
                submissions += 1
                FounderWorkoutSubmission.Unavailable
            }
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(0, submissions)
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun offlineCompletionStaysPendingAndANewStoreSeesIt() = runBlocking {
        val sync = sync(
            accepting = true,
            submit = { FounderWorkoutSubmission.Unavailable }
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(FounderWorkoutFlush.Retry, sync.flush())
        val restarted = FounderWorkoutOutbox(context)
        assertEquals(workoutId.toString(), restarted.pending().single().clientWorkoutId)
        assertEquals(1_700_000_000_000L, restarted.pending().single().completedAtEpochMilli)
    }

    @Test
    fun retrySendsTheSameUuidAndSuccessRemovesIt() = runBlocking {
        val submitted = mutableListOf<String>()
        val sync = sync(
            accepting = true,
            submit = {
                submitted += it.clientWorkoutId
                if (submitted.size == 1) {
                    FounderWorkoutSubmission.Unavailable
                } else {
                    FounderWorkoutSubmission.Accepted(snapshot(workouts = 1))
                }
            }
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(FounderWorkoutFlush.Retry, sync.flush())
        assertEquals(FounderWorkoutFlush.Done, sync.flush())
        assertEquals(listOf(workoutId.toString(), workoutId.toString()), submitted)
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun duplicateEnqueueDoesNotCreateASecondEvent() = runBlocking {
        val sync = sync(accepting = true, submit = { FounderWorkoutSubmission.Unavailable })
        sync.onNativeWorkoutCompleted(workoutId.toString())
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(1, outbox.pending().size)
    }

    @Test
    fun unauthenticatedResponseKeepsTheOutboxAndDoesNotSignIn() = runBlocking {
        var unauthenticated = 0
        var signInRequests = 0
        val sync = sync(
            accepting = true,
            submit = { FounderWorkoutSubmission.Unauthenticated },
            onUnauthenticated = { unauthenticated += 1 }
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(FounderWorkoutFlush.Done, sync.flush())
        assertEquals(1, unauthenticated)
        assertEquals(0, signInRequests)
        assertEquals(workoutId.toString(), outbox.pending().single().clientWorkoutId)
    }

    @Test
    fun acceptedSnapshotIsAppliedOnceWhenTheSameUuidIsFlushedAgain() = runBlocking {
        val counts = mutableListOf<Int>()
        val sync = sync(
            accepting = true,
            submit = { FounderWorkoutSubmission.Accepted(snapshot(workouts = 1)) },
            onAccepted = { counts += it.qualifyingWorkouts }
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        sync.flush()
        sync.flush()
        assertEquals(listOf(1), counts)
    }

    @Test
    fun httpSubmissionUsesTheUuidAndClearsTheBearerOn401() = runBlocking {
        val server = MockWebServer()
        val sessions = MemorySession()
        sessions.write(StoredStrictSession(StrictBearerToken("bearer-token"), "2026-11-01T00:00:00Z", UUID.randomUUID().toString()))
        val http = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errorCode":"UNAUTHENTICATED"}"""))
        try {
            val result = api.submitFounderWorkout(
                clientWorkoutId = workoutId.toString(),
                completedAt = Instant.ofEpochMilli(1_700_000_000_000L),
                localDate = java.time.LocalDate.parse("2026-10-04"),
                zone = ZoneOffset.UTC,
                observation = null
            )
            val recorded = server.takeRequest()
            val body = JSONObject(recorded.body.readUtf8())
            assertEquals("/api/v1/founder/workouts", recorded.path)
            assertEquals("Bearer bearer-token", recorded.getHeader("Authorization"))
            assertEquals(workoutId.toString(), body.getString("workoutId"))
            assertEquals("2026-10-04", body.getString("localDate"))
            assertFalse(body.toString().contains(roomSessionId.toString()))
            assertFalse(body.has("status"))
            assertFalse(body.has("founderLifetime"))
            assertEquals(FounderWorkoutSubmission.Unauthenticated, result)
            assertNull(sessions.read())
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test
    fun outboxKeepsTheFirstObservationAndReadsOlderRecords() = runBlocking {
        val first = FounderWorkoutObservation("Legs", 100, 3, 9, true, true)
        val replacement = FounderWorkoutObservation("Changed", 1, 1, 1, false, false)
        outbox.enqueue(PendingFounderWorkout(workoutId.toString(), 1_700_000_000_000L, "2026-10-04", first))
        outbox.enqueue(PendingFounderWorkout(workoutId.toString(), 1_700_000_000_000L, "2026-10-04", replacement))
        val stored = FounderWorkoutOutbox(context).pending().single()
        assertEquals(first, stored.observation)

        clearOutbox()
        outbox.enqueue(PendingFounderWorkout(workoutId.toString(), 1_700_000_000_000L, "2026-10-04"))
        assertNull(FounderWorkoutOutbox(context).pending().single().observation)
    }

    @Test
    fun retrySubmitsTheObservationFrozenAtEnqueue() = runBlocking {
        val frozen = FounderWorkoutObservation("Push", 90, 2, 4, true, false)
        val later = FounderWorkoutObservation("Changed", 1, 9, 9, false, true)
        var lookups = 0
        val seen = mutableListOf<FounderWorkoutObservation?>()
        val sync = FounderWorkoutSync(
            outbox = outbox,
            accepting = { true },
            lookup = {
                lookups += 1
                NativeFounderWorkout(
                    clientWorkoutId = workoutId.toString(),
                    completedAtEpochMilli = 1_700_000_000_000L,
                    localDate = "2026-10-04",
                    imported = false,
                    observation = if (lookups == 1) frozen else later
                )
            },
            submit = {
                seen += it.observation
                FounderWorkoutSubmission.Unavailable
            },
            onAccepted = {},
            onUnauthenticated = {}
        )
        sync.onNativeWorkoutCompleted(workoutId.toString())
        sync.onNativeWorkoutCompleted(workoutId.toString())
        assertEquals(FounderWorkoutFlush.Retry, sync.flush())
        assertEquals(listOf(frozen), seen)
        assertEquals(frozen, outbox.pending().single().observation)
    }

    @Test
    fun httpWorkoutPayloadCarriesObservationsWithoutSetDetails() = runBlocking {
        val server = MockWebServer()
        val sessions = MemorySession()
        sessions.write(StoredStrictSession(StrictBearerToken("bearer-token"), "2026-11-01T00:00:00Z", UUID.randomUUID().toString()))
        val http = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errorCode":"UNAUTHENTICATED"}"""))
        val observation = FounderWorkoutObservation("Push day", 1800, 4, 12, true, false)
        try {
            api.submitFounderWorkout(
                clientWorkoutId = workoutId.toString(),
                completedAt = Instant.ofEpochMilli(1_700_000_000_000L),
                localDate = java.time.LocalDate.parse("2026-10-04"),
                zone = ZoneOffset.UTC,
                observation = observation
            )
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("Push day", body.getString("displayName"))
            assertEquals(1800, body.getInt("durationSeconds"))
            assertEquals(4, body.getInt("exerciseCount"))
            assertEquals(12, body.getInt("completedSetCount"))
            assertTrue(body.getBoolean("fromTemplate"))
            assertFalse(body.getBoolean("usedExternalLoad"))
            assertFalse(body.has("reps"))
            assertFalse(body.has("weightKg"))
            assertFalse(body.has("exerciseName"))
            assertFalse(body.has("templateId"))
            assertFalse(body.has("notes"))
            assertFalse(body.has("status"))
            assertFalse(body.has("requiredWorkoutCount"))
            assertFalse(body.has("trainingComplete"))
            assertFalse(body.toString().contains(roomSessionId.toString()))
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test
    fun onlyActiveBackendEnrollmentAcceptsWorkouts() {
        assertTrue(FounderWorkoutSync.accepts(FounderProgramStatus.ActiveFree, backendOwned = true))
        assertTrue(FounderWorkoutSync.accepts(FounderProgramStatus.ActivePro, backendOwned = true))
        assertFalse(FounderWorkoutSync.accepts(FounderProgramStatus.ActiveFree, backendOwned = false))
        assertFalse(FounderWorkoutSync.accepts(FounderProgramStatus.PendingApproval, backendOwned = true))
        assertFalse(FounderWorkoutSync.accepts(FounderProgramStatus.Approved, backendOwned = true))
    }

    private fun sync(
        accepting: Boolean,
        imported: Boolean = false,
        submit: suspend (PendingFounderWorkout) -> FounderWorkoutSubmission,
        schedule: () -> Unit = {},
        onAccepted: suspend (BackendFounderSnapshot) -> Unit = {},
        onUnauthenticated: suspend () -> Unit = {}
    ): FounderWorkoutSync {
        return FounderWorkoutSync(
            outbox = outbox,
            accepting = { accepting },
            lookup = {
                NativeFounderWorkout(
                    clientWorkoutId = workoutId.toString(),
                    completedAtEpochMilli = 1_700_000_000_000L,
                    localDate = "2026-10-04",
                    imported = imported
                )
            },
            submit = submit,
            onAccepted = onAccepted,
            onUnauthenticated = onUnauthenticated,
            schedule = schedule
        )
    }

    private fun snapshot(workouts: Int): BackendFounderSnapshot {
        return BackendFounderSnapshot(
            status = if (workouts >= 1) FounderProgramStatus.ActivePro else FounderProgramStatus.ActiveFree,
            enrolledOn = java.time.LocalDate.parse("2026-10-01"),
            deadline = java.time.LocalDate.parse("2026-11-15"),
            qualifyingWorkouts = workouts,
            distinctWorkoutDays = 1,
            feedbackSubmitted = false,
            reportSubmitted = false,
            requiredWorkouts = 2,
            requiredDistinctDays = 1,
            temporaryProRequiredWorkouts = 1,
            temporaryProActive = workouts >= 1
        )
    }

    private suspend fun clearOutbox() {
        outbox.pending().forEach { outbox.remove(it.clientWorkoutId) }
    }

    private class MemorySession : StrictSessionStore {
        private var current: StoredStrictSession? = null
        override fun read(): StoredStrictSession? = current
        override fun write(session: StoredStrictSession) {
            current = session
        }
        override fun clear() {
            current = null
        }
    }
}
