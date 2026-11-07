package eu.strictworkout.promotion;

import eu.strictworkout.account.DeletedAccountPolicy;
import eu.strictworkout.billing.AccountPaidAccess;
import eu.strictworkout.founder.FounderApplicationRepository;
import eu.strictworkout.founder.FounderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Server decision for a new Pro Discovery grant.
 * An existing promotional_trial row is never moved and is never extended.
 */
@Service
public class ProDiscoveryGrants {

    public static final String NOT_ELIGIBLE = "NOT_ELIGIBLE";
    public static final String ELIGIBLE = "ELIGIBLE";
    public static final String ACTIVE_TRIAL = "ACTIVE_TRIAL";
    public static final String ALREADY_USED = "ALREADY_USED";
    public static final String PROGRAM_CLOSED = "PROGRAM_CLOSED";
    public static final String INCOMPATIBLE = "INCOMPATIBLE";

    private final PromotionalTrialService trials;
    private final ProDiscoveryActivity activity;
    private final PromotionalPrograms programs;
    private final FounderApplicationRepository applications;
    private final AccountPaidAccess paidAccess;
    private final eu.strictworkout.promotion.WelcomeBackService welcomeBack;
    private final DeletedAccountPolicy deletedAccounts;
    private final Clock clock;

    public ProDiscoveryGrants(
            PromotionalTrialService trials,
            ProDiscoveryActivity activity,
            PromotionalPrograms programs,
            FounderApplicationRepository applications,
            AccountPaidAccess paidAccess,
            WelcomeBackService welcomeBack,
            DeletedAccountPolicy deletedAccounts,
            Clock clock
    ) {
        this.trials = trials;
        this.activity = activity;
        this.programs = programs;
        this.applications = applications;
        this.paidAccess = paidAccess;
        this.welcomeBack = welcomeBack;
        this.deletedAccounts = deletedAccounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public View status(UUID userId) {
        return evaluate(userId, clock.instant());
    }

    @Transactional
    public View activate(UUID userId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        activity.lock(userId, now);
        View current = evaluate(userId, now);
        if (!ELIGIBLE.equals(current.status())) {
            return current;
        }
        PromotionalTrialService.Result created = trials.activate(userId, now, true, false);
        if (created.status() == PromotionalTrialService.Status.ACTIVE) {
            activity.deleteFor(userId);
        }
        return fromTrial(userId, created, now);
    }

    /**
     * Legacy device-trial import. Returns an existing server trial unchanged.
     * Does not create a trial that still grants Pro. An already expired device
     * trial can be recorded so the one-time use is consumed, without granting Pro.
     */
    @Transactional
    public PromotionalTrialService.Result migrate(UUID userId, Instant activatedAt, Instant expiresAt) {
        if (trials.stored(userId)) {
            return trials.migrate(userId, activatedAt, expiresAt, clock.instant());
        }
        if (deletedAccounts.proDiscoveryUsed(userId)) {
            return new PromotionalTrialService.Result(PromotionalTrialService.Status.ALREADY_USED, null, null);
        }
        if (incompatible(userId, clock.instant())) {
            return new PromotionalTrialService.Result(PromotionalTrialService.Status.INCOMPATIBLE, null, null);
        }
        if (!programs.acceptingNewGrants(PromotionalProgram.PRO_DISCOVERY)) {
            return new PromotionalTrialService.Result(PromotionalTrialService.Status.CLOSED, null, null);
        }
        return trials.migrate(userId, activatedAt, expiresAt, clock.instant());
    }

    private View evaluate(UUID userId, Instant now) {
        PromotionalTrialService.Trial trial = trials.current(userId);
        ProDiscoveryEligibility.Progress progress = ProDiscoveryEligibility.evaluate(activity.dates(userId), ProDiscoveryEligibility.evaluationDate(now));
        long revision = activity.revision(userId);
        if (trial != null && now.isBefore(trial.expiresAt())) {
            return view(ACTIVE_TRIAL, trial.activatedAt(), trial.expiresAt(), revision, progress, now);
        }
        if (trial != null || deletedAccounts.proDiscoveryUsed(userId)) {
            Instant activated = trial == null ? null : trial.activatedAt();
            Instant expires = trial == null ? null : trial.expiresAt();
            return view(ALREADY_USED, activated, expires, revision, progress, now);
        }
        if (!programs.acceptingNewGrants(PromotionalProgram.PRO_DISCOVERY)) {
            return view(PROGRAM_CLOSED, null, null, revision, progress, now);
        }
        if (incompatible(userId, now)) {
            return view(INCOMPATIBLE, null, null, revision, progress, now);
        }
        if (!progress.eligible()) {
            return view(NOT_ELIGIBLE, null, null, revision, progress, now);
        }
        return view(ELIGIBLE, null, null, revision, progress, now);
    }

    private View fromTrial(UUID userId, PromotionalTrialService.Result result, Instant now) {
        String status = switch (result.status()) {
            case ACTIVE -> "ACTIVE";
            case ALREADY_USED -> ALREADY_USED;
            case CLOSED -> PROGRAM_CLOSED;
            case INCOMPATIBLE -> INCOMPATIBLE;
            case INVALID -> NOT_ELIGIBLE;
        };
        ProDiscoveryEligibility.Progress progress = ProDiscoveryEligibility.evaluate(
                activity.dates(userId),
                ProDiscoveryEligibility.evaluationDate(now)
        );
        return view(status, result.activatedAt(), result.expiresAt(), activity.revision(userId), progress, now);
    }

    private boolean incompatible(UUID userId, Instant now) {
        boolean founderBlocks = applications.findStatusByUserId(userId)
                .map(status -> status == FounderStatus.APPROVED
                        || status == FounderStatus.ACTIVE_PRO
                        || status == FounderStatus.PENDING_APPROVAL)
                .orElse(false);
        return founderBlocks || paidAccess.entitledNow(userId, now) || welcomeBack.activeNow(userId, now);
    }

    private static View view(
            String status,
            Instant activatedAt,
            Instant expiresAt,
            long revision,
            ProDiscoveryEligibility.Progress progress,
            Instant now
    ) {
        return new View(
                status,
                activatedAt,
                expiresAt,
                revision,
                progress.qualifyingDays(),
                progress.remainingDays(),
                progress.firstWorkoutDate(),
                progress.matureOn(),
                progress.recentDays(),
                progress.remainingRecentDays(),
                ProDiscoveryEligibility.evaluationDate(now),
                ProDiscoveryEligibility.REQUIRED_DAYS,
                ProDiscoveryEligibility.REQUIRED_RECENT_DAYS
        );
    }

    public record View(
            String status,
            Instant activatedAt,
            Instant expiresAt,
            long activityRevision,
            int qualifyingDays,
            int remainingDays,
            LocalDate firstWorkoutDate,
            LocalDate matureOn,
            int recentDays,
            int remainingRecentDays,
            LocalDate evaluationDate,
            int requiredDays,
            int requiredRecentDays
    ) {
    }
}
