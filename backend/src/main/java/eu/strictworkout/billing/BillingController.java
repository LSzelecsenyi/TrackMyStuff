package eu.strictworkout.billing;

import eu.strictworkout.auth.StrictUserAuthentication;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
class BillingController {

    private final PlaySubscriptionService subscriptions;
    private final GooglePushAuthenticator push;
    private final Clock clock;

    BillingController(PlaySubscriptionService subscriptions, GooglePushAuthenticator push, Clock clock) {
        this.subscriptions = subscriptions;
        this.push = push;
        this.clock = clock;
    }

    @PostMapping("/api/v1/billing/subscriptions/verify")
    ResponseEntity<?> verify(@RequestBody VerifyRequest request) {
        UUID user = currentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHENTICATED"));
        }
        PlaySubscriptionService.Outcome outcome = subscriptions.verify(
                request.purchaseToken(),
                request.productId(),
                user
        );
        return switch (outcome.status()) {
            case VERIFIED -> ResponseEntity.ok(body(outcome.subscription()));
            case NOT_CONFIGURED, UNAVAILABLE -> ResponseEntity.status(503).body(Map.of("status", outcome.status().name()));
            case CONFLICT -> ResponseEntity.status(409).body(Map.of("status", "CONFLICT"));
            default -> ResponseEntity.badRequest().body(Map.of("status", outcome.status().name()));
        };
    }

    @PostMapping("/api/v1/billing/rtdn")
    ResponseEntity<?> rtdn(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String body
    ) {
        if (!push.configured()) {
            return ResponseEntity.status(503).body(Map.of("status", "NOT_CONFIGURED"));
        }
        if (!push.verify(authorization)) {
            return ResponseEntity.status(401).body(Map.of("status", "UNAUTHORIZED"));
        }
        ParsedRtdn parsed = ParsedRtdn.parse(body);
        if (!parsed.subscription()) {
            return ResponseEntity.noContent().build();
        }
        subscriptions.onNotification(
                parsed.messageId(),
                parsed.eventTime(),
                parsed.packageName(),
                parsed.purchaseToken(),
                parsed.notificationType()
        );
        return ResponseEntity.noContent().build();
    }

    @Scheduled(fixedDelay = 3_600_000L)
    void reconcileMissedNotifications() {
        subscriptions.reconcile(clock.instant());
    }

    private static Map<String, Object> body(StoredPlaySubscription subscription) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", subscription.productId());
        body.put("basePlanId", subscription.basePlanId());
        body.put("state", subscription.state());
        body.put("expiresAt", subscription.expiry() == null ? null : subscription.expiry().toString());
        body.put("autoRenewing", subscription.autoRenewing());
        body.put("entitled", subscription.entitled());
        return body;
    }

    private static UUID currentUser() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof StrictUserAuthentication authentication) {
            return authentication.getStrictPrincipal().userId();
        }
        return null;
    }

    public record VerifyRequest(String purchaseToken, String productId) {
    }
}
