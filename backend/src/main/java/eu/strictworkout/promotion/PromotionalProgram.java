package eu.strictworkout.promotion;

import eu.strictworkout.program.ProgramLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotional_program")
public class PromotionalProgram {

    public static final String PRO_DISCOVERY = "PRO_DISCOVERY";
    public static final String WELCOME_BACK = "WELCOME_BACK";

    @Id
    @Column(length = 32)
    private String program;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProgramLifecycle lifecycle;

    protected PromotionalProgram() {
    }

    public String getProgram() {
        return program;
    }

    public ProgramLifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(ProgramLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public boolean acceptingNewGrants() {
        return lifecycle == ProgramLifecycle.ACTIVE;
    }
}
