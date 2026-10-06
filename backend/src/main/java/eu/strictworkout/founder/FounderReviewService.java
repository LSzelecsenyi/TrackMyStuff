package eu.strictworkout.founder;

import eu.strictworkout.admin.AdminUserRepository;
import eu.strictworkout.entitlement.EntitlementGrant;
import eu.strictworkout.entitlement.EntitlementGrantRepository;
import eu.strictworkout.identity.AppUserRepository;
import eu.strictworkout.identity.ExternalIdentityRepository;
import eu.strictworkout.identity.IdentityProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FounderReviewService {

    public static final int REASON_MAX_LENGTH = 2000;

    private final FounderApplicationRepository applications;
    private final FounderReviewSnapshotRepository snapshots;
    private final FounderWorkoutEventRepository events;
    private final FounderReviewDecisionRepository decisions;
    private final EntitlementGrantRepository grants;
    private final AdminUserRepository admins;
    private final AppUserRepository users;
    private final ExternalIdentityRepository identities;
    private final Clock clock;

    public FounderReviewService(
            FounderApplicationRepository applications,
            FounderReviewSnapshotRepository snapshots,
            FounderWorkoutEventRepository events,
            FounderReviewDecisionRepository decisions,
            EntitlementGrantRepository grants,
            AdminUserRepository admins,
            AppUserRepository users,
            ExternalIdentityRepository identities,
            Clock clock
    ) {
        this.applications = applications;
        this.snapshots = snapshots;
        this.events = events;
        this.decisions = decisions;
        this.grants = grants;
        this.admins = admins;
        this.users = users;
        this.identities = identities;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FounderReviewSummary> pending() {
        return applications.findByStatusOrderByPendingAtAsc(FounderStatus.PENDING_APPROVAL).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public FounderReviewDetail get(UUID applicationId) {
        return detail(applications.findById(applicationId).orElseThrow(FounderReviewService::missing));
    }

    @Transactional
    public FounderReviewDetail approve(UUID adminId, UUID applicationId) {
        FounderApplication application = lock(applicationId);
        return switch (FounderReviewRules.approve(application.getStatus())) {
            case IDEMPOTENT -> detail(application);
            case CONFLICT -> throw conflict();
            case APPLY -> {
                Instant now = clock.instant();
                application.markReviewed(FounderStatus.APPROVED, now);
                grants.saveAndFlush(new EntitlementGrant(
                        UUID.randomUUID(),
                        users.findById(application.getUserId()).orElseThrow(),
                        application,
                        now
                ));
                decisions.saveAndFlush(new FounderReviewDecision(
                        UUID.randomUUID(),
                        application,
                        admins.findById(adminId).orElseThrow(),
                        FounderStatus.APPROVED,
                        null,
                        now
                ));
                yield detail(application);
            }
        };
    }

    @Transactional
    public FounderReviewDetail reject(UUID adminId, UUID applicationId, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > REASON_MAX_LENGTH) {
            throw new FounderCommandException(
                    HttpStatus.BAD_REQUEST,
                    "REJECTION_REASON_REQUIRED",
                    "A rejection reason is required."
            );
        }
        FounderApplication application = lock(applicationId);
        FounderReviewDecision existing = decisions.findByApplication_Id(application.getId()).orElse(null);
        return switch (FounderReviewRules.reject(
                application.getStatus(),
                existing == null ? "" : existing.getReason(),
                trimmed
        )) {
            case IDEMPOTENT -> detail(application);
            case CONFLICT -> throw conflict();
            case APPLY -> {
                Instant now = clock.instant();
                application.markReviewed(FounderStatus.REJECTED, now);
                decisions.saveAndFlush(new FounderReviewDecision(
                        UUID.randomUUID(),
                        application,
                        admins.findById(adminId).orElseThrow(),
                        FounderStatus.REJECTED,
                        trimmed,
                        now
                ));
                yield detail(application);
            }
        };
    }

    private FounderApplication lock(UUID applicationId) {
        return applications.lockById(applicationId).orElseThrow(FounderReviewService::missing);
    }

    private FounderReviewSummary summary(FounderApplication application) {
        return FounderReviewSummary.from(
                application,
                verifiedEmail(application),
                snapshots.findByApplicationId(application.getId()).orElse(null)
        );
    }

    private FounderReviewDetail detail(FounderApplication application) {
        return FounderReviewDetail.from(
                application,
                verifiedEmail(application),
                snapshots.findByApplicationId(application.getId()).orElse(null),
                events.findForReview(application.getId()),
                decisions.findByApplication_Id(application.getId()).orElse(null)
        );
    }

    private String verifiedEmail(FounderApplication application) {
        return identities.findByUser_IdAndProvider(application.getUserId(), IdentityProvider.GOOGLE)
                .filter(identity -> identity.isEmailVerified() && identity.getEmail() != null)
                .map(identity -> identity.getEmail())
                .orElse(null);
    }

    private static FounderCommandException missing() {
        return new FounderCommandException(
                HttpStatus.NOT_FOUND,
                "FOUNDER_NOT_FOUND",
                "Founder application was not found."
        );
    }

    private static FounderCommandException conflict() {
        return new FounderCommandException(
                HttpStatus.CONFLICT,
                "REVIEW_CONFLICT",
                "The Founder application cannot take that review decision."
        );
    }
}
