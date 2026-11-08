package eu.strictworkout.account;

import org.springframework.http.HttpStatus;

public class AccountDeletionException extends RuntimeException {

    public enum Code {
        CONFIRMATION_REQUIRED(HttpStatus.BAD_REQUEST),
        REAUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
        ACCOUNT_MISMATCH(HttpStatus.FORBIDDEN),
        NOT_FOUND(HttpStatus.NOT_FOUND),
        MARKER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

        private final HttpStatus status;

        Code(HttpStatus status) {
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    private final Code code;

    public AccountDeletionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public HttpStatus status() {
        return code.status();
    }

    public String errorCode() {
        return code.name();
    }

    public static AccountDeletionException markerKeyMissing() {
        return new AccountDeletionException(
                Code.MARKER_UNAVAILABLE,
                "Account deletion is unavailable because the marker key is not configured."
        );
    }

    public static AccountDeletionException markerKeyMismatch() {
        return new AccountDeletionException(
                Code.MARKER_UNAVAILABLE,
                "Account deletion is unavailable because the marker key does not match its stored checksum."
        );
    }
}
