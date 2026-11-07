package app.mymusclemap.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NativeCompletionQueryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() {
        database.close()
    }

    @Test
    fun onlyCompletedNativeWorkoutsCount() = runBlocking {
        insert(status = SessionStatus.COMPLETED, finishedAt = 1_000L, fingerprint = null)
        insert(status = SessionStatus.COMPLETED, finishedAt = 2_000L, fingerprint = "imported")
        insert(status = SessionStatus.ABANDONED, finishedAt = null, fingerprint = null)
        insert(status = SessionStatus.IN_PROGRESS, finishedAt = null, fingerprint = null)
        assertEquals(listOf(1_000L), database.workoutSessionDao().nativeCompletionEpochMillis())
    }

    @Test
    fun welcomeBackHistoryIgnoresImportedAndAbandonedSessions() = runBlocking {
        insert(
            status = SessionStatus.COMPLETED,
            finishedAt = 1_000L,
            fingerprint = null,
            clientWorkoutId = "native"
        )
        insert(
            status = SessionStatus.COMPLETED,
            finishedAt = 2_000L,
            fingerprint = "imported",
            clientWorkoutId = "imported"
        )
        insert(
            status = SessionStatus.ABANDONED,
            finishedAt = null,
            fingerprint = null,
            clientWorkoutId = "abandoned"
        )
        val rows = database.workoutSessionDao().nativeCompletionRows()
        assertEquals(listOf("native"), rows.map { it.clientWorkoutId })
        assertEquals(listOf(1_000L), rows.map { it.completedAt })
    }

    private suspend fun insert(
        status: SessionStatus,
        finishedAt: Long?,
        fingerprint: String?,
        clientWorkoutId: String = java.util.UUID.randomUUID().toString()
    ) {
        database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Session",
                status = status.name,
                workoutDate = "2026-06-09",
                startedAt = finishedAt ?: 50L,
                finishedAt = finishedAt,
                abandonedAt = if (status == SessionStatus.ABANDONED) 60L else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN.name,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L,
                activeLock = if (status == SessionStatus.IN_PROGRESS) 1 else null,
                importFingerprint = fingerprint,
                clientWorkoutId = clientWorkoutId
            )
        )
    }
}
