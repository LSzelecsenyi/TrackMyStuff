package eu.strictworkout.founder;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
class FounderEnrollment {

    private final FounderApplicationRepository applications;
    private final FounderProgramCapacityRepository capacity;
    private final AppUserRepository users;
    private final FounderRules rules;
    private final Clock clock;

    FounderEnrollment(
            FounderApplicationRepository applications,
            FounderProgramCapacityRepository capacity,
            AppUserRepository users,
            FounderRules rules,
            Clock clock
    ) {
        this.applications = applications;
        this.capacity = capacity;
        this.users = users;
        this.rules = rules;
        this.clock = clock;
    }

    /**
     * New participants take one locked slot. Someone who already has an application
     * does not take another, including when enrollment is full or closed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(UUID userId) {
        FounderProgramCapacity program = capacity.lockSingleton();
        if (applications.findIdByUserId(userId).isPresent()) {
            return;
        }
        if (!program.isEnrollmentOpen()) {
            throw closed();
        }
        if (!program.hasRoom()) {
            throw full();
        }
        AppUser user = users.findById(userId).orElseThrow();
        Instant enrolledAt = clock.instant();
        applications.saveAndFlush(new FounderApplication(
                UUID.randomUUID(),
                user,
                enrolledAt,
                FounderWindow.deadline(enrolledAt, rules)
        ));
        program.claimSlot();
    }

    @Transactional(readOnly = true)
    public FounderEnrollmentState current() {
        return state(capacity.require());
    }

    @Transactional
    public FounderEnrollmentState setOpen(boolean open) {
        FounderProgramCapacity program = capacity.lockSingleton();
        program.setEnrollmentOpen(open);
        return state(program);
    }

    private static FounderEnrollmentState state(FounderProgramCapacity program) {
        return new FounderEnrollmentState(program.isEnrollmentOpen(), program.getEnrolledCount(), program.getCapacity());
    }

    private static FounderCommandException closed() {
        return new FounderCommandException(
                HttpStatus.CONFLICT,
                "ENROLLMENT_CLOSED",
                "Founding Tester enrollment is closed."
        );
    }

    private static FounderCommandException full() {
        return new FounderCommandException(
                HttpStatus.CONFLICT,
                "ENROLLMENT_FULL",
                "Founding Tester enrollment is full."
        );
    }

    public record FounderEnrollmentState(boolean open, int enrolledCount, int capacity) {
    }
}
