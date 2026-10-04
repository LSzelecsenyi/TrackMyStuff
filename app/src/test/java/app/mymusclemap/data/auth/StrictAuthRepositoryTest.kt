package app.mymusclemap.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class StrictAuthRepositoryTest {
    @Test
    fun googleSuccessPersistsTheServerUserAndDoesNotSendABearer() = runBlocking {
        val token = "opaque-bearer-token"
        val userId = "11111111-1111-4111-8111-111111111111"
        withBackend { server, sessions, repository ->
            server.enqueue(sessionResponse(token, userId))
            val result = repository.signIn()
            val recorded = server.takeRequest()
            assertEquals(StrictSignInResult.SignedIn(userId), result)
            assertEquals("/api/v1/auth/google", recorded.path)
            assertNull(recorded.getHeader("Authorization"))
            assertEquals("""{"idToken":"google-id-token"}""", recorded.body.readUtf8())
            val stored = sessions.read()
            assertEquals(token, stored?.accessToken?.value)
            assertEquals(userId, stored?.userId)
            assertEquals("StrictBearerToken(redacted)", stored?.accessToken.toString())
            assertLoggedNothing(token, "google-id-token")
        }
    }

    @Test
    fun googleCancellationDoesNotCallTheBackendOrCreateASession() = runBlocking {
        withBackend(google = GoogleIdTokenRequest.Cancelled) { server, sessions, repository ->
            assertEquals(StrictSignInResult.Cancelled, repository.signIn())
            assertEquals(0, server.requestCount)
            assertNull(sessions.read())
        }
    }

    @Test
    fun googleFailureDoesNotCreateASession() = runBlocking {
        withBackend(google = GoogleIdTokenRequest.Failed) { server, sessions, repository ->
            assertEquals(StrictSignInResult.GoogleFailed, repository.signIn())
            assertEquals(0, server.requestCount)
            assertNull(sessions.read())
        }
    }

    @Test
    fun invalidGoogleTokenDoesNotCreateOrClearASession() = runBlocking {
        val existing = stored("already-present-token")
        withBackend { server, sessions, repository ->
            sessions.write(existing)
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody(
                        """{"errorCode":"INVALID_GOOGLE_TOKEN","message":"The Google sign-in could not be verified."}"""
                    )
            )
            assertEquals(StrictSignInResult.InvalidGoogleToken, repository.signIn())
            assertEquals(existing, sessions.read())
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertLoggedNothing("already-present-token", "google-id-token")
        }
    }

    @Test
    fun currentUserSendsTheBearerAndReturnsTheServerUser() = runBlocking {
        val token = "me-bearer-token"
        val userId = "22222222-2222-4222-8222-222222222222"
        withBackend { server, sessions, repository ->
            sessions.write(stored(token, userId))
            server.enqueue(MockResponse().setBody("""{"id":"$userId"}"""))
            assertEquals(StrictCurrentUserResult.SignedIn(userId), repository.currentUser())
            val recorded = server.takeRequest()
            assertEquals("/api/v1/me", recorded.path)
            assertEquals("Bearer $token", recorded.getHeader("Authorization"))
            assertEquals(token, sessions.read()?.accessToken?.value)
            assertLoggedNothing(token)
        }
    }

    @Test
    fun http401ClearsOnlyTheLocalSession() = runBlocking {
        val token = "rejected-bearer-token"
        withBackend { server, sessions, repository ->
            sessions.write(stored(token))
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"errorCode":"UNAUTHENTICATED","message":"Authentication is required."}""")
            )
            assertEquals(StrictCurrentUserResult.SessionRejected, repository.currentUser())
            assertNull(sessions.read())
            assertLoggedNothing(token)
        }
    }

    @Test
    fun offlineBackendDoesNotClearTheSession() = runBlocking {
        val token = "offline-bearer-token"
        withBackend { server, sessions, repository ->
            sessions.write(stored(token))
            server.shutdown()
            assertEquals(StrictCurrentUserResult.Unavailable, repository.currentUser())
            assertEquals(token, sessions.read()?.accessToken?.value)
        }
    }

    @Test
    fun timeoutDoesNotClearTheSession() = runBlocking {
        val token = "timeout-bearer-token"
        withBackend(callTimeoutMillis = 200) { server, sessions, repository ->
            sessions.write(stored(token))
            assertEquals(StrictCurrentUserResult.Unavailable, repository.currentUser())
            assertEquals(token, sessions.read()?.accessToken?.value)
            assertLoggedNothing(token)
        }
    }

    @Test
    fun logoutRevokesTheBackendSessionAndClearsTheLocalToken() = runBlocking {
        val token = "logout-bearer-token"
        withBackend { server, sessions, repository ->
            sessions.write(stored(token))
            server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(StrictLogoutResult.LoggedOut, repository.logout())
            val recorded = server.takeRequest()
            assertEquals("DELETE", recorded.method)
            assertEquals("/api/v1/auth/session", recorded.path)
            assertEquals("Bearer $token", recorded.getHeader("Authorization"))
            assertNull(sessions.read())
            assertLoggedNothing(token)
        }
    }

    @Test
    fun logoutFailureStillClearsTheLocalToken() = runBlocking {
        val token = "logout-offline-token"
        withBackend { server, sessions, repository ->
            sessions.write(stored(token))
            server.shutdown()
            assertEquals(StrictLogoutResult.LoggedOutLocallyOnly, repository.logout())
            assertNull(sessions.read())
        }
    }

    @Test
    fun httpClientDoesNotInstallALoggingInterceptor() {
        val client = strictOkHttpClient()
        val interceptors = client.interceptors + client.networkInterceptors
        assertTrue(interceptors.none { it.javaClass.name.contains("Logging", ignoreCase = true) })
    }

    private suspend fun withBackend(
        google: GoogleIdTokenRequest = GoogleIdTokenRequest.Issued(GoogleIdTokenValue("google-id-token")),
        callTimeoutMillis: Long = 5_000,
        block: suspend (MockWebServer, MemoryStrictSessionStore, StrictAuthRepository) -> Unit
    ) {
        val server = MockWebServer()
        server.start()
        val sessions = MemoryStrictSessionStore()
        val http = OkHttpClient.Builder()
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        val repository = StrictAuthRepository(
            google = ScriptedGoogleIdentityProvider(google),
            api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions),
            sessions = sessions
        )
        try {
            block(server, sessions, repository)
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    private fun sessionResponse(token: String, userId: String): MockResponse {
        return MockResponse().setBody(
            """{"accessToken":"$token","tokenType":"Bearer","expiresAt":"2026-11-03T00:00:00Z","user":{"id":"$userId"}}"""
        )
    }

    private fun stored(
        token: String,
        userId: String = "33333333-3333-4333-8333-333333333333"
    ): StoredStrictSession {
        return StoredStrictSession(StrictBearerToken(token), "2026-11-03T00:00:00Z", userId)
    }

    private fun assertLoggedNothing(vararg secrets: String) {
        val logs = ShadowLog.getLogs().joinToString(separator = "\n") { "${it.tag} ${it.msg}" }
        secrets.forEach { secret ->
            assertFalse(logs.contains(secret))
        }
    }
}

private class ScriptedGoogleIdentityProvider(
    private val result: GoogleIdTokenRequest
) : GoogleIdentityProvider {
    override suspend fun requestIdToken(): GoogleIdTokenRequest = result
}

private class MemoryStrictSessionStore : StrictSessionStore {
    private var current: StoredStrictSession? = null

    override fun read(): StoredStrictSession? = current

    override fun write(session: StoredStrictSession) {
        current = session
    }

    override fun clear() {
        current = null
    }
}
