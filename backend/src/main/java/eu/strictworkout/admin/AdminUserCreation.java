package eu.strictworkout.admin;

import eu.strictworkout.identity.VerifiedExternalIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class AdminUserCreation {

    private final AdminUserRepository admins;
    private final Clock clock;

    AdminUserCreation(AdminUserRepository admins, Clock clock) {
        this.admins = admins;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AdminUser insert(VerifiedExternalIdentity identity) {
        AdminUser admin = new AdminUser(
                UUID.randomUUID(),
                identity.subject(),
                identity.email(),
                identity.emailVerified(),
                clock.instant()
        );
        return admins.saveAndFlush(admin);
    }
}
