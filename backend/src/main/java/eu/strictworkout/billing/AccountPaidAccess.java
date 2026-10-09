package eu.strictworkout.billing;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

public interface AccountPaidAccess {
    boolean entitledNow(UUID userId, Instant now);
}

@Repository
class AccountPaidAccessJpa implements AccountPaidAccess {
    private final PlaySubscriptionJpaRepository subscriptions;

    AccountPaidAccessJpa(PlaySubscriptionJpaRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public boolean entitledNow(UUID userId, Instant now) {
        return subscriptions.hasEntitledSubscription(userId, now);
    }
}
