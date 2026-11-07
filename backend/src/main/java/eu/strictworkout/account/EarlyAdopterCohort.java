package eu.strictworkout.account;

import eu.strictworkout.program.ProgramLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "early_adopter_cohort")
public class EarlyAdopterCohort {

    @Id
    private Short id;

    @Column(name = "assigned_count", nullable = false)
    private int assignedCount;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "backfill_completed", nullable = false)
    private boolean backfillCompleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgramLifecycle lifecycle;

    protected EarlyAdopterCohort() {
    }

    public int getAssignedCount() {
        return assignedCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isBackfillCompleted() {
        return backfillCompleted;
    }

    public ProgramLifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(ProgramLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public boolean hasRoom() {
        return assignedCount < capacity;
    }

    public void claimSlot() {
        if (!hasRoom()) {
            throw new IllegalStateException("Early Adopter cohort is closed");
        }
        assignedCount = assignedCount + 1;
    }

    public void completeBackfill() {
        backfillCompleted = true;
    }
}
