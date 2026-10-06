package eu.strictworkout.admin;

/**
 * Public view of the current admin session.
 * The session credential, its hash, and the Google subject are not included.
 * Email is display information from the verified identity stored at login.
 * It is not an authorization input.
 */
public record AdminSessionView(String email) {
}
