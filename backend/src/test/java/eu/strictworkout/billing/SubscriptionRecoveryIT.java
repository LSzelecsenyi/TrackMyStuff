package eu.strictworkout.billing;

import eu.strictworkout.account.MarkerKeyRing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SubscriptionRecoveryIT {

    private static final String PRODUCT = "strict_pro";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private PlaySubscriptionRepository repository;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private PurchaseAccountTags tags;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarkerKeyRing keys;

    private final ScriptedPlay play = new ScriptedPlay();
    private PlaySubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PlaySubscriptionService(
                play,
                repository,
                "com.strictworkout.app",
                Set.of(PRODUCT),
                Clock.systemUTC(),
                transactions,
                tags
        );
        play.purchase = active(Instant.now().plus(Duration.ofDays(30)), true);
    }

    @Test
    void anActiveSubscriptionCanBeRecoveredWithoutRestoringPromotions() {
        UUID deleted = insertUser("deleted-active");
        UUID returned = insertUser("returned-active");
        String token = "active-token";
        assertEquals(PlaySubscriptionService.Status.VERIFIED, service.verify(token, PRODUCT, deleted).status());
        unlink(deleted);
        PlaySubscriptionService.Outcome recovered = service.verify(token, PRODUCT, returned);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, recovered.status());
        assertTrue(recovered.subscription().entitled());
        assertEquals(returned, recovered.subscription().linkedUserId());
        assertEquals(PlaySubscriptionService.Status.CONFLICT, service.verify(token, PRODUCT, deleted).status());
        assertEquals(returned, linkedUser(token));
        assertPromotionsAbsent(returned);
    }

    @Test
    void aCanceledSubscriptionWithFutureExpiryRemainsRecoverable() {
        UUID user = insertUser("canceled-user");
        play.purchase = purchase("CANCELED", Instant.now().plus(Duration.ofDays(10)), false, null, null);
        PlaySubscriptionService.Outcome outcome = service.verify("canceled-token", PRODUCT, user);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, outcome.status());
        assertTrue(outcome.subscription().entitled());
        assertFalse(outcome.subscription().autoRenewing());
        assertEquals(user, outcome.subscription().linkedUserId());
    }

    @Test
    void anExpiredSubscriptionDoesNotGrantPro() {
        UUID user = insertUser("expired-user");
        play.purchase = purchase("EXPIRED", Instant.now().minus(Duration.ofDays(1)), false, null, null);
        PlaySubscriptionService.Outcome outcome = service.verify("expired-token", PRODUCT, user);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, outcome.status());
        assertFalse(outcome.subscription().entitled());
        assertNull(outcome.subscription().linkedUserId());
        assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(user).status());
    }

    @Test
    void aRevokedSubscriptionDoesNotGrantPro() {
        UUID user = insertUser("revoked-user");
        play.purchase = purchase("REVOKED", Instant.now().plus(Duration.ofDays(5)), false, null, null);
        PlaySubscriptionService.Outcome outcome = service.verify("revoked-token", PRODUCT, user);
        assertFalse(outcome.subscription().entitled());
        assertNull(outcome.subscription().linkedUserId());
        assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(user).status());
    }

    @Test
    void onePurchaseCannotGrantProToTwoAccounts() {
        UUID first = insertUser("owner-one");
        UUID second = insertUser("owner-two");
        assertEquals(PlaySubscriptionService.Status.VERIFIED, service.verify("exclusive-token", PRODUCT, first).status());
        assertEquals(PlaySubscriptionService.Status.CONFLICT, service.verify("exclusive-token", PRODUCT, second).status());
        assertEquals(first, linkedUser("exclusive-token"));
        assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(second).status());
    }

    @Test
    void concurrentVerificationCannotSucceedForTwoAccounts() throws Exception {
        UUID first = insertUser("race-one");
        UUID second = insertUser("race-two");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<PlaySubscriptionService.Status> left = pool.submit(() -> {
                start.await();
                return service.verify("race-token", PRODUCT, first).status();
            });
            Future<PlaySubscriptionService.Status> right = pool.submit(() -> {
                start.await();
                return service.verify("race-token", PRODUCT, second).status();
            });
            start.countDown();
            PlaySubscriptionService.Status firstStatus = left.get(30, TimeUnit.SECONDS);
            PlaySubscriptionService.Status secondStatus = right.get(30, TimeUnit.SECONDS);
            assertEquals(
                    Set.of(PlaySubscriptionService.Status.VERIFIED, PlaySubscriptionService.Status.CONFLICT),
                    Set.of(firstStatus, secondStatus)
            );
            UUID winner = linkedUser("race-token");
            assertTrue(winner.equals(first) || winner.equals(second));
            UUID loser = winner.equals(first) ? second : first;
            assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(loser).status());
            assertEquals(1, count(
                    "select count(*) from play_subscription where purchase_token = 'race-token' and linked_user_id is not null"
            ));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aLegacyClaimBlockedRowCanBeRecovered() {
        UUID user = insertUser("blocked-user");
        String token = "blocked-token";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update(
                """
                insert into play_subscription (
                    token_hash, purchase_token, package_name, product_id, subscription_state,
                    expiry_time, auto_renewing, entitled, linked_user_id, latest_order_id, updated_at, claim_blocked
                ) values (?, ?, 'com.strictworkout.app', 'strict_pro', 'ACTIVE', ?, true, false, null, 'GPA.blocked', ?, true)
                """,
                PlaySubscriptionService.sha256(token),
                token,
                now.plusDays(15),
                now.minusHours(8)
        );
        PlaySubscriptionService.Outcome outcome = service.verify(token, PRODUCT, user);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, outcome.status());
        assertTrue(outcome.subscription().entitled());
        assertEquals(user, outcome.subscription().linkedUserId());
        assertFalse(outcome.subscription().claimBlocked());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Given a legacy Play purchase lacks obfuscatedAccountId, when its valid active purchase is recovered, then recovery succeeds without duplicate ownership")
    void aLegacyPurchaseWithoutAnAccountTagCanBeRecovered() {
        UUID user = insertUser("legacy-buyer");
        play.purchase = active(Instant.now().plus(Duration.ofDays(20)), true);
        assertNull(play.purchase.obfuscatedAccountId());
        PlaySubscriptionService.Outcome outcome = service.verify("legacy-token", PRODUCT, user);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, outcome.status());
        assertTrue(outcome.subscription().entitled());
        assertEquals(user, outcome.subscription().linkedUserId());
        UUID other = insertUser("legacy-other");
        assertEquals(PlaySubscriptionService.Status.CONFLICT, service.verify("legacy-token", PRODUCT, other).status());
        assertEquals(user, linkedUser("legacy-token"));
    }

    @Test
    void aMismatchedAccountTagIsRejected() {
        UUID owner = insertUser("tag-owner");
        UUID other = insertUser("tag-other");
        play.purchase = purchase(
                "ACTIVE",
                Instant.now().plus(Duration.ofDays(12)),
                true,
                keys.purchaseTag("tag-owner"),
                null
        );
        assertEquals(PlaySubscriptionService.Status.CONFLICT, service.verify("tagged-token", PRODUCT, other).status());
        assertNull(linkedUser("tagged-token"));
        PlaySubscriptionService.Outcome linked = service.verify("tagged-token", PRODUCT, owner);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, linked.status());
        assertTrue(linked.subscription().entitled());
        play.purchase = purchase(
                "ACTIVE",
                Instant.now().plus(Duration.ofDays(12)),
                true,
                keys.purchaseTag("tag-other"),
                null
        );
        assertEquals(PlaySubscriptionService.Status.CONFLICT, service.verify("tagged-token", PRODUCT, owner).status());
        assertFalse(repository.findLinked(owner).orElseThrow().entitled());
        assertEquals(owner, linkedUser("tagged-token"));
    }

    @Test
    void notificationsAndReconciliationDoNotTransferOwnership() {
        UUID owner = insertUser("notify-owner");
        UUID other = insertUser("notify-other");
        assertEquals(PlaySubscriptionService.Status.VERIFIED, service.verify("notify-token", PRODUCT, owner).status());
        play.purchase = purchase(
                "ACTIVE",
                Instant.now().plus(Duration.ofDays(9)),
                true,
                keys.purchaseTag("notify-other"),
                null
        );
        assertEquals(
                PlaySubscriptionService.Status.VERIFIED,
                service.onNotification("notify-1", Instant.now(), "com.strictworkout.app", "notify-token", 2).status()
        );
        assertEquals(owner, linkedUser("notify-token"));
        assertFalse(repository.findLinked(owner).orElseThrow().entitled());
        assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(other).status());

        jdbc.update(
                "update play_subscription set linked_user_id = null, updated_at = ? where purchase_token = 'notify-token'",
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(8)
        );
        play.purchase = active(Instant.now().plus(Duration.ofDays(9)), true);
        assertTrue(service.reconcile(Instant.now()) >= 1);
        assertNull(linkedUser("notify-token"));
        assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(owner).status());
    }

    private UUID linkedUser(String token) {
        List<UUID> ids = jdbc.query(
                "select linked_user_id from play_subscription where purchase_token = ?",
                (rs, row) -> rs.getObject(1, UUID.class),
                token
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void unlink(UUID userId) {
        jdbc.update(
                "update play_subscription set linked_user_id = null, claim_blocked = false where linked_user_id = ?",
                userId
        );
    }

    private void assertPromotionsAbsent(UUID userId) {
        assertEquals(0, count("select count(*) from promotional_trial where user_id = ?", userId));
        assertEquals(0, count("select count(*) from welcome_back_grant where user_id = ?", userId));
        assertEquals(0, count("select count(*) from founder_application where user_id = ?", userId));
        assertEquals(0, count(
                "select count(*) from account_status_grant where user_id = ? and status = 'EARLY_ADOPTER'",
                userId
        ));
    }

    private int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private UUID insertUser(String subject) {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into app_user (id, created_at, updated_at) values (?, ?, ?)", userId, now, now);
        jdbc.update(
                """
                insert into external_identity (
                    id, user_id, provider, provider_subject, email, email_verified, created_at, updated_at
                ) values (?, ?, 'GOOGLE', ?, null, true, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                subject,
                now,
                now
        );
        return userId;
    }

    private static PlayPurchase active(Instant expiry, boolean renew) {
        return purchase("ACTIVE", expiry, renew, null, null);
    }

    private static PlayPurchase purchase(
            String state,
            Instant expiry,
            boolean renew,
            String accountTag,
            String linkedToken
    ) {
        return new PlayPurchase(
                "com.strictworkout.app",
                state,
                "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
                "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", expiry, renew)),
                accountTag,
                linkedToken
        );
    }

    private static final class ScriptedPlay implements PlayDeveloperApi {
        volatile PlayPurchase purchase;

        @Override
        public PlayPurchase fetch(String packageName, String purchaseToken) {
            return purchase;
        }

        @Override
        public void acknowledge(String packageName, String productId, String purchaseToken) {
        }
    }
}
