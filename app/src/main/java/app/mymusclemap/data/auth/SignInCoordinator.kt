package app.mymusclemap.data.auth

import android.app.Activity
import android.content.Context
import app.mymusclemap.BuildConfig

/** Google sign-in used before an account database is open. */
class SignInCoordinator(context: Context) {
    private val google = ActivityBoundGoogleIdentityProvider(BuildConfig.STRICT_GOOGLE_SERVER_CLIENT_ID)
    private val sessions = EncryptedFileStrictSessionStore.create(context)
    private val api = OkHttpStrictBackendApi(
        baseUrl = BuildConfig.STRICT_API_BASE_URL,
        http = strictOkHttpClient(),
        sessions = sessions
    )
    val auth = StrictAuthRepository(google, api, sessions)

    suspend fun continueWithGoogle() = auth.signIn()

    fun bind(activity: Activity) {
        google.bind(activity)
    }

    fun unbind(activity: Activity) {
        google.unbind(activity)
    }
}
