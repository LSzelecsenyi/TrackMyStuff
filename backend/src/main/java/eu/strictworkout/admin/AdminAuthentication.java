package eu.strictworkout.admin;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public final class AdminAuthentication extends AbstractAuthenticationToken {

    private final AdminPrincipal principal;

    public AdminAuthentication(AdminPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ADMIN")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AdminPrincipal getPrincipal() {
        return principal;
    }
}
