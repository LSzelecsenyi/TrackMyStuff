package eu.strictworkout.promotion;

import eu.strictworkout.account.DeletedAccountPolicy;
import eu.strictworkout.billing.AccountPaidAccess;
import eu.strictworkout.founder.FounderApplicationRepository;
import eu.strictworkout.founder.FounderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Server decision for Pro Discovery V3.
 * An existing promotional trial is never moved and is never extended.
 * Workout history is not an input.
 */
@Service
public class ProDiscoveryGrants {

    public static final String NOT_ELIGIBLE = "NOT_ELIGIBLE";
    public static final String ELIGIBLE = "ELIGIBLE";
    public static final String ACTIVE_TRIAL = "ACTIVE_TRIAL";
    public static final String ALREADY_USED = "ALREADY_USED";
    public static final String PROGRAM_CLOSED = "PROGRAM_CLOSED";
    public static final String INCOMPATIBLE = "INCOMPATIBLE";

    public static final String OFFER_NONE = "NONE";
    public static final String OFFER_TRIAL = "TRIAL";
    public static final String OFFER_SUBSCRIPTION = "SUBSCRIPTION";

    private final PromotionalTrialService trials;
    private final ProDiscoveryOffers offers;
    private final PromotionalPrograms programs;
    private final FounderApplicationRepository applications;
    private final AccountPaidAccess paidAccess;
    private final WelcomeBackService welcomeBack;
    private final DeletedAccountPolicy deletedAccounts;
    private final Clock clock;

    public ProDiscoveryGrants(
            PromotionalTrialService trials,
            ProDiscoveryOffers offers,
            PromotionalPrograms programs,
            FounderApplicationRepository applications,
            AccountPaidAccess paidAccess,
            WelcomeBackService welcomeBack,
            DeletedAccountPolicy deletedAccounts,
            Clock clock
    ) {
        this.trials = trials;
        this.offers = offers;
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
        offers.lock(userId);
        View current = evaluate(userId, now);
        if (!ELIGIBLE.equals(current.status()) || !OFFER_TRIAL.equals(current.offer())) {
            return current;
        }
        trials.activate(userId, now, true, false);
        return evaluate(userId, now);
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
        Instant registration = offers.registeredAt(userId);
        Instant presented = offers.presentedAt(userId);
        Instant anchor = presented == null ? registration : presented;
        Instant nextOfferAt = anchor == null ? null : ProDiscoveryEligibility.offerDueAt(anchor);
        boolean due = ProDiscoveryEligibility.offerDue(anchor, now);
        if (trial != null && now.isBefore(trial.expiresAt())) {
            return view(ACTIVE_TRIAL, OFFER_NONE, trial.activatedAt(), trial.expiresAt(), nextOfferAt);
        }
        if (incompatible(userId, now)) {
            return view(INCOMPATIBLE, OFFER_NONE, trialTime(trial, true), trialTime(trial, false), nextOfferAt);
        }
        boolean consumed = trial != null || deletedAccounts.proDiscoveryUsed(userId);
        if (consumed && !due) {
            return view(ALREADY_USED, OFFER_NONE, trialTime(trial, true), trialTime(trial, false), nextOfferAt);
        }
        if (!programs.acceptingNewGrants(PromotionalProgram.PRO_DISCOVERY)) {
            return view(PROGRAM_CLOSED, OFFER_NONE, trialTime(trial, true), trialTime(trial, false), nextOfferAt);
        }
        if (!due) {
            return view(NOT_ELIGIBLE, OFFER_NONE, null, null, nextOfferAt);
        }
        if (consumed) {
            return view(ALREADY_USED, OFFER_SUBSCRIPTION, trialTime(trial, true), trialTime(trial, false), nextOfferAt);
        }
        return view(ELIGIBLE, OFFER_TRIAL, null, null, nextOfferAt);
    }

    private boolean incompatible(UUID userId, Instant now) {
        boolean founderBlocks = applications.findStatusByUserId(userId)
                .map(status -> status == FounderStatus.APPROVED
                        || status == FounderStatus.ACTIVE_PRO
                        || status == FounderStatus.PENDING_APPROVAL)
                .orElse(false);
        return founderBlocks || paidAccess.entitledNow(userId, now) || welcomeBack.activeNow(userId, now);
    }

    private static Instant trialTime(PromotionalTrialService.Trial trial, boolean activated) {
        if (trial == null) {
            return null;
        }
        return activated ? trial.activatedAt() : trial.expiresAt();
    }

    private static View view(
            String status,
            String offer,
            Instant activatedAt,
            Instant expiresAt,
            Instant nextOfferAt
    ) {
        return new View(status, offer, activatedAt, expiresAt, nextOfferAt);
    }

    public record View(
            String status,
            String offer,
            Instant activatedAt,
            Instant expiresAt,
            Instant nextOfferAt
    ) {
    }
}
