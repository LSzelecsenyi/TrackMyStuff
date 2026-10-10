package eu.strictworkout.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 of the Google subject. The raw subject is not stored in the marker. */
public final class GoogleSubjectHash {

    private GoogleSubjectHash() {
    }

    public static String of(String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("google:" + subject).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
