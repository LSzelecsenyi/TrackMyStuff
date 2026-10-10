package eu.strictworkout.founder;

import eu.strictworkout.account.DeletedAccountPolicy;
import eu.strictworkout.auth.StrictRequests;
import eu.strictworkout.billing.AccountPaidAccess;
import eu.strictworkout.promotion.PromotionalTrialService;
import eu.strictworkout.promotion.WelcomeBackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
class WelcomeBackController {

    private final WelcomeBackService welcomeBack;
    private final PromotionalTrialService discovery;
    private final FounderEnrollment enrollment;
    private final FounderApplicationRepository applications;
    private final AccountPaidAccess paidAccess;
    private final DeletedAccountPolicy deletedAccounts;
    private final Clock clock;

    WelcomeBackController(
            WelcomeBackService welcomeBack,
            PromotionalTrialService discovery,
            FounderEnrollment enrollment,
            FounderApplicationRepository applications,
            AccountPaidAccess paidAccess,
            DeletedAccountPolicy deletedAccounts,
            Clock clock
    ) {
        this.welcomeBack = welcomeBack;
        this.discovery = discovery;
        this.enrollment = enrollment;
        this.applications = applications;
        this.paidAccess = paidAccess;
        this.deletedAccounts = deletedAccounts;
        this.clock = clock;
    }

    @GetMapping("/api/v1/promotions/welcome-back")
    ResponseEntity<?> current() {
        WelcomeBackService.Grant grant = welcomeBack.current(StrictRequests.current().userId());
        if (grant == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(body(grant));
    }

    @PostMapping("/api/v1/promotions/welcome-back/activate")
    ResponseEntity<?> activate(@RequestBody ActivateRequest request) {
        UUID userId = StrictRequests.current().userId();
        if (deletedAccounts.welcomeBackUsed(userId)) {
            return respond(new WelcomeBackService.Result(WelcomeBackService.Status.CONSUMED, null, null, null));
        }
        return respond(welcomeBack.activate(
                userId,
                request.qualifyingWorkoutId(),
                clock.instant(),
                !enrollment.current().open(),
                incompatible(userId)
        ));
    }

    private boolean incompatible(UUID userId) {
        boolean founderPro = applications.findStatusByUserId(userId)
                .map(status -> status == FounderStatus.APPROVED
                        || status == FounderStatus.ACTIVE_PRO
                        || status == FounderStatus.PENDING_APPROVAL)
                .orElse(false);
        return founderPro
                || paidAccess.entitledNow(userId, clock.instant())
                || discovery.activeNow(userId, clock.instant());
    }

    private static ResponseEntity<?> respond(WelcomeBackService.Result result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result.status().name());
        payload.put("activatedAt", result.activatedAt() == null ? "" : result.activatedAt().toString());
        payload.put("expiresAt", result.expiresAt() == null ? "" : result.expiresAt().toString());
        payload.put("cooldownUntil", result.cooldownUntil() == null ? "" : result.cooldownUntil().toString());
        return switch (result.status()) {
            case ACTIVE -> ResponseEntity.ok(payload);
            case CLOSED -> ResponseEntity.status(403).body(payload);
            case COOLDOWN, REPLAY, INCOMPATIBLE, INVALID, CONSUMED -> ResponseEntity.status(409).body(payload);
        };
    }

    private static Map<String, Object> body(WelcomeBackService.Grant grant) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualifyingWorkoutId", grant.workoutId());
        payload.put("activatedAt", grant.activatedAt().toString());
        payload.put("expiresAt", grant.expiresAt().toString());
        payload.put("cooldownUntil", grant.cooldownUntil().toString());
        return payload;
    }

    public record ActivateRequest(String qualifyingWorkoutId) {
    }
}
