package eu.strictworkout.founder;

import eu.strictworkout.account.DeletedAccountPolicy;
import eu.strictworkout.auth.StrictRequests;
import eu.strictworkout.billing.AccountPaidAccess;
import eu.strictworkout.promotion.PromotionalTrialService;
import eu.strictworkout.promotion.WelcomeBackService;
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
    private final WelcomeBackService welcomeBack;
    private final FounderEnrollment enrollment;
    private final FounderApplicationRepository applications;
    private final AccountPaidAccess paidAccess;
    private final DeletedAccountPolicy deletedAccounts;
    private final Clock clock;

    ProDiscoveryTrialController(
            PromotionalTrialService trials,
            WelcomeBackService welcomeBack,
            FounderEnrollment enrollment,
            FounderApplicationRepository applications,
            AccountPaidAccess paidAccess,
            DeletedAccountPolicy deletedAccounts,
            Clock clock
    ) {
        this.trials = trials;
        this.welcomeBack = welcomeBack;
        this.enrollment = enrollment;
        this.applications = applications;
        this.paidAccess = paidAccess;
        this.deletedAccounts = deletedAccounts;
        this.clock = clock;
    }

    @PostMapping("/api/v1/promotions/pro-discovery/activate")
    ResponseEntity<?> activate() {
        if (deletedAccounts.proDiscoveryUsed(StrictRequests.current().userId())) {
            return respond(new PromotionalTrialService.Result(PromotionalTrialService.Status.ALREADY_USED, null, null));
        }
        return respond(trials.activate(
                StrictRequests.current().userId(),
                clock.instant(),
                promotionsOpen(),
                incompatible(StrictRequests.current().userId())
        ));
    }

    @PostMapping("/api/v1/promotions/pro-discovery/migrate")
    ResponseEntity<?> migrate(@RequestBody MigrateRequest request) {
        if (deletedAccounts.proDiscoveryUsed(StrictRequests.current().userId())) {
            return respond(new PromotionalTrialService.Result(PromotionalTrialService.Status.ALREADY_USED, null, null));
        }
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
        return founderPro
                || paidAccess.entitledNow(userId, clock.instant())
                || welcomeBack.activeNow(userId, clock.instant());
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
