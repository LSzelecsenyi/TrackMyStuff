package eu.strictworkout.founder;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FounderProgramCapacityRepository extends JpaRepository<FounderProgramCapacity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select program from FounderProgramCapacity program where program.id = 1")
    FounderProgramCapacity lockSingleton();

    @Query("select program from FounderProgramCapacity program where program.id = 1")
    FounderProgramCapacity require();
}
