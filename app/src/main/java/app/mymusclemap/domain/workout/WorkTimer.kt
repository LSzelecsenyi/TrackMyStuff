package app.mymusclemap.domain.workout

fun WorkoutSession.toWorkSnapshot(): WorkTimerSnapshot {
    return WorkTimerSnapshot(
        phase = WorkPhase.fromStored(workPhase),
        setId = workSetId,
        targetMillis = workTargetMillis,
        elapsedActiveMillis = workElapsedActiveMillis,
        anchorElapsedRealtimeMillis = workAnchorElapsedRealtimeMillis,
        deadlineElapsedRealtimeMillis = workDeadlineElapsedRealtimeMillis,
        deadlineWallClockMillis = workDeadlineWallClockMillis,
        generation = workGeneration
    )
}

/**
 * Countdown for one timed working set.
 *
 * Remaining time is a deadline minus a monotonic clock. Paused time is not
 * counted: only a running interval adds to [WorkTimerSnapshot.elapsedActiveMillis].
 *
 * Recorded duration is whole seconds, truncated toward zero, then coerced into
 * `0..targetSeconds`. Expiry records the full target. A negative or
 * longer-than-target duration is not stored.
 */
enum class WorkPhase {
    RUNNING,
    PAUSED,
    AWAITING;

    companion object {
        fun fromStored(raw: String?): WorkPhase? = entries.firstOrNull { it.name == raw }
    }
}

data class WorkTimerSnapshot(
    val phase: WorkPhase?,
    val setId: Long?,
    val targetMillis: Long?,
    val elapsedActiveMillis: Long?,
    val anchorElapsedRealtimeMillis: Long?,
    val deadlineElapsedRealtimeMillis: Long?,
    val deadlineWallClockMillis: Long?,
    val generation: Long
) {
    val active: Boolean get() = phase != null
}

object WorkDuration {
    const val MAX_SECONDS = 30 * 60
    val PRESETS_SECONDS = listOf(15, 20, 30, 45, 60, 90, 120)
}

object WorkTimerLogic {
    fun started(
        setId: Long,
        targetMillis: Long,
        clock: RestClockSample,
        generation: Long
    ): WorkTimerSnapshot {
        val bounded = targetMillis.coerceAtLeast(1_000L)
        return WorkTimerSnapshot(
            phase = WorkPhase.RUNNING,
            setId = setId,
            targetMillis = bounded,
            elapsedActiveMillis = 0L,
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            deadlineElapsedRealtimeMillis = clock.elapsedRealtimeMillis + bounded,
            deadlineWallClockMillis = clock.wallClockMillis + bounded,
            generation = generation
        )
    }

    fun pause(snapshot: WorkTimerSnapshot, clock: RestClockSample): WorkTimerSnapshot? {
        if (snapshot.phase != WorkPhase.RUNNING) return null
        val elapsed = elapsedActive(snapshot, clock) ?: return null
        return snapshot.copy(
            phase = WorkPhase.PAUSED,
            elapsedActiveMillis = elapsed,
            anchorElapsedRealtimeMillis = null,
            deadlineElapsedRealtimeMillis = null,
            deadlineWallClockMillis = null
        )
    }

    fun resume(snapshot: WorkTimerSnapshot, clock: RestClockSample): WorkTimerSnapshot? {
        if (snapshot.phase != WorkPhase.PAUSED) return null
        val target = snapshot.targetMillis ?: return null
        val done = snapshot.elapsedActiveMillis ?: 0L
        val left = (target - done).coerceAtLeast(0L)
        if (left == 0L) return expire(snapshot)
        return snapshot.copy(
            phase = WorkPhase.RUNNING,
            anchorElapsedRealtimeMillis = clock.elapsedRealtimeMillis,
            deadlineElapsedRealtimeMillis = clock.elapsedRealtimeMillis + left,
            deadlineWallClockMillis = clock.wallClockMillis + left
        )
    }

    fun finishEarly(snapshot: WorkTimerSnapshot, clock: RestClockSample): WorkTimerSnapshot? {
        if (snapshot.phase != WorkPhase.RUNNING && snapshot.phase != WorkPhase.PAUSED) return null
        val elapsed = elapsedActive(snapshot, clock) ?: return null
        return snapshot.copy(
            phase = WorkPhase.AWAITING,
            elapsedActiveMillis = elapsed,
            anchorElapsedRealtimeMillis = null,
            deadlineElapsedRealtimeMillis = null,
            deadlineWallClockMillis = null
        )
    }

    fun expire(snapshot: WorkTimerSnapshot): WorkTimerSnapshot? {
        if (snapshot.phase != WorkPhase.RUNNING) return null
        return snapshot.copy(
            phase = WorkPhase.AWAITING,
            elapsedActiveMillis = snapshot.targetMillis,
            anchorElapsedRealtimeMillis = null,
            deadlineElapsedRealtimeMillis = null,
            deadlineWallClockMillis = null
        )
    }

    fun reanchorAfterReboot(snapshot: WorkTimerSnapshot, clock: RestClockSample): WorkTimerSnapshot? {
        if (snapshot.phase != WorkPhase.RUNNING) return null
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

    fun remaining(snapshot: WorkTimerSnapshot, clock: RestClockSample): RestRemaining {
        if (snapshot.phase == WorkPhase.PAUSED) {
            val target = snapshot.targetMillis ?: return RestRemaining.Inactive
            val done = (snapshot.elapsedActiveMillis ?: 0L).coerceIn(0L, target)
            return RestRemaining.Active(target - done, target)
        }
        if (snapshot.phase != WorkPhase.RUNNING) return RestRemaining.Inactive
        val anchor = snapshot.anchorElapsedRealtimeMillis
        val elapsedDeadline = snapshot.deadlineElapsedRealtimeMillis
        val wallDeadline = snapshot.deadlineWallClockMillis
        val left = if (anchor != null && elapsedDeadline != null && clock.elapsedRealtimeMillis >= anchor) {
            elapsedDeadline - clock.elapsedRealtimeMillis
        } else if (wallDeadline != null) {
            wallDeadline - clock.wallClockMillis
        } else {
            return RestRemaining.Inactive
        }
        if (left <= 0L) return RestRemaining.Expired
        val target = snapshot.targetMillis ?: left
        val segment = if (anchor != null && elapsedDeadline != null && elapsedDeadline >= anchor) {
            elapsedDeadline - anchor
        } else {
            target
        }
        return RestRemaining.Active(RestTimerLogic.displayedRemainingMillis(left, segment, target), target)
    }

    fun actualSeconds(snapshot: WorkTimerSnapshot, clock: RestClockSample): Int {
        val target = ((snapshot.targetMillis ?: 0L) / 1000L).toInt().coerceAtLeast(0)
        if (snapshot.phase == WorkPhase.AWAITING && snapshot.elapsedActiveMillis == snapshot.targetMillis) {
            return target
        }
        val elapsed = elapsedActive(snapshot, clock) ?: 0L
        return (elapsed / 1000L).toInt().coerceIn(0, target)
    }

    fun elapsedActive(snapshot: WorkTimerSnapshot, clock: RestClockSample): Long? {
        val base = snapshot.elapsedActiveMillis ?: 0L
        val target = snapshot.targetMillis ?: return null
        if (snapshot.phase != WorkPhase.RUNNING) {
            return base.coerceIn(0L, target)
        }
        val anchor = snapshot.anchorElapsedRealtimeMillis
        val ran = if (anchor != null && clock.elapsedRealtimeMillis >= anchor) {
            clock.elapsedRealtimeMillis - anchor
        } else if (snapshot.deadlineWallClockMillis != null && anchor != null && clock.elapsedRealtimeMillis < anchor) {
            val left = snapshot.deadlineWallClockMillis - clock.wallClockMillis
            val runningFor = (target - base) - left
            runningFor.coerceAtLeast(0L)
        } else {
            0L
        }
        return (base + ran).coerceIn(0L, target)
    }
}

object WorkoutTimerAccess {
    fun allowed(entitlements: app.mymusclemap.domain.entitlement.FeatureEntitlements): Boolean {
        return entitlements.hasAccess(app.mymusclemap.domain.entitlement.AppFeature.WorkoutTimers)
    }

    /** A positive rest duration is a Pro change. Turning rest off stays available. */
    fun allowsRestChange(requestedSeconds: Int?, pro: Boolean): Boolean {
        if (pro) return true
        return (requestedSeconds ?: 0) <= 0
    }
}
