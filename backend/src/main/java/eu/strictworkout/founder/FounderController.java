package eu.strictworkout.founder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.strictworkout.auth.StrictRequests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/founder")
public class FounderController {

    private final FounderService founder;

    public FounderController(FounderService founder) {
        this.founder = founder;
    }

    @PostMapping("/enrollment")
    public FounderView enroll() {
        return founder.enroll(StrictRequests.current().userId());
    }

    @GetMapping
    public FounderView current() {
        return founder.current(StrictRequests.current().userId());
    }

    @PostMapping("/workouts")
    public FounderView recordWorkout(@Valid @RequestBody WorkoutEventRequest request) {
        return respond(founder.recordWorkout(
                StrictRequests.current().userId(),
                request.workoutId(),
                request.completedAt(),
                request.localDate()
        ));
    }

    @PutMapping("/feedback")
    public FounderView saveFeedback(@Valid @RequestBody FeedbackRequest request) {
        return respond(founder.saveFeedback(StrictRequests.current().userId(), request.text()));
    }

    @PostMapping("/tester-report")
    public FounderView submitReport(@Valid @RequestBody TesterReportRequest request) {
        return respond(founder.submitReport(
                StrictRequests.current().userId(),
                request.appVersion(),
                request.platform()
        ));
    }

    private static FounderView respond(FounderCommandResult result) {
        if (result.rejected()) {
            throw new FounderCommandException(result.status(), result.code(), result.message());
        }
        return result.view();
    }

    public record WorkoutEventRequest(
            @NotNull UUID workoutId,
            @NotNull Instant completedAt,
            @NotNull LocalDate localDate
    ) {
    }

    public record FeedbackRequest(
            @NotBlank @Size(max = FounderService.FEEDBACK_MAX_LENGTH) String text
    ) {
    }

    public record TesterReportRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[0-9A-Za-z._+-]{1,32}") String appVersion,
            @NotBlank @Pattern(regexp = "android") String platform
    ) {
    }
}
