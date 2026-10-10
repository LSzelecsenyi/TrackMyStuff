package eu.strictworkout.account;

import java.time.Instant;
import java.util.UUID;

public interface AccountDeletionStore {

    UUID findGoogleUser(String subject);

    String googleSubject(UUID userId);

    boolean lock(UUID userId);

    Usage usage(UUID userId);

    void blockSubscriptionClaims(UUID userId, Instant now);

    void deleteAccountRows(UUID userId);

    void insertMarker(String subjectHash, Instant deletedAt, Usage usage);

    void deleteUser(UUID userId);

    boolean marker(String subjectHash);

    boolean paidSubscriptionKnown(UUID userId);

    record Usage(
            boolean founder,
            boolean proDiscovery,
            boolean welcomeBack,
            boolean earlyAdopter
    ) {
    }
}
