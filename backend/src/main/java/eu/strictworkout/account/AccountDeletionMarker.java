package eu.strictworkout.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "account_deletion_marker")
public class AccountDeletionMarker {

    @Id
    @Column(name = "subject_hash", length = 64)
    private String subjectHash;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    @Column(name = "founder_used", nullable = false)
    private boolean founderUsed;

    @Column(name = "pro_discovery_used", nullable = false)
    private boolean proDiscoveryUsed;

    @Column(name = "welcome_back_used", nullable = false)
    private boolean welcomeBackUsed;

    @Column(name = "early_adopter_used", nullable = false)
    private boolean earlyAdopterUsed;

    protected AccountDeletionMarker() {
    }

    public boolean isFounderUsed() {
        return founderUsed;
    }

    public boolean isProDiscoveryUsed() {
        return proDiscoveryUsed;
    }

    public boolean isWelcomeBackUsed() {
        return welcomeBackUsed;
    }

    public boolean isEarlyAdopterUsed() {
        return earlyAdopterUsed;
    }
}
