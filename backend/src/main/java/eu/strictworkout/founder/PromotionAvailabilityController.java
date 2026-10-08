package eu.strictworkout.founder;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read used by the Android app to decide whether a new promotional trial may start.
 * Enrollment, qualification, and grants are unchanged. Existing trials are not stored here.
 */
@RestController
class PromotionAvailabilityController {

    private final FounderEnrollment enrollment;

    PromotionAvailabilityController(FounderEnrollment enrollment) {
        this.enrollment = enrollment;
    }

    @GetMapping("/api/v1/promotions/availability")
    PromotionAvailability availability() {
        boolean founderProgramActive = enrollment.current().open();
        return new PromotionAvailability(founderProgramActive, !founderProgramActive);
    }

    public record PromotionAvailability(boolean founderProgramActive, boolean promotionsEnabled) {
    }
}
