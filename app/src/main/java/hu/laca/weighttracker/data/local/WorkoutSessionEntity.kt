package hu.laca.weighttracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ScheduledWorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduledWorkoutId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["activeLock"], unique = true),
        Index(value = ["status"]),
        Index(value = ["templateId"]),
        Index(value = ["workoutDate"]),
        Index(value = ["importFingerprint"], unique = true),
        Index(value = ["scheduledWorkoutId"], unique = true)
    ]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val templateName: String,
    val status: String,
    val workoutDate: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val abandonedAt: Long?,
    val notes: String?,
    val bodyWeightKg: Double?,
    val bodyWeightSource: String,
    val bodyWeightSourceDate: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val activeLock: Int?,
    val importFingerprint: String? = null,
    val scheduledWorkoutId: Long? = null
)

@Entity(
    tableName = "workout_session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
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
        Index(value = ["sessionId", "position"], unique = true),
        Index(value = ["sessionId"]),
        Index(value = ["exerciseId"])
    ]
)
data class WorkoutSessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val name: String,
    val category: String,
    val movementPattern: String,
    val measurementType: String,
    val resistanceBasis: String,
    val weightInterpretation: String,
    val primaryMuscle: String,
    val notes: String?
)

@Entity(
    tableName = "workout_session_exercise_muscles",
    primaryKeys = ["sessionExerciseId", "muscleGroup"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionExerciseId"]),
        Index(value = ["muscleGroup"])
    ]
)
data class WorkoutSessionExerciseMuscleEntity(
    val sessionExerciseId: Long,
    val muscleGroup: String,
    val role: String
)

@Entity(
    tableName = "workout_session_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionExerciseId", "position"], unique = true),
        Index(value = ["sessionExerciseId"]),
        Index(value = ["status"])
    ]
)
data class WorkoutSessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val plannedMinReps: Int?,
    val plannedMaxReps: Int?,
    val plannedLoadKind: String,
    val plannedWeightKg: Double?,
    val plannedDurationSeconds: Int?,
    val plannedDistanceMeters: Double?,
    val actualReps: Int?,
    val actualLoadKind: String?,
    val actualWeightKg: Double?,
    val actualDurationSeconds: Int?,
    val actualDistanceMeters: Double?,
    val status: String,
    val completedAt: Long?,
    val addedDuringWorkout: Boolean
)
