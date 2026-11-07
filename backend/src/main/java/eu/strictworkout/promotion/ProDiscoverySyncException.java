package eu.strictworkout.promotion;

import org.springframework.http.HttpStatus;

public class ProDiscoverySyncException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Long activityRevision;
    private final Long generation;

    private ProDiscoverySyncException(
            HttpStatus status,
            String errorCode,
            String message,
            Long activityRevision,
            Long generation
    ) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.activityRevision = activityRevision;
        this.generation = generation;
    }

    public static ProDiscoverySyncException invalid() {
        return new ProDiscoverySyncException(
                HttpStatus.BAD_REQUEST,
                "ACTIVITY_INVALID",
                "The activity dates were rejected.",
                null,
                null
        );
    }

    public static ProDiscoverySyncException stale(long activityRevision) {
        return new ProDiscoverySyncException(
                HttpStatus.CONFLICT,
                "ACTIVITY_REVISION_STALE",
                "A newer activity snapshot is already stored.",
                activityRevision,
                null
        );
    }

    public static ProDiscoverySyncException trialAlreadyRecorded() {
        return new ProDiscoverySyncException(
                HttpStatus.CONFLICT,
                "TRIAL_ALREADY_RECORDED",
                "Activity dates are no longer accepted for this account.",
                null,
                null
        );
    }

    public static ProDiscoverySyncException notPermitted() {
        return new ProDiscoverySyncException(
                HttpStatus.FORBIDDEN,
                "ACTIVITY_NOT_PERMITTED",
                "Pro Discovery activity synchronization is off for this account.",
                null,
                null
        );
    }

    public static ProDiscoverySyncException generationStale(long generation) {
        return new ProDiscoverySyncException(
                HttpStatus.CONFLICT,
                "ACTIVITY_GENERATION_STALE",
                "This activity update belongs to an older synchronization permission.",
                null,
                generation
        );
    }

    public static ProDiscoverySyncException deviceRequired() {
        return new ProDiscoverySyncException(
                HttpStatus.CONFLICT,
                "ACTIVITY_DEVICE_REQUIRED",
                "An activity update must name one installation. It cannot replace another device's dates.",
                null,
                null
        );
    }

    public static ProDiscoverySyncException scopeMismatch() {
        return new ProDiscoverySyncException(
                HttpStatus.CONFLICT,
                "ACTIVITY_SCOPE_MISMATCH",
                "This permission does not match the current activity synchronization scope.",
                null,
                null
        );
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public Long activityRevision() {
        return activityRevision;
    }

    public Long generation() {
        return generation;
    }
}
