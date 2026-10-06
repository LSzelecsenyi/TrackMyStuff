package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "unlocked_achievements")
data class UnlockedAchievementEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

@Entity(
    tableName = "progress_events",
    indices = [Index(value = ["dedupeKey"], unique = true)]
)
data class ProgressEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val kind: String,
    val payload: String,
    val occurredAt: Long,
    val celebratedAt: Long?,
    val triggerClientWorkoutId: String?
)

@Entity(tableName = "achievement_state")
data class AchievementStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val initialized: Boolean,
    val updatedAt: Long
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
