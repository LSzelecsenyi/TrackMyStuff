package eu.strictworkout.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionMarkerRepository extends JpaRepository<AccountDeletionMarker, String> {
}
