package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerAudioTest {
    @Test
    fun legacyOnBecomesBeepsAndOffBecomesSilent() {
        assertEquals(TimerAudioMode.BEEPS, TimerAudioPreference.resolve(storedMode = null, legacyEnabled = true))
        assertEquals(TimerAudioMode.SILENT, TimerAudioPreference.resolve(storedMode = null, legacyEnabled = false))
    }

    @Test
    fun newInstallationDefaultsToBeeps() {
        assertEquals(TimerAudioMode.BEEPS, TimerAudioPreference.resolve(storedMode = null, legacyEnabled = null))
        val fresh = TimerAudioPreference.migration(storedMode = null, legacyPresent = false, legacyEnabled = true)
        assertNull(fresh.writeMode)
        assertFalse(fresh.removeLegacy)
    }

    @Test
    fun storedVoiceWinsOverTheLegacyBoolean() {
        assertEquals(TimerAudioMode.VOICE, TimerAudioPreference.resolve("VOICE", legacyEnabled = false))
        val migrated = TimerAudioPreference.migration(
            storedMode = "VOICE",
            legacyPresent = true,
            legacyEnabled = false
        )
        assertNull(migrated.writeMode)
        assertTrue(migrated.removeLegacy)
    }

    @Test
    fun legacyBooleanIsWrittenOnceAndThenRemoved() {
        val on = TimerAudioPreference.migration(storedMode = null, legacyPresent = true, legacyEnabled = true)
        assertEquals(TimerAudioMode.BEEPS.name, on.writeMode)
        assertTrue(on.removeLegacy)
        val off = TimerAudioPreference.migration(storedMode = null, legacyPresent = true, legacyEnabled = false)
        assertEquals(TimerAudioMode.SILENT.name, off.writeMode)
        assertTrue(off.removeLegacy)
    }

    @Test
    fun silentProducesNoAudioEvents() {
        val step = TimerAudioReducer.step(
            TimerAudioState(),
            running(remaining = 2_500L),
            nowElapsed = 0L,
            mode = TimerAudioMode.SILENT
        )
        assertNull(step.play)
        assertTrue(TimerCueEvent.COUNTDOWN_3 in step.state.consumed.values.flatten())
        val done = TimerAudioReducer.step(
            step.state,
            running(remaining = 0L, phase = TimerAudioPhase.FINISHED_NATURALLY),
            nowElapsed = 3_000L,
            mode = TimerAudioMode.SILENT
        )
        assertNull(done.play)
    }

    @Test
    fun countdownMarksPlayOnceAtEachThreshold() {
        assertEquals(TimerCueEvent.COUNTDOWN_3, played(remaining = 3_000L))
        assertEquals(TimerCueEvent.COUNTDOWN_2, played(remaining = 2_000L))
        assertEquals(TimerCueEvent.COUNTDOWN_1, played(remaining = 1_000L))
        assertEquals(TimerCueEvent.INTERVAL_COMPLETE, played(remaining = 0L, phase = TimerAudioPhase.FINISHED_NATURALLY))
    }

    @Test
    fun theSameSampleDoesNotPlayTwice() {
        val first = TimerAudioReducer.step(TimerAudioState(), running(remaining = 2_500L), 0L, TimerAudioMode.BEEPS)
        assertEquals(TimerCueEvent.COUNTDOWN_3, first.play?.event)
        val second = TimerAudioReducer.step(first.state, running(remaining = 2_500L), 0L, TimerAudioMode.BEEPS)
        assertNull(second.play)
    }

    @Test
    fun resumeInsideTheLastSecondDoesNotBurstMissedNumbers() {
        val fresh = TimerAudioReducer.step(TimerAudioState(), running(remaining = 800L), 0L, TimerAudioMode.VOICE)
        assertEquals(TimerCueEvent.COUNTDOWN_1, fresh.play?.event)
        assertEquals(TimerSpokenWord.ONE, fresh.play?.word)
        val consumed = fresh.state.consumed.values.single()
        assertTrue(TimerCueEvent.COUNTDOWN_3 in consumed)
        assertTrue(TimerCueEvent.COUNTDOWN_2 in consumed)
        val again = TimerAudioReducer.step(fresh.state, running(remaining = 700L), 0L, TimerAudioMode.VOICE)
        assertNull(again.play)
    }

    @Test
    fun pauseStopsCuesAndResumeDoesNotReplayThem() {
        val heardThree = TimerAudioReducer.step(TimerAudioState(), running(remaining = 2_800L), 0L, TimerAudioMode.VOICE)
        assertEquals(TimerSpokenWord.THREE, heardThree.play?.word)
        val paused = TimerAudioReducer.step(
            heardThree.state,
            running(remaining = 2_400L, phase = TimerAudioPhase.PAUSED, deadline = null),
            0L,
            TimerAudioMode.VOICE
        )
        assertTrue(paused.stop)
        assertNull(paused.play)
        val resumed = TimerAudioReducer.step(
            paused.state,
            running(remaining = 900L, deadline = 9_000L),
            8_100L,
            TimerAudioMode.VOICE
        )
        assertEquals(TimerCueEvent.COUNTDOWN_1, resumed.play?.event)
        assertFalse(resumed.state.consumed.values.single().contains(TimerCueEvent.COUNTDOWN_3).not())
    }

    @Test
    fun stopAndSkipCancelTheCompletionCue() {
        val counting = TimerAudioReducer.step(TimerAudioState(), running(remaining = 1_200L), 0L, TimerAudioMode.BEEPS)
        val stopped = TimerAudioReducer.step(
            counting.state,
            running(remaining = null, phase = TimerAudioPhase.CANCELLED, deadline = null),
            0L,
            TimerAudioMode.BEEPS
        )
        assertTrue(stopped.stop)
        assertNull(stopped.play)
        val late = TimerAudioReducer.step(
            stopped.state,
            running(remaining = 0L, phase = TimerAudioPhase.FINISHED_NATURALLY),
            0L,
            TimerAudioMode.BEEPS
        )
        assertNull(late.play)

        val rest = TimerAudioReducer.step(TimerAudioState(), rest(remaining = 4_000L), 1_000L, TimerAudioMode.VOICE)
        val skipped = TimerAudioReducer.step(rest.state, null, nowElapsed = 2_000L, mode = TimerAudioMode.VOICE)
        assertTrue(skipped.stop)
        assertNull(skipped.play)
    }

    @Test
    fun extensionClearsStaleCuesAndWaitsForTheNewEnding() {
        val nearEnd = TimerAudioReducer.step(TimerAudioState(), rest(remaining = 1_500L, duration = 10_000L), 8_500L, TimerAudioMode.BEEPS)
        assertEquals(TimerCueEvent.COUNTDOWN_2, nearEnd.play?.event)
        val extended = TimerAudioReducer.step(
            nearEnd.state,
            rest(remaining = 11_500L, duration = 20_000L, deadline = 20_000L),
            8_500L,
            TimerAudioMode.BEEPS
        )
        assertNull(extended.play)
        assertTrue(extended.state.consumed[restId()].isNullOrEmpty())
        val later = TimerAudioReducer.step(
            extended.state,
            rest(remaining = 2_500L, duration = 20_000L, deadline = 20_000L),
            17_500L,
            TimerAudioMode.BEEPS
        )
        assertEquals(TimerCueEvent.COUNTDOWN_3, later.play?.event)
    }

    @Test
    fun naturalExpiryAnnouncesWithoutReplayingTheCountdown() {
        var state = TimerAudioState()
        state = TimerAudioReducer.step(state, running(remaining = 2_500L, completion = TimerSpokenWord.REST), 0L, TimerAudioMode.VOICE).state
        val expired = TimerAudioReducer.step(
            state,
            null,
            nowElapsed = 3_000L,
            mode = TimerAudioMode.VOICE
        )
        assertEquals(TimerCueEvent.INTERVAL_COMPLETE, expired.play?.event)
        assertEquals(TimerSpokenWord.REST, expired.play?.word)
    }

    @Test
    fun preparationCountdownIsSeparateFromMeasuredWork() {
        val prep = WorkPreparationLogic.started(setId = 4L, elapsedRealtimeMillis = 1_000L, generation = 1L)
        assertEquals(4_000L, prep.deadlineElapsed)
        val step = TimerAudioReducer.step(
            TimerAudioState(),
            TimerIntervalSlot(
                id = TimerIntervalId(TimerCueKind.PREPARATION, 1L),
                deadlineElapsed = prep.deadlineElapsed,
                phase = TimerAudioPhase.RUNNING,
                remainingMillis = 3_000L,
                durationMillis = WorkPreparationLogic.DURATION_MILLIS,
                completion = TimerSpokenWord.GO,
                sessionId = 1L,
                ledgerGeneration = 1L,
                setId = 4L
            ),
            nowElapsed = 1_000L,
            mode = TimerAudioMode.VOICE
        )
        assertEquals(TimerSpokenWord.THREE, step.play?.word)
        val started = WorkTimerLogic.started(
            setId = 4L,
            targetMillis = 30_000L,
            clock = RestClockSample(prep.deadlineElapsed, 9_000L),
            generation = 2L
        )
        assertEquals(30_000L, started.targetMillis)
        assertEquals(0L, started.elapsedActiveMillis)
        assertEquals(prep.deadlineElapsed + 30_000L, started.deadlineElapsedRealtimeMillis)
    }

    @Test
    fun workExpiryKeepsTheSetAwaitingAndDoesNotStartRest() {
        val started = WorkTimerLogic.started(4L, 30_000L, RestClockSample(0L, 0L), 1L)
        val expired = WorkTimerLogic.expire(started)!!
        assertEquals(WorkPhase.AWAITING, expired.phase)
        assertEquals(started.setId, expired.setId)
        assertEquals(started.targetMillis, expired.elapsedActiveMillis)
        assertEquals(TimerSpokenWord.REST, WorkCompletionCue.word(twoSets(rest = 60), setId = 1L, timersAllowed = true))
        assertNull(expired.deadlineElapsedRealtimeMillis)
    }

    @Test
    fun completionWordFollowsTheNextInterval() {
        assertEquals(TimerSpokenWord.REST, WorkCompletionCue.word(twoSets(rest = 90), 1L, timersAllowed = true))
        assertEquals(TimerSpokenWord.NONE, WorkCompletionCue.word(twoSets(rest = 0), 1L, timersAllowed = true))
        assertEquals(TimerSpokenWord.NONE, WorkCompletionCue.word(twoSets(rest = 90), 1L, timersAllowed = false))
        val both = twoSets(rest = 90)
        val onlyFirst = listOf(both.single().copy(sets = listOf(both.single().sets.first())))
        assertEquals(TimerSpokenWord.DONE, WorkCompletionCue.word(onlyFirst, 1L, timersAllowed = true))
    }

    @Test
    fun restExpirySaysGoForEitherProgressionMode() {
        val strict = TimerAudioReducer.step(TimerAudioState(), rest(remaining = 0L, phase = TimerAudioPhase.FINISHED_NATURALLY), 10_000L, TimerAudioMode.VOICE)
        val flexible = TimerAudioReducer.step(TimerAudioState(), rest(remaining = 0L, phase = TimerAudioPhase.FINISHED_NATURALLY), 10_000L, TimerAudioMode.VOICE)
        assertEquals(TimerSpokenWord.GO, strict.play?.word)
        assertEquals(TimerSpokenWord.GO, flexible.play?.word)
        assertEquals(strict.play?.event, flexible.play?.event)
    }

    @Test
    fun speechPhrasesStayInOneLanguage() {
        assertEquals(TimerSpeechLanguage.HU, TimerSpeechLanguage.resolve("hu"))
        assertEquals(TimerSpeechLanguage.EN, TimerSpeechLanguage.resolve("en"))
        assertEquals(TimerSpeechLanguage.EN, TimerSpeechLanguage.resolve(null))
        val hungarian = TimerSpokenWord.entries.mapNotNull { TimerSpeech.phrase(TimerSpeechLanguage.HU, it) }
        val english = TimerSpokenWord.entries.mapNotNull { TimerSpeech.phrase(TimerSpeechLanguage.EN, it) }
        assertEquals(listOf("Három", "Kettő", "Egy", "Rajt", "Pihenő", "Kész"), hungarian)
        assertEquals(listOf("Three", "Two", "One", "Go", "Rest", "Done"), english)
        assertTrue(hungarian.none { it in english })
        assertNull(TimerSpeech.phrase(TimerSpeechLanguage.EN, TimerSpokenWord.NONE))
    }

    @Test
    fun missingOrFailedSpeechFallsBackToTonesWithoutChangingTheMode() {
        assertEquals(
            TimerCueOutput.COUNTDOWN_TONE,
            TimerCueOutputSelector.choose(
                TimerAudioMode.VOICE,
                TimerCueEvent.COUNTDOWN_2,
                TimerSpokenWord.TWO,
                speechReady = false,
                languageSupported = true
            )
        )
        assertEquals(
            TimerCueOutput.COMPLETION_TONE,
            TimerCueOutputSelector.choose(
                TimerAudioMode.VOICE,
                TimerCueEvent.INTERVAL_COMPLETE,
                TimerSpokenWord.GO,
                speechReady = true,
                languageSupported = false
            )
        )
        assertEquals(
            TimerCueOutput.SPEECH,
            TimerCueOutputSelector.choose(
                TimerAudioMode.VOICE,
                TimerCueEvent.COUNTDOWN_1,
                TimerSpokenWord.ONE,
                speechReady = true,
                languageSupported = true
            )
        )
        assertEquals(
            TimerCueOutput.NONE,
            TimerCueOutputSelector.choose(
                TimerAudioMode.SILENT,
                TimerCueEvent.INTERVAL_COMPLETE,
                TimerSpokenWord.DONE,
                speechReady = true,
                languageSupported = true
            )
        )
        assertEquals(TimerAudioMode.VOICE, TimerAudioPreference.resolve("VOICE", legacyEnabled = null))
    }

    @Test
    fun beepsUseTonesAndANeutralEndingDoesNotSayRest() {
        assertEquals(
            TimerCueOutput.COUNTDOWN_TONE,
            TimerCueOutputSelector.choose(TimerAudioMode.BEEPS, TimerCueEvent.COUNTDOWN_3, TimerSpokenWord.THREE, true, true)
        )
        assertEquals(
            TimerCueOutput.COMPLETION_TONE,
            TimerCueOutputSelector.choose(TimerAudioMode.BEEPS, TimerCueEvent.INTERVAL_COMPLETE, TimerSpokenWord.DONE, true, true)
        )
        assertEquals(
            TimerCueOutput.COMPLETION_TONE,
            TimerCueOutputSelector.choose(TimerAudioMode.VOICE, TimerCueEvent.INTERVAL_COMPLETE, TimerSpokenWord.NONE, true, true)
        )
    }

    @Test
    fun foregroundAndClaimedCompletionsDoNotAlsoSoundTheNotification() {
        assertFalse(TimerNotificationAudio.shouldPlay(TimerAudioMode.BEEPS, appInForeground = true, completionAlreadyClaimed = false))
        assertFalse(TimerNotificationAudio.shouldPlay(TimerAudioMode.BEEPS, appInForeground = false, completionAlreadyClaimed = true))
        assertTrue(TimerNotificationAudio.shouldPlay(TimerAudioMode.BEEPS, appInForeground = false, completionAlreadyClaimed = false))
        assertFalse(TimerNotificationAudio.shouldPlay(TimerAudioMode.VOICE, appInForeground = false, completionAlreadyClaimed = false))
        assertFalse(TimerNotificationAudio.shouldPlay(TimerAudioMode.SILENT, appInForeground = false, completionAlreadyClaimed = false))
    }

    private fun played(remaining: Long, phase: TimerAudioPhase = TimerAudioPhase.RUNNING): TimerCueEvent? {
        return TimerAudioReducer.step(TimerAudioState(), running(remaining = remaining, phase = phase), 0L, TimerAudioMode.BEEPS).play?.event
    }

    private fun running(
        remaining: Long?,
        phase: TimerAudioPhase = TimerAudioPhase.RUNNING,
        deadline: Long? = null,
        completion: TimerSpokenWord = TimerSpokenWord.DONE
    ): TimerIntervalSlot {
        return TimerIntervalSlot(
            id = TimerIntervalId(TimerCueKind.WORK, 7L),
            deadlineElapsed = if (phase == TimerAudioPhase.RUNNING || phase == TimerAudioPhase.FINISHED_NATURALLY) {
                deadline ?: remaining
            } else {
                deadline
            },
            phase = phase,
            remainingMillis = remaining,
            durationMillis = 30_000L,
            completion = completion,
            sessionId = 1L,
            ledgerGeneration = 7L,
            setId = 4L
        )
    }

    private fun rest(
        remaining: Long,
        duration: Long = 10_000L,
        deadline: Long = 10_000L,
        phase: TimerAudioPhase = TimerAudioPhase.RUNNING
    ): TimerIntervalSlot {
        return TimerIntervalSlot(
            id = restId(),
            deadlineElapsed = deadline,
            phase = phase,
            remainingMillis = remaining,
            durationMillis = duration,
            completion = TimerSpokenWord.GO,
            sessionId = 1L,
            ledgerGeneration = 3L,
            setId = 2L
        )
    }

    private fun restId() = TimerIntervalId(TimerCueKind.REST, 3L)

    private fun twoSets(rest: Int): List<SessionExerciseItem> {
        val exercise = SessionExercise(
            id = 10L,
            sessionId = 1L,
            exerciseId = 7L,
            position = 0,
            name = "Dip",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PUSH,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.TRICEPS,
            secondaryMuscles = emptyList(),
            notes = null,
            restSeconds = rest
        )
        return listOf(
            SessionExerciseItem(
                exercise = exercise,
                sets = listOf(pending(1L), pending(2L))
            )
        )
    }

    private fun pending(id: Long) = SessionSet(
        id = id,
        sessionExerciseId = 10L,
        position = (id - 1L).toInt(),
        plannedMinReps = 8,
        plannedMaxReps = 8,
        plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
        plannedWeightKg = null,
        plannedDurationSeconds = null,
        plannedDistanceMeters = null,
        actualReps = null,
        actualLoadKind = null,
        actualWeightKg = null,
        actualDurationSeconds = null,
        actualDistanceMeters = null,
        status = SessionSetStatus.PENDING,
        completedAt = null,
        addedDuringWorkout = false,
        restSeconds = null
    )
}
