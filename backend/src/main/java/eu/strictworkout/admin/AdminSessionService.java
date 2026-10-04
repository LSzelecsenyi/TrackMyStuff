package eu.strictworkout.admin;

import eu.strictworkout.auth.OpaqueTokenGenerator;
import eu.strictworkout.auth.TokenDigests;
import eu.strictworkout.identity.ExternalIdentityVerifier;
import eu.strictworkout.identity.VerifiedExternalIdentity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminSessionService {

    private final AdminSessionRepository sessions;
    private final AdminUserRepository admins;
    private final AdminUserCreation creation;
    private final ExternalIdentityVerifier identities;
    private final AdminProperties properties;
    private final Clock clock;

    public AdminSessionService(
            AdminSessionRepository sessions,
            AdminUserRepository admins,
            AdminUserCreation creation,
            ExternalIdentityVerifier identities,
            AdminProperties properties,
            Clock clock
    ) {
        this.sessions = sessions;
        this.admins = admins;
        this.creation = creation;
        this.identities = identities;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAdminSession login(String idToken) {
        VerifiedExternalIdentity identity = identities.verify(idToken);
        if (!properties.allowedSubjects().contains(identity.subject())) {
            throw new AdminAccessException(
                    HttpStatus.UNAUTHORIZED,
                    "ADMIN_NOT_ALLOWED",
                    "This Google account is not an admin."
            );
        }
        AdminUser admin = admins.findByGoogleSubject(identity.subject()).orElseGet(() -> insert(identity));
        return createSession(admin);
    }

    @Transactional(readOnly = true)
    public Optional<AdminPrincipal> authenticate(String rawToken) {
        if (!OpaqueTokenGenerator.matchesFormat(rawToken)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByTokenHash(TokenDigests.sha256(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .filter(session -> session.getExpiresAt().isAfter(now))
                .map(session -> new AdminPrincipal(session.getAdminId(), session.getId()));
    }

    @Transactional
    public void revoke(AdminPrincipal principal) {
        sessions.findById(principal.sessionId()).ifPresent(session -> {
            if (session.getAdminId().equals(principal.adminId())) {
                session.revoke(clock.instant());
            }
        });
    }

    private AdminUser insert(VerifiedExternalIdentity identity) {
        try {
            return creation.insert(identity);
        } catch (DataIntegrityViolationException ex) {
            return admins.findByGoogleSubject(identity.subject()).orElseThrow();
        }
    }

    @Transactional
    public IssuedAdminSession createSession(AdminUser admin) {
        String rawToken = OpaqueTokenGenerator.generate();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(properties.sessionLifetime());
        sessions.save(new AdminSession(
                UUID.randomUUID(),
                admin,
                TokenDigests.sha256(rawToken),
                createdAt,
                expiresAt
        ));
        return new IssuedAdminSession(rawToken, expiresAt, admin.getId());
    }

    public record IssuedAdminSession(String accessToken, Instant expiresAt, UUID adminId) {
    }
}
