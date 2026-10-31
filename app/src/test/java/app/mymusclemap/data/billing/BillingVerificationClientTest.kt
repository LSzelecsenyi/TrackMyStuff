package app.mymusclemap.data.billing

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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BillingVerificationClientTest {
    @Test
    fun currentReadsTheLinkedSubscriptionWithoutSendingAPurchaseToken() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {"productId":"strict_pro","basePlanId":"monthly","state":"EXPIRED",
                    "expiresAt":"2026-06-01T12:05:00Z","autoRenewing":false,"entitled":false}
                    """.trimIndent()
                )
            )
            val client = BillingVerificationClient(server.url("/").toString(), OkHttpClient()) { "session-token" }
            val result = runBlocking { client.current() }
            val verified = result as BillingVerification.Verified
            assertEquals("strict_pro", verified.subscription.productId)
            assertEquals("EXPIRED", verified.subscription.state)
            assertFalse(verified.subscription.entitled)
            assertEquals(Instant.parse("2026-06-01T12:05:00Z"), verified.subscription.expiresAt)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/billing/subscriptions/current", request.path)
            assertEquals("Bearer session-token", request.getHeader("Authorization"))
            assertNull(request.body.readUtf8().ifBlank { null })
            assertFalse(request.path!!.contains("purchase-token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun absentUnavailableAndNetworkFailureDoNotLookLikeARevocation() {
        val server = MockWebServer()
        server.start()
        try {
            val client = BillingVerificationClient(server.url("/").toString(), OkHttpClient()) { null }
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ABSENT"}"""))
            assertTrue(runBlocking { client.current() } is BillingVerification.Absent)
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"status":"UNAVAILABLE"}"""))
            assertTrue(runBlocking { client.current() } is BillingVerification.NotConfigured)
        } finally {
            server.shutdown()
        }
        val closed = MockWebServer()
        closed.start()
        val closedUrl = closed.url("/").toString()
        closed.shutdown()
        val offline = BillingVerificationClient(closedUrl, OkHttpClient()) { null }
        assertTrue(runBlocking { offline.current() } is BillingVerification.Failed)
    }
}
