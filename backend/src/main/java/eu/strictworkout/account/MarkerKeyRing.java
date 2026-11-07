package eu.strictworkout.account;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned HMAC-SHA-256 keys for deletion markers and Play account tags.
 * The raw Google subject and the key bytes are never stored.
 * An empty ring means the secret is not configured. New markers are not written in that state.
 */
public final class MarkerKeyRing {

    private static final String HMAC = "HmacSHA256";
    private static final String MARKER_DOMAIN = "strict-deletion-marker-v1";
    private static final String PURCHASE_DOMAIN = "strict-purchase-account-v1";
    private static final String VERIFIER_DOMAIN = "strict-marker-key-verifier-v1";

    private final List<Key> keys;
    private final Key active;

    private MarkerKeyRing(List<Key> keys, Key active) {
        this.keys = keys;
        this.active = active;
    }

    public static MarkerKeyRing parse(String encodedKeys, String activeKeyId) {
        if (encodedKeys == null || encodedKeys.isBlank()) {
            return new MarkerKeyRing(List.of(), null);
        }
        Map<Integer, Key> parsed = new LinkedHashMap<>();
        for (String part : encodedKeys.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalStateException("Marker HMAC keys must use keyId:base64");
            }
            int id;
            try {
                id = Integer.parseInt(entry.substring(0, separator).trim());
            } catch (NumberFormatException error) {
                throw new IllegalStateException("Marker HMAC key id is not an integer");
            }
            if (id < 1) {
                throw new IllegalStateException("Marker HMAC key id must be positive");
            }
            byte[] secret = decode(entry.substring(separator + 1).trim());
            if (secret.length < 32) {
                throw new IllegalStateException("Marker HMAC key must be at least 32 bytes");
            }
            if (parsed.put(id, new Key(id, secret)) != null) {
                throw new IllegalStateException("Duplicate marker HMAC key id");
            }
        }
        if (parsed.isEmpty()) {
            return new MarkerKeyRing(List.of(), null);
        }
        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new IllegalStateException("The active marker HMAC key id is required when keys are set");
        }
        int activeId;
        try {
            activeId = Integer.parseInt(activeKeyId.trim());
        } catch (NumberFormatException error) {
            throw new IllegalStateException("The active marker HMAC key id is not an integer");
        }
        Key active = parsed.get(activeId);
        if (active == null) {
            throw new IllegalStateException("The active marker HMAC key id is not in the key list");
        }
        return new MarkerKeyRing(List.copyOf(parsed.values()), active);
    }

    public boolean configured() {
        return active != null;
    }

    public int activeKeyId() {
        requireActive();
        return active.id;
    }

    public String markerHash(String subject) {
        requireActive();
        return markerHash(active, subject);
    }

    public List<String> markerHashes(String subject) {
        List<String> hashes = new ArrayList<>();
        if (active != null) {
            hashes.add(markerHash(active, subject));
        }
        for (Key key : keys) {
            if (active == null || key.id != active.id) {
                hashes.add(markerHash(key, subject));
            }
        }
        return hashes;
    }

    /**
     * Stable for one Google subject and key version. Short enough for
     * BillingFlowParams.setObfuscatedAccountId, whose limit is 64 characters.
     */
    public String purchaseTag(String subject) {
        requireActive();
        return purchaseTag(active, subject);
    }

    public boolean purchaseTagMatches(String subject, String presented) {
        if (!configured() || presented == null || presented.isBlank()) {
            return false;
        }
        byte[] actual = presented.getBytes(StandardCharsets.UTF_8);
        boolean match = false;
        for (Key key : keys) {
            byte[] expected = purchaseTag(key, subject).getBytes(StandardCharsets.UTF_8);
            if (expected.length == actual.length && MessageDigest.isEqual(expected, actual)) {
                match = true;
            }
        }
        return match;
    }

    public String verifier() {
        requireActive();
        return verifier(active);
    }

    public String verifierFor(int keyId) {
        for (Key key : keys) {
            if (key.id == keyId) {
                return verifier(key);
            }
        }
        return null;
    }

    public boolean hasKey(int keyId) {
        for (Key key : keys) {
            if (key.id == keyId) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when stored checksums cannot be checked with the configured secrets.
     * A missing secret after a checksum was stored, a wrong secret for a known key id,
     * or a dropped key id all fail closed.
     */
    public static boolean untrustworthy(MarkerKeyRing ring, Map<Integer, String> storedVerifiers) {
        if (storedVerifiers == null || storedVerifiers.isEmpty()) {
            return false;
        }
        if (ring == null || !ring.configured()) {
            return true;
        }
        for (Key key : ring.keys) {
            String stored = storedVerifiers.get(key.id);
            if (stored != null && !stored.equals(ring.verifier(key))) {
                return true;
            }
        }
        for (Integer id : storedVerifiers.keySet()) {
            if (!ring.hasKey(id)) {
                return true;
            }
        }
        return false;
    }

    private String markerHash(Key key, String subject) {
        return HexFormat.of().formatHex(hmac(key.secret, MARKER_DOMAIN, subject));
    }

    private String purchaseTag(Key key, String subject) {
        String tag = key.id + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(key.secret, PURCHASE_DOMAIN, subject));
        if (tag.length() > 64) {
            throw new IllegalStateException("Purchase account tag exceeds 64 characters");
        }
        return tag;
    }

    private String verifier(Key key) {
        return HexFormat.of().formatHex(hmac(key.secret, VERIFIER_DOMAIN, "key"));
    }

    private void requireActive() {
        if (active == null) {
            throw new IllegalStateException("Marker HMAC key is not configured");
        }
    }

    private static byte[] hmac(byte[] secret, String domain, String material) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            mac.update(domain.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            mac.update(material.getBytes(StandardCharsets.UTF_8));
            return mac.doFinal();
        } catch (Exception error) {
            throw new IllegalStateException("HMAC-SHA-256 is not available");
        }
    }

    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return Base64.getUrlDecoder().decode(secret);
        }
    }

    private record Key(int id, byte[] secret) {
    }
}
