package eu.strictworkout.program;

public enum ProgramLifecycle {
    ACTIVE,
    INACTIVE,
    RETIRED;

    /**
     * ACTIVE to INACTIVE, INACTIVE to ACTIVE, and either open state to RETIRED.
     * RETIRED stays RETIRED. Every other move away from RETIRED is rejected.
     */
    public ProgramLifecycle transitionTo(ProgramLifecycle requested) {
        if (requested == null) {
            throw ProgramLifecycleException.invalid();
        }
        if (this == requested) {
            return this;
        }
        if (this == RETIRED) {
            throw ProgramLifecycleException.retired();
        }
        return switch (requested) {
            case ACTIVE, INACTIVE, RETIRED -> requested;
        };
    }

    public boolean acceptingNewParticipants(boolean hasRoom) {
        return this == ACTIVE && hasRoom;
    }
}
