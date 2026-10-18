package app.mymusclemap.ui.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import app.mymusclemap.domain.workout.TimerAudioMode
import app.mymusclemap.domain.workout.TimerCueEvent
import app.mymusclemap.domain.workout.TimerCueOutput
import app.mymusclemap.domain.workout.TimerCueOutputSelector
import app.mymusclemap.domain.workout.TimerSpeech
import app.mymusclemap.domain.workout.TimerSpeechLanguage
import app.mymusclemap.domain.workout.TimerSpokenWord
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

fun currentTimerSpeechLanguage(): TimerSpeechLanguage {
    val tag = try {
        val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) Locale.getDefault().language else locales[0]?.language
    } catch (_: RuntimeException) {
        Locale.getDefault().language
    }
    return TimerSpeechLanguage.resolve(tag)
}

enum class VoiceReadiness {
    READY,
    UNAVAILABLE
}

interface TimerCuePlayer {
    fun play(
        mode: TimerAudioMode,
        event: TimerCueEvent,
        word: TimerSpokenWord,
        language: TimerSpeechLanguage
    )

    fun stop()
    fun release()
    suspend fun prepareVoice(timeoutMillis: Long): VoiceReadiness = VoiceReadiness.UNAVAILABLE

    object None : TimerCuePlayer {
        override fun play(
            mode: TimerAudioMode,
            event: TimerCueEvent,
            word: TimerSpokenWord,
            language: TimerSpeechLanguage
        ) = Unit

        override fun stop() = Unit
        override fun release() = Unit
    }
}

/**
 * One speech engine and one tone generator for the whole workout.
 * Speech failure falls back to tones for that cue and does not change the saved mode.
 */
class AndroidTimerCuePlayer(context: Context) : TimerCuePlayer {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val speechReady = AtomicBoolean(false)
    private val speechFailed = AtomicBoolean(false)
    private var speech: TextToSpeech? = null
    private var readyContinuation: ((Boolean) -> Unit)? = null
    private var focusRequest: AudioFocusRequest? = null
    private val supportedLanguages = HashMap<TimerSpeechLanguage, Boolean>()

    override fun play(
        mode: TimerAudioMode,
        event: TimerCueEvent,
        word: TimerSpokenWord,
        language: TimerSpeechLanguage
    ) {
        val output = TimerCueOutputSelector.choose(
            mode = mode,
            event = event,
            word = word,
            speechReady = speechReady.get() && !speechFailed.get(),
            languageSupported = supportedLanguages[language] != false
        )
        when (output) {
            TimerCueOutput.NONE -> Unit
            TimerCueOutput.SPEECH -> if (!speak(word, language)) tone(event)
            TimerCueOutput.COUNTDOWN_TONE -> tone(TimerCueEvent.COUNTDOWN_1)
            TimerCueOutput.COMPLETION_TONE -> tone(TimerCueEvent.INTERVAL_COMPLETE)
        }
    }

    override suspend fun prepareVoice(timeoutMillis: Long): VoiceReadiness {
        ensureSpeech()
        val ready = withTimeoutOrNull(timeoutMillis) { awaitReady() } ?: return VoiceReadiness.UNAVAILABLE
        if (!ready || speechFailed.get()) return VoiceReadiness.UNAVAILABLE
        val language = currentTimerSpeechLanguage()
        return if (applyLanguage(language)) VoiceReadiness.READY else VoiceReadiness.UNAVAILABLE
    }

    override fun stop() {
        speech?.stop()
        abandonFocus()
    }

    override fun release() {
        stop()
        speech?.shutdown()
        speech = null
        speechReady.set(false)
        RestTimerAlarm.releaseTone()
    }

    private fun speak(word: TimerSpokenWord, language: TimerSpeechLanguage): Boolean {
        val engine = speech ?: return false
        if (!speechReady.get() || speechFailed.get()) return false
        val phrase = TimerSpeech.phrase(language, word) ?: return false
        if (!applyLanguage(language)) return false
        requestFocus()
        val result = engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "timer-${word.name}")
        return result == TextToSpeech.SUCCESS
    }

    private fun tone(event: TimerCueEvent) {
        requestFocus()
        if (event == TimerCueEvent.INTERVAL_COMPLETE) {
            RestTimerAlarm.playCompletionTone()
        } else {
            RestTimerAlarm.playCountdownTone()
        }
    }

    private fun ensureSpeech() {
        if (speech != null || speechFailed.get()) return
        speech = TextToSpeech(appContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            speechReady.set(ok)
            if (!ok) speechFailed.set(true)
            speech?.setAudioAttributes(attributes)
            readyContinuation?.invoke(ok)
            readyContinuation = null
        }
    }

    private suspend fun awaitReady(): Boolean {
        if (speechReady.get()) return true
        if (speechFailed.get()) return false
        return suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            fun finish(ok: Boolean) {
                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                    readyContinuation = null
                    continuation.resume(ok)
                }
            }
            readyContinuation = { ok -> finish(ok) }
            if (speechReady.get()) finish(true)
            else if (speechFailed.get()) finish(false)
            continuation.invokeOnCancellation { readyContinuation = null }
        }
    }

    private fun applyLanguage(language: TimerSpeechLanguage): Boolean {
        supportedLanguages[language]?.let { return it }
        val engine = speech ?: return false
        val locale = when (language) {
            TimerSpeechLanguage.HU -> Locale.forLanguageTag("hu")
            TimerSpeechLanguage.EN -> Locale.ENGLISH
        }
        val result = engine.setLanguage(locale)
        val supported = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        supportedLanguages[language] = supported
        return supported
    }

    private fun requestFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
            .also { focusRequest = it }
        try {
            manager.requestAudioFocus(request)
        } catch (_: RuntimeException) {
        }
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        val request = focusRequest ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            manager.abandonAudioFocusRequest(request)
        } catch (_: RuntimeException) {
        }
    }
}
