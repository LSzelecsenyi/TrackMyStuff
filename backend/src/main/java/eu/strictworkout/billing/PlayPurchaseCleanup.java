package eu.strictworkout.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Deletes a Play purchase row 24 calendar months after it stopped being recoverable.
 * Active, paused, pending, grace, and account-hold rows are kept. The two-day
 * reconciliation lookback is not used. Rows with a retention hold, or without a
 * reliable end time, are left in place and counted.
 */
@Component
public class PlayPurchaseCleanup {

    private static final Logger log = LoggerFactory.getLogger(PlayPurchaseCleanup.class);

    private static final String ELIGIBLE = """
            retention_hold = false
              and entitled = false
              and ended_at is not null
              and ((ended_at at time zone 'utc') + interval '24 months') <= (now() at time zone 'utc')
              and (
                    (subscription_state in ('EXPIRED', 'ACTIVE', 'CANCELED', 'IN_GRACE_PERIOD')
                        and expiry_time is not null
                        and expiry_time <= now())
                    or subscription_state in ('REVOKED', 'PENDING_PURCHASE_CANCELED')
                  )
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PlayPurchaseCleanup(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions);
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void scheduled() {
        try {
            cleanup();
        } catch (RuntimeException exception) {
            log.warn("Play purchase cleanup did not finish: {}", exception.getClass().getSimpleName());
        }
    }

    public int cleanup() {
        Integer removed = transactions.execute(status -> {
            jdbc.queryForList("select pg_advisory_xact_lock(hashtext('strict-play-purchase-cleanup'))");
            List<String> hashes = jdbc.queryForList(
                    "select token_hash from play_subscription where " + ELIGIBLE + " order by token_hash",
                    String.class
            );
            int deleted = 0;
            for (String hash : hashes) {
                jdbc.queryForList("select pg_advisory_xact_lock(hashtext(?))", hash);
                deleted += jdbc.update(
                        "delete from play_subscription where token_hash = ? and " + ELIGIBLE,
                        hash
                );
            }
            int held = count("select count(*) from play_subscription where retention_hold = true");
            int unverifiable = count("""
                    select count(*) from play_subscription
                    where ended_at is null
                      and entitled = false
                      and retention_hold = false
                      and subscription_state not in (
                          'ACTIVE', 'CANCELED', 'IN_GRACE_PERIOD', 'PAUSED', 'ON_HOLD', 'PENDING'
                      )
                    """);
            log.info(
                    "Play purchase cleanup removed {} ended purchase rows, left {} on retention hold, left {} unverifiable",
                    deleted,
                    held,
                    unverifiable
            );
            return deleted;
        });
        return removed == null ? 0 : removed;
    }

    private int count(String sql) {
        Integer count = jdbc.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }
}
