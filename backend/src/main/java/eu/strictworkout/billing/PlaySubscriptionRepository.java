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
        Instant updatedAt
) {
}

interface PlaySubscriptionRepository {
    Optional<StoredPlaySubscription> find(String tokenHash);

    StoredPlaySubscription save(StoredPlaySubscription subscription);

    List<StoredPlaySubscription> reconciliationCandidates(Instant now);

    /**
     * @return false when this Pub/Sub message was already recorded
     */
    boolean rememberMessage(String messageId, Instant eventTime, int notificationType);
}
