package app.mymusclemap.data.founder

import app.mymusclemap.data.auth.GoogleIdTokenRequest
import app.mymusclemap.data.auth.GoogleIdTokenValue
import app.mymusclemap.data.auth.GoogleIdentityProvider
import app.mymusclemap.data.auth.OkHttpStrictBackendApi
import app.mymusclemap.data.auth.StoredStrictSession
import app.mymusclemap.data.auth.StrictAuthRepository
import app.mymusclemap.data.auth.StrictBearerToken
import app.mymusclemap.data.auth.StrictSessionStore
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class FounderJoinCoordinatorTest {
    private val userId = "11111111-1111-4111-8111-111111111111"
    private val zone = ZoneOffset.UTC

    @Test
    fun joinWithoutASessionStartsGoogleThenEnrollsWithTheBearer() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")))
        withJoin(google) { server, sessions, join, applied ->
            server.enqueue(sessionResponse("opaque-bearer-token"))
            server.enqueue(founderResponse())
            assertEquals(FounderJoinResult.Enrolled, join.join())
            assertEquals(1, google.calls)
            val login = server.takeRequest()
            assertEquals("/api/v1/auth/google", login.path)
            assertNull(login.getHeader("Authorization"))
            val enrollment = server.takeRequest()
            assertEnrollment(enrollment, "opaque-bearer-token")
            assertEquals("opaque-bearer-token", sessions.read()?.accessToken?.value)
            assertEquals(FounderProgramStatus.ActiveFree, applied.single().status)
            assertEquals(LocalDate.of(2026, 6, 1), applied.single().enrolledOn)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun cancelledGoogleAuthDoesNotEnroll() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Cancelled)
        withJoin(google) { server, sessions, join, applied ->
            assertEquals(FounderJoinResult.Cancelled, join.join())
            assertEquals(1, google.calls)
            assertEquals(0, server.requestCount)
            assertNull(sessions.read())
            assertTrue(applied.isEmpty())
        }
    }

    @Test
    fun failedGoogleAuthDoesNotEnroll() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Failed)
        withJoin(google) { server, sessions, join, applied ->
            assertEquals(FounderJoinResult.GoogleFailed, join.join())
            assertEquals(0, server.requestCount)
            assertNull(sessions.read())
            assertTrue(applied.isEmpty())
        }
    }

    @Test
    fun backendLoginFailureDoesNotEnroll() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")))
        withJoin(google) { server, sessions, join, applied ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"errorCode":"INVALID_GOOGLE_TOKEN","message":"The Google sign-in could not be verified."}""")
            )
            assertEquals(FounderJoinResult.Rejected, join.join())
            assertEquals("/api/v1/auth/google", server.takeRequest().path)
            assertEquals(1, server.requestCount)
            assertNull(sessions.read())
            assertTrue(applied.isEmpty())
        }
    }

    @Test
    fun enrollmentFailureKeepsTheSessionAndDoesNotWriteLocalFounderState() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")))
        withJoin(google) { server, sessions, join, applied ->
            server.enqueue(sessionResponse("kept-bearer"))
            server.enqueue(MockResponse().setResponseCode(500))
            assertEquals(FounderJoinResult.Rejected, join.join())
            assertEquals("kept-bearer", sessions.read()?.accessToken?.value)
            assertTrue(applied.isEmpty())
            assertFalse(server.takeRequest().path!!.contains("workout"))
            assertFalse(server.takeRequest().path!!.contains("workout"))
        }
    }

    @Test
    fun enrollmentTimeoutKeepsTheSession() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")))
        withJoin(google, callTimeoutMillis = 200) { server, sessions, join, applied ->
            server.enqueue(sessionResponse("kept-bearer"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(FounderJoinResult.Unavailable, join.join())
            assertEquals("kept-bearer", sessions.read()?.accessToken?.value)
            assertTrue(applied.isEmpty())
        }
    }

    @Test
    fun anExistingValidSessionIsReusedWithoutGoogle() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")))
        withJoin(google) { server, sessions, join, applied ->
            sessions.write(stored("existing-bearer"))
            server.enqueue(MockResponse().setBody("""{"id":"$userId"}"""))
            server.enqueue(founderResponse(status = "ACTIVE_PRO", workouts = 7, days = 4))
            assertEquals(FounderJoinResult.Enrolled, join.join())
            assertEquals(0, google.calls)
            assertEquals("/api/v1/me", server.takeRequest().path)
            assertEnrollment(server.takeRequest(), "existing-bearer")
            assertEquals(FounderProgramStatus.ActivePro, applied.single().status)
            assertEquals(7, applied.single().qualifyingWorkouts)
            assertEquals(4, applied.single().distinctWorkoutDays)
        }
    }

    @Test
    fun aRejectedBearerIsClearedAndGoogleCanReplaceIt() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")))
        withJoin(google) { server, sessions, join, applied ->
            sessions.write(stored("stale-bearer"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errorCode":"UNAUTHENTICATED"}"""))
            server.enqueue(sessionResponse("fresh-bearer"))
            server.enqueue(founderResponse())
            assertEquals(FounderJoinResult.Enrolled, join.join())
            assertEquals(1, google.calls)
            assertEquals("/api/v1/me", server.takeRequest().path)
            assertEquals("/api/v1/auth/google", server.takeRequest().path)
            assertEnrollment(server.takeRequest(), "fresh-bearer")
            assertEquals("fresh-bearer", sessions.read()?.accessToken?.value)
            assertEquals(1, applied.size)
        }
    }

    @Test
    fun enrollmentUnauthorizedAfterLoginDoesNotRetryGoogleForever() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")))
        withJoin(google) { server, sessions, join, applied ->
            server.enqueue(sessionResponse("short-lived"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errorCode":"UNAUTHENTICATED"}"""))
            assertEquals(FounderJoinResult.Rejected, join.join())
            assertEquals(1, google.calls)
            assertNull(sessions.read())
            assertTrue(applied.isEmpty())
            assertEquals("/api/v1/auth/google", server.takeRequest().path)
            assertEquals("/api/v1/founder/enrollment", server.takeRequest().path)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun anExistingBackendEnrollmentIsAppliedAsReturned() = runBlocking {
        val google = CountingGoogle(GoogleIdTokenRequest.Cancelled)
        withJoin(google) { server, sessions, join, applied ->
            sessions.write(stored("existing-bearer"))
            val body = founderResponse(
                status = "PENDING_APPROVAL",
                enrolledAt = "2026-05-01T00:00:00Z",
                deadlineAt = "2026-06-15T00:00:00Z",
                workouts = 10,
                days = 6
            )
            server.enqueue(MockResponse().setBody("""{"id":"$userId"}"""))
            server.enqueue(body)
            server.enqueue(MockResponse().setBody("""{"id":"$userId"}"""))
            server.enqueue(body)
            assertEquals(FounderJoinResult.Enrolled, join.join())
            assertEquals(FounderJoinResult.Enrolled, join.join())
            assertEquals(0, google.calls)
            assertEquals(2, applied.size)
            assertEquals(applied[0], applied[1])
            assertEquals(FounderProgramStatus.PendingApproval, applied[0].status)
            assertEquals(LocalDate.of(2026, 5, 1), applied[0].enrolledOn)
            assertEquals(LocalDate.of(2026, 6, 15), applied[0].deadline)
            repeat(4) {
                val path = server.takeRequest().path
                assertFalse(path!!.contains("workout"))
                assertFalse(path.contains("/approval"))
            }
        }
    }

    @Test
    fun aSecondJoinWhileTheFirstIsRunningDoesNotStartAnotherSubmission() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val google = object : GoogleIdentityProvider {
            var calls = 0
            override suspend fun requestIdToken(): GoogleIdTokenRequest {
                calls += 1
                started.complete(Unit)
                release.await()
                return GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token"))
            }
        }
        withJoin(google) { server, _, join, applied ->
            server.enqueue(sessionResponse("opaque-bearer-token"))
            server.enqueue(founderResponse())
            val first = async { join.join() }
            started.await()
            assertEquals(FounderJoinResult.InProgress, join.join())
            release.complete(Unit)
            assertEquals(FounderJoinResult.Enrolled, first.await())
            assertEquals(1, google.calls)
            assertEquals(1, applied.size)
            assertEquals(2, server.requestCount)
        }
    }

    private fun assertEnrollment(request: RecordedRequest, token: String) {
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/founder/enrollment", request.path)
        assertEquals("Bearer $token", request.getHeader("Authorization"))
        assertEquals("", body)
        assertFalse(body.contains("APPROVED"))
        assertFalse(body.contains("lifetime"))
        assertFalse(body.contains("isPro"))
        assertFalse(body.contains("status"))
    }

    private suspend fun withJoin(
        google: GoogleIdentityProvider,
        callTimeoutMillis: Long = 5_000,
        block: suspend (
            MockWebServer,
            MemorySession,
            FounderJoinCoordinator,
            MutableList<BackendFounderSnapshot>
        ) -> Unit
    ) {
        val server = MockWebServer()
        server.start()
        val sessions = MemorySession()
        val http = OkHttpClient.Builder()
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        val applied = mutableListOf<BackendFounderSnapshot>()
        val join = FounderJoinCoordinator(
            auth = StrictAuthRepository(google, api, sessions),
            api = api,
            applyEnrollment = { applied += it },
            zone = zone
        )
        try {
            block(server, sessions, join, applied)
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    private fun sessionResponse(token: String): MockResponse {
        return MockResponse().setBody(
            """{"accessToken":"$token","tokenType":"Bearer","expiresAt":"2026-11-03T00:00:00Z","user":{"id":"$userId"}}"""
        )
    }

    private fun founderResponse(
        status: String = "ACTIVE_FREE",
        enrolledAt: String = "2026-06-01T00:00:00Z",
        deadlineAt: String = "2026-07-16T00:00:00Z",
        workouts: Int = 0,
        days: Int = 0
    ): MockResponse {
        return MockResponse().setBody(
            """
            {
              "status":"$status",
              "enrolledAt":"$enrolledAt",
              "deadlineAt":"$deadlineAt",
              "progress":{
                "qualifyingWorkouts":$workouts,
                "requiredWorkouts":10,
                "distinctWorkoutDays":$days,
                "requiredDistinctWorkoutDays":6
              },
              "temporaryProActive":false,
              "trainingRequirementsComplete":false,
              "feedback":{"required":true,"submitted":false},
              "testerReport":{"required":true,"submitted":false},
              "nextAction":"KEEP_TRAINING"
            }
            """.trimIndent()
        )
    }

    private fun stored(token: String): StoredStrictSession {
        return StoredStrictSession(StrictBearerToken(token), "2026-11-03T00:00:00Z", userId)
    }

    private class CountingGoogle(
        private val result: GoogleIdTokenRequest
    ) : GoogleIdentityProvider {
        var calls = 0
        override suspend fun requestIdToken(): GoogleIdTokenRequest {
            calls += 1
            return result
        }
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
