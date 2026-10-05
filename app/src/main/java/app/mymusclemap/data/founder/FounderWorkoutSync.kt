package app.mymusclemap.data.founder

import app.mymusclemap.domain.entitlement.FounderProgramStatus
import java.time.LocalDate
import java.util.UUID

data class NativeFounderWorkout(
    val clientWorkoutId: String,
    val completedAtEpochMilli: Long,
    val localDate: String,
    val imported: Boolean,
    val observation: FounderWorkoutObservation? = null
)

enum class FounderWorkoutFlush {
    Done,
    Retry
}

sealed interface FounderWorkoutSubmission {
    data class Accepted(val snapshot: BackendFounderSnapshot) : FounderWorkoutSubmission
    data object Unauthenticated : FounderWorkoutSubmission
    data object NoSession : FounderWorkoutSubmission
    data object Rejected : FounderWorkoutSubmission
    data object Unavailable : FounderWorkoutSubmission
}

/**
 * Queues one native Founder workout and delivers it later.
 * This type does not start Google sign-in and does not send the Room session id.
 */
class FounderWorkoutSync(
    private val outbox: FounderWorkoutOutbox,
    private val accepting: suspend () -> Boolean,
    private val lookup: suspend (String) -> NativeFounderWorkout?,
    private val submit: suspend (PendingFounderWorkout) -> FounderWorkoutSubmission,
    private val onAccepted: suspend (BackendFounderSnapshot) -> Unit,
    private val onUnauthenticated: suspend () -> Unit,
    private val schedule: () -> Unit = {}
) {
    suspend fun onNativeWorkoutCompleted(clientWorkoutId: String) {
        if (!accepting()) {
            return
        }
        val parsed = runCatching { UUID.fromString(clientWorkoutId) }.getOrNull() ?: return
        val workout = lookup(parsed.toString()) ?: return
        if (workout.imported || workout.clientWorkoutId != parsed.toString()) {
            return
        }
        if (runCatching { LocalDate.parse(workout.localDate) }.isFailure) {
            return
        }
        outbox.enqueue(
            PendingFounderWorkout(
                clientWorkoutId = workout.clientWorkoutId,
                completedAtEpochMilli = workout.completedAtEpochMilli,
                localDate = workout.localDate,
                observation = workout.observation
            )
        )
        schedule()
    }

    suspend fun flush(): FounderWorkoutFlush {
        while (true) {
            val event = outbox.pending().firstOrNull() ?: return FounderWorkoutFlush.Done
            when (val result = submit(event)) {
                is FounderWorkoutSubmission.Accepted -> {
                    onAccepted(result.snapshot)
                    outbox.remove(event.clientWorkoutId)
                }
                FounderWorkoutSubmission.Rejected -> outbox.remove(event.clientWorkoutId)
                FounderWorkoutSubmission.Unauthenticated -> {
                    onUnauthenticated()
                    return FounderWorkoutFlush.Done
                }
                FounderWorkoutSubmission.NoSession -> return FounderWorkoutFlush.Done
                FounderWorkoutSubmission.Unavailable -> return FounderWorkoutFlush.Retry
            }
        }
    }

    companion object {
        fun accepts(status: FounderProgramStatus, backendOwned: Boolean): Boolean {
            return backendOwned &&
                (status == FounderProgramStatus.ActiveFree || status == FounderProgramStatus.ActivePro)
        }
    }
}
