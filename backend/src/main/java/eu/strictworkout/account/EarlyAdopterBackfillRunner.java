package eu.strictworkout.account;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class EarlyAdopterBackfillRunner implements ApplicationRunner {

    private final EarlyAdopterAssignment assignment;

    EarlyAdopterBackfillRunner(EarlyAdopterAssignment assignment) {
        this.assignment = assignment;
    }

    @Override
    public void run(ApplicationArguments args) {
        assignment.backfillExistingUsers();
    }
}
