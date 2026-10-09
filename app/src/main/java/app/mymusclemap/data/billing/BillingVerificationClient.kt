package app.mymusclemap.data.billing

import app.mymusclemap.domain.billing.VerifiedPaidSubscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

sealed interface BillingVerification {
    data class Verified(val subscription: VerifiedPaidSubscription) : BillingVerification
data object Failed : BillingVerification
data object OwnershipConflict : BillingVerification
data object NotConfigured : BillingVerification
}

/**
 * Sends a Play purchase token to the backend. The response expiration is stored.
 * The client does not decide that a purchase is active.
 */
class BillingVerificationClient(
    baseUrl: String,
    private val http: OkHttpClient,
    private val sessionToken: () -> String? = { null }
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun verify(purchaseToken: String, productId: String): BillingVerification = withContext(Dispatchers.IO) {
        if (root.isEmpty() || purchaseToken.isBlank() || productId.isBlank()) {
            return@withContext BillingVerification.Failed
        }
        val body = JSONObject()
            .put("purchaseToken", purchaseToken)
            .put("productId", productId)
            .toString()
        val requestBuilder = Request.Builder()
            .url("$root/api/v1/billing/subscriptions/verify")
            .post(body.toRequestBody(json))
        sessionToken()?.let { requestBuilder.header("Authorization", "Bearer $it") }
        try {
            http.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    503 -> BillingVerification.NotConfigured
                    409 -> BillingVerification.OwnershipConflict
                    401 -> BillingVerification.Failed
                    200 -> parse(response.body.string())
                    else -> BillingVerification.Failed
                }
            }
        } catch (_: IOException) {
            BillingVerification.Failed
        }
    }

    private fun parse(raw: String?): BillingVerification {
        if (raw.isNullOrBlank()) {
            return BillingVerification.Failed
        }
        return try {
            val json = JSONObject(raw)
            val expires = json.optString("expiresAt", "")
            BillingVerification.Verified(
                VerifiedPaidSubscription(
                    productId = json.optString("productId"),
                    expiresAt = expires.takeIf { it.isNotBlank() }?.let(Instant::parse),
                    autoRenewing = json.optBoolean("autoRenewing"),
                    state = json.optString("state"),
                    entitled = json.optBoolean("entitled")
                )
            )
        } catch (_: org.json.JSONException) {
            BillingVerification.Failed
        }
    }
}
