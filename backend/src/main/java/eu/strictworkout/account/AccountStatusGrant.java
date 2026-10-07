package eu.strictworkout.account;

import eu.strictworkout.identity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "account_status_grant")
public class AccountStatusGrant {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountStatus status;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountStatusGrant() {
    }

    public AccountStatusGrant(UUID id, AppUser user, AccountStatus status, Instant grantedAt, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.status = status;
        this.grantedAt = grantedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
