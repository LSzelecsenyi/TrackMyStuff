package app.mymusclemap.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

fun strictOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}

interface StrictBackendApi {
    suspend fun exchangeGoogleIdToken(idToken: GoogleIdTokenValue): GoogleExchangeResult
    suspend fun currentUser(): CurrentUserCall
    suspend fun revokeSession(): RevokeCall
}

class OkHttpStrictBackendApi(
    baseUrl: String,
    private val http: OkHttpClient,
    private val sessions: StrictSessionStore
) : StrictBackendApi {
    private val root = baseUrl.trim().trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun exchangeGoogleIdToken(idToken: GoogleIdTokenValue): GoogleExchangeResult {
        if (!isCredentialSafe(idToken.value)) {
            return GoogleExchangeResult.Rejected
        }
        val body = JSONObject().put("idToken", idToken.value).toString()
        val request = Request.Builder()
            .url("$root/api/v1/auth/google")
            .post(body.toRequestBody(json))
            .build()
        val response = execute(request) ?: return GoogleExchangeResult.Unavailable
        response.use {
            if (it.code == 401) {
                return if (errorCode(it) == "INVALID_GOOGLE_TOKEN") {
                    GoogleExchangeResult.InvalidGoogleToken
                } else {
                    GoogleExchangeResult.Rejected
                }
            }
            if (!it.isSuccessful) {
                return GoogleExchangeResult.Rejected
            }
            val session = parseSession(it.body.string()) ?: return GoogleExchangeResult.Rejected
            return GoogleExchangeResult.Accepted(session)
        }
    }

    override suspend fun currentUser(): CurrentUserCall {
        val session = sessions.read() ?: return CurrentUserCall.NoSession
        val request = Request.Builder()
            .url("$root/api/v1/me")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .get()
            .build()
        val response = execute(request) ?: return CurrentUserCall.Unavailable
        response.use {
            if (it.code == 401) {
                sessions.clear()
                return CurrentUserCall.Rejected
            }
            if (!it.isSuccessful) {
                return CurrentUserCall.Unavailable
            }
            val userId = parseUserId(it.body.string()) ?: return CurrentUserCall.Unavailable
            return CurrentUserCall.SignedIn(userId)
        }
    }

    override suspend fun revokeSession(): RevokeCall {
        val session = sessions.read() ?: return RevokeCall.NoSession
        val request = Request.Builder()
            .url("$root/api/v1/auth/session")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .delete()
            .build()
        val response = execute(request) ?: return RevokeCall.Failed
        response.use {
            if (it.code == 401) {
                sessions.clear()
                return RevokeCall.Failed
            }
            return if (it.isSuccessful) RevokeCall.Revoked else RevokeCall.Failed
        }
    }

    private suspend fun execute(request: Request) = try {
        withContext(Dispatchers.IO) {
            http.newCall(request).execute()
        }
    } catch (_: IOException) {
        null
    }

    private fun errorCode(response: okhttp3.Response): String? {
        val raw = response.body.string()
        return runCatching { JSONObject(raw).optString("errorCode").takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun parseSession(raw: String): StoredStrictSession? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val token = json.optString("accessToken")
        val tokenType = json.optString("tokenType")
        val expiresAt = json.optString("expiresAt")
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        if (tokenType != "Bearer" || !isCredentialSafe(token) || expiresAt.isBlank()) {
            return null
        }
        val parsedUserId = runCatching { UUID.fromString(userId).toString() }.getOrNull() ?: return null
        return StoredStrictSession(StrictBearerToken(token), expiresAt, parsedUserId)
    }

    private fun parseUserId(raw: String): String? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return runCatching { UUID.fromString(json.optString("id")).toString() }.getOrNull()
    }
}
