package app.mymusclemap.data.promotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Unauthenticated read of whether general promotions are enabled.
 * A failure leaves the previous cache in place. The caller treats a missing cache as disabled.
 */
class PromotionAvailabilityClient(
    baseUrl: String,
    private val http: OkHttpClient
) {
    private val root = baseUrl.trim().trimEnd('/')

    suspend fun promotionsEnabled(): Boolean? = withContext(Dispatchers.IO) {
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
                val body = response.body?.string() ?: return@withContext null
                JSONObject(body).getBoolean("promotionsEnabled")
            }
        } catch (error: IOException) {
            null
        } catch (error: org.json.JSONException) {
            null
        }
    }
}
