package eu.strictworkout.founder;

import eu.strictworkout.admin.AdminRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/founder/applications")
public class FounderReviewController {

    private final FounderReviewService reviews;

    public FounderReviewController(FounderReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public List<FounderReviewSummary> pending() {
        return reviews.pending();
    }

    @GetMapping("/{id}")
    public FounderReviewDetail get(@PathVariable UUID id) {
        return reviews.get(id);
    }

    @PostMapping("/{id}/approval")
    public FounderReviewDetail approve(@PathVariable UUID id) {
        return reviews.approve(AdminRequests.current().adminId(), id);
    }

    @PostMapping("/{id}/rejection")
    public FounderReviewDetail reject(@PathVariable UUID id, @Valid @RequestBody RejectionRequest request) {
        return reviews.reject(AdminRequests.current().adminId(), id, request.reason());
    }

    public record RejectionRequest(
            @NotBlank @Size(max = FounderReviewService.REASON_MAX_LENGTH) String reason
    ) {
    }
}
