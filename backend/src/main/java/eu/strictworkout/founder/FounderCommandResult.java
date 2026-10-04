package eu.strictworkout.founder;

import org.springframework.http.HttpStatus;

public record FounderCommandResult(FounderView view, HttpStatus status, String code, String message) {

    public static FounderCommandResult ok(FounderView view) {
        return new FounderCommandResult(view, null, null, null);
    }

    public static FounderCommandResult reject(FounderView view, HttpStatus status, String code, String message) {
        return new FounderCommandResult(view, status, code, message);
    }

    public boolean rejected() {
        return code != null;
    }
}
