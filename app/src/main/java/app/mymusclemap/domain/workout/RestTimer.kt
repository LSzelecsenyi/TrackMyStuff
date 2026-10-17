package app.mymusclemap.domain.workout

enum class RestProgressionMode {
    STRICT,
    FLEXIBLE;

    companion object {
        fun fromStored(raw: String?): RestProgressionMode {
            return entries.firstOrNull { it.name == raw } ?: STRICT
        }
    }
}

enum class RestPhase {
    COUNTING,
    AWAITING_NEXT;

    companion object {
        fun fromStored(raw: String?): RestPhase? {
            return entries.firstOrNull { it.name == raw }
        }
    }
}

/**
 * Rest configured on a plan.
 *
 * A set value of null inherits the exercise default. Zero disables rest for that
 * set. A positive value is the rest duration in seconds. An exercise value of
 * null means that exercise does not add rest unless a set overrides it.
 * Existing plans leave both null, so completing a set behaves as it did before.
 */
object RestDuration {
    const val MAX_SECONDS = 30 * 60
    val PRESETS_SECONDS = listOf(30, 60, 90, 120, 180)

    fun effectiveSeconds(exerciseRestSeconds: Int?, setRestSeconds: Int?): Int {
        val chosen = setRestSeconds ?: exerciseRestSeconds
        return chosen?.coerceIn(0, MAX_SECONDS) ?: 0
    }
}

data class RestClockSample(
    val elapsedRealtimeMillis: Long,
    val wallClockMillis: Long
)

data class RestTimerSnapshot(
    val phase: RestPhase?,
    val originSetId: Long?,
    val nextSetId: Long?,
    val anchorElapsedRealtimeMillis: Long?,
    val deadlineElapsedRealtimeMillis: Long?,
    val deadlineWallClockMillis: Long?,
    val durationMillis: Long?,
    val generation: Long,
    val mode: RestProgressionMode
) {
    val active: Boolean get() = phase != null
}

sealed interface RestExpiryResult {
    data class Advanced(val originSetId: Long) : RestExpiryResult
    data object Waiting : RestExpiryResult
    data object Ignored : RestExpiryResult
}

sealed interface RestRemaining {
    data class Active(val remainingMillis: Long, val durationMillis: Long) : RestRemaining
    data object Expired : RestRemaining
    data object Inactive : RestRemaining
}

data class RestStartPlan(
    val originSetId: Long,
    val nextSetId: Long,
    val durationMillis: Long,
    val mode: RestProgressionMode
)

object RestTimerLogic {
    const val EXTENSION_MILLIS = 10_000L

    fun planAfterCompletion(
        aggregate: WorkoutSessionAggregate,
        completedSetId: Long
    ): RestStartPlan? {
        if (aggregate.session.status != SessionStatus.IN_PROGRESS) return null
        val target = SessionFocusLogic.focusAfterResolving(aggregate.exercises, completedSetId)
        val next = target as? WorkoutFocusTarget.Set ?: return null
        val completed = aggregate.exercises
            .flatMap { it.sets }
            .firstOrNull { it.id == completedSetId }
            ?: return null
        val exercise = aggregate.exercises.firstOrNull { item ->
            item.sets.any { it.id == completedSetId }
        } ?: return null
        val seconds = RestDuration.effectiveSeconds(
            exercise.exercise.restSeconds,
            completed.restSeconds
        )
        if (seconds <= 0) return null
        return RestStartPlan(
            originSetId = completedSetId,
            nextSetId = next.setId,
            durationMillis = seconds * 1_000L,
            mode = aggregate.session.restProgressionMode
        )
    }

    fun started(
        plan: RestStartPlan,
        clock: RestClockSample,
        generation: Long
    ): RestTimerSnapshot {
        return RestTimerSnapshot(
            phase = RestPhase.COUNTING,
            originSetId = plan.originSetId,
            nextSetId = plan.nextSetId,
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            deadlineElapsedRealtimeMillis = clock.elapsedRealtimeMillis + plan.durationMillis,
            deadlineWallClockMillis = clock.wallClockMillis + plan.durationMillis,
            durationMillis = plan.durationMillis,
            generation = generation,
            mode = plan.mode
        )
    }

    fun extend(snapshot: RestTimerSnapshot, clock: RestClockSample): RestTimerSnapshot? {
        if (snapshot.phase != RestPhase.COUNTING) return null
        val remaining = remaining(snapshot, clock)
        val base = when (remaining) {
            is RestRemaining.Active -> remaining.remainingMillis
            RestRemaining.Expired, RestRemaining.Inactive -> return null
        }
        val duration = (snapshot.durationMillis ?: 0L) + EXTENSION_MILLIS
        val left = base + EXTENSION_MILLIS
        return snapshot.copy(
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            deadlineElapsedRealtimeMillis = clock.elapsedRealtimeMillis + left,
            deadlineWallClockMillis = clock.wallClockMillis + left,
            durationMillis = duration
        )
    }

    /**
     * A monotonic deadline from a previous boot is not a deadline on this boot.
     * When [elapsedRealtimeMillis] moves backwards, remaining time is recovered
     * from the wall-clock deadline and anchored again.
     */
    fun reanchorAfterReboot(snapshot: RestTimerSnapshot, clock: RestClockSample): RestTimerSnapshot? {
        if (snapshot.phase != RestPhase.COUNTING) return null
        val anchor = snapshot.anchorElapsedRealtimeMillis ?: return null
        if (clock.elapsedRealtimeMillis >= anchor) return null
        val wallDeadline = snapshot.deadlineWallClockMillis ?: return null
        val left = wallDeadline - clock.wallClockMillis
        if (left <= 0L) return null
        return snapshot.copy(
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            deadlineElapsedRealtimeMillis = clock.elapsedRealtimeMillis + left
        )
    }

    fun remaining(snapshot: RestTimerSnapshot, clock: RestClockSample): RestRemaining {
        if (snapshot.phase != RestPhase.COUNTING) return RestRemaining.Inactive
        val anchor = snapshot.anchorElapsedRealtimeMillis
        val elapsedDeadline = snapshot.deadlineElapsedRealtimeMillis
        val wallDeadline = snapshot.deadlineWallClockMillis
        val duration = snapshot.durationMillis ?: return RestRemaining.Inactive
        val left = if (anchor != null && elapsedDeadline != null && clock.elapsedRealtimeMillis >= anchor) {
            elapsedDeadline - clock.elapsedRealtimeMillis
        } else if (wallDeadline != null) {
            wallDeadline - clock.wallClockMillis
        } else {
            return RestRemaining.Inactive
        }
        if (left <= 0L) return RestRemaining.Expired
        val segment = if (anchor != null && elapsedDeadline != null && elapsedDeadline >= anchor) {
            elapsedDeadline - anchor
        } else {
            duration
        }
        return RestRemaining.Active(displayedRemainingMillis(left, segment, duration), duration)
    }

    /**
     * Ceiling keeps the last partial second visible, but a sample taken before
     * the interval anchor must not display one second more than that interval.
     * [segmentMillis] is the current running piece, including a +10 extension.
     */
    fun displayedRemainingMillis(rawLeft: Long, segmentMillis: Long, durationMillis: Long): Long {
        var left = rawLeft.coerceAtLeast(0L)
        if (segmentMillis > 0L) left = left.coerceAtMost(segmentMillis)
        if (durationMillis > 0L) left = left.coerceAtMost(durationMillis)
        return left
    }

    fun formatCountdown(remainingMillis: Long): String {
        val totalSeconds = if (remainingMillis <= 0L) {
            0L
        } else {
            (remainingMillis + 999L) / 1000L
        }
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
    }

    fun progress(remaining: RestRemaining): Float {
        val active = remaining as? RestRemaining.Active ?: return 0f
        if (active.durationMillis <= 0L) return 0f
        return (active.remainingMillis.toFloat() / active.durationMillis.toFloat()).coerceIn(0f, 1f)
    }
}

fun WorkoutSession.toRestSnapshot(): RestTimerSnapshot {
    return RestTimerSnapshot(
        phase = restPhase,
        originSetId = restOriginSetId,
        nextSetId = restNextSetId,
        anchorElapsedRealtimeMillis = restAnchorElapsedRealtimeMillis,
        deadlineElapsedRealtimeMillis = restDeadlineElapsedRealtimeMillis,
        deadlineWallClockMillis = restDeadlineWallClockMillis,
        durationMillis = restDurationMillis,
        generation = restGeneration,
        mode = restProgressionMode
    )
}

data class RestNextSet(
    val exerciseName: String,
    val setNumber: Int,
    val setCount: Int
)

fun WorkoutSessionAggregate.restNextSet(): RestNextSet? {
    val nextId = session.restNextSetId ?: return null
    val item = exercises.firstOrNull { entry -> entry.sets.any { it.id == nextId } } ?: return null
    val set = item.sets.firstOrNull { it.id == nextId } ?: return null
    return RestNextSet(
        exerciseName = item.exercise.name,
        setNumber = set.position + 1,
        setCount = item.sets.size
    )
}
