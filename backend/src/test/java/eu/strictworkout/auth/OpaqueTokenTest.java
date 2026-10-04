package eu.strictworkout.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpaqueTokenTest {

    @Test
    void generatedTokensHave256BitsOfEntropyAndDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            String token = OpaqueTokenGenerator.generate();
            assertTrue(tokens.add(token));
            assertTrue(OpaqueTokenGenerator.matchesFormat(token));
            assertEquals(OpaqueTokenGenerator.ENTROPY_BYTES, Base64.getUrlDecoder().decode(token).length);
        }
    }

    @Test
    void sessionLifetimeMustBePositiveAndBounded() {
        assertEquals(Duration.ofDays(30), new AuthProperties(Duration.ofDays(30)).sessionLifetime());
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(Duration.ofDays(366)));
    }
}
