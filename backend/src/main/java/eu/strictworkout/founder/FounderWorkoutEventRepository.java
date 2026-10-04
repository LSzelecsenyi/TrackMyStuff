package eu.strictworkout.founder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FounderWorkoutEventRepository extends JpaRepository<FounderWorkoutEvent, UUID> {

    List<FounderWorkoutEvent> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    Optional<FounderWorkoutEvent> findByApplicationIdAndClientWorkoutId(UUID applicationId, UUID clientWorkoutId);
}
