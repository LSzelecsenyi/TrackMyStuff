package eu.strictworkout.promotion;

import org.springframework.http.HttpStatus;

/**
 * Compatibility rejection for the removed activity-upload endpoint.
 * Installed clients receive a stable error and the server stores nothing.
 */
public class ProDiscoverySyncException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    private ProDiscoverySyncException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ProDiscoverySyncException notPermitted() {
        return new ProDiscoverySyncException(
                HttpStatus.FORBIDDEN,
                "ACTIVITY_NOT_PERMITTED",
                "Pro Discovery no longer accepts workout activity."
        );
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public Long activityRevision() {
        return null;
    }

    public Long generation() {
        return null;
    }
}
