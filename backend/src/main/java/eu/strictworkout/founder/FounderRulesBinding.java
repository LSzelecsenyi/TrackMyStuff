package eu.strictworkout.founder;

/**
 * The Founder rules currently used for new decisions.
 * A review snapshot copies these values when it is created and does not read them again.
 */
public final class FounderRulesBinding {

    private volatile String profile;
    private volatile FounderRules rules;

    public FounderRulesBinding(String profile, FounderRules rules) {
        this.profile = profile;
        this.rules = rules;
    }

    public String profile() {
        return profile;
    }

    public FounderRules rules() {
        return rules;
    }

    public void replace(String profile, FounderRules rules) {
        this.profile = profile;
        this.rules = rules;
    }
}
