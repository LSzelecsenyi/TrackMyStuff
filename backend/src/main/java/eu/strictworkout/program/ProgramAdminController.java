package eu.strictworkout.program;

import eu.strictworkout.account.EarlyAdopterProgram;
import eu.strictworkout.founder.FounderEnrollment;
import eu.strictworkout.promotion.PromotionalProgram;
import eu.strictworkout.promotion.PromotionalPrograms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/programs")
public class ProgramAdminController {

    private final FounderEnrollment founders;
    private final EarlyAdopterProgram earlyAdopters;
    private final PromotionalPrograms promotions;

    public ProgramAdminController(
            FounderEnrollment founders,
            EarlyAdopterProgram earlyAdopters,
            PromotionalPrograms promotions
    ) {
        this.founders = founders;
        this.earlyAdopters = earlyAdopters;
        this.promotions = promotions;
    }

    @GetMapping
    public ProgramsView current() {
        return new ProgramsView(
                founderSnapshot(),
                earlyAdopters.current(),
                promotion(PromotionalProgram.PRO_DISCOVERY),
                promotion(PromotionalProgram.WELCOME_BACK)
        );
    }

    @PutMapping("/founder")
    public ProgramSnapshot founder(@Valid @RequestBody LifecycleRequest request) {
        founders.transition(request.lifecycle());
        return founderSnapshot();
    }

    @PutMapping("/early-adopter")
    public ProgramSnapshot earlyAdopter(@Valid @RequestBody LifecycleRequest request) {
        return earlyAdopters.transition(request.lifecycle());
    }

    @PutMapping("/pro-discovery")
    public PromotionView proDiscovery(@Valid @RequestBody LifecycleRequest request) {
        promotions.transition(PromotionalProgram.PRO_DISCOVERY, request.lifecycle());
        return promotion(PromotionalProgram.PRO_DISCOVERY);
    }

    @PutMapping("/welcome-back")
    public PromotionView welcomeBack(@Valid @RequestBody LifecycleRequest request) {
        promotions.transition(PromotionalProgram.WELCOME_BACK, request.lifecycle());
        return promotion(PromotionalProgram.WELCOME_BACK);
    }

    private ProgramSnapshot founderSnapshot() {
        FounderEnrollment.FounderEnrollmentState state = founders.current();
        return ProgramSnapshot.of("FOUNDER", state.lifecycle(), state.enrolledCount(), state.capacity());
    }

    private PromotionView promotion(String program) {
        PromotionalProgram stored = promotions.current(program);
        return PromotionView.of(stored.getProgram(), stored.getLifecycle());
    }

    public record ProgramsView(
            ProgramSnapshot founder,
            ProgramSnapshot earlyAdopter,
            PromotionView proDiscovery,
            PromotionView welcomeBack
    ) {
    }

    public record PromotionView(
            String program,
            ProgramLifecycle lifecycle,
            boolean acceptingNewGrants,
            String summary,
            String eligibility
    ) {
        public static PromotionView of(String program, ProgramLifecycle lifecycle) {
            boolean discovery = PromotionalProgram.PRO_DISCOVERY.equals(program);
            return new PromotionView(
                    program,
                    lifecycle,
                    lifecycle == ProgramLifecycle.ACTIVE,
                    discovery
                            ? "A Pro offer two calendar months after registration, then every two calendar months after the offer was shown. The first offer can start one 14-day trial. Later offers lead to subscription."
                            : "A 7-day Pro grant after a qualifying return workout, followed by a 180-day cooldown.",
                    discovery
                            ? "A new trial is issued only while this program is Active, the account is two calendar months old, and that Google account has not used the trial. An existing trial keeps its original expiry. No workout upload is required."
                            : "A new grant is issued only while this program is Active, and not while Founder Pro, paid Pro, or Pro Discovery is already active. An existing grant stays in place."
            );
        }
    }

    public record LifecycleRequest(@NotNull ProgramLifecycle lifecycle) {
    }
}
