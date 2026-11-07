package eu.strictworkout.promotion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Account-scoped reminder. The row is created only when an offer was displayed.
 * Reading eligibility does not write it. A second acknowledgement in the same
 * cycle does not move the timestamp.
 */
@Repository
public class ProDiscoveryOffers {

    private final JdbcTemplate jdbc;

    public ProDiscoveryOffers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(UUID userId) {
        jdbc.query(
                "select id from app_user where id = ? for update",
                (rs, row) -> rs.getObject("id", UUID.class),
                userId
        );
    }

    public Instant registeredAt(UUID userId) {
        List<Instant> times = jdbc.query(
                "select created_at from app_user where id = ?",
                (rs, row) -> rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                userId
        );
        return times.isEmpty() ? null : times.get(0);
    }

    public Instant presentedAt(UUID userId) {
        List<Instant> times = jdbc.query(
                "select presented_at from pro_discovery_offer where user_id = ?",
                (rs, row) -> {
                    OffsetDateTime value = rs.getObject("presented_at", OffsetDateTime.class);
                    return value == null ? null : value.toInstant();
                },
                userId
        );
        return times.isEmpty() ? null : times.get(0);
    }

    /**
     * @return true when this call recorded a new presentation
     */
    @Transactional
    public boolean acknowledge(UUID userId, Instant now) {
        lock(userId);
        Instant registration = registeredAt(userId);
        if (registration == null) {
            return false;
        }
        Instant presented = presentedAt(userId);
        Instant anchor = presented == null ? registration : presented;
        if (!ProDiscoveryEligibility.offerDue(anchor, now)) {
            return false;
        }
        jdbc.update(
                """
                insert into pro_discovery_offer (user_id, presented_at, updated_at)
                values (?, ?, ?)
                on conflict (user_id) do update set
                    presented_at = excluded.presented_at,
                    updated_at = excluded.updated_at
                """,
                userId,
                utc(now),
                utc(now)
        );
        return true;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
