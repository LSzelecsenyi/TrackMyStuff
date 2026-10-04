package eu.strictworkout.identity;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

interface GoogleIdTokenChecker {

    GoogleIdToken.Payload verify(String idToken);
}
