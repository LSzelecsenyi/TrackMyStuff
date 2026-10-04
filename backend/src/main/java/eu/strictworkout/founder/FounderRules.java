package eu.strictworkout.founder;

public record FounderRules(
        int temporaryProWorkoutCount,
        int founderWorkoutCount,
        int requiredDistinctWorkoutDays,
        int qualificationWindowDays,
        boolean feedbackRequired,
        boolean testerAnalyticsReportRequired
) {
    public static final FounderRules PRODUCTION = new FounderRules(5, 10, 6, 45, true, true);

    public FounderRules {
        if (temporaryProWorkoutCount < 1
                || founderWorkoutCount < 1
                || requiredDistinctWorkoutDays < 1
                || qualificationWindowDays < 1) {
            throw new IllegalArgumentException("Founder rules must be positive");
        }
        if (requiredDistinctWorkoutDays > founderWorkoutCount) {
            throw new IllegalArgumentException("Distinct days cannot exceed the workout count");
        }
    }
}
