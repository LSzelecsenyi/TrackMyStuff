package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_templates",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["archived"])
    ]
)
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val notes: String?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "workout_template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["templateId", "position"], unique = true),
        Index(value = ["templateId"]),
        Index(value = ["exerciseId"])
    ]
)
data class WorkoutTemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val notes: String?,
    val createdAt: Long
)

@Entity(
    tableName = "workout_template_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["templateExerciseId", "position"], unique = true),
        Index(value = ["templateExerciseId"])
    ]
)
data class WorkoutTemplateSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateExerciseId: Long,
    val position: Int,
    val minReps: Int?,
    val maxReps: Int?,
    val loadKind: String,
    val weightKg: Double?,
    val durationSeconds: Int?,
    val distanceMeters: Double?
)
