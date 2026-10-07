package eu.strictworkout.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
