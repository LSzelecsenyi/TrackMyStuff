package eu.strictworkout.account;

import eu.strictworkout.program.ProgramLifecycle;
import eu.strictworkout.program.ProgramSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EarlyAdopterProgram {

    private final EarlyAdopterCohortRepository cohorts;

    public EarlyAdopterProgram(EarlyAdopterCohortRepository cohorts) {
        this.cohorts = cohorts;
    }

    @Transactional(readOnly = true)
    public ProgramSnapshot current() {
        return snapshot(cohorts.require());
    }

    @Transactional
    public ProgramSnapshot transition(ProgramLifecycle requested) {
        EarlyAdopterCohort cohort = cohorts.lockSingleton();
        cohort.setLifecycle(cohort.getLifecycle().transitionTo(requested));
        return snapshot(cohort);
    }

    private static ProgramSnapshot snapshot(EarlyAdopterCohort cohort) {
        return ProgramSnapshot.of(
                "EARLY_ADOPTER",
                cohort.getLifecycle(),
                cohort.getAssignedCount(),
                cohort.getCapacity()
        );
    }
}
