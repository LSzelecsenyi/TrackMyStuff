package app.mymusclemap.ui.workout

import app.mymusclemap.domain.workout.PlayedCue
import app.mymusclemap.domain.workout.TimerAudioMode
import app.mymusclemap.domain.workout.TimerAudioReducer
import app.mymusclemap.domain.workout.TimerAudioState
import app.mymusclemap.domain.workout.TimerCompletionKeys
import app.mymusclemap.domain.workout.TimerCueEvent
import app.mymusclemap.domain.workout.TimerCueKind
import app.mymusclemap.domain.workout.TimerIntervalSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Schedules countdown cues from the active deadline.
 * The work and rest engines stay the source of remaining time.
 */
class TimerAudioCoordinator(
    private val scope: CoroutineScope,
    private val elapsedNow: () -> Long,
    private val player: TimerCuePlayer,
    private val ledger: TimerCompletionLedger,
    private val language: () -> app.mymusclemap.domain.workout.TimerSpeechLanguage,
    private val onPreparationFinished: (Long) -> Unit
) {
    private var state = TimerAudioState()
    private var job: Job? = null
    private var latestSlot: TimerIntervalSlot? = null
    private var latestMode = TimerAudioMode.BEEPS
    private var watchKey: WatchKey? = null

    fun sync(slot: TimerIntervalSlot?, mode: TimerAudioMode) {
        latestSlot = slot
        latestMode = mode
        val key = slot?.let { WatchKey(it.id, it.phase, it.deadlineElapsed, it.durationMillis, mode) }
        if (key == watchKey && job?.isActive == true) return
        watchKey = key
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val step = TimerAudioReducer.step(state, latestSlot, elapsedNow(), latestMode)
                state = step.state
                if (step.stop) player.stop()
                val cue = step.play
                if (cue != null) {
                    dispatch(cue)
                }
                val wait = step.waitMillis ?: break
                delay(wait.coerceAtLeast(1L))
            }
        }
    }

    fun stop() {
        latestSlot = null
        watchKey = null
        job?.cancel()
        job = null
        player.stop()
        state = state.copy(slot = null)
    }

    fun release() {
        stop()
        player.release()
    }

    private fun dispatch(cue: PlayedCue) {
        if (cue.event == TimerCueEvent.INTERVAL_COMPLETE && cue.id.kind != TimerCueKind.PREPARATION) {
            val key = TimerCompletionKeys.key(cue.id.kind, cue.sessionId, cue.ledgerGeneration)
            if (!ledger.tryClaim(key)) return
        }
        player.play(
            mode = latestMode,
            event = cue.event,
            word = cue.word,
            language = language()
        )
        if (cue.id.kind == TimerCueKind.PREPARATION && cue.event == TimerCueEvent.INTERVAL_COMPLETE) {
            val setId = cue.setId ?: return
            onPreparationFinished(setId)
        }
    }

    private data class WatchKey(
        val id: app.mymusclemap.domain.workout.TimerIntervalId,
        val phase: app.mymusclemap.domain.workout.TimerAudioPhase,
        val deadlineElapsed: Long?,
        val durationMillis: Long?,
        val mode: TimerAudioMode
    )
}
