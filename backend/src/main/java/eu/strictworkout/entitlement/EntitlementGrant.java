package eu.strictworkout.entitlement;

import eu.strictworkout.founder.FounderApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entitlement_grant")
public class EntitlementGrant {

    public static final String FOUNDER_LIFETIME = "FOUNDER_LIFETIME";
    public static final String FOUNDER_PRO = "FOUNDER_PRO";

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private eu.strictworkout.identity.AppUser user;

    @Column(nullable = false, length = 32)
    private String source;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "founder_application_id", nullable = false)
    private FounderApplication application;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected EntitlementGrant() {
    }

    public EntitlementGrant(UUID id, eu.strictworkout.identity.AppUser user, FounderApplication application, Instant grantedAt) {
        this.id = id;
        this.user = user;
        this.source = FOUNDER_PRO;
        this.application = application;
        this.grantedAt = grantedAt;
        this.createdAt = grantedAt;
        this.expiresAt = FounderProTerm.expiresAt(grantedAt);
    }

    public String getSource() {
        return source;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean legacyLifetime() {
        return FOUNDER_LIFETIME.equals(source);
    }

    public boolean founderProActive(Instant now) {
        return FOUNDER_PRO.equals(source) && FounderProTerm.active(expiresAt, now);
    }
}
