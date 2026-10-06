package eu.strictworkout.admin;

import eu.strictworkout.auth.OpaqueTokenGenerator;
import eu.strictworkout.auth.TokenDigests;
import eu.strictworkout.identity.ExternalIdentityVerifier;
import eu.strictworkout.identity.VerifiedExternalIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AdminSessionService.class);

    private final AdminSessionRepository sessions;
    private final AdminUserRepository admins;
    private final AdminUserCreation creation;
    private final ExternalIdentityVerifier identities;
    private final AdminAllowlist allowlist;
    private final AdminProperties properties;
    private final Clock clock;

    public AdminSessionService(
            AdminSessionRepository sessions,
            AdminUserRepository admins,
            AdminUserCreation creation,
            ExternalIdentityVerifier identities,
            AdminAllowlist allowlist,
            AdminProperties properties,
            Clock clock
    ) {
        this.sessions = sessions;
        this.admins = admins;
        this.creation = creation;
        this.identities = identities;
        this.allowlist = allowlist;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAdminSession login(String idToken) {
        VerifiedExternalIdentity identity = identities.verify(idToken);
        if (!allowlist.allows(identity.subject())) {
            throw new AdminAccessException(
                    HttpStatus.UNAUTHORIZED,
                    "ADMIN_NOT_ALLOWED",
                    "This Google account is not an admin."
            );
        }
        AdminUser admin = admins.findByGoogleSubject(identity.subject()).orElseGet(() -> insert(identity));
        return createSession(admin);
    }

    @Transactional
    public Optional<AdminPrincipal> authenticate(String rawToken) {
        if (!OpaqueTokenGenerator.matchesFormat(rawToken)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<AdminSession> found = sessions.findByTokenHash(TokenDigests.sha256(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AdminSession session = found.get();
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (!allowlist.allows(session.getGoogleSubject())) {
            session.revoke(now);
            log.info(
                    "Admin session rejected because its identity is no longer allowlisted: sessionId={}",
                    session.getId()
            );
            return Optional.empty();
        }
        return Optional.of(new AdminPrincipal(session.getAdminId(), session.getId()));
    }

    @Transactional(readOnly = true)
    public AdminSessionView current(AdminPrincipal principal) {
        AdminUser admin = admins.findById(principal.adminId()).orElseThrow(() -> new AdminAccessException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentication is required."
        ));
        return new AdminSessionView(displayEmail(admin));
    }

    @Transactional
    public void revoke(AdminPrincipal principal) {
        sessions.findById(principal.sessionId()).ifPresent(session -> {
            if (session.getAdminId().equals(principal.adminId())) {
                session.revoke(clock.instant());
            }
        });
    }

    @Transactional
    public void revokePresented(String rawToken) {
        authenticate(rawToken).ifPresent(this::revoke);
    }

    private static String displayEmail(AdminUser admin) {
        if (!admin.isEmailVerified()) {
            return null;
        }
        String email = admin.getEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        return email;
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
