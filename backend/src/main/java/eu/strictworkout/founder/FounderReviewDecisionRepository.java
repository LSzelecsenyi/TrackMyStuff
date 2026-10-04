package eu.strictworkout.founder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FounderReviewDecisionRepository extends JpaRepository<FounderReviewDecision, UUID> {

    Optional<FounderReviewDecision> findByApplication_Id(UUID applicationId);
}
