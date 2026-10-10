package eu.strictworkout.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcAccountDeletionStore implements AccountDeletionStore {

    private final JdbcTemplate jdbc;

    JdbcAccountDeletionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID findGoogleUser(String subject) {
        List<UUID> ids = jdbc.query(
                "select user_id from external_identity where provider = 'GOOGLE' and provider_subject = ?",
                (rs, row) -> rs.getObject("user_id", UUID.class),
                subject
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Override
    public String googleSubject(UUID userId) {
        List<String> subjects = jdbc.query(
                "select provider_subject from external_identity where user_id = ? and provider = 'GOOGLE'",
                (rs, row) -> rs.getString("provider_subject"),
                userId
        );
        return subjects.isEmpty() ? null : subjects.get(0);
    }

    @Override
    public boolean lock(UUID userId) {
        List<UUID> locked = jdbc.query(
                "select id from app_user where id = ? for update",
                (rs, row) -> rs.getObject("id", UUID.class),
                userId
        );
        return !locked.isEmpty();
    }

    @Override
    public Usage usage(UUID userId) {
        return new Usage(
                exists("select 1 from founder_application where user_id = ?", userId),
                exists("select 1 from promotional_trial where user_id = ?", userId),
                exists("select 1 from welcome_back_grant where user_id = ?", userId),
                exists("select 1 from account_status_grant where user_id = ? and status = 'EARLY_ADOPTER'", userId)
        );
    }

    @Override
    public void blockSubscriptionClaims(UUID userId, Instant now) {
        jdbc.update(
                """
                update play_subscription
                set linked_user_id = null, claim_blocked = true, updated_at = ?
                where linked_user_id = ?
                """,
                utc(now),
                userId
        );
    }

    @Override
    public void deleteAccountRows(UUID userId) {
        jdbc.update(
                """
                delete from founder_review_decision
                where founder_application_id in (select id from founder_application where user_id = ?)
                """,
                userId
        );
        jdbc.update("delete from entitlement_grant where user_id = ?", userId);
        jdbc.update(
                """
                delete from founder_review_snapshot
                where founder_application_id in (select id from founder_application where user_id = ?)
                """,
                userId
        );
        jdbc.update(
                """
                delete from founder_workout_event
                where founder_application_id in (select id from founder_application where user_id = ?)
                """,
                userId
        );
        jdbc.update("delete from founder_application where user_id = ?", userId);
        jdbc.update("delete from promotional_trial where user_id = ?", userId);
        jdbc.update("delete from welcome_back_grant where user_id = ?", userId);
        jdbc.update("delete from account_status_grant where user_id = ?", userId);
        jdbc.update("delete from auth_session where user_id = ?", userId);
        jdbc.update("delete from external_identity where user_id = ?", userId);
    }

    @Override
    public void insertMarker(String subjectHash, Instant deletedAt, Usage usage) {
        jdbc.update(
                """
                insert into account_deletion_marker (
                    subject_hash, deleted_at, founder_used, pro_discovery_used, welcome_back_used, early_adopter_used
                ) values (?, ?, ?, ?, ?, ?)
                on conflict (subject_hash) do update set
                    deleted_at = excluded.deleted_at,
                    founder_used = account_deletion_marker.founder_used or excluded.founder_used,
                    pro_discovery_used = account_deletion_marker.pro_discovery_used or excluded.pro_discovery_used,
                    welcome_back_used = account_deletion_marker.welcome_back_used or excluded.welcome_back_used,
                    early_adopter_used = account_deletion_marker.early_adopter_used or excluded.early_adopter_used
                """,
                subjectHash,
                utc(deletedAt),
                usage.founder(),
                usage.proDiscovery(),
                usage.welcomeBack(),
                usage.earlyAdopter()
        );
    }

    @Override
    public void deleteUser(UUID userId) {
        int removed = jdbc.update("delete from app_user where id = ?", userId);
        if (removed != 1) {
            throw new IllegalStateException("Account row was not deleted");
        }
    }

    @Override
    public boolean marker(String subjectHash) {
        Integer count = jdbc.queryForObject(
                "select count(*) from account_deletion_marker where subject_hash = ?",
                Integer.class,
                subjectHash
        );
        return count != null && count > 0;
    }

    @Override
    public boolean paidSubscriptionKnown(UUID userId) {
        Boolean known = jdbc.queryForObject(
                """
                select exists(
                    select 1 from play_subscription
                    where linked_user_id = ? and (entitled or auto_renewing)
                )
                """,
                Boolean.class,
                userId
        );
        return Boolean.TRUE.equals(known);
    }

    private boolean exists(String sql, UUID userId) {
        Integer count = jdbc.queryForObject("select count(*) from (" + sql + ") usage_row", Integer.class, userId);
        return count != null && count > 0;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
