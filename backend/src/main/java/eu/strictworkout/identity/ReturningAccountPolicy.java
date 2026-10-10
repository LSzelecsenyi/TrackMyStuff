package eu.strictworkout.identity;

/**
 * A Google subject that already deleted a Strict account is not a new Early Adopter.
 * The implementation lives with account deletion so identity does not depend on that table.
 */
public interface ReturningAccountPolicy {

    boolean skipEarlyAdopter(String googleSubject);
}
