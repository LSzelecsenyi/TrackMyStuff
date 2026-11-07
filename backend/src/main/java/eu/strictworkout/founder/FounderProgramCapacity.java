package eu.strictworkout.founder;

import eu.strictworkout.program.ProgramLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgramLifecycle lifecycle;

    protected FounderProgramCapacity() {
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public ProgramLifecycle getLifecycle() {
        return lifecycle;
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

    public void setLifecycle(ProgramLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }
}
