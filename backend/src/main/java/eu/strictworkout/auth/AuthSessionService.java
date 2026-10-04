package eu.strictworkout.auth;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthSessionService {

    private final AuthSessionRepository sessions;
    private final AppUserRepository users;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthSessionService(
            AuthSessionRepository sessions,
            AppUserRepository users,
            AuthProperties properties,
            Clock clock
    ) {
        this.sessions = sessions;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedSession create(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow();
        String rawToken = OpaqueTokenGenerator.generate();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(properties.sessionLifetime());
        AuthSession session = new AuthSession(
                UUID.randomUUID(),
                user,
                TokenDigests.sha256(rawToken),
                createdAt,
                expiresAt
        );
        sessions.save(session);
        return new IssuedSession(rawToken, expiresAt, user.getId());
    }

    @Transactional(readOnly = true)
    public Optional<StrictPrincipal> authenticate(String rawToken) {
        if (!OpaqueTokenGenerator.matchesFormat(rawToken)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByTokenHash(TokenDigests.sha256(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .filter(session -> session.getExpiresAt().isAfter(now))
                .map(session -> new StrictPrincipal(session.getUser().getId(), session.getId()));
    }

    @Transactional
    public void revoke(StrictPrincipal principal) {
        sessions.findById(principal.sessionId()).ifPresent(session -> {
            if (session.getUser().getId().equals(principal.userId())) {
                session.revoke(clock.instant());
            }
        });
    }

    public record IssuedSession(String rawToken, Instant expiresAt, UUID userId) {
    }
}
