package eu.strictworkout.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import eu.strictworkout.admin.AdminAccessException;
import eu.strictworkout.founder.FounderCommandException;
import eu.strictworkout.identity.UnverifiedIdentityException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        if (status.is5xxServerError()) {
            log.error("Request failed with {}", status, exception);
        } else {
            log.info("Request rejected with {}", status);
        }
        return new ResponseEntity<>(apiError(status), headers, status);
    }

    @ExceptionHandler(AdminAccessException.class)
    public ResponseEntity<ApiError> adminAccess(AdminAccessException exception) {
        log.info("Admin access rejected: {}", exception.errorCode());
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(FounderCommandException.class)
    public ResponseEntity<ApiError> founderCommand(FounderCommandException exception) {
        log.info("Founder command rejected: {}", exception.errorCode());
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(UnverifiedIdentityException.class)
    public ResponseEntity<ApiError> unverifiedIdentity(UnverifiedIdentityException exception) {
        log.info("External identity verification failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_GOOGLE_TOKEN", "The Google sign-in could not be verified."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(internalError());
    }

    private static ApiError apiError(HttpStatusCode status) {
        if (status instanceof HttpStatus httpStatus) {
            return switch (httpStatus) {
                case NOT_FOUND -> new ApiError("NOT_FOUND", "The requested resource was not found.");
                case METHOD_NOT_ALLOWED -> new ApiError("METHOD_NOT_ALLOWED", "The requested method is not allowed.");
                case BAD_REQUEST -> new ApiError("BAD_REQUEST", "The request could not be understood.");
                case UNAUTHORIZED -> new ApiError("UNAUTHENTICATED", "Authentication is required.");
                default -> status.is5xxServerError()
                        ? internalError()
                        : new ApiError("REQUEST_FAILED", "The request could not be completed.");
            };
        }
        return status.is5xxServerError()
                ? internalError()
                : new ApiError("REQUEST_FAILED", "The request could not be completed.");
    }

    private static ApiError internalError() {
        return new ApiError("INTERNAL_ERROR", "The request could not be completed.");
    }
}
