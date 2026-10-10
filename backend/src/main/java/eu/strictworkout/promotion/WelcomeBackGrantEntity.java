package eu.strictworkout.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "welcome_back_grant")
class WelcomeBackGrantEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "qualifying_workout_id", nullable = false, length = 80)
    private String qualifyingWorkoutId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "cooldown_until", nullable = false)
    private Instant cooldownUntil;

    protected WelcomeBackGrantEntity() {
    }

    WelcomeBackGrantEntity(
            UUID userId,
            String qualifyingWorkoutId,
            Instant activatedAt,
            Instant expiresAt,
            Instant cooldownUntil
    ) {
        this.userId = userId;
        this.qualifyingWorkoutId = qualifyingWorkoutId;
        this.activatedAt = activatedAt;
        this.expiresAt = expiresAt;
        this.cooldownUntil = cooldownUntil;
    }

    String qualifyingWorkoutId() {
        return qualifyingWorkoutId;
    }

    Instant activatedAt() {
        return activatedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Instant cooldownUntil() {
        return cooldownUntil;
    }
}

interface WelcomeBackGrantJpaRepository extends JpaRepository<WelcomeBackGrantEntity, UUID> {
}

@Repository
class JpaWelcomeBackStore implements WelcomeBackService.Store {

    private final WelcomeBackGrantJpaRepository grants;

    JpaWelcomeBackStore(WelcomeBackGrantJpaRepository grants) {
        this.grants = grants;
    }

    @Override
    public WelcomeBackService.Grant find(UUID userId) {
        return grants.findById(userId)
                .map(entity -> new WelcomeBackService.Grant(
                        userId,
                        entity.qualifyingWorkoutId(),
                        entity.activatedAt(),
                        entity.expiresAt(),
                        entity.cooldownUntil()
                ))
                .orElse(null);
    }

    @Override
    public boolean insert(WelcomeBackService.Grant grant) {
        try {
            grants.saveAndFlush(entity(grant));
            return true;
        } catch (DataIntegrityViolationException error) {
            return false;
        }
    }

    @Override
    @Transactional
    public boolean replace(WelcomeBackService.Grant grant) {
        try {
            grants.deleteById(grant.userId());
            grants.saveAndFlush(entity(grant));
            return true;
        } catch (DataIntegrityViolationException error) {
            return false;
        }
    }

    private static WelcomeBackGrantEntity entity(WelcomeBackService.Grant grant) {
        return new WelcomeBackGrantEntity(
                grant.userId(),
                grant.workoutId(),
                grant.activatedAt(),
                grant.expiresAt(),
                grant.cooldownUntil()
        );
    }
}
