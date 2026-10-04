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

    protected FounderWorkoutEvent() {
    }

    public FounderWorkoutEvent(
            UUID id,
            FounderApplication application,
            UUID clientWorkoutId,
            Instant completedAt,
            LocalDate workoutLocalDate,
            Instant createdAt
    ) {
        this.id = id;
        this.application = application;
        this.clientWorkoutId = clientWorkoutId;
        this.completedAt = completedAt;
        this.workoutLocalDate = workoutLocalDate;
        this.createdAt = createdAt;
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

    public boolean samePayload(Instant completedAt, LocalDate localDate) {
        return this.completedAt.equals(completedAt) && this.workoutLocalDate.equals(localDate);
    }

    public FounderWorkoutFact fact() {
        return new FounderWorkoutFact(clientWorkoutId, completedAt, workoutLocalDate);
    }
}
