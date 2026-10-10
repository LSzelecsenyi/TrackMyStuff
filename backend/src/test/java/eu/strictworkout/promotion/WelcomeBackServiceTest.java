package eu.strictworkout.promotion;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeBackServiceTest {

    private final Instant now = Instant.parse("2026-10-09T08:00:00Z");
    private final MemoryStore store = new MemoryStore();
    private final WelcomeBackService service = new WelcomeBackService(store);

    @Test
    void activationLastsSevenDaysAndStartsAOneHundredEightyDayCooldown() {
        UUID user = UUID.randomUUID();
        WelcomeBackService.Result result = service.activate(user, "workout-b", now, true, false);
        assertEquals(WelcomeBackService.Status.ACTIVE, result.status());
        assertEquals(now, result.activatedAt());
        assertEquals(now.plus(Duration.ofHours(7 * 24)), result.expiresAt());
        assertEquals(now.plus(Duration.ofDays(180)), result.cooldownUntil());
        assertTrue(service.activeNow(user, result.expiresAt().minusMillis(1)));
        assertFalse(service.activeNow(user, result.expiresAt()));
    }

    @Test
    void repeatDuringTheTrialReturnsTheExistingGrant() {
        UUID user = UUID.randomUUID();
        WelcomeBackService.Result first = service.activate(user, "workout-b", now, true, false);
        WelcomeBackService.Result second = service.activate(user, "workout-c", now.plusSeconds(5), true, false);
        assertEquals(first, second);
        assertEquals(1, store.size());
    }

    @Test
    void cooldownRejectsAndTheSameWorkoutCannotBeReplayedLater() {
        UUID user = UUID.randomUUID();
        service.activate(user, "workout-b", now, true, false);
        Instant during = now.plus(Duration.ofDays(8));
        assertEquals(
                WelcomeBackService.Status.COOLDOWN,
                service.activate(user, "workout-c", during, true, false).status()
        );
        Instant after = now.plus(Duration.ofDays(180));
        assertEquals(
                WelcomeBackService.Status.REPLAY,
                service.activate(user, "workout-b", after, true, false).status()
        );
        WelcomeBackService.Result next = service.activate(user, "workout-d", after, true, false);
        assertEquals(WelcomeBackService.Status.ACTIVE, next.status());
        assertEquals(after, next.activatedAt());
        assertEquals("workout-d", store.find(user).workoutId());
    }

    @Test
    void accountACannotReadOrReplaceAccountB() {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        service.activate(accountA, "workout-a", now, true, false);
        assertEquals(null, service.current(accountB));
        WelcomeBackService.Result other = service.activate(accountB, "workout-a", now, true, false);
        assertEquals(WelcomeBackService.Status.ACTIVE, other.status());
        assertEquals("workout-a", service.current(accountA).workoutId());
        assertEquals(accountA, service.current(accountA).userId());
        assertEquals(accountB, service.current(accountB).userId());
    }

    @Test
    void closedAndIncompatiblePromotionsDoNotGrant() {
        UUID user = UUID.randomUUID();
        assertEquals(
                WelcomeBackService.Status.CLOSED,
                service.activate(user, "workout-b", now, false, false).status()
        );
        assertEquals(
                WelcomeBackService.Status.INCOMPATIBLE,
                service.activate(user, "workout-b", now, true, true).status()
        );
        assertEquals(null, service.current(user));
    }

    @Test
    void aLostInsertReturnsTheGrantThatWonTheRace() {
        UUID user = UUID.randomUUID();
        WelcomeBackService.Grant winner = new WelcomeBackService.Grant(
                user,
                "workout-b",
                now,
                now.plus(WelcomeBackService.LENGTH),
                now.plus(WelcomeBackService.COOLDOWN)
        );
        store.insert(winner);
        store.rejectNextInsert = true;
        WelcomeBackService.Result raced = service.activate(user, "workout-c", now.plusSeconds(1), true, false);
        assertEquals(WelcomeBackService.Status.ACTIVE, raced.status());
        assertEquals(winner.activatedAt(), raced.activatedAt());
        assertEquals(winner.expiresAt(), raced.expiresAt());
    }

    private static final class MemoryStore implements WelcomeBackService.Store {
        private final Map<UUID, WelcomeBackService.Grant> rows = new HashMap<>();
        private boolean rejectNextInsert;

        @Override
        public WelcomeBackService.Grant find(UUID userId) {
            return rows.get(userId);
        }

        @Override
        public boolean insert(WelcomeBackService.Grant grant) {
            if (rejectNextInsert || rows.containsKey(grant.userId())) {
                rejectNextInsert = false;
                return false;
            }
            rows.put(grant.userId(), grant);
            return true;
        }

        @Override
        public boolean replace(WelcomeBackService.Grant grant) {
            rows.put(grant.userId(), grant);
            return true;
        }

        private int size() {
            return rows.size();
        }
    }
}
