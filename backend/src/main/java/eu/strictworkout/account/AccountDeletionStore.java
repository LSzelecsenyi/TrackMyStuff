package eu.strictworkout.account;

import java.time.Instant;
import java.util.UUID;

public interface AccountDeletionStore {

    UUID findGoogleUser(String subject);

    String googleSubject(UUID userId);

    boolean lock(UUID userId);

    Usage usage(UUID userId);

    /**
     * Unlinks Play rows from the account. The purchase token stays so a still-active
     * subscription can be verified again. This does not cancel Google Play billing.
     */
    void detachSubscriptions(UUID userId, Instant now);

    void deleteAccountRows(UUID userId);

    void insertMarker(String subjectHash, Instant deletedAt, Usage usage);

    void deleteUser(UUID userId);

    boolean marker(String subjectHash);

    default java.util.Optional<MarkerRow> findMarker(String subjectHash) {
        return java.util.Optional.empty();
    }

    default void deleteMarker(String subjectHash) {
    }

    boolean paidSubscriptionKnown(UUID userId);

    record MarkerRow(
            String subjectHash,
            Instant deletedAt,
            boolean founder,
            boolean proDiscovery,
            boolean welcomeBack,
            boolean earlyAdopter
    ) {
    }

    record Usage(
            boolean founder,
            boolean proDiscovery,
            boolean welcomeBack,
            boolean earlyAdopter
    ) {
    }
}
