package eu.strictworkout.founder;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FounderApplicationRepository extends JpaRepository<FounderApplication, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from FounderApplication application where application.user.id = :userId")
    Optional<FounderApplication> lockByUserId(@Param("userId") UUID userId);

    @Query("select application.id from FounderApplication application where application.user.id = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);
}
