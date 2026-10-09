package eu.strictworkout.promotion;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class PromotionalTrialService {

    public static final String PRO_DISCOVERY = "PRO_DISCOVERY";
    public static final Duration LENGTH = Duration.ofHours(14L * 24L);

    public enum Status {
        ACTIVE,
        ALREADY_USED,
        CLOSED,
        INCOMPATIBLE,
        INVALID
    }

    public record Trial(UUID userId, Instant activatedAt, Instant expiresAt) {
    }

    public record Result(Status status, Instant activatedAt, Instant expiresAt) {
    }

    public interface Store {
        Trial find(UUID userId);

        boolean insert(Trial trial);
    }

    private final Store store;

    public PromotionalTrialService(Store store) {
        this.store = store;
    }

    public Result activate(UUID userId, Instant now, boolean promotionsOpen, boolean incompatiblePro) {
        Trial existing = store.find(userId);
        if (existing != null) {
            return existingResult(existing, now);
        }
        if (!promotionsOpen) {
            return new Result(Status.CLOSED, null, null);
        }
        if (incompatiblePro) {
            return new Result(Status.INCOMPATIBLE, null, null);
        }
        Trial created = new Trial(userId, now, now.plus(LENGTH));
        if (!store.insert(created)) {
            Trial raced = store.find(userId);
            return raced == null ? new Result(Status.INVALID, null, null) : existingResult(raced, now);
        }
        return new Result(Status.ACTIVE, created.activatedAt(), created.expiresAt());
    }

    /**
     * One-time import of a trial that already existed only on the device.
     * The client timestamps are clamped to 14 days and are not treated as proof
     * that the workouts were real. A later activation cannot extend this row.
     */
    public Result migrate(UUID userId, Instant activatedAt, Instant expiresAt, Instant now) {
        if (activatedAt == null || expiresAt == null || !expiresAt.isAfter(activatedAt) || activatedAt.isAfter(now)) {
            return new Result(Status.INVALID, null, null);
        }
        Trial existing = store.find(userId);
        if (existing != null) {
            return existingResult(existing, now);
        }
        Instant cap = activatedAt.plus(LENGTH);
        Instant storedExpiry = expiresAt.isAfter(cap) ? cap : expiresAt;
        Trial created = new Trial(userId, activatedAt, storedExpiry);
        if (!store.insert(created)) {
            Trial raced = store.find(userId);
            return raced == null ? new Result(Status.INVALID, null, null) : existingResult(raced, now);
        }
        return new Result(Status.ACTIVE, created.activatedAt(), created.expiresAt());
    }

    private static Result existingResult(Trial existing, Instant now) {
        if (now.isBefore(existing.expiresAt())) {
            return new Result(Status.ACTIVE, existing.activatedAt(), existing.expiresAt());
        }
        return new Result(Status.ALREADY_USED, existing.activatedAt(), existing.expiresAt());
    }
}
