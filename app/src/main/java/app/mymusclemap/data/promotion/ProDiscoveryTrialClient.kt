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
import app.mymusclemap.domain.entitlement.ProDiscoveryServerStatus
import app.mymusclemap.domain.entitlement.ProDiscoveryServerView

class ProDiscoveryTrialClient(
    baseUrl: String,
    private val http: OkHttpClient,
    private val sessionToken: () -> String?
) {
    private val root = baseUrl.trim().trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun activate(): TrialAuthorization = call("activate", null)

    suspend fun current(): ProDiscoveryServerView? = withContext(Dispatchers.IO) {
        val token = sessionToken() ?: return@withContext null
        if (root.isEmpty()) {
            return@withContext null
        }
        val request = Request.Builder()
            .url("$root/api/v1/promotions/pro-discovery")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return@withContext null
                }
                val payload = runCatching { JSONObject(response.body.string()) }.getOrNull()
                    ?: return@withContext null
                serverView(payload)
            }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun synchronize(
        installationId: String,
        revision: Long,
        generation: Long,
        dates: List<String>
    ): ActivitySync {
        val body = JSONObject()
            .put("installationId", installationId)
            .put("baseRevision", revision)
            .put("generation", generation)
            .put("dates", org.json.JSONArray(dates))
        return syncCall(body)
    }

    suspend fun setActivitySync(enabled: Boolean): ActivityPermission? = withContext(Dispatchers.IO) {
        val token = sessionToken() ?: return@withContext null
        if (root.isEmpty()) {
            return@withContext null
        }
        val body = JSONObject()
            .put("enabled", enabled)
            .put("scopeVersion", app.mymusclemap.domain.entitlement.ProDiscoverySyncPermission.SCOPE_VERSION)
        val request = Request.Builder()
            .url("$root/api/v1/promotions/pro-discovery/activity/permission")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(json))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return@withContext null
                }
                val payload = runCatching { JSONObject(response.body.string()) }.getOrNull()
                    ?: return@withContext null
                ActivityPermission(
                    enabled = payload.optBoolean("enabled"),
                    generation = payload.optLong("generation")
                )
            }
        } catch (_: IOException) {
            null
        }
    }

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
                    response.code == 200 && (status == "ACTIVE" || status == "ACTIVE_TRIAL") -> granted(payload)
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

    private suspend fun syncCall(body: JSONObject): ActivitySync = withContext(Dispatchers.IO) {
        val token = sessionToken() ?: return@withContext ActivitySync.Failed
        if (root.isEmpty()) {
            return@withContext ActivitySync.Failed
        }
        val request = Request.Builder()
            .url("$root/api/v1/promotions/pro-discovery/activity")
            .header("Authorization", "Bearer $token")
            .put(body.toString().toRequestBody(json))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val payload = runCatching { JSONObject(response.body.string()) }.getOrNull()
                when {
                    response.code == 200 -> ActivitySync.Applied(payload?.optLong("revision") ?: return@use ActivitySync.Failed)
                    payload?.optString("errorCode") == "TRIAL_ALREADY_RECORDED" -> ActivitySync.NotRequired
                    payload?.optString("errorCode") == "ACTIVITY_REVISION_STALE" ->
                        ActivitySync.Stale(payload.optLong("activityRevision"))
                    payload?.optString("errorCode") == "ACTIVITY_NOT_PERMITTED" ||
                        payload?.optString("errorCode") == "ACTIVITY_GENERATION_STALE" -> ActivitySync.Rejected
                    else -> ActivitySync.Failed
                }
            }
        } catch (_: IOException) {
            ActivitySync.Failed
        }
    }

    private fun serverView(payload: JSONObject): ProDiscoveryServerView {
        val status = when (payload.optString("status")) {
            "NOT_ELIGIBLE" -> ProDiscoveryServerStatus.NOT_ELIGIBLE
            "ELIGIBLE" -> ProDiscoveryServerStatus.ELIGIBLE
            "ACTIVE_TRIAL" -> ProDiscoveryServerStatus.ACTIVE_TRIAL
            "ALREADY_USED" -> ProDiscoveryServerStatus.ALREADY_USED
            "PROGRAM_CLOSED" -> ProDiscoveryServerStatus.PROGRAM_CLOSED
            "INCOMPATIBLE" -> ProDiscoveryServerStatus.INCOMPATIBLE
            else -> ProDiscoveryServerStatus.UNKNOWN
        }
        return ProDiscoveryServerView(
            status = status,
            activatedAt = payload.optString("activatedAt").takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
            expiresAt = payload.optString("expiresAt").takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
            qualifyingDays = payload.optInt("qualifyingDays"),
            remainingDays = payload.optInt("remainingDays"),
            recentDays = payload.optInt("recentDays"),
            remainingRecentDays = payload.optInt("remainingRecentDays"),
            requiredDays = payload.optInt("requiredDays", 24),
            requiredRecentDays = payload.optInt("requiredRecentDays", 2),
            matureOn = payload.optString("matureOn").takeIf { it.isNotBlank() }
        )
    }
}

data class ActivityPermission(val enabled: Boolean, val generation: Long)

sealed interface ActivitySync {
    data class Applied(val revision: Long) : ActivitySync
    data object NotRequired : ActivitySync
    data class Stale(val revision: Long) : ActivitySync
    data object Rejected : ActivitySync
    data object Failed : ActivitySync
}
