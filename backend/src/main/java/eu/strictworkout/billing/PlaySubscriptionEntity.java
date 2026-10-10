package eu.strictworkout.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "play_subscription")
class PlaySubscriptionEntity {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "purchase_token", nullable = false)
    private String purchaseToken;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "base_plan_id")
    private String basePlanId;

    @Column(name = "subscription_state", nullable = false, length = 64)
    private String subscriptionState;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "expiry_time")
    private Instant expiryTime;

    @Column(name = "auto_renewing", nullable = false)
    private boolean autoRenewing;

    @Column(nullable = false)
    private boolean entitled;

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    @Column(name = "latest_order_id")
    private String latestOrderId;

    @Column(name = "claim_blocked", nullable = false)
    private boolean claimBlocked;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlaySubscriptionEntity() {
    }

    static PlaySubscriptionEntity from(StoredPlaySubscription subscription) {
        PlaySubscriptionEntity entity = new PlaySubscriptionEntity();
        entity.tokenHash = subscription.tokenHash();
        entity.copy(subscription);
        return entity;
    }

    void copy(StoredPlaySubscription subscription) {
        purchaseToken = subscription.purchaseToken();
        packageName = subscription.packageName();
        productId = subscription.productId();
        basePlanId = subscription.basePlanId();
        subscriptionState = subscription.state();
        expiryTime = subscription.expiry();
        autoRenewing = subscription.autoRenewing();
        entitled = subscription.entitled();
        linkedUserId = subscription.linkedUserId();
        latestOrderId = subscription.orderId();
        claimBlocked = subscription.claimBlocked();
        updatedAt = subscription.updatedAt();
    }

    StoredPlaySubscription toRecord() {
        return new StoredPlaySubscription(
                tokenHash,
                purchaseToken,
                packageName,
                productId,
                basePlanId,
                subscriptionState,
                expiryTime,
                autoRenewing,
                entitled,
                linkedUserId,
                latestOrderId,
                updatedAt,
                claimBlocked
        );
    }
}
