package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_workouts",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scheduledDate"]),
        Index(value = ["templateId"]),
        Index(value = ["cancelledAt"])
    ]
)
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledDate: String,
    val originalScheduledDate: String,
    val templateId: Long?,
    val templateName: String,
    val createdAt: Long,
    val cancelledAt: Long? = null
)
