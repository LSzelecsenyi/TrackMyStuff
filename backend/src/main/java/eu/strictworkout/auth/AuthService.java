package eu.strictworkout.auth;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.ExternalIdentityVerifier;
import eu.strictworkout.identity.IdentityService;
import eu.strictworkout.identity.VerifiedExternalIdentity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final ExternalIdentityVerifier identities;
    private final IdentityService identityService;
    private final AuthSessionService sessions;

    public AuthService(
            ExternalIdentityVerifier identities,
            IdentityService identityService,
            AuthSessionService sessions
    ) {
        this.identities = identities;
        this.identityService = identityService;
        this.sessions = sessions;
    }

    public SessionIssued login(String idToken) {
        VerifiedExternalIdentity verified = identities.verify(idToken);
        AppUser user = identityService.resolve(verified);
        AuthSessionService.IssuedSession issued = sessions.create(user.getId());
        return new SessionIssued(issued.rawToken(), "Bearer", issued.expiresAt(), new UserRef(issued.userId()));
    }

    public void logout(StrictPrincipal principal) {
        sessions.revoke(principal);
    }

    public record SessionIssued(String accessToken, String tokenType, Instant expiresAt, UserRef user) {
    }

    public record UserRef(UUID id) {
    }
}
