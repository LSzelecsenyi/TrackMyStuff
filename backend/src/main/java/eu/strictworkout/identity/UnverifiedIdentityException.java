package eu.strictworkout.identity;

public class UnverifiedIdentityException extends RuntimeException {

    public UnverifiedIdentityException() {
        super("External identity could not be verified");
    }
}
