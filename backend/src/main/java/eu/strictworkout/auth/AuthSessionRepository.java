package eu.strictworkout.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Query("select session from AuthSession session join fetch session.user where session.tokenHash = :tokenHash")
    Optional<AuthSession> findByTokenHash(@Param("tokenHash") byte[] tokenHash);
}
