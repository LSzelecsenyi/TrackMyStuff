package app.mymusclemap.data.founder

import app.mymusclemap.data.auth.FounderReportSubmission
import app.mymusclemap.data.auth.OkHttpStrictBackendApi
import app.mymusclemap.data.auth.StoredStrictSession
import app.mymusclemap.data.auth.StrictBearerToken
import app.mymusclemap.data.auth.StrictSessionStore
import app.mymusclemap.data.auth.strictOkHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class FounderReportApiTest {
    @Test
    fun submissionBodyHasNoAuthorityFieldsAnd401ClearsTheSession() {
        val server = MockWebServer()
        val sessions = MemorySession()
        server.start()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), strictOkHttpClient(), sessions)
        val submissionId = "22222222-2222-4222-8222-222222222222"
        sessions.write(
            StoredStrictSession(
                accessToken = StrictBearerToken("bearer-token"),
                expiresAt = "2099-01-01T00:00:00Z",
                userId = "11111111-1111-4111-8111-111111111111"
            )
        )
        try {
            server.enqueue(MockResponse().setResponseCode(401))
            val result = runBlocking {
                api.submitFounderReport(
                    submissionId = submissionId,
                    feedback = "Keep the rest timer visible.",
                    appVersion = "0.1.0-debug",
                    zone = ZoneOffset.UTC
                )
            }
            val recorded = server.takeRequest()
            val body = recorded.body.readUtf8()
            assertEquals("POST", recorded.method)
            assertEquals("/api/v1/founder/tester-report", recorded.path)
            assertEquals("Bearer bearer-token", recorded.getHeader("Authorization"))
            assertTrue(body.contains("\"submissionId\":\"$submissionId\""))
            assertTrue(body.contains("\"feedback\":\"Keep the rest timer visible.\""))
            assertTrue(body.contains("\"appVersion\":\"0.1.0-debug\""))
            assertFalse(body.contains("userId"))
            assertFalse(body.contains("applicationId"))
            assertFalse(body.contains("PENDING_APPROVAL"))
            assertFalse(body.contains("founderLifetime"))
            assertFalse(body.contains("status"))
            assertFalse(body.contains("displayName"))
            assertFalse(body.contains("durationSeconds"))
            assertFalse(body.contains("exerciseCount"))
            assertFalse(body.contains("workoutId"))
            assertTrue(result is FounderReportSubmission.Unauthenticated)
            assertNull(sessions.read())
        } finally {
            server.shutdown()
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
