package eu.strictworkout.founder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "founder_program_capacity")
public class FounderProgramCapacity {

    @Id
    private Short id;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "enrollment_open", nullable = false)
    private boolean enrollmentOpen;

    protected FounderProgramCapacity() {
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isEnrollmentOpen() {
        return enrollmentOpen;
    }

    public boolean hasRoom() {
        return enrolledCount < capacity;
    }

    public void claimSlot() {
        if (!hasRoom()) {
            throw new IllegalStateException("Founding Tester enrollment is full");
        }
        enrolledCount = enrolledCount + 1;
    }

    public void setEnrollmentOpen(boolean enrollmentOpen) {
        this.enrollmentOpen = enrollmentOpen;
    }
}
