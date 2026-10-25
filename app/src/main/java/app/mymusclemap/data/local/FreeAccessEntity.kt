package app.mymusclemap.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "free_access_state")
data class FreeAccessStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val periodId: Long,
    val lastKnownPro: Boolean?,
    val phase: String,
    @ColumnInfo(defaultValue = "0")
    val catalogCaptured: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(
    tableName = "free_access_plans",
    primaryKeys = ["periodId", "planId"]
)
data class FreeAccessPlanEntity(
    val periodId: Long,
    val planId: Long
)

@Entity(
    tableName = "free_access_exercises",
    primaryKeys = ["periodId", "exerciseId"]
)
data class FreeAccessExerciseEntity(
    val periodId: Long,
    val exerciseId: Long
)

@Entity(
    tableName = "free_access_released_exercises",
    primaryKeys = ["periodId", "exerciseId"]
)
data class FreeAccessReleasedExerciseEntity(
    val periodId: Long,
    val exerciseId: Long
)

@Entity(
    tableName = "free_access_known_exercises",
    primaryKeys = ["periodId", "exerciseId"]
)
data class FreeAccessKnownExerciseEntity(
    val periodId: Long,
    val exerciseId: Long
)
