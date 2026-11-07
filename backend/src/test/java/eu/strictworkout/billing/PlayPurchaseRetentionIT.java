package eu.strictworkout.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Testcontainers
class PlayPurchaseRetentionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlayPurchaseCleanup cleanup;

    @Autowired
    private PlaySubscriptionRepository subscriptions;

    @Test
    @DisplayName("Given a Play subscription is still entitled, when purchase cleanup runs, then its recovery data is preserved")
    void entitledPurchaseIsPreserved() {
        String hash = hash("entitled");
        insert(hash, "ACTIVE", Instant.parse("2026-12-01T00:00:00Z"), true, null, false);
        assertEquals(0, cleanup.cleanup());
        assertEquals(1, count(hash));
        assertNotNull(token(hash));
    }

    @Test
    @DisplayName("Given a subscription has genuinely ended and its 24-month retention period has elapsed, when cleanup runs, then the purchase row is removed")
    void endedPurchaseIsRemovedAfterTwentyFourMonths() {
        String hash = hash("ended");
        insert(hash, "EXPIRED", Instant.parse("2024-01-31T00:00:00Z"), false, Instant.parse("2024-01-31T00:00:00Z"), false);
        assertEquals(1, cleanup.cleanup());
        assertEquals(0, count(hash));
    }

    @Test
    @DisplayName("Given an ended subscription reactivates before cleanup, when cleanup runs, then the subscription is preserved")
    void reactivationClearsTheEndAndKeepsThePurchase() {
        String hash = hash("reactivated");
        insert(hash, "EXPIRED", Instant.parse("2024-01-31T00:00:00Z"), false, Instant.parse("2024-01-31T00:00:00Z"), false);
        subscriptions.save(new StoredPlaySubscription(
                hash,
                "reactivated-token",
                "com.strictworkout.app",
                "strict_pro",
                "monthly",
                "ACTIVE",
                Instant.parse("2026-12-01T00:00:00Z"),
                true,
                true,
                null,
                "GPA.reactivated",
                Instant.parse("2026-08-01T00:00:00Z"),
                false
        ));
        assertNull(endedAt(hash));
        assertEquals(0, cleanup.cleanup());
        assertEquals(1, count(hash));
    }

    @Test
    @DisplayName("Given a paused, held, or unverifiable purchase exists, when cleanup runs, then those rows are kept")
    void ambiguousAndHeldPurchasesAreKept() {
        String paused = hash("paused");
        String held = hash("held");
        String unknown = hash("unknown");
        insert(paused, "PAUSED", Instant.parse("2024-01-01T00:00:00Z"), false, Instant.parse("2024-01-01T00:00:00Z"), false);
        insert(held, "EXPIRED", Instant.parse("2024-01-01T00:00:00Z"), false, Instant.parse("2024-01-01T00:00:00Z"), true);
        insert(unknown, "EXPIRED", null, false, null, false);
        cleanup.cleanup();
        assertEquals(1, count(paused));
        assertEquals(1, count(held));
        assertEquals(1, count(unknown));
    }

    @Test
    @DisplayName("Given two backend instances run purchase cleanup concurrently, when they process the same eligible row, then it is removed once")
    void concurrentPurchaseCleanupIsIdempotent() throws Exception {
        String hash = hash("concurrent");
        insert(hash, "REVOKED", Instant.parse("2020-01-01T00:00:00Z"), false, Instant.parse("2020-01-01T00:00:00Z"), false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> cleanup.cleanup());
            Future<Integer> second = pool.submit(() -> cleanup.cleanup());
            assertEquals(1, first.get() + second.get());
        } finally {
            pool.shutdown();
        }
        assertEquals(0, count(hash));
        assertEquals(0, cleanup.cleanup());
    }

    private static String hash(String label) {
        return UUID.nameUUIDFromBytes(label.getBytes()).toString().replace("-", "") + "00000000000000000000000000000000";
    }

    private void insert(
            String hash,
            String state,
            Instant expiry,
            boolean entitled,
            Instant endedAt,
            boolean hold
    ) {
        jdbc.update("""
                insert into play_subscription (
                    token_hash, purchase_token, package_name, product_id, subscription_state,
                    expiry_time, auto_renewing, entitled, latest_order_id, updated_at, ended_at, retention_hold
                ) values (?, ?, 'com.strictworkout.app', 'strict_pro', ?, ?, false, ?, 'GPA.1', now(), ?, ?)
                """,
                hash,
                hash.substring(0, 12) + "-token",
                state,
                expiry == null ? null : java.time.OffsetDateTime.ofInstant(expiry, java.time.ZoneOffset.UTC),
                entitled,
                endedAt == null ? null : java.time.OffsetDateTime.ofInstant(endedAt, java.time.ZoneOffset.UTC),
                hold
        );
    }

    private int count(String hash) {
        return jdbc.queryForObject("select count(*) from play_subscription where token_hash = ?", Integer.class, hash);
    }

    private String token(String hash) {
        return jdbc.queryForObject("select purchase_token from play_subscription where token_hash = ?", String.class, hash);
    }

    private Instant endedAt(String hash) {
        List<Instant> values = jdbc.query(
                "select ended_at from play_subscription where token_hash = ?",
                (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                hash
        );
        return values.isEmpty() ? null : values.get(0);
    }
}
