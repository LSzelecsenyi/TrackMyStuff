package eu.strictworkout.billing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

record StoredPlaySubscription(
        String tokenHash,
        String purchaseToken,
        String packageName,
        String productId,
        String basePlanId,
        String state,
        Instant expiry,
        boolean autoRenewing,
        boolean entitled,
        UUID linkedUserId,
        String orderId,
        Instant updatedAt,
        boolean claimBlocked
) {
}

interface PlaySubscriptionRepository {
    Optional<StoredPlaySubscription> find(String tokenHash);

    /**
     * The newest subscription linked to this account. Empty when the user has none.
     * The caller decides whether to re-read Play; this lookup does not write.
     */
    Optional<StoredPlaySubscription> findLinked(UUID userId);

    StoredPlaySubscription save(StoredPlaySubscription subscription);

    List<StoredPlaySubscription> reconciliationCandidates(Instant now);

    /**
     * @return false when this Pub/Sub message was already recorded
     */
    boolean rememberMessage(String messageId, Instant eventTime, int notificationType);
}
