package eu.strictworkout.entitlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EntitlementGrantRepository extends JpaRepository<EntitlementGrant, UUID> {

    boolean existsByUser_IdAndSource(UUID userId, String source);
}
