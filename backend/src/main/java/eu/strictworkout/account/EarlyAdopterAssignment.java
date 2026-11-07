package eu.strictworkout.account;

import eu.strictworkout.identity.AppUser;
import eu.strictworkout.identity.AppUserRepository;
import eu.strictworkout.identity.ExternalIdentityRepository;
import eu.strictworkout.identity.IdentityProvider;
import eu.strictworkout.program.ProgramLifecycle;
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
    private final ExternalIdentityRepository identities;
    private final OneTimeMarkers markers;
    private final Clock clock;

    public EarlyAdopterAssignment(
            EarlyAdopterCohortRepository cohorts,
            AccountStatusGrantRepository grants,
            AppUserRepository users,
            ExternalIdentityRepository identities,
            OneTimeMarkers markers,
            Clock clock
    ) {
        this.cohorts = cohorts;
        this.grants = grants;
        this.users = users;
        this.identities = identities;
        this.markers = markers;
        this.clock = clock;
    }

    /**
     * Records whether this new account may ever receive Early Adopter.
     * A registration while the program is paused or retired stays ineligible
     * after a later resume. A Google account that already used the benefit
     * stays ineligible. An account created while the program is active stays
     * eligible even when the cohort is full.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void register(AppUser user, boolean alreadyUsed) {
        EarlyAdopterCohort cohort = cohorts.lockSingleton();
        if (alreadyUsed || cohort.getLifecycle() != ProgramLifecycle.ACTIVE) {
            user.setEarlyAdopterIneligible(true);
            users.saveAndFlush(user);
            return;
        }
        grantIfRoom(cohort, user);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assignNewUser(AppUser user) {
        EarlyAdopterCohort cohort = cohorts.lockSingleton();
        if (user.isEarlyAdopterIneligible() || cohort.getLifecycle() != ProgramLifecycle.ACTIVE) {
            return;
        }
        grantIfRoom(cohort, user);
    }

    private void grantIfRoom(EarlyAdopterCohort cohort, AppUser user) {
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
        if (cohort.getLifecycle() != ProgramLifecycle.ACTIVE) {
            return;
        }
        if (markers.untrustworthy()) {
            return;
        }
        int guard = Math.max(1, cohort.getCapacity());
        for (int pass = 0; pass < guard && cohort.hasRoom(); pass++) {
            int remaining = cohort.getCapacity() - cohort.getAssignedCount();
            if (remaining <= 0) {
                break;
            }
            var page = users.findWithoutEarlyAdopter(PageRequest.of(0, remaining));
            if (page.isEmpty()) {
                break;
            }
            Instant writtenAt = clock.instant();
            boolean blockedSomeone = false;
            for (AppUser user : page) {
                if (!cohort.hasRoom()) {
                    break;
                }
                if (alreadyUsed(user)) {
                    user.setEarlyAdopterIneligible(true);
                    users.saveAndFlush(user);
                    blockedSomeone = true;
                    continue;
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
            if (!blockedSomeone) {
                break;
            }
        }
        cohort.completeBackfill();
    }

    private boolean alreadyUsed(AppUser user) {
        return identities.findByUser_IdAndProvider(user.getId(), IdentityProvider.GOOGLE)
                .map(identity -> identity.getProviderSubject())
                .map(subject -> {
                    var row = markers.find(subject);
                    return row.isPresent() && row.get().earlyAdopter();
                })
                .orElse(false);
    }
}
