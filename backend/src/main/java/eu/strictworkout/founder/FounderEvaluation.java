package eu.strictworkout.founder;

public record FounderEvaluation(
        FounderStatus status,
        int qualifyingWorkouts,
        int distinctWorkoutDays,
        boolean temporaryProActive,
        boolean trainingRequirementsComplete,
        boolean feedbackRequirementMet,
        boolean reportRequirementMet,
        FounderNextAction nextAction
) {
}
