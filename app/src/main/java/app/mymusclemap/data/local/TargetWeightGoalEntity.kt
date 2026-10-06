package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "target_weight_goals")
data class TargetWeightGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetKg: Double,
    val baselineKg: Double?,
    val direction: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val retiredAt: Long?
)
