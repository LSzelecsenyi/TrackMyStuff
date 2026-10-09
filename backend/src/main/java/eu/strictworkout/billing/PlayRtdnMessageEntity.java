package eu.strictworkout.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "play_rtdn_message")
class PlayRtdnMessageEntity {

    @Id
    @Column(name = "message_id")
    private String messageId;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "event_time")
    private Instant eventTime;

    @Column(name = "notification_type")
    private Integer notificationType;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PlayRtdnMessageEntity() {
    }

    PlayRtdnMessageEntity(String messageId, Instant eventTime, int notificationType, Instant receivedAt) {
        this.messageId = messageId;
        this.eventTime = eventTime;
        this.notificationType = notificationType;
        this.receivedAt = receivedAt;
    }
}
