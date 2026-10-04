package app.mymusclemap.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OkHttpStrictBackendApiThreadingTest {
    @Test
    fun responseBodiesAreConsumedOffTheCallingMainThread() {
        val main = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, MAIN_THREAD)
        }
        Dispatchers.setMain(main.asCoroutineDispatcher())
        val server = MockWebServer()
        val recorder = BodyThreadRecorder()
        val http = OkHttpClient.Builder().addInterceptor(recorder).build()
        val sessions = MemorySession()
        server.start()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        val userId = "11111111-1111-4111-8111-111111111111"
        try {
            runBlocking {
                withContext(Dispatchers.Main) {
                    assertTrue(Thread.currentThread().name.startsWith(MAIN_THREAD))
                    server.enqueue(
                        MockResponse()
                            .setResponseCode(401)
                            .setBody(
                                """{"errorCode":"INVALID_GOOGLE_TOKEN","message":"The Google sign-in could not be verified."}"""
                            )
                    )
                    assertEquals(
                        GoogleExchangeResult.InvalidGoogleToken,
                        api.exchangeGoogleIdToken(GoogleIdTokenValue("google-id-token"))
                    )
                    assertNull(sessions.read())

                    sessions.write(StoredStrictSession(StrictBearerToken("bearer-token"), "2026-11-03T00:00:00Z", userId))
                    server.enqueue(MockResponse().setBody("""{"id":"$userId"}"""))
                    assertEquals(CurrentUserCall.SignedIn(userId), api.currentUser())

                    server.enqueue(founderBody())
                    val enrolled = api.enrollFounder(ZoneOffset.UTC)
                    assertTrue(enrolled is FounderEnrollmentCall.Enrolled)

                    server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errorCode":"UNAUTHENTICATED"}"""))
                    assertEquals(FounderEnrollmentCall.Unauthenticated, api.enrollFounder(ZoneOffset.UTC))
                    assertNull(sessions.read())

                    sessions.write(StoredStrictSession(StrictBearerToken("bearer-token"), "2026-11-03T00:00:00Z", userId))
                    server.enqueue(MockResponse().setResponseCode(204))
                    assertEquals(RevokeCall.Revoked, api.revokeSession())
                    assertTrue(Thread.currentThread().name.startsWith(MAIN_THREAD))
                }
            }
            assertTrue(recorder.threads.isNotEmpty())
            assertTrue(recorder.threads.none { it.startsWith(MAIN_THREAD) })
        } finally {
            Dispatchers.resetMain()
            main.shutdownNow()
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    private fun founderBody(): MockResponse {
        return MockResponse().setBody(
            """
            {
              "status":"ACTIVE_FREE",
              "enrolledAt":"2026-06-01T00:00:00Z",
              "deadlineAt":"2026-07-16T00:00:00Z",
              "progress":{
                "qualifyingWorkouts":0,
                "requiredWorkouts":10,
                "distinctWorkoutDays":0,
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

    private class BodyThreadRecorder : Interceptor {
        val threads = CopyOnWriteArrayList<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            return response.newBuilder()
                .body(RecordingBody(response.body, threads))
                .build()
        }
    }

    private class RecordingBody(
        private val delegate: ResponseBody,
        private val threads: MutableList<String>
    ) : ResponseBody() {
        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun source(): BufferedSource {
            threads += Thread.currentThread().name
            return delegate.source()
        }

        override fun close() {
            threads += Thread.currentThread().name
            delegate.close()
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
        const val MAIN_THREAD = "strict-test-main"
    }
}
