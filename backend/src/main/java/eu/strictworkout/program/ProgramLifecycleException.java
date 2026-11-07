package eu.strictworkout.program;

import org.springframework.http.HttpStatus;

public class ProgramLifecycleException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ProgramLifecycleException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ProgramLifecycleException retired() {
        return new ProgramLifecycleException(
                HttpStatus.CONFLICT,
                "PROGRAM_RETIRED",
                "This program is retired and cannot be reactivated."
        );
    }

    public static ProgramLifecycleException invalid() {
        return new ProgramLifecycleException(
                HttpStatus.BAD_REQUEST,
                "PROGRAM_LIFECYCLE_INVALID",
                "The program lifecycle is not recognized."
        );
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}
