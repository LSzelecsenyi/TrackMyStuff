package eu.strictworkout.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountStatusGrantRepository extends JpaRepository<AccountStatusGrant, UUID> {

    boolean existsByUser_IdAndStatus(UUID userId, AccountStatus status);

    List<AccountStatusGrant> findByUser_IdOrderByStatusAsc(UUID userId);
}
