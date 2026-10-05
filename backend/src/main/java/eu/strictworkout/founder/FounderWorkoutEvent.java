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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "founder_workout_event")
public class FounderWorkoutEvent {

    @Id
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "founder_application_id", nullable = false)
    private FounderApplication application;

    @Column(name = "client_workout_id", nullable = false)
    private UUID clientWorkoutId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "workout_local_date", nullable = false)
    private LocalDate workoutLocalDate;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "exercise_count")
    private Integer exerciseCount;

    @Column(name = "completed_set_count")
    private Integer completedSetCount;

    @Column(name = "from_template")
    private Boolean fromTemplate;

    @Column(name = "used_external_load")
    private Boolean usedExternalLoad;

    protected FounderWorkoutEvent() {
    }

    public FounderWorkoutEvent(
            UUID id,
            FounderApplication application,
            UUID clientWorkoutId,
            Instant completedAt,
            LocalDate workoutLocalDate,
            Instant createdAt,
            WorkoutObservation observation
    ) {
        this.id = id;
        this.application = application;
        this.clientWorkoutId = clientWorkoutId;
        this.completedAt = completedAt;
        this.workoutLocalDate = workoutLocalDate;
        this.createdAt = createdAt;
        WorkoutObservation stored = observation == null ? null : observation.normalized();
        if (stored != null) {
            this.displayName = stored.displayName();
            this.durationSeconds = stored.durationSeconds();
            this.exerciseCount = stored.exerciseCount();
            this.completedSetCount = stored.completedSetCount();
            this.fromTemplate = stored.fromTemplate();
            this.usedExternalLoad = stored.usedExternalLoad();
        }
    }

    public UUID getClientWorkoutId() {
        return clientWorkoutId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public LocalDate getWorkoutLocalDate() {
        return workoutLocalDate;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Integer getExerciseCount() {
        return exerciseCount;
    }

    public Integer getCompletedSetCount() {
        return completedSetCount;
    }

    public Boolean getFromTemplate() {
        return fromTemplate;
    }

    public Boolean getUsedExternalLoad() {
        return usedExternalLoad;
    }

    public boolean samePayload(Instant completedAt, LocalDate localDate) {
        return this.completedAt.equals(completedAt) && this.workoutLocalDate.equals(localDate);
    }

    public FounderWorkoutFact fact() {
        return new FounderWorkoutFact(clientWorkoutId, completedAt, workoutLocalDate);
    }
}
