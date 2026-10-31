package eu.strictworkout.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaySubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final String TOKEN = "purchase-token-1";
    private static final String PRODUCT = "strict_pro";

    private final MemoryRepository repository = new MemoryRepository();
    private final ScriptedPlay play = new ScriptedPlay();
    private PlaySubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PlaySubscriptionService(
                play,
                repository,
                "com.strictworkout.app",
                Set.of(PRODUCT),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        play.purchase = active(NOW.plusSeconds(86_400), true);
    }

    @Test
    void validPurchaseIsVerifiedOnceAndAcknowledged() {
        play.purchase = new PlayPurchase(
                "com.strictworkout.app",
                "ACTIVE",
                "ACKNOWLEDGEMENT_STATE_PENDING",
                "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(86_400), true))
        );
        PlaySubscriptionService.Outcome first = service.verify(TOKEN, PRODUCT, null);
        PlaySubscriptionService.Outcome second = service.verify(TOKEN, PRODUCT, null);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, first.status());
        assertEquals(PlaySubscriptionService.Status.VERIFIED, second.status());
        assertEquals(1, repository.rows.size());
        assertEquals(1, play.acknowledgements);
        assertTrue(first.subscription().entitled());
        assertEquals(first.subscription().tokenHash(), second.subscription().tokenHash());
    }

    @Test
    void invalidTokenAndMismatchesAreRejected() {
        play.failure = PlayApiFailure.INVALID_TOKEN;
        assertEquals(PlaySubscriptionService.Status.INVALID_TOKEN, service.verify(TOKEN, PRODUCT, null).status());
        play.failure = null;
        assertEquals(PlaySubscriptionService.Status.PRODUCT_MISMATCH, service.verify(TOKEN, "other", null).status());
        play.purchase = new PlayPurchase("com.other.app", "ACTIVE", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(60), true)));
        assertEquals(PlaySubscriptionService.Status.PACKAGE_MISMATCH, service.verify(TOKEN, PRODUCT, null).status());
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void aTokenCannotBeReboundToAnotherAccount() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(PlaySubscriptionService.Status.VERIFIED, service.verify(TOKEN, PRODUCT, first).status());
        PlaySubscriptionService.Outcome conflict = service.verify(TOKEN, PRODUCT, second);
        assertEquals(PlaySubscriptionService.Status.CONFLICT, conflict.status());
        assertEquals(first, repository.rows.values().iterator().next().linkedUserId());
    }

    @Test
    void renewalCancellationExpirationGraceHoldAndRevocationFollowPlayState() {
        assertTrue(service.verify(TOKEN, PRODUCT, null).subscription().entitled());

        play.purchase = active(NOW.plusSeconds(172_800), true);
        PlaySubscriptionService.Outcome renewed = service.verify(TOKEN, PRODUCT, null);
        assertEquals(NOW.plusSeconds(172_800), renewed.subscription().expiry());
        assertEquals(1, repository.rows.size());

        play.purchase = new PlayPurchase("com.strictworkout.app", "CANCELED", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(3_600), false)));
        PlaySubscriptionService.Outcome canceled = service.verify(TOKEN, PRODUCT, null);
        assertTrue(canceled.subscription().entitled());
        assertFalse(canceled.subscription().autoRenewing());

        play.purchase = active(NOW.plusMillis(1), true);
        assertTrue(service.verify(TOKEN, PRODUCT, null).subscription().entitled());
        play.purchase = active(NOW, true);
        assertFalse(service.verify(TOKEN, PRODUCT, null).subscription().entitled());
        play.purchase = active(NOW.minusSeconds(1), true);
        assertFalse(service.verify(TOKEN, PRODUCT, null).subscription().entitled());

        play.purchase = new PlayPurchase("com.strictworkout.app", "IN_GRACE_PERIOD", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(60), true)));
        assertTrue(service.verify(TOKEN, PRODUCT, null).subscription().entitled());

        play.purchase = new PlayPurchase("com.strictworkout.app", "ON_HOLD", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.minusSeconds(60), false)));
        assertFalse(service.verify(TOKEN, PRODUCT, null).subscription().entitled());

        play.purchase = new PlayPurchase("com.strictworkout.app", "EXPIRED", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.minusSeconds(60), false)));
        assertFalse(service.verify(TOKEN, PRODUCT, null).subscription().entitled());
    }

    @Test
    void pendingPurchaseDoesNotGrant() {
        play.purchase = new PlayPurchase("com.strictworkout.app", "PENDING", "ACKNOWLEDGEMENT_STATE_PENDING", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(60), true)));
        assertFalse(service.verify(TOKEN, PRODUCT, null).subscription().entitled());
        assertEquals(0, play.acknowledgements);
    }

    @Test
    void duplicateAndOutOfOrderNotificationsUseThePlayQuery() {
        assertEquals(PlaySubscriptionService.Status.VERIFIED, service.onNotification("m1", NOW, "com.strictworkout.app", TOKEN, 2).status());
        int fetches = play.fetches;
        assertEquals(PlaySubscriptionService.Status.DUPLICATE, service.onNotification("m1", NOW, "com.strictworkout.app", TOKEN, 3).status());
        assertEquals(fetches, play.fetches);

        play.purchase = active(NOW.plusSeconds(172_800), true);
        PlaySubscriptionService.Outcome stale = service.onNotification("m2", NOW.minusSeconds(60), "com.strictworkout.app", TOKEN, 3);
        assertEquals("ACTIVE", stale.subscription().state());
        assertEquals(NOW.plusSeconds(172_800), stale.subscription().expiry());
    }

    @Test
    void missedNotificationReconciliationRereadsPlay() {
        service.verify(TOKEN, PRODUCT, null);
        play.purchase = active(NOW.minusSeconds(10), false);
        repository.rows.values().iterator().next();
        StoredPlaySubscription stored = repository.rows.values().iterator().next();
        repository.rows.put(stored.tokenHash(), new StoredPlaySubscription(
                stored.tokenHash(), stored.purchaseToken(), stored.packageName(), stored.productId(), stored.basePlanId(),
                stored.state(), stored.expiry(), stored.autoRenewing(), stored.entitled(), stored.linkedUserId(),
                stored.orderId(), NOW.minusSeconds(7 * 3600), stored.claimBlocked()
        ));
        assertEquals(1, service.reconcile(NOW.plusSeconds(1)));
        assertFalse(repository.rows.values().iterator().next().entitled());
    }

    @Test
    void aBlockedTokenCannotBeClaimedAndNotificationsLeaveItUnlinked() {
        service.verify(TOKEN, PRODUCT, null);
        StoredPlaySubscription stored = repository.rows.values().iterator().next();
        repository.rows.put(stored.tokenHash(), new StoredPlaySubscription(
                stored.tokenHash(), stored.purchaseToken(), stored.packageName(), stored.productId(), stored.basePlanId(),
                stored.state(), stored.expiry(), stored.autoRenewing(), stored.entitled(), null,
                stored.orderId(), stored.updatedAt(), true
        ));
        UUID user = UUID.randomUUID();
        assertEquals(PlaySubscriptionService.Status.CONFLICT, service.verify(TOKEN, PRODUCT, user).status());
        play.purchase = active(NOW.plusSeconds(1_000), true);
        assertEquals(
                PlaySubscriptionService.Status.VERIFIED,
                service.onNotification("blocked", NOW, "com.strictworkout.app", TOKEN, 2).status()
        );
        StoredPlaySubscription after = repository.rows.values().iterator().next();
        assertTrue(after.claimBlocked());
        assertNull(after.linkedUserId());
        assertEquals(NOW.plusSeconds(1_000), after.expiry());
    }

    @Test
    void expiredWithAFutureExpiryIsNotEntitled() {
        UUID user = UUID.randomUUID();
        service.verify(TOKEN, PRODUCT, user);
        play.purchase = new PlayPurchase("com.strictworkout.app", "EXPIRED", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(300), false)));
        PlaySubscriptionService.Outcome current = service.current(user);
        assertEquals(PlaySubscriptionService.Status.VERIFIED, current.status());
        assertFalse(current.subscription().entitled());
        assertEquals(NOW.plusSeconds(300), current.subscription().expiry());
        assertFalse(repository.findLinked(user).orElseThrow().entitled());
    }

    @Test
    void canceledWithAFutureExpiryStaysEntitled() {
        UUID user = UUID.randomUUID();
        service.verify(TOKEN, PRODUCT, user);
        play.purchase = new PlayPurchase("com.strictworkout.app", "CANCELED", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(3_600), false)));
        PlaySubscriptionService.Outcome current = service.current(user);
        assertTrue(current.subscription().entitled());
        assertFalse(current.subscription().autoRenewing());
        assertEquals(NOW.plusSeconds(3_600), current.subscription().expiry());
    }

    @Test
    void unavailableAndInvalidTokenLeaveTheStoredEntitlement() {
        UUID user = UUID.randomUUID();
        service.verify(TOKEN, PRODUCT, user);
        play.failure = PlayApiFailure.UNAVAILABLE;
        assertEquals(PlaySubscriptionService.Status.UNAVAILABLE, service.current(user).status());
        assertTrue(repository.findLinked(user).orElseThrow().entitled());
        BillingController controller = new BillingController(
                service,
                new GooglePushAuthenticator("", ""),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new eu.strictworkout.auth.StrictUserAuthentication(
                        new eu.strictworkout.auth.StrictPrincipal(user, UUID.randomUUID())
                )
        );
        try {
            org.springframework.http.ResponseEntity<?> unavailable = controller.current();
            assertEquals(503, unavailable.getStatusCode().value());
            assertEquals(java.util.Map.of("status", "UNAVAILABLE"), unavailable.getBody());
            assertFalse(String.valueOf(unavailable.getBody()).contains(TOKEN));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        assertTrue(repository.findLinked(user).orElseThrow().entitled());
        play.failure = PlayApiFailure.INVALID_TOKEN;
        assertEquals(PlaySubscriptionService.Status.INVALID_TOKEN, service.current(user).status());
        assertTrue(repository.findLinked(user).orElseThrow().entitled());
        assertEquals("ACTIVE", repository.findLinked(user).orElseThrow().state());
    }

    @Test
    void aUserWithoutALinkedSubscriptionIsAbsent() {
        int fetches = play.fetches;
        assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(UUID.randomUUID()).status());
        assertEquals(fetches, play.fetches);
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void currentBelongsToTheAuthenticatedUserAndHidesThePurchaseToken() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        service.verify(TOKEN, PRODUCT, owner);
        int fetches = play.fetches;
        assertEquals(PlaySubscriptionService.Status.ABSENT, service.current(other).status());
        assertEquals(fetches, play.fetches);
        assertEquals(owner, repository.findLinked(owner).orElseThrow().linkedUserId());

        BillingController controller = new BillingController(
                service,
                new GooglePushAuthenticator("", ""),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertEquals(401, controller.current().getStatusCode().value());

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new eu.strictworkout.auth.StrictUserAuthentication(
                        new eu.strictworkout.auth.StrictPrincipal(owner, UUID.randomUUID())
                )
        );
        try {
            play.purchase = new PlayPurchase("com.strictworkout.app", "EXPIRED", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED", "GPA.1",
                    List.of(new PlayLineItem(PRODUCT, "monthly", NOW.plusSeconds(300), false)));
            org.springframework.http.ResponseEntity<?> response = controller.current();
            assertEquals(200, response.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertEquals(false, body.get("entitled"));
            assertEquals("EXPIRED", body.get("state"));
            assertFalse(body.containsKey("purchaseToken"));
            assertFalse(body.values().toString().contains(TOKEN));

            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new eu.strictworkout.auth.StrictUserAuthentication(
                            new eu.strictworkout.auth.StrictPrincipal(other, UUID.randomUUID())
                    )
            );
            org.springframework.http.ResponseEntity<?> absent = controller.current();
            assertEquals(200, absent.getStatusCode().value());
            assertEquals(Map.of("status", "ABSENT"), absent.getBody());
            assertFalse(repository.findLinked(owner).orElseThrow().entitled());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rtdnPayloadDecodesTheSubscriptionToken() {
        String notification = """
                {"packageName":"com.strictworkout.app","eventTimeMillis":"1503349566168","subscriptionNotification":{"notificationType":4,"purchaseToken":"token-1"}}
                """;
        String body = "{\"message\":{\"messageId\":\"abc\",\"data\":\""
                + Base64.getEncoder().encodeToString(notification.getBytes())
                + "\"}}";
        ParsedRtdn parsed = ParsedRtdn.parse(body);
        assertEquals("abc", parsed.messageId());
        assertEquals("token-1", parsed.purchaseToken());
        assertEquals(4, parsed.notificationType());
        assertTrue(parsed.subscription());
        assertNull(service.verify(null, PRODUCT, null).subscription());
    }

    private static PlayPurchase active(Instant expiry, boolean renew) {
        return new PlayPurchase(
                "com.strictworkout.app",
                "ACTIVE",
                "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
                "GPA.1",
                List.of(new PlayLineItem(PRODUCT, "monthly", expiry, renew))
        );
    }

    private static final class ScriptedPlay implements PlayDeveloperApi {
        PlayPurchase purchase;
        PlayApiFailure failure;
        int fetches;
        int acknowledgements;

        @Override
        public PlayPurchase fetch(String packageName, String purchaseToken) {
            fetches++;
            if (failure != null) {
                throw new PlayApiException(failure);
            }
            return purchase;
        }

        @Override
        public void acknowledge(String packageName, String productId, String purchaseToken) {
            acknowledgements++;
            if (purchase != null) {
                purchase = new PlayPurchase(
                        purchase.packageName(),
                        purchase.state(),
                        "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
                        purchase.orderId(),
                        purchase.lineItems()
                );
            }
        }
    }

    private static final class MemoryRepository implements PlaySubscriptionRepository {
        final Map<String, StoredPlaySubscription> rows = new HashMap<>();
        final Set<String> messages = new HashSet<>();

        @Override
        public Optional<StoredPlaySubscription> find(String tokenHash) {
            return Optional.ofNullable(rows.get(tokenHash));
        }

        @Override
        public Optional<StoredPlaySubscription> findLinked(UUID userId) {
            return rows.values().stream()
                    .filter(row -> userId != null && userId.equals(row.linkedUserId()))
                    .max(Comparator.comparing(StoredPlaySubscription::updatedAt));
        }

        @Override
        public StoredPlaySubscription save(StoredPlaySubscription subscription) {
            rows.put(subscription.tokenHash(), subscription);
            return subscription;
        }

        @Override
        public List<StoredPlaySubscription> reconciliationCandidates(Instant now) {
            return new ArrayList<>(rows.values());
        }

        @Override
        public boolean rememberMessage(String messageId, Instant eventTime, int notificationType) {
            return messages.add(messageId);
        }
    }
}
