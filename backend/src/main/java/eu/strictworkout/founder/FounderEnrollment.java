package eu.strictworkout.founder;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.AppUserRepository;
import eu.strictworkout.program.ProgramLifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class FounderEnrollment {

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
     * New participants take one locked slot while the program is ACTIVE.
     * Someone who already has an application does not take another slot and is not
     * blocked by a later pause, retirement, or a full cohort.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(UUID userId) {
        FounderProgramCapacity program = capacity.lockSingleton();
        if (applications.findIdByUserId(userId).isPresent()) {
            return;
        }
        if (program.getLifecycle() != ProgramLifecycle.ACTIVE) {
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

    /**
     * The older enrollment switch. Open is ACTIVE and closed is INACTIVE.
     * A retired program rejects both, so this endpoint cannot reopen it.
     */
    @Transactional
    public FounderEnrollmentState setOpen(boolean open) {
        return transition(open ? ProgramLifecycle.ACTIVE : ProgramLifecycle.INACTIVE);
    }

    @Transactional
    public FounderEnrollmentState transition(ProgramLifecycle requested) {
        FounderProgramCapacity program = capacity.lockSingleton();
        program.setLifecycle(program.getLifecycle().transitionTo(requested));
        return state(program);
    }

    private static FounderEnrollmentState state(FounderProgramCapacity program) {
        boolean accepting = program.getLifecycle() == ProgramLifecycle.ACTIVE;
        return new FounderEnrollmentState(
                accepting,
                program.getEnrolledCount(),
                program.getCapacity(),
                program.getLifecycle()
        );
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

    public record FounderEnrollmentState(
            boolean open,
            int enrolledCount,
            int capacity,
            ProgramLifecycle lifecycle
    ) {
    }
}
