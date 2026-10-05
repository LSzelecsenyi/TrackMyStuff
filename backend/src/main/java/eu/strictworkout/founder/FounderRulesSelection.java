package eu.strictworkout.founder;

/**
 * Production rules stay the published 5/10/6/45 set.
 * {@code fast} is a local manual-test selection (1/2/1/45) and is rejected on the prod profile.
 * Callers never supply these numbers.
 */
public final class FounderRulesSelection {

    private FounderRulesSelection() {
    }

    public static FounderRules select(String requested, boolean productionProfile) {
        String mode = requested == null || requested.isBlank() ? "production" : requested.trim();
        if (productionProfile && !"production".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Production profile cannot use non-production Founder rules.");
        }
        if ("fast".equalsIgnoreCase(mode)) {
            return new FounderRules(1, 2, 1, 45, true, true);
        }
        if (!"production".equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Unknown Founder rules selection.");
        }
        return FounderRules.PRODUCTION;
    }

    public static String profile(String requested, boolean productionProfile) {
        select(requested, productionProfile);
        String mode = requested == null || requested.isBlank() ? "production" : requested.trim();
        return "fast".equalsIgnoreCase(mode) ? "fast" : "production";
    }

    public static FounderRulesBinding binding(String requested, boolean productionProfile) {
        return new FounderRulesBinding(profile(requested, productionProfile), select(requested, productionProfile));
    }
}
