package eu.strictworkout.admin;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AdminRequests {

    private AdminRequests() {
    }

    public static AdminPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AdminAuthentication admin) {
            return admin.getPrincipal();
        }
        throw new IllegalStateException("Authenticated request has no admin principal");
    }
}
