package eu.strictworkout.admin;

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
@Table(name = "admin_session")
public class AdminSession {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AdminUser admin;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AdminSession() {
    }

    public AdminSession(UUID id, AdminUser admin, byte[] tokenHash, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.admin = admin;
        this.tokenHash = tokenHash.clone();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return admin.getId();
    }

    public String getGoogleSubject() {
        return admin.getGoogleSubject();
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
}
