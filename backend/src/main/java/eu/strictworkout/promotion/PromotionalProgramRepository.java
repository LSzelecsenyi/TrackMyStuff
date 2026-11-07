package eu.strictworkout.promotion;

import eu.strictworkout.program.ProgramLifecycle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionalProgramRepository extends JpaRepository<PromotionalProgram, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select program from PromotionalProgram program where program.program = :program")
    PromotionalProgram lock(@Param("program") String program);

    @Query("select program from PromotionalProgram program where program.program = :program")
    PromotionalProgram require(@Param("program") String program);
}
