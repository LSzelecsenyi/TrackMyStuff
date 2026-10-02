package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weekly_workout_goals",
    indices = [
        Index(value = ["effectiveWeekStart"], unique = true)
    ]
)
data class WeeklyWorkoutGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveWeekStart: String,
    val workoutsPerWeek: Int?,
    val graceWeek: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
