package eu.strictworkout.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminSessionRepository extends JpaRepository<AdminSession, UUID> {

    Optional<AdminSession> findByTokenHash(byte[] tokenHash);
}
