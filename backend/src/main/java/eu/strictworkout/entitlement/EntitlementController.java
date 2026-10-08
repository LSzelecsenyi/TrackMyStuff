package eu.strictworkout.entitlement;

import eu.strictworkout.account.AccountStatusGrantRepository;
import eu.strictworkout.auth.StrictRequests;
import eu.strictworkout.founder.FounderApplication;
import eu.strictworkout.founder.FounderApplicationRepository;
import eu.strictworkout.founder.FounderFacts;
import eu.strictworkout.founder.FounderStateMachine;
import eu.strictworkout.founder.FounderStatus;
import eu.strictworkout.founder.FounderWorkoutEventRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/entitlements")
public class EntitlementController {

    private final FounderApplicationRepository applications;
    private final FounderWorkoutEventRepository events;
    private final EntitlementGrantRepository grants;
    private final AccountStatusGrantRepository accountStatuses;
    private final FounderStateMachine machine;
    private final eu.strictworkout.founder.FounderRules rules;
    private final Clock clock;

    public EntitlementController(
            FounderApplicationRepository applications,
            FounderWorkoutEventRepository events,
            EntitlementGrantRepository grants,
            AccountStatusGrantRepository accountStatuses,
            FounderStateMachine machine,
            eu.strictworkout.founder.FounderRules rules,
            Clock clock
    ) {
        this.applications = applications;
        this.events = events;
        this.grants = grants;
        this.accountStatuses = accountStatuses;
        this.machine = machine;
        this.rules = rules;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public EntitlementResponse current() {
        UUID userId = StrictRequests.current().userId();
        Instant now = clock.instant();
        FounderStatus status = applications.findIdByUserId(userId)
                .flatMap(applications::findById)
                .map(this::evaluatedStatus)
                .orElse(null);
        List<EntitlementGrant> founderGrants = grants.findByUser_Id(userId);
        EntitlementGrant legacy = founderGrants.stream().filter(EntitlementGrant::legacyLifetime).findFirst().orElse(null);
        EntitlementGrant founderPro = founderGrants.stream()
                .filter(grant -> EntitlementGrant.FOUNDER_PRO.equals(grant.getSource()))
                .findFirst()
                .orElse(null);
        boolean lifetime = legacy != null;
        boolean founderProActive = founderPro != null && founderPro.founderProActive(now);
        EffectiveEntitlement entitlement = EffectiveEntitlement.resolve(status, lifetime, founderProActive, false);
        EntitlementGrant recognition = founderPro != null ? founderPro : legacy;
        boolean founderRecognized = status == FounderStatus.APPROVED || recognition != null;
        List<SpecialAchievementResponse> specials = accountStatuses.findByUser_IdOrderByStatusAsc(userId)
                .stream()
                .map(grant -> new SpecialAchievementResponse(grant.getStatus().name(), grant.getGrantedAt()))
                .toList();
        return new EntitlementResponse(
                entitlement.access(),
                entitlement.founderLifetime(),
                entitlement.temporaryFounderPro(),
                recognition == null ? null : recognition.getGrantedAt(),
                specials,
                founderRecognized,
                founderPro == null ? null : founderPro.getExpiresAt()
        );
    }

    private FounderStatus evaluatedStatus(FounderApplication application) {
        return machine.evaluate(new FounderFacts(
                application.getStatus(),
                application.getEnrolledAt(),
                application.getDeadlineAt(),
                events.findByApplicationIdOrderByCreatedAtAsc(application.getId()).stream()
                        .map(event -> event.fact())
                        .toList(),
                application.feedbackSubmitted(),
                application.reportSubmitted()
        ), rules, clock.instant()).status();
    }

    public record EntitlementResponse(
            EntitlementAccess access,
            boolean founderLifetime,
            boolean temporaryFounderPro,
            Instant founderGrantedAt,
            List<SpecialAchievementResponse> specialAchievements,
            boolean founderRecognized,
            Instant founderProExpiresAt
    ) {
    }

    public record SpecialAchievementResponse(
            String key,
            Instant grantedAt
    ) {
    }
}
