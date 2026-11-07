package app.mymusclemap.data.promotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class PromotionAvailability(
    val proDiscoveryEnabled: Boolean,
    val welcomeBackEnabled: Boolean
)

/**
 * Unauthenticated read of the two promotion flags.
 * An older response that only has promotionsEnabled applies that value to both.
 * A failure leaves the previous cache in place. A missing cache stays disabled.
 */
class PromotionAvailabilityClient(
    baseUrl: String,
    private val http: OkHttpClient
) {
    private val root = baseUrl.trim().trimEnd('/')

    suspend fun read(): PromotionAvailability? = withContext(Dispatchers.IO) {
        if (root.isEmpty()) {
            return@withContext null
        }
        val request = Request.Builder()
            .url("$root/api/v1/promotions/availability")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                val body = response.body.string()
                val json = JSONObject(body)
                val shared = json.optBoolean("promotionsEnabled", false)
                PromotionAvailability(
                    proDiscoveryEnabled = if (json.has("proDiscoveryEnabled")) json.getBoolean("proDiscoveryEnabled") else shared,
                    welcomeBackEnabled = if (json.has("welcomeBackEnabled")) json.getBoolean("welcomeBackEnabled") else shared
                )
            }
        } catch (error: IOException) {
            null
        } catch (error: org.json.JSONException) {
            null
        }
    }
}
