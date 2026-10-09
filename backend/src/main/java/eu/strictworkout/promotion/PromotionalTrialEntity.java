package eu.strictworkout.promotion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotional_trial")
@IdClass(PromotionalTrialEntity.Key.class)
class PromotionalTrialEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "promotion_type")
    private String promotionType;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PromotionalTrialEntity() {
    }

    PromotionalTrialEntity(UUID userId, String promotionType, Instant activatedAt, Instant expiresAt) {
        this.userId = userId;
        this.promotionType = promotionType;
        this.activatedAt = activatedAt;
        this.expiresAt = expiresAt;
    }

    Instant activatedAt() {
        return activatedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private String promotionType;

        public Key() {
        }

        public Key(UUID userId, String promotionType) {
            this.userId = userId;
            this.promotionType = promotionType;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && userId.equals(key.userId) && promotionType.equals(key.promotionType);
        }

        @Override
        public int hashCode() {
            return userId.hashCode() * 31 + promotionType.hashCode();
        }
    }
}

interface PromotionalTrialJpaRepository extends JpaRepository<PromotionalTrialEntity, PromotionalTrialEntity.Key> {
}

@Repository
class JpaPromotionalTrialStore implements PromotionalTrialService.Store {

    private final PromotionalTrialJpaRepository trials;

    JpaPromotionalTrialStore(PromotionalTrialJpaRepository trials) {
        this.trials = trials;
    }

    @Override
    public PromotionalTrialService.Trial find(UUID userId) {
        return trials.findById(new PromotionalTrialEntity.Key(userId, PromotionalTrialService.PRO_DISCOVERY))
                .map(entity -> new PromotionalTrialService.Trial(userId, entity.activatedAt(), entity.expiresAt()))
                .orElse(null);
    }

    @Override
    public boolean insert(PromotionalTrialService.Trial trial) {
        try {
            trials.saveAndFlush(new PromotionalTrialEntity(
                    trial.userId(),
                    PromotionalTrialService.PRO_DISCOVERY,
                    trial.activatedAt(),
                    trial.expiresAt()
            ));
            return true;
        } catch (DataIntegrityViolationException error) {
            return false;
        }
    }
}
