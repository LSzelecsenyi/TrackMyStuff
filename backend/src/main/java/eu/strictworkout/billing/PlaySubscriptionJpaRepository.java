package eu.strictworkout.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface PlaySubscriptionJpaRepository extends JpaRepository<PlaySubscriptionEntity, String> {

    @Query("""
            select subscription from PlaySubscriptionEntity subscription
            where subscription.expiryTime is null or subscription.expiryTime > :cutoff
            """)
    List<PlaySubscriptionEntity> expiringAfter(@Param("cutoff") Instant cutoff);

    @Query("""
            select count(subscription) > 0 from PlaySubscriptionEntity subscription
            where subscription.linkedUserId = :userId
            and subscription.entitled = true
            and subscription.expiryTime > :now
            """)
    boolean hasEntitledSubscription(@Param("userId") java.util.UUID userId, @Param("now") Instant now);
}

interface PlayRtdnMessageJpaRepository extends JpaRepository<PlayRtdnMessageEntity, String> {
}
