package eu.strictworkout.billing;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class PlaySubscriptionService {

    public enum Status {
        VERIFIED,
        NOT_CONFIGURED,
        INVALID_TOKEN,
        PRODUCT_MISMATCH,
        PACKAGE_MISMATCH,
        CONFLICT,
        DUPLICATE,
        UNAVAILABLE,
        ABSENT
    }

    public record Outcome(Status status, StoredPlaySubscription subscription) {
    }

    @FunctionalInterface
    interface SubscriptionWork {
        <T> T inTransaction(Supplier<T> action);
    }

    private static final SubscriptionWork DIRECT = new SubscriptionWork() {
        @Override
        public <T> T inTransaction(Supplier<T> action) {
            return action.get();
        }
    };

    private final PlayDeveloperApi play;
    private final PlaySubscriptionRepository subscriptions;
    private final String expectedPackage;
    private final Set<String> allowedProducts;
    private final Clock clock;
    private final SubscriptionWork transactions;
    private final PurchaseAccountTags tags;

    public PlaySubscriptionService(
            PlayDeveloperApi play,
            PlaySubscriptionRepository subscriptions,
            String expectedPackage,
            Set<String> allowedProducts,
            Clock clock
    ) {
        this(play, subscriptions, expectedPackage, allowedProducts, clock, DIRECT, PurchaseAccountTags.disabled());
    }

    public PlaySubscriptionService(
            PlayDeveloperApi play,
            PlaySubscriptionRepository subscriptions,
            String expectedPackage,
            Set<String> allowedProducts,
            Clock clock,
            PlatformTransactionManager transactions,
            PurchaseAccountTags tags
    ) {
        this(
                play,
                subscriptions,
                expectedPackage,
                allowedProducts,
                clock,
                template(transactions),
                tags == null ? PurchaseAccountTags.disabled() : tags
        );
    }

    PlaySubscriptionService(
            PlayDeveloperApi play,
            PlaySubscriptionRepository subscriptions,
            String expectedPackage,
            Set<String> allowedProducts,
            Clock clock,
            SubscriptionWork transactions,
            PurchaseAccountTags tags
    ) {
        this.play = play;
        this.subscriptions = subscriptions;
        this.expectedPackage = expectedPackage;
        this.allowedProducts = Set.copyOf(allowedProducts);
        this.clock = clock;
        this.transactions = transactions;
        this.tags = tags == null ? PurchaseAccountTags.disabled() : tags;
    }

    public Outcome verify(String purchaseToken, String claimedProductId, UUID userId) {
        if (purchaseToken == null || purchaseToken.isBlank() || claimedProductId == null || claimedProductId.isBlank()) {
            return new Outcome(Status.INVALID_TOKEN, null);
        }
        if (allowedProducts.isEmpty() || !allowedProducts.contains(claimedProductId)) {
            return new Outcome(Status.PRODUCT_MISMATCH, null);
        }
        return apply(purchaseToken, claimedProductId, userId, userId != null);
    }

    /**
     * Re-reads the account's linked purchase from Play. No linked row is an absent
     * result and writes nothing. An unavailable Play call returns before the stored
     * entitlement is replaced. This refresh does not move the purchase to another account.
     */
    public Outcome current(UUID userId) {
        Optional<StoredPlaySubscription> linked = subscriptions.findLinked(userId);
        if (linked.isEmpty()) {
            return new Outcome(Status.ABSENT, null);
        }
        StoredPlaySubscription row = linked.get();
        return apply(row.purchaseToken(), row.productId(), userId, false);
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
        return apply(purchaseToken, null, null, false);
    }

    public int reconcile(Instant now) {
        int updated = 0;
        for (StoredPlaySubscription row : subscriptions.reconciliationCandidates(now)) {
            Outcome outcome = apply(row.purchaseToken(), row.productId(), null, false);
            if (outcome.status == Status.VERIFIED) {
                updated++;
            }
        }
        return updated;
    }

    private Outcome apply(String purchaseToken, String claimedProductId, UUID userId, boolean claim) {
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
        String linkedToken = blankToNull(purchase.linkedPurchaseToken());
        String previousHash = linkedToken == null ? null : sha256(linkedToken);
        boolean playEntitled = PlayEntitlement.entitled(purchase.state(), line.expiry(), clock.instant());
        boolean tagMismatch = claim && tagMismatch(userId, purchase.obfuscatedAccountId());
        List<String> locks = new ArrayList<>();
        locks.add(hash);
        if (previousHash != null) {
            locks.add(previousHash);
        }
        final boolean entitledNow = playEntitled;
        Outcome outcome;
        try {
            outcome = transactions.inTransaction(() -> {
                subscriptions.lockTokens(locks);
                try {
                    return commit(purchase, line, hash, previousHash, userId, claim, entitledNow, tagMismatch, purchaseToken);
                } finally {
                    subscriptions.unlockTokens();
                }
            });
        } catch (DataIntegrityViolationException error) {
            Optional<StoredPlaySubscription> winner = subscriptions.find(hash);
            if (winner.isPresent() && claim && winner.get().linkedUserId() != null
                    && !winner.get().linkedUserId().equals(userId)) {
                return new Outcome(Status.CONFLICT, winner.get());
            }
            throw error;
        }
        if (outcome.status == Status.VERIFIED
                && outcome.subscription() != null
                && outcome.subscription().entitled()
                && "ACKNOWLEDGEMENT_STATE_PENDING".equals(purchase.acknowledgementState())) {
            play.acknowledge(expectedPackage, line.productId(), purchaseToken);
        }
        return outcome;
    }

    private Outcome commit(
            PlayPurchase purchase,
            PlayLineItem line,
            String hash,
            String previousHash,
            UUID userId,
            boolean claim,
            boolean playEntitled,
            boolean tagMismatch,
            String purchaseToken
    ) {
        StoredPlaySubscription existing = subscriptions.find(hash).orElse(null);
        UUID linked = existing == null ? null : existing.linkedUserId();
        boolean claimBlocked = existing != null && existing.claimBlocked();
        UUID predecessorOwner = previousHash == null
                ? null
                : subscriptions.find(previousHash).map(StoredPlaySubscription::linkedUserId).orElse(null);
        Status status = Status.VERIFIED;
        boolean storedEntitled = playEntitled;
        boolean linkedTagMismatch = linked != null && tagMismatch(linked, purchase.obfuscatedAccountId());
        if (!claim && linkedTagMismatch) {
            storedEntitled = false;
        }
        if (claim) {
            if (tagMismatch) {
                status = Status.CONFLICT;
                if (linked != null && linked.equals(userId)) {
                    storedEntitled = false;
                }
            } else if (linked != null && !linked.equals(userId)) {
                status = Status.CONFLICT;
            } else if (predecessorOwner != null && !predecessorOwner.equals(userId)) {
                status = Status.CONFLICT;
                if (linked != null && linked.equals(userId)) {
                    storedEntitled = false;
                }
            } else if (playEntitled) {
                linked = userId;
                claimBlocked = false;
            }
        }
        Instant now = clock.instant();
        if (existing == null && SubscriptionEndedAt.removable(
                purchase.state(),
                line.expiry(),
                storedEntitled,
                SubscriptionEndedAt.advance(null, purchase.state(), line.expiry(), storedEntitled, now),
                false,
                now
        )) {
            return new Outcome(Status.ABSENT, null);
        }
        StoredPlaySubscription saved = subscriptions.save(new StoredPlaySubscription(
                hash,
                purchaseToken,
                expectedPackage,
                line.productId(),
                line.basePlanId(),
                purchase.state(),
                line.expiry(),
                line.autoRenewing(),
                storedEntitled,
                linked,
                purchase.orderId(),
                now,
                claimBlocked
        ));
        return new Outcome(status, saved);
    }

    private boolean tagMismatch(UUID userId, String presented) {
        if (!tags.enabled() || presented == null || presented.isBlank()) {
            return false;
        }
        return !tags.matches(userId, presented);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static SubscriptionWork template(PlatformTransactionManager manager) {
        TransactionTemplate template = new TransactionTemplate(manager);
        return new SubscriptionWork() {
            @Override
            public <T> T inTransaction(Supplier<T> action) {
                return template.execute(status -> action.get());
            }
        };
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
