package eu.strictworkout.promotion;

import eu.strictworkout.program.ProgramLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionalPrograms {

    private final PromotionalProgramRepository programs;

    public PromotionalPrograms(PromotionalProgramRepository programs) {
        this.programs = programs;
    }

    @Transactional(readOnly = true)
    public PromotionalProgram current(String program) {
        return programs.require(program);
    }

    @Transactional(readOnly = true)
    public boolean acceptingNewGrants(String program) {
        return programs.require(program).acceptingNewGrants();
    }

    @Transactional
    public PromotionalProgram transition(String program, ProgramLifecycle requested) {
        PromotionalProgram stored = programs.lock(program);
        stored.setLifecycle(stored.getLifecycle().transitionTo(requested));
        return stored;
    }
}
