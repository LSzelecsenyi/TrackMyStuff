package eu.strictworkout.promotion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionalTrialServiceTest {
    private final Memory store = new Memory();
    private final PromotionalTrialService trials = new PromotionalTrialService(store);
    private final UUID user = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void activationIsFourteenDaysAndCannotBeRepeated() {
        PromotionalTrialService.Result started = trials.activate(user, now, true, false);
        assertEquals(PromotionalTrialService.Status.ACTIVE, started.status());
        assertEquals(now.plus(PromotionalTrialService.LENGTH), started.expiresAt());
        PromotionalTrialService.Result again = trials.activate(user, now.plusSeconds(60), true, false);
        assertEquals(started.expiresAt(), again.expiresAt());
        PromotionalTrialService.Result expired = trials.activate(user, started.expiresAt(), true, false);
        assertEquals(PromotionalTrialService.Status.ALREADY_USED, expired.status());
    }

    @Test
    void anotherAccountAndClosedOrPaidUsersCannotStartIt() {
        trials.activate(user, now, true, false);
        UUID other = UUID.randomUUID();
        assertEquals(PromotionalTrialService.Status.ACTIVE, trials.activate(other, now, true, false).status());
        assertEquals(PromotionalTrialService.Status.CLOSED, trials.activate(UUID.randomUUID(), now, false, false).status());
        assertEquals(PromotionalTrialService.Status.INCOMPATIBLE, trials.activate(UUID.randomUUID(), now, true, true).status());
    }

    @Test
    void migrationDoesNotCreateALiveGrantAndDoesNotExtendAnExistingTrial() {
        PromotionalTrialService.Result rejected = trials.migrate(
                user,
                now.minusSeconds(3_600),
                now.plus(PromotionalTrialService.LENGTH),
                now
        );
        assertEquals(PromotionalTrialService.Status.INVALID, rejected.status());
        assertEquals(0, store.rows.size());

        PromotionalTrialService.Result started = trials.activate(user, now, true, false);
        PromotionalTrialService.Result second = trials.migrate(user, now.minusSeconds(10), now.plusSeconds(10), now);
        assertEquals(started.expiresAt(), second.expiresAt());
        assertEquals(1, store.rows.size());
        assertTrue(now.isBefore(second.expiresAt()));
    }

    @Test
    void anExpiredLegacyTrialIsRecordedWithoutGrantingPro() {
        Instant activated = now.minus(PromotionalTrialService.LENGTH).minusSeconds(60);
        PromotionalTrialService.Result consumed = trials.migrate(user, activated, now.minusSeconds(1), now);
        assertEquals(PromotionalTrialService.Status.ALREADY_USED, consumed.status());
        assertTrue(consumed.expiresAt().compareTo(now) <= 0);
        assertEquals(PromotionalTrialService.Status.ALREADY_USED, trials.activate(user, now, true, false).status());
    }

    private static final class Memory implements PromotionalTrialService.Store {
        final Map<UUID, PromotionalTrialService.Trial> rows = new HashMap<>();

        @Override
        public PromotionalTrialService.Trial find(UUID userId) {
            return rows.get(userId);
        }

        @Override
        public boolean insert(PromotionalTrialService.Trial trial) {
            return rows.putIfAbsent(trial.userId(), trial) == null;
        }
    }
}
