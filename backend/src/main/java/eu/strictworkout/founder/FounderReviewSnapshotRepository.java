package eu.strictworkout.founder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FounderReviewSnapshotRepository extends JpaRepository<FounderReviewSnapshot, UUID> {

    Optional<FounderReviewSnapshot> findByApplicationId(UUID applicationId);
}
