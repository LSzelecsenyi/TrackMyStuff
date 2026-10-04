package eu.strictworkout.auth;

import java.security.SecureRandom;
import java.util.Base64;

public final class OpaqueTokenGenerator {

    static final int ENTROPY_BYTES = 32;
    public static final int ENCODED_LENGTH = 43;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[ENTROPY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean matchesFormat(String token) {
        if (token == null || token.length() != ENCODED_LENGTH) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            return decoded.length == ENTROPY_BYTES;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
