package eu.strictworkout.founder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-side enrollment gate. Closing it does not remove people who are already enrolled.
 */
@RestController
@RequestMapping("/api/v1/admin/founder/enrollment")
public class FounderEnrollmentAdminController {

    private final FounderEnrollment enrollment;

    public FounderEnrollmentAdminController(FounderEnrollment enrollment) {
        this.enrollment = enrollment;
    }

    @GetMapping
    public FounderEnrollment.FounderEnrollmentState current() {
        return enrollment.current();
    }

    @PutMapping
    public FounderEnrollment.FounderEnrollmentState update(@Valid @RequestBody EnrollmentOpenRequest request) {
        return enrollment.setOpen(request.open());
    }

    public record EnrollmentOpenRequest(@NotNull Boolean open) {
    }
}
