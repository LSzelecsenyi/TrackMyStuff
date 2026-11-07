package eu.strictworkout.founder;

import eu.strictworkout.promotion.PromotionalProgram;
import eu.strictworkout.promotion.PromotionalPrograms;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read used by the Android app. Each program has its own flag.
 * promotionsEnabled stays true only when both promotions accept new grants,
 * which is the only single value an older client can apply to both offers.
 */
@RestController
class PromotionAvailabilityController {

    private final FounderEnrollment enrollment;
    private final PromotionalPrograms promotions;

    PromotionAvailabilityController(FounderEnrollment enrollment, PromotionalPrograms promotions) {
        this.enrollment = enrollment;
        this.promotions = promotions;
    }

    @GetMapping("/api/v1/promotions/availability")
    PromotionAvailability availability() {
        boolean founderProgramActive = enrollment.current().open();
        boolean proDiscoveryEnabled = promotions.acceptingNewGrants(PromotionalProgram.PRO_DISCOVERY);
        boolean welcomeBackEnabled = promotions.acceptingNewGrants(PromotionalProgram.WELCOME_BACK);
        return new PromotionAvailability(
                founderProgramActive,
                proDiscoveryEnabled && welcomeBackEnabled,
                proDiscoveryEnabled,
                welcomeBackEnabled
        );
    }

    public record PromotionAvailability(
            boolean founderProgramActive,
            boolean promotionsEnabled,
            boolean proDiscoveryEnabled,
            boolean welcomeBackEnabled
    ) {
    }
}
