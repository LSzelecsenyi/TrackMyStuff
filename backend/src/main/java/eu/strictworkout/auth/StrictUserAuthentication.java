package eu.strictworkout.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public final class StrictUserAuthentication extends AbstractAuthenticationToken {

    private final StrictPrincipal principal;

    public StrictUserAuthentication(StrictPrincipal principal) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public StrictPrincipal getStrictPrincipal() {
        return principal;
    }
}
