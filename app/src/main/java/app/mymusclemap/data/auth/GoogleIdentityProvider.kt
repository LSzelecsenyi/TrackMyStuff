package app.mymusclemap.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.concurrent.atomic.AtomicReference

/**
 * ViewModels depend on this, not on Credential Manager types.
 * Tests supply a fake. Nothing in the app calls Google during ordinary startup.
 */
interface GoogleIdentityProvider {
    suspend fun requestIdToken(): GoogleIdTokenRequest
}

/**
 * Explicit Sign in with Google via Credential Manager.
 * [serverClientId] is the Web OAuth client ID. That value is the ID token audience,
 * which the backend checks as `STRICT_GOOGLE_CLIENT_ID`. A blank ID fails closed
 * and does not open the Google UI.
 */
class CredentialManagerGoogleIdentityProvider(
    private val serverClientId: String,
    private val uiContext: Context
) : GoogleIdentityProvider {
    override suspend fun requestIdToken(): GoogleIdTokenRequest {
        val clientId = serverClientId.trim()
        if (clientId.isEmpty()) {
            return GoogleIdTokenRequest.NotConfigured
        }
        return try {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = CredentialManager.create(uiContext).getCredential(uiContext, request)
            val credential = response.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleIdTokenRequest.Failed
            }
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            if (!isCredentialSafe(idToken)) {
                GoogleIdTokenRequest.Failed
            } else {
                GoogleIdTokenRequest.Issued(GoogleIdTokenValue(idToken))
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleIdTokenRequest.Cancelled
        } catch (_: GetCredentialException) {
            GoogleIdTokenRequest.Failed
        }
    }
}

/**
 * Holds the activity used for the Google account UI. Unbound calls fail closed.
 * [WeightTrackerApplication] does not bind this during startup.
 */
class ActivityBoundGoogleIdentityProvider(
    private val serverClientId: String
) : GoogleIdentityProvider {
    private val uiContext = AtomicReference<Context?>(null)

    fun bind(context: Context) {
        uiContext.set(context)
    }

    fun unbind(context: Context) {
        uiContext.compareAndSet(context, null)
    }

    override suspend fun requestIdToken(): GoogleIdTokenRequest {
        val context = uiContext.get() ?: return GoogleIdTokenRequest.NotConfigured
        return CredentialManagerGoogleIdentityProvider(serverClientId, context).requestIdToken()
    }
}
