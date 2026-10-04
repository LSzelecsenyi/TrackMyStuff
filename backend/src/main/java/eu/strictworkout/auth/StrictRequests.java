package eu.strictworkout.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class StrictRequests {

    private StrictRequests() {
    }

    public static StrictPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof StrictUserAuthentication strict) {
            return strict.getStrictPrincipal();
        }
        throw new IllegalStateException("Authenticated request has no Strict principal");
    }
}
