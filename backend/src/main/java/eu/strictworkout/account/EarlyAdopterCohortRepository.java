package eu.strictworkout.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EarlyAdopterCohortRepository extends JpaRepository<EarlyAdopterCohort, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cohort from EarlyAdopterCohort cohort where cohort.id = 1")
    EarlyAdopterCohort lockSingleton();
}
