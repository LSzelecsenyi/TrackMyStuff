package eu.strictworkout.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlaySubscriptionService {

    public enum Status {
        VERIFIED,
        NOT_CONFIGURED,
        INVALID_TOKEN,
        PRODUCT_MISMATCH,
        PACKAGE_MISMATCH,
        CONFLICT,
        DUPLICATE,
        UNAVAILABLE
    }

    public record Outcome(Status status, StoredPlaySubscription subscription) {
    }

    private final PlayDeveloperApi play;
    private final PlaySubscriptionRepository subscriptions;
    private final String expectedPackage;
    private final Set<String> allowedProducts;
    private final Clock clock;

    public PlaySubscriptionService(
            PlayDeveloperApi play,
            PlaySubscriptionRepository subscriptions,
            String expectedPackage,
            Set<String> allowedProducts,
            Clock clock
    ) {
        this.play = play;
        this.subscriptions = subscriptions;
        this.expectedPackage = expectedPackage;
        this.allowedProducts = Set.copyOf(allowedProducts);
        this.clock = clock;
    }

    public Outcome verify(String purchaseToken, String claimedProductId, UUID userId) {
        if (purchaseToken == null || purchaseToken.isBlank() || claimedProductId == null || claimedProductId.isBlank()) {
            return new Outcome(Status.INVALID_TOKEN, null);
        }
        if (allowedProducts.isEmpty() || !allowedProducts.contains(claimedProductId)) {
            return new Outcome(Status.PRODUCT_MISMATCH, null);
        }
        return apply(purchaseToken, claimedProductId, userId);
    }

    public Outcome onNotification(
            String messageId,
            Instant eventTime,
            String packageName,
            String purchaseToken,
            int notificationType
    ) {
        if (messageId == null || messageId.isBlank()) {
            return new Outcome(Status.INVALID_TOKEN, null);
        }
        if (!subscriptions.rememberMessage(messageId, eventTime, notificationType)) {
            return new Outcome(Status.DUPLICATE, null);
        }
        if (packageName == null || !packageName.equals(expectedPackage)) {
            return new Outcome(Status.PACKAGE_MISMATCH, null);
        }
        if (purchaseToken == null || purchaseToken.isBlank()) {
            return new Outcome(Status.INVALID_TOKEN, null);
        }
        return apply(purchaseToken, null, null);
    }

    public int reconcile(Instant now) {
        int updated = 0;
        for (StoredPlaySubscription row : subscriptions.reconciliationCandidates(now)) {
            Outcome outcome = apply(row.purchaseToken(), row.productId(), row.linkedUserId());
            if (outcome.status == Status.VERIFIED) {
                updated++;
            }
        }
        return updated;
    }

    private Outcome apply(String purchaseToken, String claimedProductId, UUID userId) {
        PlayPurchase purchase;
        try {
            purchase = play.fetch(expectedPackage, purchaseToken);
        } catch (PlayApiException error) {
            return switch (error.failure) {
                case NOT_CONFIGURED -> new Outcome(Status.NOT_CONFIGURED, null);
                case INVALID_TOKEN -> new Outcome(Status.INVALID_TOKEN, null);
                case UNAVAILABLE -> new Outcome(Status.UNAVAILABLE, null);
            };
        }
        if (purchase.packageName() == null || !purchase.packageName().equals(expectedPackage)) {
            return new Outcome(Status.PACKAGE_MISMATCH, null);
        }
        PlayLineItem line = selectLine(purchase, claimedProductId);
        if (line == null) {
            return new Outcome(Status.PRODUCT_MISMATCH, null);
        }
        String hash = sha256(purchaseToken);
        Optional<StoredPlaySubscription> existing = subscriptions.find(hash);
        boolean claimBlocked = existing.map(StoredPlaySubscription::claimBlocked).orElse(false);
        UUID linked = existing.map(StoredPlaySubscription::linkedUserId).orElse(null);
        if (linked != null && userId != null && !linked.equals(userId)) {
            return new Outcome(Status.CONFLICT, existing.get());
        }
        if (claimBlocked && userId != null && (linked == null || !linked.equals(userId))) {
            return new Outcome(Status.CONFLICT, existing.orElse(null));
        }
        if (linked == null && !claimBlocked) {
            linked = userId;
        }
        Instant now = clock.instant();
        boolean entitled = PlayEntitlement.entitled(purchase.state(), line.expiry(), now);
        StoredPlaySubscription saved = subscriptions.save(new StoredPlaySubscription(
                hash,
                purchaseToken,
                expectedPackage,
                line.productId(),
                line.basePlanId(),
                purchase.state(),
                line.expiry(),
                line.autoRenewing(),
                entitled,
                linked,
                purchase.orderId(),
                now,
                claimBlocked
        ));
        if (entitled && "ACKNOWLEDGEMENT_STATE_PENDING".equals(purchase.acknowledgementState())) {
            play.acknowledge(expectedPackage, line.productId(), purchaseToken);
        }
        return new Outcome(Status.VERIFIED, saved);
    }

    private PlayLineItem selectLine(PlayPurchase purchase, String claimedProductId) {
        if (purchase.lineItems() == null) {
            return null;
        }
        for (PlayLineItem line : purchase.lineItems()) {
            if (line == null || line.productId() == null || !allowedProducts.contains(line.productId())) {
                continue;
            }
            if (claimedProductId == null || claimedProductId.equals(line.productId())) {
                return line;
            }
        }
        return null;
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    static boolean dueForReconcile(StoredPlaySubscription row, Instant now) {
        if (row.expiry() == null) {
            return true;
        }
        if (!row.expiry().isBefore(now)) {
            return row.updatedAt().isBefore(now.minus(Duration.ofHours(6)));
        }
        return row.expiry().isAfter(now.minus(Duration.ofDays(2)));
    }
}
