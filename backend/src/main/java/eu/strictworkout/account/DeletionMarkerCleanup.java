package eu.strictworkout.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Clears one marker flag after that program is RETIRED. A paused or full program
 * keeps its flag. A row is deleted only when every flag is false. Entitlements,
 * Play purchases, and program counters are not touched.
 */
@Component
public class DeletionMarkerCleanup {

    private static final Logger log = LoggerFactory.getLogger(DeletionMarkerCleanup.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public DeletionMarkerCleanup(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions);
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void scheduled() {
        try {
            cleanup();
        } catch (RuntimeException exception) {
            log.warn("Deletion marker cleanup did not finish: {}", exception.getClass().getSimpleName());
        }
    }

    public int cleanup() {
        Integer changed = transactions.execute(status -> {
            jdbc.queryForList("select pg_advisory_xact_lock(hashtext('strict-deletion-marker-cleanup'))");
            int founder = jdbc.update("""
                    update account_deletion_marker
                    set founder_used = false
                    where founder_used
                      and exists (
                          select 1 from founder_program_capacity
                          where id = 1 and lifecycle = 'RETIRED'
                      )
                    """);
            int earlyAdopter = jdbc.update("""
                    update account_deletion_marker
                    set early_adopter_used = false
                    where early_adopter_used
                      and exists (
                          select 1 from early_adopter_cohort
                          where id = 1 and lifecycle = 'RETIRED'
                      )
                    """);
            int proDiscovery = jdbc.update("""
                    update account_deletion_marker
                    set pro_discovery_used = false
                    where pro_discovery_used
                      and exists (
                          select 1 from promotional_program
                          where program = 'PRO_DISCOVERY' and lifecycle = 'RETIRED'
                      )
                    """);
            int welcomeBack = jdbc.update("""
                    update account_deletion_marker
                    set welcome_back_used = false
                    where welcome_back_used
                      and exists (
                          select 1 from promotional_program
                          where program = 'WELCOME_BACK' and lifecycle = 'RETIRED'
                      )
                    """);
            int removed = jdbc.update("""
                    delete from account_deletion_marker
                    where not founder_used
                      and not pro_discovery_used
                      and not welcome_back_used
                      and not early_adopter_used
                    """);
            return founder + earlyAdopter + proDiscovery + welcomeBack + removed;
        });
        return changed == null ? 0 : changed;
    }
}
