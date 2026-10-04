package eu.strictworkout.founder;

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
@Table(name = "founder_application")
public class FounderApplication {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FounderStatus status;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "feedback_text", length = 8000)
    private String feedbackText;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "feedback_submitted_at")
    private Instant feedbackSubmittedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "tester_report_submitted_at")
    private Instant testerReportSubmittedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "pending_at")
    private Instant pendingAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "expired_at")
    private Instant expiredAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FounderApplication() {
    }

    public FounderApplication(UUID id, AppUser user, Instant enrolledAt, Instant deadlineAt) {
        this.id = id;
        this.user = user;
        this.status = FounderStatus.ACTIVE_FREE;
        this.enrolledAt = enrolledAt;
        this.deadlineAt = deadlineAt;
        this.createdAt = enrolledAt;
        this.updatedAt = enrolledAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public FounderStatus getStatus() {
        return status;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public Instant getFeedbackSubmittedAt() {
        return feedbackSubmittedAt;
    }

    public Instant getTesterReportSubmittedAt() {
        return testerReportSubmittedAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Instant getPendingAt() {
        return pendingAt;
    }

    public boolean feedbackSubmitted() {
        return feedbackSubmittedAt != null;
    }

    public boolean reportSubmitted() {
        return testerReportSubmittedAt != null;
    }

    public void replaceFeedback(String text, Instant submittedAt) {
        this.feedbackText = text;
        this.feedbackSubmittedAt = submittedAt;
        this.updatedAt = submittedAt;
    }

    public void markReportSubmitted(Instant submittedAt) {
        this.testerReportSubmittedAt = submittedAt;
        this.updatedAt = submittedAt;
    }

    public void applyStatus(FounderStatus next, Instant at) {
        if (next == status) {
            return;
        }
        this.status = next;
        this.updatedAt = at;
        if (next == FounderStatus.PENDING_APPROVAL && pendingAt == null) {
            this.pendingAt = at;
        }
        if (next == FounderStatus.EXPIRED && expiredAt == null) {
            this.expiredAt = at;
        }
    }

    public void markReviewed(FounderStatus decision, Instant at) {
        if (status != FounderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only a pending Founder application can be reviewed");
        }
        if (decision != FounderStatus.APPROVED && decision != FounderStatus.REJECTED) {
            throw new IllegalStateException("A review decision must approve or reject");
        }
        this.status = decision;
        this.updatedAt = at;
    }
}
