package eu.strictworkout.account;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerKeyRingTest {

    @Test
    void hmacMarkersStayStableAndDoNotContainTheSubject() {
        MarkerKeyRing keys = ring();
        String subject = "google-subject-1";
        assertEquals(64, keys.markerHash(subject).length());
        assertEquals(keys.markerHash(subject), keys.markerHash(subject));
        assertNotEquals(keys.markerHash(subject), keys.markerHash("google-subject-2"));
        assertFalse(keys.markerHash(subject).contains(subject));
        assertNotEquals(GoogleSubjectHash.of(subject), keys.markerHash(subject));
        String tag = keys.purchaseTag(subject);
        assertTrue(tag.length() <= 64);
        assertTrue(tag.startsWith("1."));
        assertEquals(tag, keys.purchaseTag(subject));
        assertTrue(keys.purchaseTagMatches(subject, tag));
        assertFalse(keys.purchaseTagMatches(subject, keys.purchaseTag("google-subject-2")));
        assertFalse(tag.contains(subject));
    }

    @Test
    void aMissingOrWrongSecretFailsClosed() {
        MarkerKeyRing keys = ring();
        assertFalse(MarkerKeyRing.untrustworthy(keys, Map.of()));
        assertFalse(MarkerKeyRing.untrustworthy(keys, Map.of(1, keys.verifier())));
        assertTrue(MarkerKeyRing.untrustworthy(keys, Map.of(1, "wrong")));
        assertTrue(MarkerKeyRing.untrustworthy(MarkerKeyRing.parse("", ""), Map.of(1, keys.verifier())));
        assertFalse(MarkerKeyRing.untrustworthy(MarkerKeyRing.parse("", ""), Map.of()));
        assertTrue(MarkerKeyRing.untrustworthy(keys, Map.of(2, "aa")));
    }

    @Test
    void anOlderKeyStillMatchesUntilItIsRemoved() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        byte[] next = new byte[32];
        next[0] = 9;
        String second = Base64.getEncoder().encodeToString(next);
        MarkerKeyRing rotated = MarkerKeyRing.parse("1:" + secret + ",2:" + second, "2");
        String subject = "returning-subject";
        MarkerKeyRing original = MarkerKeyRing.parse("1:" + secret, "1");
        assertTrue(rotated.purchaseTagMatches(subject, original.purchaseTag(subject)));
        assertTrue(rotated.markerHashes(subject).contains(original.markerHash(subject)));
        assertEquals(rotated.markerHash(subject), MarkerKeyRing.parse("2:" + second, "2").markerHash(subject));
    }

    private static MarkerKeyRing ring() {
        return MarkerKeyRing.parse("1:" + Base64.getEncoder().encodeToString(new byte[32]), "1");
    }
}
