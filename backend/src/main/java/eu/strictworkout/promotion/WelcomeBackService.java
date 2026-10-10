package eu.strictworkout.promotion;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Account-bound Welcome Back grant.
 *
 * Trust boundary: the phone decides that a native workout followed a 40-day gap.
 * Those local timestamps are not tamper-proof and are not stored as proof.
 * This service decides identity, one grant at a time, the 7-day expiry,
 * the 180-day cooldown, replay of the same workout id, and whether promotions are open.
 * The client cannot choose the user id or the expiration.
 */
public final class WelcomeBackService {

    public static final Duration LENGTH = Duration.ofHours(7L * 24L);
    public static final Duration COOLDOWN = Duration.ofDays(180);

    public enum Status {
        ACTIVE,
        COOLDOWN,
        REPLAY,
        CLOSED,
        INCOMPATIBLE,
        INVALID,
        CONSUMED
    }

    public record Grant(
            UUID userId,
            String workoutId,
            Instant activatedAt,
            Instant expiresAt,
            Instant cooldownUntil
    ) {
    }

    public record Result(
            Status status,
            Instant activatedAt,
            Instant expiresAt,
            Instant cooldownUntil
    ) {
    }

    public interface Store {
        Grant find(UUID userId);

        boolean insert(Grant grant);

        boolean replace(Grant grant);
    }

    private final Store store;

    public WelcomeBackService(Store store) {
        this.store = store;
    }

    public Grant current(UUID userId) {
        return store.find(userId);
    }

    public boolean activeNow(UUID userId, Instant now) {
        Grant grant = store.find(userId);
        return grant != null && now.isBefore(grant.expiresAt());
    }

    public Result activate(
            UUID userId,
            String workoutId,
            Instant now,
            boolean promotionsOpen,
            boolean incompatiblePro
    ) {
        if (userId == null || workoutId == null || workoutId.isBlank() || workoutId.length() > 80) {
            return empty(Status.INVALID);
        }
        Grant existing = store.find(userId);
        if (existing != null && now.isBefore(existing.expiresAt())) {
            return active(existing);
        }
        if (existing != null && now.isBefore(existing.cooldownUntil())) {
            return copy(Status.COOLDOWN, existing);
        }
        if (existing != null && existing.workoutId().equals(workoutId)) {
            return copy(Status.REPLAY, existing);
        }
        if (!promotionsOpen) {
            return empty(Status.CLOSED);
        }
        if (incompatiblePro) {
            return empty(Status.INCOMPATIBLE);
        }
        Grant created = new Grant(
                userId,
                workoutId,
                now,
                now.plus(LENGTH),
                now.plus(COOLDOWN)
        );
        boolean saved = existing == null ? store.insert(created) : store.replace(created);
        if (!saved) {
            Grant raced = store.find(userId);
            if (raced == null) {
                return empty(Status.INVALID);
            }
            if (now.isBefore(raced.expiresAt())) {
                return active(raced);
            }
            return copy(Status.COOLDOWN, raced);
        }
        return active(created);
    }

    private static Result active(Grant grant) {
        return copy(Status.ACTIVE, grant);
    }

    private static Result copy(Status status, Grant grant) {
        return new Result(status, grant.activatedAt(), grant.expiresAt(), grant.cooldownUntil());
    }

    private static Result empty(Status status) {
        return new Result(status, null, null, null);
    }
}
