package app.mymusclemap.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

data class NativeCompletedWorkoutRow(
    val id: Long,
    val workoutDate: String,
    val templateName: String
)

/** Earliest completed native Strict session, used as a stable Journey timestamp. */
data class NativeCompletedStamp(
    val finishedAt: Long?,
    val startedAt: Long
)

/** Native completion used for Welcome Back. finishedAt is already coalesced with startedAt. */
data class NativeCompletionRow(
    val clientWorkoutId: String,
    val completedAt: Long
)

data class PreviousExerciseOccurrenceRow(
    val sessionId: Long,
    val templateId: Long?,
    val templateName: String,
    val workoutDate: String,
    val startedAt: Long,
    val status: String,
    val sessionExerciseId: Long,
    val exerciseId: Long,
    val exercisePosition: Int,
    val measurementType: String,
    val resistanceBasis: String,
    val weightInterpretation: String
)

data class PreviousExerciseSetRow(
    val sessionExerciseId: Long,
    val position: Int,
    val status: String,
    val actualReps: Int?,
    val actualLoadKind: String?,
    val actualWeightKg: Double?,
    val actualDurationSeconds: Int?,
    val actualDistanceMeters: Double?
)

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
        Index(value = ["scheduledWorkoutId"], unique = true),
        Index(value = ["clientWorkoutId"], unique = true)
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
    val scheduledWorkoutId: Long? = null,
    val clientWorkoutId: String = java.util.UUID.randomUUID().toString(),
    /** Set in the same transaction as an approved plan update from this workout. */
    @ColumnInfo(defaultValue = "0")
    val planProgressionApplied: Boolean = false
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
    val notes: String?,
    /**
     * True only for an occurrence added to an in-progress session.
     * Exercises copied from a plan at start stay false, as do imported workouts and
     * rows created before this column existed. Those historical rows are not extras:
     * the product had no way to add an exercise during a workout, so the default is
     * not a guess about origin.
     * [sourceTemplateExerciseId] is the plan-exercise row copied when the session
     * started. It is null for extras, imports, and sessions started before the
     * column existed. Those nulls are not backfilled.
     */
    @ColumnInfo(defaultValue = "0")
    val addedDuringWorkout: Boolean = false,
    val sourceTemplateExerciseId: Long? = null
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
    val addedDuringWorkout: Boolean,
    val draftPayload: String? = null,
    /** Plan-set row copied at start. Null when this set has no reliable plan source. */
    val sourceTemplateSetId: Long? = null
)
