package eu.strictworkout.founder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FounderWorkoutEventRepository extends JpaRepository<FounderWorkoutEvent, UUID> {

    List<FounderWorkoutEvent> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    @Query("""
            select event from FounderWorkoutEvent event
            where event.application.id = :applicationId
            order by event.workoutLocalDate asc, event.completedAt asc, event.clientWorkoutId asc
            """)
    List<FounderWorkoutEvent> findForReview(@Param("applicationId") UUID applicationId);

    Optional<FounderWorkoutEvent> findByApplicationIdAndClientWorkoutId(UUID applicationId, UUID clientWorkoutId);
}
