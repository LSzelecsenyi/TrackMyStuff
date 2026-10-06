package eu.strictworkout.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    private UUID id;

    @Column(name = "google_subject", nullable = false, length = 255)
    private String googleSubject;

    @Column(length = 320)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminUser() {
    }

    public AdminUser(UUID id, String googleSubject, String email, boolean emailVerified, Instant createdAt) {
        this.id = id;
        this.googleSubject = googleSubject;
        this.email = email;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
