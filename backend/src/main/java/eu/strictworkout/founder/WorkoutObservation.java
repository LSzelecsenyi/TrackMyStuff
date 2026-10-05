package eu.strictworkout.founder;

/**
 * Client-reported facts about one qualifying workout.
 * They do not decide whether the workout counts.
 */
public record WorkoutObservation(
        String displayName,
        Integer durationSeconds,
        Integer exerciseCount,
        Integer completedSetCount,
        Boolean fromTemplate,
        Boolean usedExternalLoad
) {
    public static final int DISPLAY_NAME_MAX = 80;

    public WorkoutObservation normalized() {
        String name = displayName == null ? null : displayName.trim();
        if (name != null && name.isEmpty()) {
            name = null;
        }
        return new WorkoutObservation(
                name,
                durationSeconds,
                exerciseCount,
                completedSetCount,
                fromTemplate,
                usedExternalLoad
        );
    }

    public boolean invalid() {
        WorkoutObservation value = normalized();
        if (value.displayName != null && value.displayName.length() > DISPLAY_NAME_MAX) {
            return true;
        }
        return negative(value.durationSeconds) || negative(value.exerciseCount) || negative(value.completedSetCount);
    }

    private static boolean negative(Integer value) {
        return value != null && value < 0;
    }
}
