package eu.strictworkout.founder;

import eu.strictworkout.auth.StrictRequests;
import eu.strictworkout.promotion.ProDiscoveryGrants;
import eu.strictworkout.promotion.ProDiscoveryOffers;
import eu.strictworkout.promotion.ProDiscoverySyncException;
import eu.strictworkout.promotion.PromotionalTrialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
class ProDiscoveryTrialController {

    private final ProDiscoveryGrants grants;
    private final ProDiscoveryOffers offers;
    private final Clock clock;

    ProDiscoveryTrialController(ProDiscoveryGrants grants, ProDiscoveryOffers offers, Clock clock) {
        this.grants = grants;
        this.offers = offers;
        this.clock = clock;
    }

    @GetMapping("/api/v1/promotions/pro-discovery")
    Map<String, Object> status() {
        return body(grants.status(StrictRequests.current().userId()));
    }

    @PostMapping("/api/v1/promotions/pro-discovery/presented")
    Map<String, Object> presented() {
        UUID userId = StrictRequests.current().userId();
        boolean recorded = offers.acknowledge(userId, clock.instant().truncatedTo(ChronoUnit.MICROS));
        Map<String, Object> response = new LinkedHashMap<>(body(grants.status(userId)));
        response.put("recorded", recorded);
        return response;
    }

    /**
     * Installed versionCode 7 can still call this. The answer is always off.
     * Nothing is stored.
     */
    @PostMapping("/api/v1/promotions/pro-discovery/activity/permission")
    Map<String, Object> permission(@RequestBody(required = false) Map<String, Object> ignored) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", false);
        body.put("scopeVersion", 1);
        body.put("generation", 0);
        return body;
    }

    /**
     * Installed versionCode 7 can still upload workout dates. The upload is
     * rejected and is not stored.
     */
    @PutMapping("/api/v1/promotions/pro-discovery/activity")
    Map<String, Object> sync(@RequestBody(required = false) Map<String, Object> ignored) {
        throw ProDiscoverySyncException.notPermitted();
    }

    @PostMapping("/api/v1/promotions/pro-discovery/activate")
    ResponseEntity<Map<String, Object>> activate() {
        return respond(grants.activate(StrictRequests.current().userId()));
    }

    @PostMapping("/api/v1/promotions/pro-discovery/migrate")
    ResponseEntity<?> migrate(@RequestBody MigrateRequest request) {
        PromotionalTrialService.Result result = grants.migrate(
                StrictRequests.current().userId(),
                request.activatedAt(),
                request.expiresAt()
        );
        Map<String, Object> body = Map.of(
                "status", result.status().name(),
                "activatedAt", result.activatedAt() == null ? "" : result.activatedAt().toString(),
                "expiresAt", result.expiresAt() == null ? "" : result.expiresAt().toString()
        );
        return switch (result.status()) {
            case ACTIVE -> ResponseEntity.ok(body);
            case CLOSED -> ResponseEntity.status(403).body(body);
            case ALREADY_USED, INCOMPATIBLE, INVALID -> ResponseEntity.status(409).body(body);
        };
    }

    private static ResponseEntity<Map<String, Object>> respond(ProDiscoveryGrants.View view) {
        Map<String, Object> payload = body(view);
        if ("PROGRAM_CLOSED".equals(view.status())) {
            payload.put("status", "CLOSED");
        }
        return switch (view.status()) {
            case "ACTIVE", "ACTIVE_TRIAL", "ELIGIBLE" -> ResponseEntity.ok(payload);
            case "PROGRAM_CLOSED" -> ResponseEntity.status(403).body(payload);
            default -> ResponseEntity.status(409).body(payload);
        };
    }

    private static Map<String, Object> body(ProDiscoveryGrants.View view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", view.status());
        body.put("offer", view.offer());
        body.put("activatedAt", view.activatedAt() == null ? "" : view.activatedAt().toString());
        body.put("expiresAt", view.expiresAt() == null ? "" : view.expiresAt().toString());
        body.put("nextOfferAt", view.nextOfferAt() == null ? "" : view.nextOfferAt().toString());
        return body;
    }

    public record MigrateRequest(Instant activatedAt, Instant expiresAt) {
    }
}
