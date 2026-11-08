package app.mymusclemap.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountDeletionRequestTest {

    @Test
    fun givenASignedInSession_whenDeletionIsRequested_thenOnlyTheSessionDeleteIsSent() = runBlocking {
        val server = MockWebServer()
        val http = OkHttpClient()
        val sessions = MemorySession()
        server.start()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        try {
            sessions.write(
                StoredStrictSession(StrictBearerToken("session-token"), "2026-12-01T00:00:00Z", USER)
            )
            server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(AccountDeletionCall.Deleted, api.deleteSignedInAccount())
            val recorded = server.takeRequest()
            assertEquals("DELETE", recorded.method)
            assertEquals("/api/v1/account", recorded.path)
            assertEquals("Bearer session-token", recorded.getHeader("Authorization"))
            val body = recorded.body.readUtf8()
            assertEquals("""{"confirmed":true}""", body)
            assertFalse(body.contains("idToken"))
            assertFalse(body.contains("email"))
            assertFalse(body.contains(USER))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test
    fun givenTheServerRejectsTheSession_whenDeletionIsRequested_thenThePublicEndpointIsNotUsed() = runBlocking {
        val server = MockWebServer()
        val http = OkHttpClient()
        val sessions = MemorySession()
        server.start()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        try {
            sessions.write(
                StoredStrictSession(StrictBearerToken("expired-token"), "2026-11-01T00:00:00Z", USER)
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"errorCode":"UNAUTHENTICATED","message":"Sign in again."}""")
            )
            assertEquals(AccountDeletionCall.Unauthenticated, api.deleteSignedInAccount())
            val recorded = server.takeRequest()
            assertEquals("/api/v1/account", recorded.path)
            assertFalse(recorded.path!!.contains("/deletion"))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test
    fun givenNoSession_whenDeletionIsRequested_thenNoRequestIsSent() = runBlocking {
        val server = MockWebServer()
        val http = OkHttpClient()
        server.start()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, MemorySession())
        try {
            assertEquals(AccountDeletionCall.Unauthenticated, api.deleteSignedInAccount())
            assertNull(server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
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

    private companion object {
        const val USER = "11111111-1111-4111-8111-111111111111"
    }
}
