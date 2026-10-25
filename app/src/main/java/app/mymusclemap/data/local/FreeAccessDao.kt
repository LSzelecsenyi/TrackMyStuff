package app.mymusclemap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FreeAccessDao {
    @Query("SELECT * FROM free_access_state WHERE id = 1 LIMIT 1")
    abstract fun observeState(): Flow<FreeAccessStateEntity?>

    @Query("SELECT * FROM free_access_state WHERE id = 1 LIMIT 1")
    abstract suspend fun state(): FreeAccessStateEntity?

    @Query("SELECT planId FROM free_access_plans WHERE periodId = :periodId")
    abstract suspend fun planIds(periodId: Long): List<Long>

    @Query("SELECT exerciseId FROM free_access_exercises WHERE periodId = :periodId")
    abstract suspend fun exerciseIds(periodId: Long): List<Long>

    @Query("SELECT exerciseId FROM free_access_released_exercises WHERE periodId = :periodId")
    abstract suspend fun releasedExerciseIds(periodId: Long): List<Long>

    @Query("SELECT exerciseId FROM free_access_known_exercises WHERE periodId = :periodId")
    abstract suspend fun knownExerciseIds(periodId: Long): List<Long>

    @Query("DELETE FROM free_access_state")
    abstract suspend fun deleteState()

    @Query("DELETE FROM free_access_plans")
    abstract suspend fun deletePlans()

    @Query("DELETE FROM free_access_exercises")
    abstract suspend fun deleteExercises()

    @Query("DELETE FROM free_access_released_exercises")
    abstract suspend fun deleteReleasedExercises()

    @Query("DELETE FROM free_access_known_exercises")
    abstract suspend fun deleteKnownExercises()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertState(entity: FreeAccessStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlans(rows: List<FreeAccessPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertExercises(rows: List<FreeAccessExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReleasedExercises(rows: List<FreeAccessReleasedExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertKnownExercises(rows: List<FreeAccessKnownExerciseEntity>)

    @Transaction
    open suspend fun read(): FreeAccessState? {
        val row = state() ?: return null
        return FreeAccessState(
            periodId = row.periodId,
            lastKnownPro = row.lastKnownPro,
            phase = FreeAccessPhase.valueOf(row.phase),
            selectedPlanIds = planIds(row.periodId).toSet(),
            selectedCustomExerciseIds = exerciseIds(row.periodId).toSet(),
            releasedCustomExerciseIds = releasedExerciseIds(row.periodId).toSet(),
            knownCustomExerciseIds = knownExerciseIds(row.periodId).toSet(),
            catalogCaptured = row.catalogCaptured
        )
    }

    @Transaction
    open suspend fun replace(state: FreeAccessState) {
        deletePlans()
        deleteExercises()
        deleteReleasedExercises()
        deleteKnownExercises()
        deleteState()
        insertState(
            FreeAccessStateEntity(
                periodId = state.periodId,
                lastKnownPro = state.lastKnownPro,
                phase = state.phase.name,
                catalogCaptured = state.catalogCaptured
            )
        )
        if (state.selectedPlanIds.isNotEmpty()) {
            insertPlans(state.selectedPlanIds.map { FreeAccessPlanEntity(state.periodId, it) })
        }
        if (state.selectedCustomExerciseIds.isNotEmpty()) {
            insertExercises(
                state.selectedCustomExerciseIds.map { FreeAccessExerciseEntity(state.periodId, it) }
            )
        }
        if (state.releasedCustomExerciseIds.isNotEmpty()) {
            insertReleasedExercises(
                state.releasedCustomExerciseIds.map {
                    FreeAccessReleasedExerciseEntity(state.periodId, it)
                }
            )
        }
        if (state.knownCustomExerciseIds.isNotEmpty()) {
            insertKnownExercises(
                state.knownCustomExerciseIds.map {
                    FreeAccessKnownExerciseEntity(state.periodId, it)
                }
            )
        }
    }
}
