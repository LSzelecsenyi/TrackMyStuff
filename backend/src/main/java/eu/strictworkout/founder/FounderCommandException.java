package eu.strictworkout.founder;

import org.springframework.http.HttpStatus;

public class FounderCommandException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public FounderCommandException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static FounderCommandException notEnrolled() {
        return new FounderCommandException(
                HttpStatus.NOT_FOUND,
                "FOUNDER_NOT_ENROLLED",
                "No Founder application exists for this user."
        );
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}
