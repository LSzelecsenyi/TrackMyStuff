package eu.strictworkout.program;

public record ProgramSnapshot(
        String program,
        ProgramLifecycle lifecycle,
        int usedCount,
        int capacity,
        int remaining,
        boolean acceptingNewParticipants
) {
    public static ProgramSnapshot of(String program, ProgramLifecycle lifecycle, int usedCount, int capacity) {
        int remaining = Math.max(0, capacity - usedCount);
        return new ProgramSnapshot(
                program,
                lifecycle,
                usedCount,
                capacity,
                remaining,
                lifecycle.acceptingNewParticipants(remaining > 0)
        );
    }
}
