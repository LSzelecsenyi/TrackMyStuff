package eu.strictworkout.account;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.AppUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Assigns Early Adopter inside the caller's transaction.
 * The cohort row is locked with SELECT FOR UPDATE so concurrent registrations
 * cannot hand out more slots than capacity. A rollback releases the lock and
 * does not keep the increment.
 */
@Service
public class EarlyAdopterAssignment {

    private final EarlyAdopterCohortRepository cohorts;
    private final AccountStatusGrantRepository grants;
    private final AppUserRepository users;
    private final Clock clock;

    public EarlyAdopterAssignment(
            EarlyAdopterCohortRepository cohorts,
            AccountStatusGrantRepository grants,
            AppUserRepository users,
            Clock clock
    ) {
        this.cohorts = cohorts;
        this.grants = grants;
        this.users = users;
        this.clock = clock;
    }

    /**
     * One new AppUser. Joins the user-creation transaction.
     * grantedAt is that user's registration instant.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignNewUser(AppUser user) {
        EarlyAdopterCohort cohort = cohorts.lockSingleton();
        if (grants.existsByUser_IdAndStatus(user.getId(), AccountStatus.EARLY_ADOPTER)) {
            return;
        }
        if (!cohort.hasRoom()) {
            return;
        }
        Instant registeredAt = user.getCreatedAt();
        grants.saveAndFlush(new AccountStatusGrant(
                UUID.randomUUID(),
                user,
                AccountStatus.EARLY_ADOPTER,
                registeredAt,
                registeredAt
        ));
        cohort.claimSlot();
    }

    /**
     * One-time pass for accounts that already existed when the cohort was introduced.
     * Ordered by registration time, then id only as a stable tie-break.
     * The counter moves forward by the number of slots claimed. It is not set from
     * a later count of grant rows, so a deleted grant cannot reopen the cohort.
     */
    @Transactional
    public void backfillExistingUsers() {
        EarlyAdopterCohort cohort = cohorts.lockSingleton();
        if (cohort.isBackfillCompleted()) {
            return;
        }
        int remaining = cohort.getCapacity() - cohort.getAssignedCount();
        if (remaining > 0) {
            Instant writtenAt = clock.instant();
            for (AppUser user : users.findWithoutEarlyAdopter(PageRequest.of(0, remaining))) {
                if (!cohort.hasRoom()) {
                    break;
                }
                if (grants.existsByUser_IdAndStatus(user.getId(), AccountStatus.EARLY_ADOPTER)) {
                    continue;
                }
                grants.save(new AccountStatusGrant(
                        UUID.randomUUID(),
                        user,
                        AccountStatus.EARLY_ADOPTER,
                        user.getCreatedAt(),
                        writtenAt
                ));
                cohort.claimSlot();
            }
        }
        cohort.completeBackfill();
    }
}
