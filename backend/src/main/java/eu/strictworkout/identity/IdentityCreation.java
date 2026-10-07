package eu.strictworkout.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
class IdentityCreation {

    private final ExternalIdentityRepository identities;
    private final AppUserRepository users;
    private final eu.strictworkout.account.EarlyAdopterAssignment earlyAdopters;
    private final Clock clock;

    IdentityCreation(
            ExternalIdentityRepository identities,
            AppUserRepository users,
            eu.strictworkout.account.EarlyAdopterAssignment earlyAdopters,
            Clock clock
    ) {
        this.identities = identities;
        this.users = users;
        this.earlyAdopters = earlyAdopters;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppUser insert(VerifiedExternalIdentity verified) {
        Instant now = clock.instant();
        AppUser user = users.saveAndFlush(new AppUser(UUID.randomUUID(), now, now));
        identities.saveAndFlush(new ExternalIdentity(
                UUID.randomUUID(),
                user,
                verified.provider(),
                verified.subject(),
                verifiedEmail(verified),
                verifiedEmail(verified) != null,
                now
        ));
        earlyAdopters.assignNewUser(user);
        return user;
    }

    private static String verifiedEmail(VerifiedExternalIdentity verified) {
        if (!verified.emailVerified() || verified.email() == null || verified.email().isBlank()) {
            return null;
        }
        return verified.email();
    }
}
