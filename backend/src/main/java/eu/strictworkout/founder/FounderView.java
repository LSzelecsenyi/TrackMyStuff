package eu.strictworkout.founder;

import java.time.Instant;

public record FounderView(
        FounderStatus status,
        Instant enrolledAt,
        Instant deadlineAt,
        Progress progress,
        boolean temporaryProActive,
        boolean trainingRequirementsComplete,
        Requirement feedback,
        Requirement testerReport,
        FounderNextAction nextAction
) {
    public record Progress(
            int qualifyingWorkouts,
            int requiredWorkouts,
            int distinctWorkoutDays,
            int requiredDistinctWorkoutDays,
            int temporaryProRequiredWorkouts
    ) {
    }

    public record Requirement(boolean required, boolean submitted) {
    }
}
