package eu.strictworkout.founder;

import eu.strictworkout.auth.StrictRequests;
import eu.strictworkout.billing.AccountPaidAccess;
import eu.strictworkout.promotion.PromotionalTrialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
class ProDiscoveryTrialController {

    private final PromotionalTrialService trials;
    private final FounderEnrollment enrollment;
    private final FounderApplicationRepository applications;
    private final AccountPaidAccess paidAccess;
    private final Clock clock;

    ProDiscoveryTrialController(
            PromotionalTrialService trials,
            FounderEnrollment enrollment,
            FounderApplicationRepository applications,
            AccountPaidAccess paidAccess,
            Clock clock
    ) {
        this.trials = trials;
        this.enrollment = enrollment;
        this.applications = applications;
        this.paidAccess = paidAccess;
        this.clock = clock;
    }

    @PostMapping("/api/v1/promotions/pro-discovery/activate")
    ResponseEntity<?> activate() {
        return respond(trials.activate(
                StrictRequests.current().userId(),
                clock.instant(),
                promotionsOpen(),
                incompatible(StrictRequests.current().userId())
        ));
    }

    @PostMapping("/api/v1/promotions/pro-discovery/migrate")
    ResponseEntity<?> migrate(@RequestBody MigrateRequest request) {
        return respond(trials.migrate(
                StrictRequests.current().userId(),
                request.activatedAt(),
                request.expiresAt(),
                clock.instant()
        ));
    }

    private boolean promotionsOpen() {
        return !enrollment.current().open();
    }

    private boolean incompatible(UUID userId) {
        boolean founderPro = applications.findStatusByUserId(userId)
                .map(status -> status == FounderStatus.APPROVED
                        || status == FounderStatus.ACTIVE_PRO
                        || status == FounderStatus.PENDING_APPROVAL)
                .orElse(false);
        return founderPro || paidAccess.entitledNow(userId, clock.instant());
    }

    private static ResponseEntity<?> respond(PromotionalTrialService.Result result) {
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

    public record MigrateRequest(Instant activatedAt, Instant expiresAt) {
    }
}
