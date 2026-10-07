package eu.strictworkout.identity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @Query("""
            select user from AppUser user
            where not exists (
                select grant from eu.strictworkout.account.AccountStatusGrant grant
                where grant.user = user
                  and grant.status = eu.strictworkout.account.AccountStatus.EARLY_ADOPTER
            )
            order by user.createdAt asc, user.id asc
            """)
    List<AppUser> findWithoutEarlyAdopter(Pageable pageable);
}
