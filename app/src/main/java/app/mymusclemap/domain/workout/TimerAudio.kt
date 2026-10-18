package app.mymusclemap.domain.workout

/**
 * Timer audio is derived from an existing monotonic deadline.
 * It does not start, pause, extend, or complete a set.
 */
enum class TimerAudioMode {
    VOICE,
    BEEPS,
    SILENT;

    companion object {
        fun fromStored(raw: String?): TimerAudioMode? = entries.firstOrNull { it.name == raw }
    }
}

enum class TimerCueKind {
    PREPARATION,
    WORK,
    REST
}

enum class TimerCueEvent {
    COUNTDOWN_3,
    COUNTDOWN_2,
    COUNTDOWN_1,
    INTERVAL_COMPLETE
}

enum class TimerSpokenWord {
    THREE,
    TWO,
    ONE,
    GO,
    REST,
    DONE,
    NONE
}

enum class TimerSpeechLanguage {
    EN,
    HU;

    companion object {
        fun resolve(language: String?): TimerSpeechLanguage {
            return if (language.equals("hu", ignoreCase = true)) HU else EN
        }
    }
}

enum class TimerAudioPhase {
    RUNNING,
    PAUSED,
    FINISHED_NATURALLY,
    CANCELLED
}

data class TimerIntervalId(
    val kind: TimerCueKind,
    val generation: Long
)

data class TimerIntervalSlot(
    val id: TimerIntervalId,
    val deadlineElapsed: Long?,
    val phase: TimerAudioPhase,
    val remainingMillis: Long?,
    val durationMillis: Long?,
    val completion: TimerSpokenWord,
    val sessionId: Long,
    val ledgerGeneration: Long,
    val setId: Long? = null
)

data class TimerAudioState(
    val consumed: Map<TimerIntervalId, Set<TimerCueEvent>> = emptyMap(),
    val durationByInterval: Map<TimerIntervalId, Long> = emptyMap(),
    val slot: TimerIntervalSlot? = null
)

data class PlayedCue(
    val id: TimerIntervalId,
    val event: TimerCueEvent,
    val word: TimerSpokenWord,
    val sessionId: Long,
    val ledgerGeneration: Long,
    val setId: Long?
)

data class TimerAudioStep(
    val state: TimerAudioState,
    val play: PlayedCue? = null,
    val stop: Boolean = false,
    val waitMillis: Long? = null
)

object TimerSpeech {
    fun phrase(language: TimerSpeechLanguage, word: TimerSpokenWord): String? {
        if (word == TimerSpokenWord.NONE) return null
        return when (language) {
            TimerSpeechLanguage.EN -> when (word) {
                TimerSpokenWord.THREE -> "Three"
                TimerSpokenWord.TWO -> "Two"
                TimerSpokenWord.ONE -> "One"
                TimerSpokenWord.GO -> "Go"
                TimerSpokenWord.REST -> "Rest"
                TimerSpokenWord.DONE -> "Done"
                TimerSpokenWord.NONE -> null
            }
            TimerSpeechLanguage.HU -> when (word) {
                TimerSpokenWord.THREE -> "Három"
                TimerSpokenWord.TWO -> "Kettő"
                TimerSpokenWord.ONE -> "Egy"
                TimerSpokenWord.GO -> "Rajt"
                TimerSpokenWord.REST -> "Pihenő"
                TimerSpokenWord.DONE -> "Kész"
                TimerSpokenWord.NONE -> null
            }
        }
    }
}

object TimerAudioPreference {
    data class Migration(
        val writeMode: String?,
        val removeLegacy: Boolean
    )

    fun resolve(storedMode: String?, legacyEnabled: Boolean?): TimerAudioMode {
        TimerAudioMode.fromStored(storedMode)?.let { return it }
        return when (legacyEnabled) {
            false -> TimerAudioMode.SILENT
            true -> TimerAudioMode.BEEPS
            null -> TimerAudioMode.BEEPS
        }
    }

    /**
     * A stored mode wins. A legacy boolean is written once as BEEPS or SILENT
     * and then removed so it cannot override a later selection.
     */
    fun migration(storedMode: String?, legacyPresent: Boolean, legacyEnabled: Boolean): Migration {
        if (TimerAudioMode.fromStored(storedMode) != null) {
            return Migration(writeMode = null, removeLegacy = legacyPresent)
        }
        if (!legacyPresent) {
            return Migration(writeMode = null, removeLegacy = false)
        }
        val mode = resolve(storedMode = null, legacyEnabled = legacyEnabled)
        return Migration(writeMode = mode.name, removeLegacy = true)
    }
}

object TimerCompletionKeys {
    fun key(kind: TimerCueKind, sessionId: Long, generation: Long): String {
        return "${kind.name}:$sessionId:$generation"
    }
}

object TimerNotificationAudio {
    fun shouldPlay(
        mode: TimerAudioMode,
        appInForeground: Boolean,
        completionAlreadyClaimed: Boolean
    ): Boolean {
        if (mode != TimerAudioMode.BEEPS) return false
        if (appInForeground || completionAlreadyClaimed) return false
        return true
    }
}

enum class TimerCueOutput {
    NONE,
    SPEECH,
    COUNTDOWN_TONE,
    COMPLETION_TONE
}

object TimerCueOutputSelector {
    fun choose(
        mode: TimerAudioMode,
        event: TimerCueEvent,
        word: TimerSpokenWord,
        speechReady: Boolean,
        languageSupported: Boolean
    ): TimerCueOutput {
        if (mode == TimerAudioMode.SILENT) return TimerCueOutput.NONE
        val completion = event == TimerCueEvent.INTERVAL_COMPLETE
        if (mode == TimerAudioMode.BEEPS) {
            return if (completion) TimerCueOutput.COMPLETION_TONE else TimerCueOutput.COUNTDOWN_TONE
        }
        if (word == TimerSpokenWord.NONE) {
            return if (completion) TimerCueOutput.COMPLETION_TONE else TimerCueOutput.NONE
        }
        if (!speechReady || !languageSupported) {
            return if (completion) TimerCueOutput.COMPLETION_TONE else TimerCueOutput.COUNTDOWN_TONE
        }
        return TimerCueOutput.SPEECH
    }
}

object WorkPreparationLogic {
    const val DURATION_MILLIS = 3_000L

    fun started(setId: Long, elapsedRealtimeMillis: Long, generation: Long): WorkPreparationSnapshot {
        return WorkPreparationSnapshot(
            setId = setId,
            generation = generation,
            deadlineElapsed = elapsedRealtimeMillis + DURATION_MILLIS
        )
    }

    fun displayCount(remainingMillis: Long): Int {
        return when {
            remainingMillis > 2_000L -> 3
            remainingMillis > 1_000L -> 2
            remainingMillis > 0L -> 1
            else -> 0
        }
    }
}

data class WorkPreparationSnapshot(
    val setId: Long,
    val generation: Long,
    val deadlineElapsed: Long
)

object WorkCompletionCue {
    fun word(
        exercises: List<SessionExerciseItem>,
        setId: Long,
        timersAllowed: Boolean
    ): TimerSpokenWord {
        val next = SessionFocusLogic.focusAfterResolving(exercises, setId)
        if (next !is WorkoutFocusTarget.Set) return TimerSpokenWord.DONE
        val item = exercises.firstOrNull { entry -> entry.sets.any { it.id == setId } }
            ?: return TimerSpokenWord.DONE
        val set = item.sets.firstOrNull { it.id == setId } ?: return TimerSpokenWord.DONE
        val restSeconds = RestDuration.effectiveSeconds(item.exercise.restSeconds, set.restSeconds)
        if (restSeconds > 0 && timersAllowed) return TimerSpokenWord.REST
        return TimerSpokenWord.NONE
    }
}

object TimerAudioReducer {
    const val MARK_3 = 3_000L
    const val MARK_2 = 2_000L
    const val MARK_1 = 1_000L

    fun step(
        state: TimerAudioState,
        incoming: TimerIntervalSlot?,
        nowElapsed: Long,
        mode: TimerAudioMode
    ): TimerAudioStep {
        val previous = state.slot
        if (previous != null && (incoming == null || incoming.id != previous.id)) {
            return handoff(state, previous, incoming, nowElapsed, mode)
        }
        if (incoming == null) {
            return TimerAudioStep(state.copy(slot = null))
        }
        return when (incoming.phase) {
            TimerAudioPhase.PAUSED -> TimerAudioStep(
                state = state.copy(slot = incoming),
                stop = previous?.phase == TimerAudioPhase.RUNNING,
                waitMillis = null
            )
            TimerAudioPhase.CANCELLED -> TimerAudioStep(
                state = state.copy(
                    slot = null,
                    consumed = consume(state.consumed, incoming.id, TimerCueEvent.entries.toSet())
                ),
                stop = true,
                waitMillis = null
            )
            TimerAudioPhase.FINISHED_NATURALLY -> finish(state, incoming, mode)
            TimerAudioPhase.RUNNING -> running(state, incoming, mode, nowElapsed)
        }
    }

    private fun handoff(
        state: TimerAudioState,
        previous: TimerIntervalSlot,
        incoming: TimerIntervalSlot?,
        nowElapsed: Long,
        mode: TimerAudioMode
    ): TimerAudioStep {
        val deadline = previous.deadlineElapsed
        val ended = previous.phase == TimerAudioPhase.RUNNING &&
            deadline != null &&
            nowElapsed >= deadline
        val base = state.copy(slot = incoming)
        if (!ended) {
            return TimerAudioStep(state = base, stop = true, waitMillis = if (incoming == null) null else 0L)
        }
        val played = completionIfNeeded(base, previous, mode)
        return played.copy(waitMillis = if (incoming == null) null else 0L)
    }

    private fun finish(
        state: TimerAudioState,
        slot: TimerIntervalSlot,
        mode: TimerAudioMode
    ): TimerAudioStep {
        return completionIfNeeded(state.copy(slot = null), slot, mode)
    }

    private fun completionIfNeeded(
        state: TimerAudioState,
        slot: TimerIntervalSlot,
        mode: TimerAudioMode
    ): TimerAudioStep {
        val already = state.consumed[slot.id].orEmpty()
        val consumed = consume(state.consumed, slot.id, TimerCueEvent.entries.toSet())
        val next = state.copy(consumed = consumed, slot = state.slot)
        if (TimerCueEvent.INTERVAL_COMPLETE in already || mode == TimerAudioMode.SILENT) {
            return TimerAudioStep(state = next)
        }
        return TimerAudioStep(
            state = next,
            play = cue(slot, TimerCueEvent.INTERVAL_COMPLETE, slot.completion),
            stop = false
        )
    }

    private fun running(
        state: TimerAudioState,
        slot: TimerIntervalSlot,
        mode: TimerAudioMode,
        nowElapsed: Long
    ): TimerAudioStep {
        var consumed = state.consumed
        var durations = state.durationByInterval
        val previousDuration = durations[slot.id]
        val duration = slot.durationMillis
        if (previousDuration != null && duration != null && duration > previousDuration) {
            consumed = consumed - slot.id
        }
        if (duration != null) {
            durations = durations + (slot.id to duration)
        }
        val remaining = slot.deadlineElapsed?.let { it - nowElapsed } ?: slot.remainingMillis
        if (remaining == null) {
            return TimerAudioStep(state.copy(consumed = consumed, durationByInterval = durations, slot = slot))
        }
        if (remaining <= 0L) {
            return finish(
                state.copy(consumed = consumed, durationByInterval = durations),
                slot,
                mode
            )
        }
        val already = consumed[slot.id].orEmpty()
        val due = dueEvent(remaining)
        val behind = eventsBehind(remaining)
        val playEvent = due?.takeIf { it !in already }
        val newlyConsumed = behind.filter { it !in already }.toSet() + listOfNotNull(playEvent)
        val nextConsumed = consume(consumed, slot.id, newlyConsumed)
        val after = nextConsumed[slot.id].orEmpty()
        val wait = waitUntilNext(remaining, after)
        val play = if (playEvent != null && mode != TimerAudioMode.SILENT) {
            cue(slot, playEvent, wordFor(playEvent, slot.completion))
        } else {
            null
        }
        return TimerAudioStep(
            state = state.copy(consumed = nextConsumed, durationByInterval = durations, slot = slot),
            play = play,
            waitMillis = wait
        )
    }

    fun dueEvent(remainingMillis: Long): TimerCueEvent? {
        return when {
            remainingMillis <= 0L -> TimerCueEvent.INTERVAL_COMPLETE
            remainingMillis <= MARK_1 -> TimerCueEvent.COUNTDOWN_1
            remainingMillis <= MARK_2 -> TimerCueEvent.COUNTDOWN_2
            remainingMillis <= MARK_3 -> TimerCueEvent.COUNTDOWN_3
            else -> null
        }
    }

    fun eventsBehind(remainingMillis: Long): Set<TimerCueEvent> {
        return when {
            remainingMillis <= MARK_1 -> setOf(TimerCueEvent.COUNTDOWN_3, TimerCueEvent.COUNTDOWN_2)
            remainingMillis <= MARK_2 -> setOf(TimerCueEvent.COUNTDOWN_3)
            else -> emptySet()
        }
    }

    fun waitUntilNext(remainingMillis: Long, consumed: Set<TimerCueEvent>): Long? {
        if (remainingMillis <= 0L) return null
        val threshold = when {
            remainingMillis > MARK_3 && TimerCueEvent.COUNTDOWN_3 !in consumed -> MARK_3
            remainingMillis > MARK_2 && TimerCueEvent.COUNTDOWN_2 !in consumed -> MARK_2
            remainingMillis > MARK_1 && TimerCueEvent.COUNTDOWN_1 !in consumed -> MARK_1
            TimerCueEvent.INTERVAL_COMPLETE !in consumed -> 0L
            else -> return null
        }
        return (remainingMillis - threshold).coerceAtLeast(0L)
    }

    private fun wordFor(event: TimerCueEvent, completion: TimerSpokenWord): TimerSpokenWord {
        return when (event) {
            TimerCueEvent.COUNTDOWN_3 -> TimerSpokenWord.THREE
            TimerCueEvent.COUNTDOWN_2 -> TimerSpokenWord.TWO
            TimerCueEvent.COUNTDOWN_1 -> TimerSpokenWord.ONE
            TimerCueEvent.INTERVAL_COMPLETE -> completion
        }
    }

    private fun cue(slot: TimerIntervalSlot, event: TimerCueEvent, word: TimerSpokenWord): PlayedCue {
        return PlayedCue(
            id = slot.id,
            event = event,
            word = word,
            sessionId = slot.sessionId,
            ledgerGeneration = slot.ledgerGeneration,
            setId = slot.setId
        )
    }

    private fun consume(
        current: Map<TimerIntervalId, Set<TimerCueEvent>>,
        id: TimerIntervalId,
        events: Set<TimerCueEvent>
    ): Map<TimerIntervalId, Set<TimerCueEvent>> {
        if (events.isEmpty()) return current
        return current + (id to current[id].orEmpty() + events)
    }
}
