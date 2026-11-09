package eu.strictworkout.entitlement;

/**
 * One Google Play review account. The address is compared with the email stored
 * from a verified Google identity, never with a value sent by the client.
 */
public final class PlayReviewAccess {

    public static final String EMAIL = "u0652739323@id.gle";

    private PlayReviewAccess() {
    }

    public static boolean matchesVerifiedEmail(String verifiedEmail) {
        if (verifiedEmail == null) {
            return false;
        }
        String email = verifiedEmail.trim();
        return !email.isEmpty() && EMAIL.equalsIgnoreCase(email);
    }
}
