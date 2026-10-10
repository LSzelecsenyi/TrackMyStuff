package eu.strictworkout.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private final ExternalIdentityRepository identities;
    private final IdentityCreation creation;
    private final ReturningAccountPolicy returningAccounts;
    private final Clock clock;

    public IdentityService(
            ExternalIdentityRepository identities,
            IdentityCreation creation,
            ReturningAccountPolicy returningAccounts,
            Clock clock
    ) {
        this.identities = identities;
        this.creation = creation;
        this.returningAccounts = returningAccounts;
        this.clock = clock;
    }

    @Transactional
    public AppUser resolve(VerifiedExternalIdentity verified) {
        return identities.findByProviderAndProviderSubject(verified.provider(), verified.subject())
                .map(existing -> updateVerifiedEmail(existing, verified))
                .orElseGet(() -> insertOrLoad(verified));
    }

    private AppUser updateVerifiedEmail(ExternalIdentity existing, VerifiedExternalIdentity verified) {
        String email = verifiedEmail(verified);
        if (email != null && !email.equals(existing.getEmail())) {
            Instant now = clock.instant();
            existing.replaceVerifiedEmail(email, now);
            existing.getUser().setUpdatedAt(now);
        }
        return existing.getUser();
    }

    private AppUser insertOrLoad(VerifiedExternalIdentity verified) {
        boolean skipEarlyAdopter = returningAccounts.skipEarlyAdopter(verified.subject());
        try {
            return creation.insert(verified, skipEarlyAdopter);
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent first login resolved to the existing {} identity", verified.provider());
            return identities.findByProviderAndProviderSubject(verified.provider(), verified.subject())
                    .map(ExternalIdentity::getUser)
                    .orElseThrow(() -> ex);
        }
    }

    private static String verifiedEmail(VerifiedExternalIdentity verified) {
        if (!verified.emailVerified() || verified.email() == null || verified.email().isBlank()) {
            return null;
        }
        return verified.email();
    }
}
