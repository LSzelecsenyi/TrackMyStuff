package eu.strictworkout.founder;

import eu.strictworkout.auth.StrictRequests;
import eu.strictworkout.promotion.ProDiscoveryActivity;
import eu.strictworkout.promotion.ProDiscoveryGrants;
import eu.strictworkout.promotion.ProDiscoverySyncException;
import eu.strictworkout.promotion.ProDiscoverySyncPermission;
import eu.strictworkout.promotion.PromotionalTrialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
class ProDiscoveryTrialController {

    private final ProDiscoveryGrants grants;
    private final ProDiscoveryActivity activity;
    private final ProDiscoverySyncPermission permission;
    private final Clock clock;

    ProDiscoveryTrialController(
            ProDiscoveryGrants grants,
            ProDiscoveryActivity activity,
            ProDiscoverySyncPermission permission,
            Clock clock
    ) {
        this.grants = grants;
        this.activity = activity;
        this.permission = permission;
        this.clock = clock;
    }

    @GetMapping("/api/v1/promotions/pro-discovery")
    Map<String, Object> status() {
        UUID userId = StrictRequests.current().userId();
        Map<String, Object> body = new LinkedHashMap<>(body(grants.status(userId)));
        ProDiscoverySyncPermission.Permission current = permission.current(userId);
        body.put("activitySyncEnabled", current.enabled());
        body.put("activityScopeVersion", current.scopeVersion());
        body.put("activityGeneration", current.generation());
        return body;
    }

    @PostMapping("/api/v1/promotions/pro-discovery/activity/permission")
    Map<String, Object> permission(@RequestBody PermissionRequest request) {
        UUID userId = StrictRequests.current().userId();
        ProDiscoverySyncPermission.Permission current = request.enabled()
                ? permission.enable(userId, request.scopeVersion(), clock.instant())
                : permission.withdraw(userId, clock.instant());
        return permissionBody(current);
    }

    @PutMapping("/api/v1/promotions/pro-discovery/activity")
    Map<String, Object> sync(@RequestBody SyncRequest request) {
        UUID userId = StrictRequests.current().userId();
        ProDiscoveryActivity.SyncResult result;
        if (request.installationId() == null || request.installationId().isBlank()) {
            activity.sync(
                    userId,
                    mode(request.mode()),
                    request.baseRevision(),
                    request.dates(),
                    clock.instant()
            );
            throw ProDiscoverySyncException.deviceRequired();
        }
        result = activity.syncDevice(
                userId,
                installation(request.installationId()),
                request.baseRevision(),
                request.generation(),
                request.dates(),
                clock.instant()
        );
        return Map.of("revision", result.revision(), "changed", result.changed());
    }

    @PostMapping("/api/v1/promotions/pro-discovery/activate")
    ResponseEntity<Map<String, Object>> activate() {
        ProDiscoveryGrants.View view = grants.activate(StrictRequests.current().userId());
        return respond(view);
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

    private static UUID installation(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (RuntimeException exception) {
            throw ProDiscoverySyncException.invalid();
        }
    }

    private static Map<String, Object> permissionBody(ProDiscoverySyncPermission.Permission current) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", current.enabled());
        body.put("scopeVersion", current.scopeVersion());
        body.put("generation", current.generation());
        return body;
    }

    private static ProDiscoveryActivity.Mode mode(String raw) {
        if ("UNION".equals(raw)) {
            return ProDiscoveryActivity.Mode.UNION;
        }
        if ("REPLACE".equals(raw)) {
            return ProDiscoveryActivity.Mode.REPLACE;
        }
        throw eu.strictworkout.promotion.ProDiscoverySyncException.invalid();
    }

    private static ResponseEntity<Map<String, Object>> respond(ProDiscoveryGrants.View view) {
        Map<String, Object> body = body(view);
        if ("PROGRAM_CLOSED".equals(view.status())) {
            body.put("status", "CLOSED");
        }
        return switch (view.status()) {
            case "ACTIVE", "ACTIVE_TRIAL", "ELIGIBLE" -> ResponseEntity.ok(body);
            case "PROGRAM_CLOSED" -> ResponseEntity.status(403).body(body);
            default -> ResponseEntity.status(409).body(body);
        };
    }

    private static Map<String, Object> body(ProDiscoveryGrants.View view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", view.status());
        body.put("activatedAt", view.activatedAt() == null ? "" : view.activatedAt().toString());
        body.put("expiresAt", view.expiresAt() == null ? "" : view.expiresAt().toString());
        body.put("activityRevision", view.activityRevision());
        body.put("qualifyingDays", view.qualifyingDays());
        body.put("remainingDays", view.remainingDays());
        body.put("firstWorkoutDate", view.firstWorkoutDate() == null ? "" : view.firstWorkoutDate().toString());
        body.put("matureOn", view.matureOn() == null ? "" : view.matureOn().toString());
        body.put("recentDays", view.recentDays());
        body.put("remainingRecentDays", view.remainingRecentDays());
        body.put("evaluationDate", view.evaluationDate().toString());
        body.put("requiredDays", view.requiredDays());
        body.put("requiredRecentDays", view.requiredRecentDays());
        return body;
    }

    public record SyncRequest(
            Long baseRevision,
            String mode,
            List<String> dates,
            String installationId,
            Long generation
    ) {
    }

    public record PermissionRequest(boolean enabled, int scopeVersion) {
    }

    public record MigrateRequest(Instant activatedAt, Instant expiresAt) {
    }
}
