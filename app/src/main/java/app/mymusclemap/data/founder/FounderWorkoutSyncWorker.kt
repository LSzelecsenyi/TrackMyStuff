package app.mymusclemap.data.founder

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import app.mymusclemap.WeightTrackerApplication
import java.util.concurrent.TimeUnit

/**
 * Retries the Founder workout outbox after process death.
 * The queue itself is the DataStore outbox. This worker only drains it.
 */
class FounderWorkoutSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? WeightTrackerApplication ?: return Result.failure()
        return when (app.container.flushFounderWorkoutOutbox()) {
            FounderWorkoutFlush.Retry -> Result.retry()
            FounderWorkoutFlush.Done -> Result.success()
        }
    }
}

object FounderWorkoutSyncScheduler {
    const val WORK_NAME = "founder-workout-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<FounderWorkoutSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
