package eu.strictworkout.admin;

import org.springframework.http.HttpStatus;

public class AdminAccessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public AdminAccessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}
