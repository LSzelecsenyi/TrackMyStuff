package app.mymusclemap.data.auth

import app.mymusclemap.data.founder.BackendFounderSnapshot
import app.mymusclemap.data.founder.FounderWorkoutObservation
import app.mymusclemap.data.founder.FounderWorkoutSubmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    suspend fun enrollFounder(zone: ZoneId): FounderEnrollmentCall
    suspend fun currentFounder(zone: ZoneId): FounderSnapshotCall
    suspend fun submitFounderWorkout(
        clientWorkoutId: String,
        completedAt: Instant,
        localDate: LocalDate,
        zone: ZoneId,
        observation: FounderWorkoutObservation? = null
    ): FounderWorkoutSubmission
    suspend fun currentEntitlements(): FounderEntitlementCall
    suspend fun submitFounderReport(
        submissionId: String,
        feedback: String,
        appVersion: String,
        zone: ZoneId
    ): FounderReportSubmission
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
        return call(request) { response ->
            when {
                response.code == 401 && errorCode(response) == "INVALID_GOOGLE_TOKEN" ->
                    GoogleExchangeResult.InvalidGoogleToken
                response.code == 401 || !response.isSuccessful ->
                    GoogleExchangeResult.Rejected
                else -> parseSession(response.body.string())
                    ?.let { GoogleExchangeResult.Accepted(it) }
                    ?: GoogleExchangeResult.Rejected
            }
        } ?: GoogleExchangeResult.Unavailable
    }

    override suspend fun currentUser(): CurrentUserCall {
        val session = sessions.read() ?: return CurrentUserCall.NoSession
        val request = Request.Builder()
            .url("$root/api/v1/me")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .get()
            .build()
        return call(request) { response ->
            when {
                response.code == 401 -> {
                    sessions.clear()
                    CurrentUserCall.Rejected
                }
                !response.isSuccessful -> CurrentUserCall.Unavailable
                else -> parseUserId(response.body.string())
                    ?.let { CurrentUserCall.SignedIn(it) }
                    ?: CurrentUserCall.Unavailable
            }
        } ?: CurrentUserCall.Unavailable
    }

    override suspend fun enrollFounder(zone: ZoneId): FounderEnrollmentCall {
        val session = sessions.read() ?: return FounderEnrollmentCall.NoSession
        val request = Request.Builder()
            .url("$root/api/v1/founder/enrollment")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        return call(request) { response ->
            when {
                response.code == 401 -> {
                    sessions.clear()
                    FounderEnrollmentCall.Unauthenticated
                }
                response.code == 409 -> when (errorCode(response)) {
                    "ENROLLMENT_CLOSED" -> FounderEnrollmentCall.Closed
                    "ENROLLMENT_FULL" -> FounderEnrollmentCall.Full
                    else -> FounderEnrollmentCall.Rejected
                }
                !response.isSuccessful -> FounderEnrollmentCall.Rejected
                else -> BackendFounderSnapshot.parse(response.body.string(), zone)
                    ?.let { FounderEnrollmentCall.Enrolled(it) }
                    ?: FounderEnrollmentCall.Rejected
            }
        } ?: FounderEnrollmentCall.Unavailable
    }

    override suspend fun currentFounder(zone: ZoneId): FounderSnapshotCall {
        val session = sessions.read() ?: return FounderSnapshotCall.NoSession
        val request = Request.Builder()
            .url("$root/api/v1/founder")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .get()
            .build()
        return call(request) { response ->
            when {
                response.code == 401 -> {
                    sessions.clear()
                    FounderSnapshotCall.Unauthenticated
                }
                !response.isSuccessful -> FounderSnapshotCall.Unavailable
                else -> BackendFounderSnapshot.parse(response.body.string(), zone)
                    ?.let { FounderSnapshotCall.Loaded(it) }
                    ?: FounderSnapshotCall.Unavailable
            }
        } ?: FounderSnapshotCall.Unavailable
    }

    override suspend fun submitFounderWorkout(
        clientWorkoutId: String,
        completedAt: Instant,
        localDate: LocalDate,
        zone: ZoneId,
        observation: FounderWorkoutObservation?
    ): FounderWorkoutSubmission {
        val parsed = runCatching { UUID.fromString(clientWorkoutId) }.getOrNull()
            ?: return FounderWorkoutSubmission.Rejected
        val session = sessions.read() ?: return FounderWorkoutSubmission.NoSession
        val payload = JSONObject()
            .put("workoutId", parsed.toString())
            .put("completedAt", completedAt.toString())
            .put("localDate", localDate.toString())
        observation?.let { observed ->
            observed.displayName?.let { payload.put("displayName", it) }
            observed.durationSeconds?.let { payload.put("durationSeconds", it) }
            observed.exerciseCount?.let { payload.put("exerciseCount", it) }
            observed.completedSetCount?.let { payload.put("completedSetCount", it) }
            observed.fromTemplate?.let { payload.put("fromTemplate", it) }
            observed.usedExternalLoad?.let { payload.put("usedExternalLoad", it) }
        }
        val body = payload.toString()
        val request = Request.Builder()
            .url("$root/api/v1/founder/workouts")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .post(body.toRequestBody(json))
            .build()
        return call(request) { response ->
            when {
                response.code == 401 -> {
                    sessions.clear()
                    FounderWorkoutSubmission.Unauthenticated
                }
                response.code in 400..499 -> FounderWorkoutSubmission.Rejected
                !response.isSuccessful -> FounderWorkoutSubmission.Unavailable
                else -> BackendFounderSnapshot.parse(response.body.string(), zone)
                    ?.let { FounderWorkoutSubmission.Accepted(it) }
                    ?: FounderWorkoutSubmission.Rejected
            }
        } ?: FounderWorkoutSubmission.Unavailable
    }

    override suspend fun submitFounderReport(
        submissionId: String,
        feedback: String,
        appVersion: String,
        zone: ZoneId
    ): FounderReportSubmission {
        val session = sessions.read() ?: return FounderReportSubmission.NoSession
        val body = JSONObject()
            .put("submissionId", submissionId)
            .put("feedback", feedback)
            .put("appVersion", appVersion)
        val request = Request.Builder()
            .url("$root/api/v1/founder/tester-report")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .post(body.toString().toRequestBody(json))
            .build()
        return call(request) { response ->
            when {
                response.code == 401 -> {
                    sessions.clear()
                    FounderReportSubmission.Unauthenticated
                }
                response.code in 400..499 -> FounderReportSubmission.Rejected
                !response.isSuccessful -> FounderReportSubmission.Unavailable
                else -> BackendFounderSnapshot.parse(response.body.string(), zone)
                    ?.let { FounderReportSubmission.Accepted(it) }
                    ?: FounderReportSubmission.Rejected
            }
        } ?: FounderReportSubmission.Unavailable
    }

    override suspend fun currentEntitlements(): FounderEntitlementCall {
        val session = sessions.read() ?: return FounderEntitlementCall.NoSession
        val request = Request.Builder()
            .url("$root/api/v1/entitlements")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .get()
            .build()
        return call(request) { response ->
            when {
                response.code == 401 -> {
                    sessions.clear()
                    FounderEntitlementCall.Unauthenticated
                }
                !response.isSuccessful -> FounderEntitlementCall.Unavailable
                else -> parseEntitlements(response.body.string())
                    ?: FounderEntitlementCall.Unavailable
            }
        } ?: FounderEntitlementCall.Unavailable
    }

    override suspend fun revokeSession(): RevokeCall {
        val session = sessions.read() ?: return RevokeCall.NoSession
        val request = Request.Builder()
            .url("$root/api/v1/auth/session")
            .header("Authorization", "Bearer ${session.accessToken.value}")
            .delete()
            .build()
        return call(request) { response ->
            when {
                response.code == 401 -> {
                    sessions.clear()
                    RevokeCall.Failed
                }
                response.isSuccessful -> RevokeCall.Revoked
                else -> RevokeCall.Failed
            }
        } ?: RevokeCall.Failed
    }

    /**
     * OkHttp's call and the response body both perform blocking network I/O.
     * The body must be read and closed before this function resumes the caller.
     */
    private suspend fun <T> call(request: Request, consume: (okhttp3.Response) -> T): T? {
        return try {
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    consume(response)
                }
            }
        } catch (_: IOException) {
            null
        }
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

    private fun parseEntitlements(raw: String): FounderEntitlementCall? = parseEntitlementPayload(raw)

    private fun parseUserId(raw: String): String? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return runCatching { UUID.fromString(json.optString("id")).toString() }.getOrNull()
    }
}

internal fun parseEntitlementPayload(raw: String): FounderEntitlementCall? {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (!json.has("temporaryFounderPro") || !json.has("founderLifetime")) {
        return null
    }
    val founderLifetime = json.optBoolean("founderLifetime", false)
    val founderRecognized = json.optBoolean("founderRecognized", false)
    val founderProExpiresAt = if (!json.has("founderProExpiresAt") || json.isNull("founderProExpiresAt")) {
        null
    } else {
        runCatching { Instant.parse(json.getString("founderProExpiresAt")) }.getOrNull()
    }
    val founderGrantedAt = if (
        (!founderLifetime && !founderRecognized && founderProExpiresAt == null) ||
        !json.has("founderGrantedAt") ||
        json.isNull("founderGrantedAt")
    ) {
        null
    } else {
        runCatching { Instant.parse(json.getString("founderGrantedAt")) }.getOrNull()
    }
    val specials = buildList {
        val array = json.optJSONArray("specialAchievements") ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("key")
            if (key != "EARLY_ADOPTER" && key != "DEVELOPER") {
                continue
            }
            val instant = runCatching { Instant.parse(item.optString("grantedAt")) }.getOrNull() ?: continue
            add(app.mymusclemap.domain.entitlement.SpecialAchievementGrant(key, instant))
        }
    }
    return FounderEntitlementCall.Loaded(
        temporaryFounderPro = json.optBoolean("temporaryFounderPro", false),
        founderLifetime = founderLifetime,
        founderGrantedAt = founderGrantedAt,
        specialAchievements = specials,
        founderRecognized = founderRecognized,
        founderProExpiresAt = founderProExpiresAt
    )
}
