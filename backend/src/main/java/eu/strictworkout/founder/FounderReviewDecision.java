package eu.strictworkout.founder;

import eu.strictworkout.admin.AdminUser;
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
@Table(name = "founder_review_decision")
public class FounderReviewDecision {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "founder_application_id", nullable = false)
    private FounderApplication application;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private AdminUser admin;

    @Column(nullable = false, length = 32)
    private String decision;

    @Column(length = 2000)
    private String reason;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected FounderReviewDecision() {
    }

    public FounderReviewDecision(
            UUID id,
            FounderApplication application,
            AdminUser admin,
            FounderStatus decision,
            String reason,
            Instant decidedAt
    ) {
        this.id = id;
        this.application = application;
        this.admin = admin;
        this.decision = decision.name();
        this.reason = reason;
        this.decidedAt = decidedAt;
    }

    public UUID getAdminId() {
        return admin.getId();
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
