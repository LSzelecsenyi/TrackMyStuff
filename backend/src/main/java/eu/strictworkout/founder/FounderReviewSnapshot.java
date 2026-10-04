package eu.strictworkout.founder;

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
@Table(name = "founder_review_snapshot")
public class FounderReviewSnapshot {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "founder_application_id", nullable = false)
    private FounderApplication application;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "app_version", nullable = false, length = 32)
    private String appVersion;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(name = "qualifying_workout_count", nullable = false)
    private int qualifyingWorkoutCount;

    @Column(name = "distinct_workout_day_count", nullable = false)
    private int distinctWorkoutDayCount;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "feedback_text", nullable = false, length = 8000)
    private String feedbackText;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FounderReviewSnapshot() {
    }

    public FounderReviewSnapshot(
            UUID id,
            FounderApplication application,
            Instant submittedAt,
            String appVersion,
            String platform,
            int qualifyingWorkoutCount,
            int distinctWorkoutDayCount,
            String feedbackText
    ) {
        this.id = id;
        this.application = application;
        this.submittedAt = submittedAt;
        this.appVersion = appVersion;
        this.platform = platform;
        this.qualifyingWorkoutCount = qualifyingWorkoutCount;
        this.distinctWorkoutDayCount = distinctWorkoutDayCount;
        this.enrolledAt = application.getEnrolledAt();
        this.deadlineAt = application.getDeadlineAt();
        this.feedbackText = feedbackText;
        this.createdAt = submittedAt;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public int getQualifyingWorkoutCount() {
        return qualifyingWorkoutCount;
    }

    public int getDistinctWorkoutDayCount() {
        return distinctWorkoutDayCount;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public boolean sameDiagnostics(String appVersion, String platform) {
        return this.appVersion.equals(appVersion) && this.platform.equals(platform);
    }
}
