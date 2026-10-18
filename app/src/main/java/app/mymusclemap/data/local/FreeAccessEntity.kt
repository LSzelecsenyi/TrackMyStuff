package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "free_access_state")
data class FreeAccessStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val periodId: Long,
    val lastKnownPro: Boolean?,
    val phase: String
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
