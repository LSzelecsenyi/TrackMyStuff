package eu.strictworkout.founder;

public final class TemporaryPro {

    private TemporaryPro() {
    }

    public static boolean active(FounderStatus status) {
        return status == FounderStatus.ACTIVE_PRO || status == FounderStatus.PENDING_APPROVAL;
    }
}
