package eu.strictworkout.billing;

import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Repository
class JpaPlaySubscriptionRepository implements PlaySubscriptionRepository {

    private final PlaySubscriptionJpaRepository subscriptions;
    private final PlayRtdnMessageJpaRepository messages;
    private final Clock clock;

    JpaPlaySubscriptionRepository(
            PlaySubscriptionJpaRepository subscriptions,
            PlayRtdnMessageJpaRepository messages,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.messages = messages;
        this.clock = clock;
    }

    @Override
    public Optional<StoredPlaySubscription> find(String tokenHash) {
        return subscriptions.findById(tokenHash).map(PlaySubscriptionEntity::toRecord);
    }

    @Override
    public StoredPlaySubscription save(StoredPlaySubscription subscription) {
        PlaySubscriptionEntity entity = subscriptions.findById(subscription.tokenHash())
                .orElseGet(() -> PlaySubscriptionEntity.from(subscription));
        entity.copy(subscription);
        return subscriptions.save(entity).toRecord();
    }

    @Override
    public List<StoredPlaySubscription> reconciliationCandidates(Instant now) {
        Instant cutoff = now.minus(2, ChronoUnit.DAYS);
        return subscriptions.expiringAfter(cutoff).stream()
                .map(PlaySubscriptionEntity::toRecord)
                .filter(row -> PlaySubscriptionService.dueForReconcile(row, now))
                .toList();
    }

    @Override
    public boolean rememberMessage(String messageId, Instant eventTime, int notificationType) {
        if (messages.existsById(messageId)) {
            return false;
        }
        messages.save(new PlayRtdnMessageEntity(messageId, eventTime, notificationType, clock.instant()));
        return true;
    }
}
