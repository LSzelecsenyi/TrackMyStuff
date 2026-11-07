package eu.strictworkout.program;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramLifecycleTest {

    @Test
    @DisplayName("Given a program is ACTIVE, when it is paused, then it becomes INACTIVE")
    void pause() {
        assertEquals(ProgramLifecycle.INACTIVE, ProgramLifecycle.ACTIVE.transitionTo(ProgramLifecycle.INACTIVE));
    }

    @Test
    @DisplayName("Given a program is INACTIVE, when it is resumed, then it becomes ACTIVE")
    void resume() {
        assertEquals(ProgramLifecycle.ACTIVE, ProgramLifecycle.INACTIVE.transitionTo(ProgramLifecycle.ACTIVE));
    }

    @Test
    @DisplayName("Given a program is ACTIVE or INACTIVE, when it is retired, then it becomes RETIRED")
    void retireFromEitherOpenState() {
        assertEquals(ProgramLifecycle.RETIRED, ProgramLifecycle.ACTIVE.transitionTo(ProgramLifecycle.RETIRED));
        assertEquals(ProgramLifecycle.RETIRED, ProgramLifecycle.INACTIVE.transitionTo(ProgramLifecycle.RETIRED));
    }

    @Test
    @DisplayName("Given a program is RETIRED, when retirement is requested again, then the state stays RETIRED")
    void retirementIsIdempotent() {
        assertEquals(ProgramLifecycle.RETIRED, ProgramLifecycle.RETIRED.transitionTo(ProgramLifecycle.RETIRED));
    }

    @Test
    @DisplayName("Given a program is RETIRED, when resume or pause is requested, then the transition is rejected")
    void retiredIsTerminal() {
        assertEquals("PROGRAM_RETIRED", assertThrows(
                ProgramLifecycleException.class,
                () -> ProgramLifecycle.RETIRED.transitionTo(ProgramLifecycle.ACTIVE)
        ).errorCode());
        assertEquals("PROGRAM_RETIRED", assertThrows(
                ProgramLifecycleException.class,
                () -> ProgramLifecycle.RETIRED.transitionTo(ProgramLifecycle.INACTIVE)
        ).errorCode());
    }

    @Test
    @DisplayName("Given a program is full, when its lifecycle is still ACTIVE, then new participants cannot join but the program is not retired")
    void capacityDoesNotRetire() {
        assertFalse(ProgramLifecycle.ACTIVE.acceptingNewParticipants(false));
        assertTrue(ProgramLifecycle.ACTIVE.acceptingNewParticipants(true));
        assertFalse(ProgramLifecycle.INACTIVE.acceptingNewParticipants(true));
        assertFalse(ProgramLifecycle.RETIRED.acceptingNewParticipants(true));
    }
}
