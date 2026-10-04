package eu.strictworkout.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

final class StrictRequests {

    private StrictRequests() {
    }

    static StrictPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof StrictUserAuthentication strict) {
            return strict.getStrictPrincipal();
        }
        throw new IllegalStateException("Authenticated request has no Strict principal");
    }
}
