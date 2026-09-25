package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["archived"]),
        Index(value = ["category"]),
        Index(value = ["movementPattern"])
    ]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val category: String,
    val movementPattern: String,
    val measurementType: String,
    val resistanceBasis: String,
    val weightInterpretation: String,
    val notes: String?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exerciseId", "muscleGroup"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["exerciseId"]),
        Index(value = ["muscleGroup"]),
        Index(value = ["role"])
    ]
)
data class ExerciseMuscleEntity(
    val exerciseId: Long,
    val muscleGroup: String,
    val role: String
)
