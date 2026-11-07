package eu.strictworkout.promotion;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Activity dates are stored per account and installation.
 * A device replaces only its own dates, and only for the current permission generation.
 * The qualifying set is the distinct union of those device sets plus any legacy
 * account-wide rows that have not yet been deleted.
 * A request without an installation id is rejected and writes nothing, so an old
 * account-wide REPLACE cannot remove another device's dates.
 */
@Service
public class ProDiscoveryActivity {

    public enum Mode {
        UNION,
        REPLACE
    }

    public record SyncResult(long revision, boolean changed) {
    }

    private final JdbcTemplate jdbc;
    private final ProDiscoverySyncPermission permission;

    public ProDiscoveryActivity(JdbcTemplate jdbc, ProDiscoverySyncPermission permission) {
        this.jdbc = jdbc;
        this.permission = permission;
    }

    /**
     * Legacy account-wide sync. Dates are validated, then the write is refused.
     * Nothing is inserted or deleted.
     */
    @Transactional
    public SyncResult sync(UUID userId, Mode mode, Long baseRevision, List<String> rawDates, Instant now) {
        parse(rawDates, ProDiscoveryEligibility.evaluationDate(now));
        throw ProDiscoverySyncException.deviceRequired();
    }

    @Transactional
    public SyncResult syncDevice(
            UUID userId,
            UUID installationId,
            Long baseRevision,
            Long generation,
            List<String> rawDates,
            Instant now
    ) {
        if (installationId == null) {
            throw ProDiscoverySyncException.deviceRequired();
        }
        LocalDate evaluationDate = ProDiscoveryEligibility.evaluationDate(now);
        List<LocalDate> dates = parse(rawDates, evaluationDate);
        permission.requireCurrent(userId, generation, now);
        lock(userId, now);
        if (trialExists(userId)) {
            throw ProDiscoverySyncException.trialAlreadyRecorded();
        }
        long revision = lockDevice(userId, installationId, generation, now);
        if (baseRevision == null || baseRevision != revision) {
            throw ProDiscoverySyncException.stale(revision);
        }
        deleteMissing(userId, installationId, dates);
        insertMissing(userId, installationId, dates, revision == 0L, now);
        revision = bumpDevice(userId, installationId, generation, now);
        return new SyncResult(revision, true);
    }

    @Transactional
    public void lock(UUID userId, Instant now) {
        jdbc.update(
                """
                insert into pro_discovery_activity_sync (user_id, revision, updated_at)
                values (?, 0, ?)
                on conflict (user_id) do nothing
                """,
                userId,
                Timestamp.from(now)
        );
        List<Long> locked = jdbc.query(
                "select revision from pro_discovery_activity_sync where user_id = ? for update",
                (rs, row) -> rs.getLong(1),
                userId
        );
        if (locked.isEmpty()) {
            jdbc.update(
                    """
                    insert into pro_discovery_activity_sync (user_id, revision, updated_at)
                    values (?, 0, ?)
                    """,
                    userId,
                    Timestamp.from(now)
            );
            jdbc.queryForObject(
                    "select revision from pro_discovery_activity_sync where user_id = ? for update",
                    Long.class,
                    userId
            );
        }
    }

    @Transactional(readOnly = true)
    public long revision(UUID userId) {
        List<Long> rows = jdbc.query(
                "select revision from pro_discovery_activity_sync where user_id = ?",
                (rs, row) -> rs.getLong(1),
                userId
        );
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<LocalDate> dates(UUID userId) {
        return jdbc.query(
                """
                select distinct workout_date from (
                    select workout_date from pro_discovery_activity_day where user_id = ?
                    union
                    select workout_date from pro_discovery_device_day where user_id = ?
                ) qualifying
                order by workout_date
                """,
                (rs, row) -> rs.getDate(1).toLocalDate(),
                userId,
                userId
        );
    }

    @Transactional
    public void deleteFor(UUID userId) {
        jdbc.update("delete from pro_discovery_device_day where user_id = ?", userId);
        jdbc.update("delete from pro_discovery_device where user_id = ?", userId);
        jdbc.update("delete from pro_discovery_activity_day where user_id = ?", userId);
    }

    private boolean trialExists(UUID userId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from promotional_trial where user_id = ? and promotion_type = 'PRO_DISCOVERY'",
                Integer.class,
                userId
        );
        return count != null && count > 0;
    }

    private long lockDevice(UUID userId, UUID installationId, long generation, Instant now) {
        jdbc.update(
                """
                insert into pro_discovery_device (
                    user_id, installation_id, revision, generation, updated_at
                ) values (?, ?, 0, ?, ?)
                on conflict (user_id, installation_id) do nothing
                """,
                userId,
                installationId,
                generation,
                Timestamp.from(now)
        );
        List<Long> locked = jdbc.query(
                """
                select revision from pro_discovery_device
                where user_id = ? and installation_id = ?
                for update
                """,
                (rs, row) -> rs.getLong(1),
                userId,
                installationId
        );
        if (!locked.isEmpty()) {
            return locked.get(0);
        }
        jdbc.update(
                """
                insert into pro_discovery_device (
                    user_id, installation_id, revision, generation, updated_at
                ) values (?, ?, 0, ?, ?)
                """,
                userId,
                installationId,
                generation,
                Timestamp.from(now)
        );
        return jdbc.queryForObject(
                """
                select revision from pro_discovery_device
                where user_id = ? and installation_id = ?
                for update
                """,
                Long.class,
                userId,
                installationId
        );
    }

    private long bumpDevice(UUID userId, UUID installationId, long generation, Instant now) {
        jdbc.update(
                """
                update pro_discovery_device
                set revision = revision + 1, generation = ?, updated_at = ?
                where user_id = ? and installation_id = ?
                """,
                generation,
                Timestamp.from(now),
                userId,
                installationId
        );
        return jdbc.queryForObject(
                "select revision from pro_discovery_device where user_id = ? and installation_id = ?",
                Long.class,
                userId,
                installationId
        );
    }

    private int insertMissing(UUID userId, UUID installationId, List<LocalDate> dates, boolean historical, Instant now) {
        if (dates.isEmpty()) {
            return 0;
        }
        jdbc.batchUpdate(
                """
                insert into pro_discovery_device_day (
                    user_id, installation_id, workout_date, historical, server_first_observed_at
                ) values (?, ?, ?, ?, ?)
                on conflict (user_id, installation_id, workout_date) do nothing
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        statement.setObject(1, userId);
                        statement.setObject(2, installationId);
                        statement.setDate(3, Date.valueOf(dates.get(index)));
                        statement.setBoolean(4, historical);
                        statement.setTimestamp(5, Timestamp.from(now));
                    }

                    @Override
                    public int getBatchSize() {
                        return dates.size();
                    }
                }
        );
        return dates.size();
    }

    private void deleteMissing(UUID userId, UUID installationId, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            jdbc.update(
                    "delete from pro_discovery_device_day where user_id = ? and installation_id = ?",
                    userId,
                    installationId
            );
            return;
        }
        StringBuilder sql = new StringBuilder(
                "delete from pro_discovery_device_day where user_id = ? and installation_id = ? and workout_date not in ("
        );
        Object[] args = new Object[dates.size() + 2];
        args[0] = userId;
        args[1] = installationId;
        for (int index = 0; index < dates.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("?");
            args[index + 2] = Date.valueOf(dates.get(index));
        }
        sql.append(")");
        jdbc.update(sql.toString(), args);
    }

    static List<LocalDate> parse(List<String> rawDates, LocalDate evaluationDate) {
        if (rawDates == null) {
            throw ProDiscoverySyncException.invalid();
        }
        if (rawDates.size() > ProDiscoveryEligibility.MAX_DATES) {
            throw ProDiscoverySyncException.invalid();
        }
        LinkedHashSet<LocalDate> unique = new LinkedHashSet<>();
        LocalDate latest = ProDiscoveryEligibility.latestAcceptedDate(evaluationDate);
        for (String raw : rawDates) {
            if (raw == null || raw.length() != 10) {
                throw ProDiscoverySyncException.invalid();
            }
            LocalDate date;
            try {
                date = LocalDate.parse(raw);
            } catch (RuntimeException exception) {
                throw ProDiscoverySyncException.invalid();
            }
            if (date.isBefore(ProDiscoveryEligibility.EARLIEST_DATE) || date.isAfter(latest)) {
                throw ProDiscoverySyncException.invalid();
            }
            unique.add(date);
        }
        if (unique.size() > ProDiscoveryEligibility.MAX_DATES) {
            throw ProDiscoverySyncException.invalid();
        }
        return new ArrayList<>(unique);
    }
}
