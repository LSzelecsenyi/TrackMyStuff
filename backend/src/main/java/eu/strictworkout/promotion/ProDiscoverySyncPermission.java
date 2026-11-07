package eu.strictworkout.promotion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Account-scoped permission for uploading Pro Discovery workout dates.
 * Collection is off until the account enables the current scope.
 * Turning it off deletes stored dates and advances the generation so an older
 * upload cannot put those dates back. A later enable starts an empty generation.
 * The promotional trial, one-time marker, and other programs are not changed.
 */
@Service
public class ProDiscoverySyncPermission {

    public static final int SCOPE_VERSION = 1;

    public record Permission(boolean enabled, int scopeVersion, long generation) {
    }

    private final JdbcTemplate jdbc;

    public ProDiscoverySyncPermission(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Permission enable(UUID userId, int scopeVersion, Instant now) {
        if (scopeVersion != SCOPE_VERSION) {
            throw ProDiscoverySyncException.scopeMismatch();
        }
        Permission current = lock(userId, now);
        if (current.enabled() && current.scopeVersion() == SCOPE_VERSION) {
            return current;
        }
        long generation = current.generation() + 1;
        jdbc.update(
                """
                update pro_discovery_activity_permission
                set enabled = true, scope_version = ?, generation = ?, updated_at = ?
                where user_id = ?
                """,
                SCOPE_VERSION,
                generation,
                Timestamp.from(now),
                userId
        );
        return new Permission(true, SCOPE_VERSION, generation);
    }

    /**
     * Idempotent. A second withdrawal does not advance the generation again,
     * and it still deletes any activity rows left behind.
     */
    @Transactional
    public Permission withdraw(UUID userId, Instant now) {
        Permission current = lock(userId, now);
        long generation = current.generation();
        if (current.enabled()) {
            generation = generation + 1;
            jdbc.update(
                    """
                    update pro_discovery_activity_permission
                    set enabled = false, generation = ?, updated_at = ?
                    where user_id = ?
                    """,
                    generation,
                    Timestamp.from(now),
                    userId
            );
        }
        deleteActivity(userId);
        return new Permission(false, SCOPE_VERSION, generation);
    }

    @Transactional
    public Permission requireCurrent(UUID userId, Long generation, Instant now) {
        Permission current = lock(userId, now);
        if (!current.enabled() || current.scopeVersion() != SCOPE_VERSION) {
            throw ProDiscoverySyncException.notPermitted();
        }
        if (generation == null || generation != current.generation()) {
            throw ProDiscoverySyncException.generationStale(current.generation());
        }
        return current;
    }

    @Transactional(readOnly = true)
    public Permission current(UUID userId) {
        List<Permission> rows = read(userId);
        if (rows.isEmpty()) {
            return new Permission(false, SCOPE_VERSION, 0L);
        }
        return rows.get(0);
    }

    private Permission lock(UUID userId, Instant now) {
        jdbc.update(
                """
                insert into pro_discovery_activity_permission (
                    user_id, enabled, scope_version, generation, updated_at
                ) values (?, false, ?, 1, ?)
                on conflict (user_id) do nothing
                """,
                userId,
                SCOPE_VERSION,
                Timestamp.from(now)
        );
        jdbc.queryForObject(
                "select generation from pro_discovery_activity_permission where user_id = ? for update",
                Long.class,
                userId
        );
        return read(userId).get(0);
    }

    private List<Permission> read(UUID userId) {
        return jdbc.query(
                """
                select enabled, scope_version, generation
                from pro_discovery_activity_permission
                where user_id = ?
                """,
                (rs, row) -> new Permission(rs.getBoolean(1), rs.getInt(2), rs.getLong(3)),
                userId
        );
    }

    private void deleteActivity(UUID userId) {
        jdbc.update("delete from pro_discovery_device_day where user_id = ?", userId);
        jdbc.update("delete from pro_discovery_device where user_id = ?", userId);
        jdbc.update("delete from pro_discovery_activity_day where user_id = ?", userId);
        jdbc.update("delete from pro_discovery_activity_sync where user_id = ?", userId);
    }
}
