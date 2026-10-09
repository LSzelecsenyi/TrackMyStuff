package app.mymusclemap.data.promotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

class ProDiscoveryTrialClient(
    baseUrl: String,
    private val http: OkHttpClient,
    private val sessionToken: () -> String?
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun activate(): TrialAuthorization = call("activate", null)

    suspend fun migrate(activatedAt: Instant, expiresAt: Instant): TrialAuthorization {
        val body = JSONObject()
            .put("activatedAt", activatedAt.toString())
            .put("expiresAt", expiresAt.toString())
        return call("migrate", body)
    }

    private suspend fun call(action: String, body: JSONObject?): TrialAuthorization = withContext(Dispatchers.IO) {
        val token = sessionToken() ?: return@withContext TrialAuthorization.Unavailable
        if (root.isEmpty()) {
            return@withContext TrialAuthorization.Unavailable
        }
        val builder = Request.Builder()
            .url("$root/api/v1/promotions/pro-discovery/$action")
            .header("Authorization", "Bearer $token")
        if (body == null) {
            builder.post(ByteArray(0).toRequestBody(null))
        } else {
            builder.post(body.toString().toRequestBody(json))
        }
        try {
            http.newCall(builder.build()).execute().use { response ->
                val payload = runCatching { JSONObject(response.body.string()) }.getOrNull()
                val status = payload?.optString("status").orEmpty()
                when {
                    response.code == 200 && status == "ACTIVE" -> granted(payload)
                    status == "ALREADY_USED" -> TrialAuthorization.AlreadyUsed
                    response.code == 409 || response.code == 403 -> TrialAuthorization.Rejected
                    else -> TrialAuthorization.Unavailable
                }
            }
        } catch (_: IOException) {
            TrialAuthorization.Unavailable
        }
    }

    private fun granted(payload: JSONObject?): TrialAuthorization {
        val activated = payload?.optString("activatedAt").orEmpty()
        val expires = payload?.optString("expiresAt").orEmpty()
        val activatedAt = runCatching { Instant.parse(activated) }.getOrNull()
        val expiresAt = runCatching { Instant.parse(expires) }.getOrNull()
        if (activatedAt == null || expiresAt == null) {
            return TrialAuthorization.Unavailable
        }
        return TrialAuthorization.Granted(activatedAt, expiresAt)
    }
}
