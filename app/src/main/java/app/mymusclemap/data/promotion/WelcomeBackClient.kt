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

sealed interface WelcomeBackAuthorization {
    data object Local : WelcomeBackAuthorization
    data class Granted(
        val activatedAt: Instant,
        val expiresAt: Instant,
        val cooldownUntil: Instant
    ) : WelcomeBackAuthorization
    data class Cooldown(val cooldownUntil: Instant?) : WelcomeBackAuthorization
    data object Replay : WelcomeBackAuthorization
    data object Rejected : WelcomeBackAuthorization
    data object Unavailable : WelcomeBackAuthorization
}

sealed interface WelcomeBackRemote {
    data object Unavailable : WelcomeBackRemote
    data object None : WelcomeBackRemote
    data class Grant(
        val workoutId: String,
        val activatedAt: Instant,
        val expiresAt: Instant,
        val cooldownUntil: Instant
    ) : WelcomeBackRemote
}

/**
 * Session-authenticated Welcome Back calls.
 * The body never includes a user id or an expiration chosen by the phone.
 */
class WelcomeBackClient(
    baseUrl: String,
    private val http: OkHttpClient,
    private val sessionToken: () -> String?
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun current(): WelcomeBackRemote = withContext(Dispatchers.IO) {
        val token = sessionToken() ?: return@withContext WelcomeBackRemote.Unavailable
        if (root.isEmpty()) {
            return@withContext WelcomeBackRemote.Unavailable
        }
        val request = Request.Builder()
            .url("$root/api/v1/promotions/welcome-back")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code == 204) {
                    return@use WelcomeBackRemote.None
                }
                val payload = runCatching { JSONObject(response.body.string()) }.getOrNull()
                val grant = grant(payload)
                if (response.code == 200 && grant != null) {
                    grant
                } else {
                    WelcomeBackRemote.Unavailable
                }
            }
        } catch (_: IOException) {
            WelcomeBackRemote.Unavailable
        }
    }

    suspend fun activate(workoutId: String): WelcomeBackAuthorization = withContext(Dispatchers.IO) {
        val token = sessionToken() ?: return@withContext WelcomeBackAuthorization.Unavailable
        if (root.isEmpty()) {
            return@withContext WelcomeBackAuthorization.Unavailable
        }
        val body = JSONObject().put("qualifyingWorkoutId", workoutId)
        val request = Request.Builder()
            .url("$root/api/v1/promotions/welcome-back/activate")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(json))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val payload = runCatching { JSONObject(response.body.string()) }.getOrNull()
                val status = payload?.optString("status").orEmpty()
                when {
                    response.code == 200 && status == "ACTIVE" -> granted(payload)
                        ?: WelcomeBackAuthorization.Unavailable
                    status == "COOLDOWN" -> WelcomeBackAuthorization.Cooldown(instant(payload, "cooldownUntil"))
                    status == "REPLAY" -> WelcomeBackAuthorization.Replay
                    response.code == 409 || response.code == 403 -> WelcomeBackAuthorization.Rejected
                    else -> WelcomeBackAuthorization.Unavailable
                }
            }
        } catch (_: IOException) {
            WelcomeBackAuthorization.Unavailable
        }
    }

    private fun granted(payload: JSONObject?): WelcomeBackAuthorization.Granted? {
        val activatedAt = instant(payload, "activatedAt") ?: return null
        val expiresAt = instant(payload, "expiresAt") ?: return null
        val cooldownUntil = instant(payload, "cooldownUntil") ?: return null
        return WelcomeBackAuthorization.Granted(activatedAt, expiresAt, cooldownUntil)
    }

    private fun grant(payload: JSONObject?): WelcomeBackRemote.Grant? {
        val workoutId = payload?.optString("qualifyingWorkoutId").orEmpty()
        val activatedAt = instant(payload, "activatedAt") ?: return null
        val expiresAt = instant(payload, "expiresAt") ?: return null
        val cooldownUntil = instant(payload, "cooldownUntil") ?: return null
        if (workoutId.isBlank()) {
            return null
        }
        return WelcomeBackRemote.Grant(workoutId, activatedAt, expiresAt, cooldownUntil)
    }

    private fun instant(payload: JSONObject?, name: String): Instant? {
        val raw = payload?.optString(name).orEmpty()
        return runCatching { Instant.parse(raw) }.getOrNull()
    }
}
