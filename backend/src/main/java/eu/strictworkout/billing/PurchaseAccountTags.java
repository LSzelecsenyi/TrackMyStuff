package eu.strictworkout.billing;

import java.util.Optional;
import java.util.UUID;

/**
 * The Play obfuscated account id for a Strict user. It is derived from the Google
 * subject, so it stays the same after the Strict account is deleted and created again.
 */
public interface PurchaseAccountTags {

    boolean enabled();

    Optional<String> tagFor(UUID userId);

    boolean matches(UUID userId, String presentedTag);

    static PurchaseAccountTags disabled() {
        return new PurchaseAccountTags() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public Optional<String> tagFor(UUID userId) {
                return Optional.empty();
            }

            @Override
            public boolean matches(UUID userId, String presentedTag) {
                return false;
            }
        };
    }
}
