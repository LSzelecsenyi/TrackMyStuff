package eu.strictworkout.promotion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes Pro Discovery activity dates after a trial row exists.
 * The trial row and the deletion marker are not touched.
 * Dates are not kept for the Play purchase retention period.
 */
@Component
public class ProDiscoveryActivityCleanup {

    private static final Logger log = LoggerFactory.getLogger(ProDiscoveryActivityCleanup.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public ProDiscoveryActivityCleanup(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions);
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void scheduled() {
        try {
            cleanup();
        } catch (RuntimeException exception) {
            log.warn("Pro Discovery activity cleanup did not finish: {}", exception.getClass().getSimpleName());
        }
    }

    public int cleanup() {
        Integer removed = transactions.execute(status -> {
            jdbc.queryForList("select pg_advisory_xact_lock(hashtext('strict-pro-discovery-activity-cleanup'))");
            int days = jdbc.update("""
                    delete from pro_discovery_activity_day day
                    where exists (
                        select 1 from promotional_trial trial
                        where trial.user_id = day.user_id
                          and trial.promotion_type = 'PRO_DISCOVERY'
                    )
                    """);
            int sync = jdbc.update("""
                    delete from pro_discovery_activity_sync sync
                    where exists (
                        select 1 from promotional_trial trial
                        where trial.user_id = sync.user_id
                          and trial.promotion_type = 'PRO_DISCOVERY'
                    )
                    """);
            int deviceDays = jdbc.update("""
                    delete from pro_discovery_device_day day
                    where exists (
                        select 1 from promotional_trial trial
                        where trial.user_id = day.user_id
                          and trial.promotion_type = 'PRO_DISCOVERY'
                    )
                    """);
            int devices = jdbc.update("""
                    delete from pro_discovery_device device
                    where exists (
                        select 1 from promotional_trial trial
                        where trial.user_id = device.user_id
                          and trial.promotion_type = 'PRO_DISCOVERY'
                    )
                    """);
            int staleDays = jdbc.update("""
                    delete from pro_discovery_device_day day
                    where exists (
                        select 1 from pro_discovery_device device
                        where device.user_id = day.user_id
                          and device.installation_id = day.installation_id
                          and device.updated_at < now() - interval '18 months'
                          and exists (
                              select 1 from pro_discovery_device other
                              where other.user_id = device.user_id
                                and other.installation_id <> device.installation_id
                                and other.updated_at >= now() - interval '18 months'
                          )
                    )
                    """);
            int staleDevices = jdbc.update("""
                    delete from pro_discovery_device device
                    where device.updated_at < now() - interval '18 months'
                      and exists (
                          select 1 from pro_discovery_device other
                          where other.user_id = device.user_id
                            and other.installation_id <> device.installation_id
                            and other.updated_at >= now() - interval '18 months'
                      )
                    """);
            return days + sync + deviceDays + devices + staleDays + staleDevices;
        });
        int count = removed == null ? 0 : removed;
        if (count > 0) {
            log.info("Pro Discovery activity cleanup removed {} rows", count);
        }
        return count;
    }
}
