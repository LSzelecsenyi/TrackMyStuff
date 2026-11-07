package app.mymusclemap.data.auth

import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleIdTokenTypeTest {

    @Test
    fun givenEitherSignInWithGoogleCredentialType_whenItIsChecked_thenBothAreAccepted() {
        assertTrue(isAcceptedGoogleIdTokenType(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL))
        assertTrue(isAcceptedGoogleIdTokenType(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL))
        assertFalse(isAcceptedGoogleIdTokenType("com.google.android.libraries.identity.googleid.OTHER"))
    }
}
