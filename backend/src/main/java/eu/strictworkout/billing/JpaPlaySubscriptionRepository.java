package eu.strictworkout.billing;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPlaySubscriptionRepository implements PlaySubscriptionRepository {

    private final PlaySubscriptionJpaRepository subscriptions;
    private final PlayRtdnMessageJpaRepository messages;
    private final EntityManager entities;
    private final Clock clock;

    JpaPlaySubscriptionRepository(
            PlaySubscriptionJpaRepository subscriptions,
            PlayRtdnMessageJpaRepository messages,
            EntityManager entities,
            Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.messages = messages;
        this.entities = entities;
        this.clock = clock;
    }

    @Override
    public Optional<StoredPlaySubscription> find(String tokenHash) {
        return subscriptions.findById(tokenHash).map(PlaySubscriptionEntity::toRecord);
    }

    @Override
    public Optional<StoredPlaySubscription> findLinked(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return subscriptions.findByLinkedUserIdOrderByUpdatedAtDesc(userId).stream()
                .findFirst()
                .map(PlaySubscriptionEntity::toRecord);
    }

    @Override
    public void lockTokens(Collection<String> tokenHashes) {
        if (tokenHashes == null) {
            return;
        }
        tokenHashes.stream()
                .filter(hash -> hash != null && !hash.isBlank())
                .distinct()
                .sorted()
                .forEach(hash -> entities.createNativeQuery("select pg_advisory_xact_lock(hashtext(:tokenHash))")
                        .setParameter("tokenHash", hash)
                        .getSingleResult());
    }

    @Override
    public void unlockTokens() {
        // The transaction-scoped advisory lock ends when the caller's transaction commits.
    }

    @Override
    public StoredPlaySubscription save(StoredPlaySubscription subscription) {
        PlaySubscriptionEntity entity = subscriptions.findById(subscription.tokenHash()).orElse(null);
        Instant previousEnd = entity == null ? null : entity.getEndedAt();
        boolean hold = entity != null && entity.isRetentionHold();
        if (entity == null) {
            entity = PlaySubscriptionEntity.from(subscription);
        } else {
            entity.copy(subscription);
        }
        entity.setEndedAt(SubscriptionEndedAt.advance(
                previousEnd,
                subscription.state(),
                subscription.expiry(),
                subscription.entitled(),
                clock.instant()
        ));
        entity.setRetentionHold(hold);
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
