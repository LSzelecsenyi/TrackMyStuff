package eu.strictworkout.founder;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.AppUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
class FounderEnrollment {

    private final FounderApplicationRepository applications;
    private final AppUserRepository users;
    private final FounderRules rules;
    private final Clock clock;

    FounderEnrollment(
            FounderApplicationRepository applications,
            AppUserRepository users,
            FounderRules rules,
            Clock clock
    ) {
        this.applications = applications;
        this.users = users;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow();
        Instant enrolledAt = clock.instant();
        applications.saveAndFlush(new FounderApplication(
                UUID.randomUUID(),
                user,
                enrolledAt,
                FounderWindow.deadline(enrolledAt, rules)
        ));
    }
}
