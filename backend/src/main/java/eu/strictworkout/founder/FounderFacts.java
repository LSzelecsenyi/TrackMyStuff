package eu.strictworkout.founder;

import java.time.Instant;
import java.util.List;

public record FounderFacts(
        FounderStatus status,
        Instant enrolledAt,
        Instant deadlineAt,
        List<FounderWorkoutFact> workouts,
        boolean feedbackSubmitted,
        boolean reportSubmitted
) {
}
