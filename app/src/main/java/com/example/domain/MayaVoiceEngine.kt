package com.example.domain

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.MayaPersona
import com.example.data.model.MayaTone
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MayaVoiceEngine"
private const val SPECTRUM_BAND_COUNT = 24
private const val VOICE_PREFS_NAME = "maya_voice_prefs"
private const val KEY_SILENT_LISTENING = "silent_listening_enabled"
private const val KEY_VOICE_REPLY = "voice_reply_enabled"
private const val KEY_CRASH_GUARD_MUTED = "streams_muted_by_maya"
private const val KEY_SAVED_MUSIC_VOL = "saved_pre_mute_music_vol"

/**
 * Minimum window (ms) to keep output streams muted after SpeechRecognizer ends/cancels/errors
 * so Google Speech Services' ~180-220ms end-of-speech or cancel beep plays into silence.
 */
private const val RECOGNIZER_BEEP_TAIL_GUARD_MS = 260L
private const val STOP_RECOGNIZER_BEEP_GUARD_MS = 350L

private val BEEP_SUPPRESSION_STREAMS = intArrayOf(
    AudioManager.STREAM_MUSIC,
    AudioManager.STREAM_NOTIFICATION,
    AudioManager.STREAM_SYSTEM
)

enum class BrainLoopPhase(val statusHinglish: String) {
    OFF("Brain Mode Off"),
    LISTENING("Silent sun rahi hoon, Boss…"),
    THINKING("Soch rahi hoon…"),
    SPEAKING("Jawab de rahi hoon…"),
    PAUSED("Brain Paused"),
    RECONNECTING("Silent mic reconnect ho raha hai…")
}

class MayaVoiceEngine(
    context: Context,
    private val recognitionAvailableOverride: Boolean? = null
) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(VOICE_PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var ttsWaveJob: Job? = null
    private var autoRestartJob: Job? = null
    private var ttsWatchdogJob: Job? = null
    private var pendingDelayedSpeakJob: Job? = null
    private var releaseMuteTailJob: Job? = null

    @Volatile
    private var isAiThinking = false

    @Volatile
    private var isRecognizerSessionActive = false

    @Volatile
    private var lastRecognizerToneEventAtMs = 0L

    private val muteLock = Any()
    private val streamsMutedByMaya = mutableSetOf<Int>()
    private var savedPreMuteMusicVolume: Int? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _wakeWordModeEnabled = MutableStateFlow(false)
    val wakeWordModeEnabled: StateFlow<Boolean> = _wakeWordModeEnabled.asStateFlow()

    private val _permanentBrainEnabled = MutableStateFlow(false)
    val permanentBrainEnabled: StateFlow<Boolean> = _permanentBrainEnabled.asStateFlow()

    private val _permanentBrainPaused = MutableStateFlow(false)
    val permanentBrainPaused: StateFlow<Boolean> = _permanentBrainPaused.asStateFlow()

    private val _brainLoopPhase = MutableStateFlow(BrainLoopPhase.OFF)
    val brainLoopPhase: StateFlow<BrainLoopPhase> = _brainLoopPhase.asStateFlow()

    /**
     * Zero-Beep Silent Listening is ON by default so Android SpeechRecognizer never plays
     * start/timeout/post-task beeps during listening or after completing tasks.
     */
    private val _silentListeningEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SILENT_LISTENING, true)
    )
    val silentListeningEnabled: StateFlow<Boolean> = _silentListeningEnabled.asStateFlow()

    private val _voiceReplyEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_VOICE_REPLY, true)
    )
    val voiceReplyEnabled: StateFlow<Boolean> = _voiceReplyEnabled.asStateFlow()

    /**
     * Normalized 0f..1f audio energy & 24-band spectrum mirroring `maya-ui` (`audio_worker.py` + `mic_visualizer.py`),
     * reacting to real microphone RMS dB during speech recognition and TTS output cadence when Maya speaks.
     */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    /**
     * Decoupled Microphone input level mirroring `workers/mic_worker.py` in `maya-ui`:
     * high-sensitivity gain, noise gate (< 0.02f -> 0f), clamp 0..1, and 0.5f smoothing.
     */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    /**
     * Decoupled Speaker / TTS output amplitude mirroring `workers/audio_worker.py` in `maya-ui`,
     * specifically driving the AI Orb's HSV Blue->Cyan shift and radius expansion.
     */
    private val _speakerAmplitude = MutableStateFlow(0f)
    val speakerAmplitude: StateFlow<Float> = _speakerAmplitude.asStateFlow()

    private val _spectrumBands = MutableStateFlow(List(SPECTRUM_BAND_COUNT) { 0.08f })
    val spectrumBands: StateFlow<List<Float>> = _spectrumBands.asStateFlow()

    private var phaseTick: Double = 0.0

    private var onFinalSpeechResult: ((String) -> Unit)? = null
    private var onSpeechErrorCallback: ((String) -> Unit)? = null

    init {
        recoverIfPreviousProcessLeftStreamsMuted()
        runCatching {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val hiIn = Locale.forLanguageTag("en-IN")
                    val res = tts?.setLanguage(hiIn)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.US)
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                                _brainLoopPhase.value = BrainLoopPhase.SPEAKING
                            }
                            startTtsOutputSpectrum()
                        }

                        override fun onDone(utteranceId: String?) {
                            onTtsUtteranceFinished()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            onTtsUtteranceFinished()
                        }
                    })
                    ttsReady = true
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "TextToSpeech init unavailable", e)
        }
    }

    private fun audioManagerOrNull(): AudioManager? {
        return appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun recoverIfPreviousProcessLeftStreamsMuted() {
        runCatching {
            if (prefs.getBoolean(KEY_CRASH_GUARD_MUTED, false)) {
                val am = audioManagerOrNull()
                val savedVol = prefs.getInt(KEY_SAVED_MUSIC_VOL, -1)
                if (am != null) {
                    for (stream in BEEP_SUPPRESSION_STREAMS) {
                        runCatching {
                            am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                        }
                    }
                    if (savedVol > 0 && am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0) }
                    }
                }
                prefs.edit()
                    .putBoolean(KEY_CRASH_GUARD_MUTED, false)
                    .remove(KEY_SAVED_MUSIC_VOL)
                    .apply()
            }
        }
    }

    private fun isContinuousListeningActive(): Boolean {
        return (_permanentBrainEnabled.value && !_permanentBrainPaused.value) || _wakeWordModeEnabled.value
    }

    /**
     * Silences Android SpeechRecognizer's built-in start, end-of-speech, timeout, and restart beeps
     * with flags = 0 (no system volume popup).
     */
    internal fun engageSilentListeningMute() {
        releaseMuteTailJob?.cancel()
        releaseMuteTailJob = null
        if (!_silentListeningEnabled.value) return
        val am = audioManagerOrNull() ?: return

        synchronized(muteLock) {
            var changed = false
            for (stream in BEEP_SUPPRESSION_STREAMS) {
                runCatching {
                    val alreadyMuted = am.isStreamMute(stream)
                    if (!alreadyMuted) {
                        if (stream == AudioManager.STREAM_MUSIC && savedPreMuteMusicVolume == null) {
                            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (curVol > 0) {
                                savedPreMuteMusicVolume = curVol
                            }
                        }
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                        streamsMutedByMaya.add(stream)
                        changed = true
                    }
                }
            }
            if (changed) {
                val editor = prefs.edit().putBoolean(KEY_CRASH_GUARD_MUTED, true)
                savedPreMuteMusicVolume?.let { editor.putInt(KEY_SAVED_MUSIC_VOL, it) }
                editor.apply()
            }
        }
    }

    /**
     * Unmutes STREAM_MUSIC right before Maya's TTS speaks so Boss hears the reply clearly,
     * while keeping STREAM_NOTIFICATION and STREAM_SYSTEM muted against late recognizer chimes.
     */
    private fun prepareStreamsForTtsSpeech() {
        releaseMuteTailJob?.cancel()
        releaseMuteTailJob = null
        val am = audioManagerOrNull() ?: return
        synchronized(muteLock) {
            if (streamsMutedByMaya.remove(AudioManager.STREAM_MUSIC)) {
                runCatching {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    val savedVol = savedPreMuteMusicVolume
                    if (savedVol != null && savedVol > 0 && am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0)
                    }
                }
            }
        }
    }

    private fun scheduleReleaseSilentListeningMute(delayMs: Long = STOP_RECOGNIZER_BEEP_GUARD_MS) {
        releaseMuteTailJob?.cancel()
        if (delayMs <= 0L) {
            releaseAllSilentListeningMutesImmediate()
            return
        }
        releaseMuteTailJob = audioScope.launch {
            delay(delayMs)
            if (!isActive) return@launch
            if (_isListening.value || isRecognizerSessionActive) return@launch
            if (isContinuousListeningActive() && !_isSpeaking.value) return@launch
            releaseAllSilentListeningMutesImmediate()
        }
    }

    internal fun releaseAllSilentListeningMutesImmediate() {
        releaseMuteTailJob?.cancel()
        releaseMuteTailJob = null
        val am = audioManagerOrNull()
        synchronized(muteLock) {
            if (am != null && streamsMutedByMaya.isNotEmpty()) {
                val toRestore = streamsMutedByMaya.toList()
                streamsMutedByMaya.clear()
                for (stream in toRestore) {
                    runCatching {
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                        if (stream == AudioManager.STREAM_MUSIC) {
                            val savedVol = savedPreMuteMusicVolume
                            if (savedVol != null && savedVol > 0 && am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0)
                            }
                        }
                    }
                }
            } else {
                streamsMutedByMaya.clear()
            }
            savedPreMuteMusicVolume = null
            prefs.edit()
                .putBoolean(KEY_CRASH_GUARD_MUTED, false)
                .remove(KEY_SAVED_MUSIC_VOL)
                .apply()
        }
    }

    /**
     * Returns the user's true media volume index even while STREAM_MUSIC is temporarily muted
     * by Maya's Silent Listening guard, so telemetry never falsely displays 0%.
     */
    fun getEffectiveMusicVolume(audioManager: AudioManager): Int {
        synchronized(muteLock) {
            if (streamsMutedByMaya.contains(AudioManager.STREAM_MUSIC)) {
                savedPreMuteMusicVolume?.let { return it }
            }
        }
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun onExternalMediaVolumeChanged(newVolumeIndex: Int) {
        synchronized(muteLock) {
            savedPreMuteMusicVolume = newVolumeIndex.takeIf { it > 0 }
            // Setting a non-zero volume on Android implicitly unmutes STREAM_MUSIC
            if (newVolumeIndex > 0) {
                streamsMutedByMaya.remove(AudioManager.STREAM_MUSIC)
            }
        }
        // If Maya is currently listening silently (not speaking), re-mute cleanly after volume update
        if (_silentListeningEnabled.value && !_isSpeaking.value &&
            (_isListening.value || isContinuousListeningActive())
        ) {
            engageSilentListeningMute()
        }
    }

    internal fun isMusicStreamMutedByGuard(): Boolean {
        synchronized(muteLock) {
            return streamsMutedByMaya.contains(AudioManager.STREAM_MUSIC)
        }
    }

    fun setSilentListeningEnabled(enabled: Boolean) {
        _silentListeningEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SILENT_LISTENING, enabled).apply()
        if (enabled) {
            if (!_isSpeaking.value && (_isListening.value || isContinuousListeningActive())) {
                engageSilentListeningMute()
            }
        } else {
            releaseAllSilentListeningMutesImmediate()
        }
    }

    fun setVoiceReplyEnabled(enabled: Boolean) {
        _voiceReplyEnabled.value = enabled
        prefs.edit().putBoolean(KEY_VOICE_REPLY, enabled).apply()
        if (!enabled) {
            stopSpeaking()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun isRecognitionSupported(): Boolean {
        return recognitionAvailableOverride ?: SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    private fun onTtsUtteranceFinished() {
        isAiThinking = false
        pendingDelayedSpeakJob?.cancel()
        pendingDelayedSpeakJob = null
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = null
        _isSpeaking.value = false
        stopTtsOutputSpectrum()

        if (isContinuousListeningActive()) {
            // Immediately re-engage silent mute BEFORE SpeechRecognizer restarts so ZERO post-task beep plays!
            if (_silentListeningEnabled.value && isRecognitionSupported()) {
                engageSilentListeningMute()
            }
            // Short 380ms acoustic tail guard so room echo of Maya's own voice fades before mic reopens
            scheduleAutoResumeListening(delayMs = 380L)
        } else {
            // Single-shot turn completed: restore any muted streams cleanly with zero beep
            releaseAllSilentListeningMutesImmediate()
        }
    }

    private fun startTtsOutputSpectrum() {
        ttsWaveJob?.cancel()
        ttsWaveJob = audioScope.launch {
            var step = 0
            while (isActive && _isSpeaking.value) {
                val syllablePulse = (0.35f + 0.50f * abs(sin(step * 0.42)).toFloat()).coerceIn(0f, 1f)
                _speakerAmplitude.value = syllablePulse
                updateSpectrumFromNormalizedEnergy(syllablePulse)
                step++
                delay(55L)
            }
            _speakerAmplitude.value = 0f
            resetSpectrumIdle()
        }
    }

    private fun stopTtsOutputSpectrum() {
        ttsWaveJob?.cancel()
        ttsWaveJob = null
        _speakerAmplitude.value = 0f
        if (!_isListening.value) {
            resetSpectrumIdle()
        }
    }

    internal fun onMicRmsUpdated(rmsdB: Float) {
        val rawAmp = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
        // Exact noise gate (< 0.02f -> 0f) + 0.5 smoothing from maya-ui workers/mic_worker.py
        val gated = if (rawAmp < 0.02f) 0f else rawAmp
        _micLevel.value = (_micLevel.value + (gated - _micLevel.value) * 0.5f).coerceIn(0f, 1f)
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
        updateSpectrumFromNormalizedEnergy(normalized)
    }

    private fun updateSpectrumFromNormalizedEnergy(energy: Float) {
        val clamped = energy.coerceIn(0f, 1f)
        _audioLevel.value = clamped
        phaseTick += 0.55
        val center = (SPECTRUM_BAND_COUNT - 1) / 2.0
        _spectrumBands.value = List(SPECTRUM_BAND_COUNT) { idx ->
            val distFromCenter = abs(idx - center) / center
            val centerWeight = (1.0 - distFromCenter * 0.62).toFloat()
            val harmonic = (0.55 + 0.45 * abs(sin(phaseTick + idx * (PI / 5.5)))).toFloat()
            (0.08f + clamped * centerWeight * harmonic).coerceIn(0.06f, 1f)
        }
    }

    private fun resetSpectrumIdle() {
        _audioLevel.value = 0f
        _micLevel.value = 0f
        if (!_isSpeaking.value) {
            _speakerAmplitude.value = 0f
        }
        _spectrumBands.value = List(SPECTRUM_BAND_COUNT) { 0.08f }
    }

    /**
     * Golden Rule 1: Never read URLs aloud; mask raw 10+ digit unknown phone numbers per Privacy Rule 9.
     */
    fun sanitizeForVoice(raw: String): String {
        return raw
            .replace(Regex("https?://\\S+"), "link")
            .replace(Regex("www\\.\\S+"), "link")
            .replace(Regex("[*_#>`~]"), "")
            .replace(Regex("\\b\\+?\\d{10,13}\\b"), "unknown number")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Speaks Maya's reply with Self-Echo Guard & Zero-Beep Stream Transition:
     * Automatically pauses microphone recognition while muted, waits out any trailing recognizer
     * end-beep window, unmutes media stream for TTS, and re-mutes before resuming listening.
     */
    fun speak(
        text: String,
        persona: MayaPersona,
        tone: MayaTone,
        voiceEnabled: Boolean = _voiceReplyEnabled.value
    ): Boolean {
        isAiThinking = false
        val clean = sanitizeForVoice(text)
        if (!voiceEnabled || clean.isBlank()) {
            if (isContinuousListeningActive()) {
                if (_silentListeningEnabled.value && isRecognitionSupported()) {
                    engageSilentListeningMute()
                }
                scheduleAutoResumeListening(delayMs = 240L)
            } else {
                scheduleReleaseSilentListeningMute(delayMs = RECOGNIZER_BEEP_TAIL_GUARD_MS)
            }
            return false
        }

        // Self-Echo Prevention: Pause mic WHILE streams are still muted so cancel produces zero beep
        pauseMicForSpeechOutput()

        if (!ttsReady || tts == null) {
            if (isContinuousListeningActive()) {
                if (_silentListeningEnabled.value && isRecognitionSupported()) {
                    engageSilentListeningMute()
                }
                scheduleAutoResumeListening(delayMs = 300L)
            } else {
                scheduleReleaseSilentListeningMute(delayMs = RECOGNIZER_BEEP_TAIL_GUARD_MS)
            }
            return false
        }

        val pitch = (persona.pitch + tone.pitchDelta).coerceIn(0.55f, 1.45f)
        val rate = (persona.speechRate + tone.rateDelta).coerceIn(0.75f, 1.40f)
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)

        val utteranceId = "maya_${System.currentTimeMillis()}"
        _isSpeaking.value = true
        if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
            _brainLoopPhase.value = BrainLoopPhase.SPEAKING
        }
        startTtsOutputSpectrum()

        val elapsedSinceRecognizerEnd = SystemClock.uptimeMillis() - lastRecognizerToneEventAtMs
        val tailWaitMs = if (isMusicStreamMutedByGuard() && elapsedSinceRecognizerEnd in 0 until RECOGNIZER_BEEP_TAIL_GUARD_MS) {
            RECOGNIZER_BEEP_TAIL_GUARD_MS - elapsedSinceRecognizerEnd
        } else {
            0L
        }

        pendingDelayedSpeakJob?.cancel()
        if (tailWaitMs > 0L) {
            pendingDelayedSpeakJob = audioScope.launch {
                delay(tailWaitMs)
                if (!isActive || !_isSpeaking.value) return@launch
                runOnMain {
                    if (!_isSpeaking.value) return@runOnMain
                    dispatchTtsSpeakNow(clean, utteranceId)
                }
            }
        } else {
            dispatchTtsSpeakNow(clean, utteranceId)
        }

        // Safety watchdog in case OEM/headless TTS engine drops onDone callback
        val estimatedMaxMs = (clean.length * 90L + 2600L + tailWaitMs).coerceIn(3000L, 24000L)
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = audioScope.launch {
            delay(estimatedMaxMs)
            if (_isSpeaking.value) {
                onTtsUtteranceFinished()
            }
        }
        return true
    }

    private fun dispatchTtsSpeakNow(clean: String, utteranceId: String) {
        prepareStreamsForTtsSpeech()
        val result = runCatching {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ?: TextToSpeech.ERROR
        }.getOrDefault(TextToSpeech.ERROR)

        if (result == TextToSpeech.ERROR) {
            onTtsUtteranceFinished()
        }
    }

    fun stopSpeaking() {
        pendingDelayedSpeakJob?.cancel()
        pendingDelayedSpeakJob = null
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = null
        runCatching { tts?.stop() }
        _isSpeaking.value = false
        stopTtsOutputSpectrum()
        if (isContinuousListeningActive() && !_isListening.value) {
            if (_silentListeningEnabled.value && isRecognitionSupported()) {
                engageSilentListeningMute()
            }
            scheduleAutoResumeListening(delayMs = 280L)
        } else if (!isContinuousListeningActive() && !_isListening.value) {
            scheduleReleaseSilentListeningMute(delayMs = 150L)
        }
    }

    private fun pauseMicForSpeechOutput() {
        autoRestartJob?.cancel()
        autoRestartJob = null
        val shouldCancelRecognizer = isRecognizerSessionActive
        if (shouldCancelRecognizer) {
            if (_silentListeningEnabled.value && isRecognitionSupported()) {
                engageSilentListeningMute()
            }
            isRecognizerSessionActive = false
            lastRecognizerToneEventAtMs = SystemClock.uptimeMillis()
            runOnMain {
                runCatching { speechRecognizer?.cancel() }
            }
        }
        _isListening.value = false
        _partialTranscript.value = ""
    }

    fun setPermanentBrainLoop(
        enabled: Boolean,
        onTranscript: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (onTranscript != null) onFinalSpeechResult = onTranscript
        if (onError != null) onSpeechErrorCallback = onError

        if (enabled) {
            isAiThinking = false
            _wakeWordModeEnabled.value = false
            _permanentBrainEnabled.value = true
            _permanentBrainPaused.value = false
            if (!_isSpeaking.value) {
                _brainLoopPhase.value = BrainLoopPhase.LISTENING
                // Avoid restarting SpeechRecognizer if it is already actively listening (prevents ERROR_RECOGNIZER_BUSY beep)
                if (!_isListening.value && !isRecognizerSessionActive) {
                    val cb = onFinalSpeechResult
                    if (cb != null) {
                        startListening(cb, onSpeechErrorCallback ?: {})
                    }
                }
            } else {
                _brainLoopPhase.value = BrainLoopPhase.SPEAKING
            }
        } else {
            isAiThinking = false
            autoRestartJob?.cancel()
            autoRestartJob = null
            _permanentBrainEnabled.value = false
            _permanentBrainPaused.value = false
            _brainLoopPhase.value = BrainLoopPhase.OFF
            stopListening()
        }
    }

    fun setPermanentBrainPaused(paused: Boolean) {
        if (!_permanentBrainEnabled.value) return
        _permanentBrainPaused.value = paused
        isAiThinking = false
        if (paused) {
            autoRestartJob?.cancel()
            autoRestartJob = null
            _brainLoopPhase.value = BrainLoopPhase.PAUSED
            stopSpeaking()
            stopListening()
        } else {
            _brainLoopPhase.value = BrainLoopPhase.LISTENING
            if (_silentListeningEnabled.value && isRecognitionSupported()) {
                engageSilentListeningMute()
            }
            scheduleAutoResumeListening(delayMs = 150L)
        }
    }

    fun bindBrainCallbacks(
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        onFinalSpeechResult = onTranscript
        onSpeechErrorCallback = onError
        if (_permanentBrainEnabled.value && !_permanentBrainPaused.value && !_isListening.value && !_isSpeaking.value) {
            scheduleAutoResumeListening(delayMs = 150L)
        }
    }

    fun notifyThinkingStarted() {
        if (isContinuousListeningActive()) {
            isAiThinking = true
            pauseMicForSpeechOutput()
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                _brainLoopPhase.value = BrainLoopPhase.THINKING
            }
        } else if (_isListening.value || isRecognizerSessionActive) {
            pauseMicForSpeechOutput()
        }
    }

    fun notifyTurnCompletedWithoutSpeech() {
        isAiThinking = false
        if (isContinuousListeningActive()) {
            if (!_isSpeaking.value && !_isListening.value && !isRecognizerSessionActive) {
                if (_silentListeningEnabled.value && isRecognitionSupported()) {
                    engageSilentListeningMute()
                }
                scheduleAutoResumeListening(delayMs = 260L)
            }
        } else if (!_isSpeaking.value && !_isListening.value && !isRecognizerSessionActive) {
            scheduleReleaseSilentListeningMute(delayMs = RECOGNIZER_BEEP_TAIL_GUARD_MS)
        }
    }

    private fun scheduleAutoResumeListening(delayMs: Long, resetRecognizerInstance: Boolean = false) {
        if (!isContinuousListeningActive()) return
        autoRestartJob?.cancel()
        autoRestartJob = audioScope.launch {
            delay(delayMs)
            if (!isActive || !isContinuousListeningActive() || _isSpeaking.value || isAiThinking) {
                return@launch
            }
            runOnMain {
                if (!isContinuousListeningActive() || _isSpeaking.value || isAiThinking) {
                    return@runOnMain
                }
                if (!resetRecognizerInstance && _isListening.value && isRecognizerSessionActive) {
                    return@runOnMain
                }
                if (resetRecognizerInstance) {
                    isRecognizerSessionActive = false
                    runCatching { speechRecognizer?.destroy() }
                    speechRecognizer = null
                }
                val cb = onFinalSpeechResult ?: return@runOnMain
                startListeningInternal(cb, onSpeechErrorCallback ?: {}, isAutoRestart = true)
            }
        }
    }

    fun setWakeWordLoop(enabled: Boolean) {
        _wakeWordModeEnabled.value = enabled
        if (!enabled && !_permanentBrainEnabled.value) {
            stopListening()
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        runOnMain {
            startListeningInternal(onResult, onError, isAutoRestart = false)
        }
    }

    private fun startListeningInternal(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        isAutoRestart: Boolean
    ) {
        if (!isAutoRestart && _isSpeaking.value) {
            stopSpeaking()
        }
        onFinalSpeechResult = onResult
        onSpeechErrorCallback = onError

        if (!isRecognitionSupported()) {
            if (_permanentBrainEnabled.value) {
                _brainLoopPhase.value = BrainLoopPhase.LISTENING
                _isListening.value = true
                if (!isAutoRestart) {
                    onError("Is device/emulator pe SpeechRecognizer uplabdh nahi hai, Boss — real phone pe Maya bina beep ke lagatar sunegi.")
                }
            } else {
                onError("Is device/emulator pe SpeechRecognizer service uplabdh nahi hai, Boss — aap type karke command de sakte hain.")
            }
            return
        }

        // Mute system/media beep streams BEFORE SpeechRecognizer initializes & starts listening
        engageSilentListeningMute()

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (_permanentBrainEnabled.value || _wakeWordModeEnabled.value) {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1450L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1150L)
                }
            }
            _partialTranscript.value = ""
            _isListening.value = true
            isRecognizerSessionActive = true
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                _brainLoopPhase.value = BrainLoopPhase.LISTENING
            }
            updateSpectrumFromNormalizedEnergy(0.18f)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            isRecognizerSessionActive = false
            _isListening.value = false
            resetSpectrumIdle()
            if (isContinuousListeningActive()) {
                if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                    _brainLoopPhase.value = BrainLoopPhase.RECONNECTING
                }
                scheduleAutoResumeListening(delayMs = 1200L, resetRecognizerInstance = true)
            } else {
                scheduleReleaseSilentListeningMute(delayMs = 250L)
                onError("Mic start nahi ho paya: ${e.localizedMessage ?: "unknown error"}")
            }
        }
    }

    fun stopListening() {
        autoRestartJob?.cancel()
        autoRestartJob = null
        val wasActive = isRecognizerSessionActive
        if (wasActive) {
            if (_silentListeningEnabled.value && isRecognitionSupported()) {
                engageSilentListeningMute()
            }
            isRecognizerSessionActive = false
            lastRecognizerToneEventAtMs = SystemClock.uptimeMillis()
            runOnMain {
                // Only call cancel() (NOT stopListening() + cancel(), which caused a double end/cancel beep)
                runCatching { speechRecognizer?.cancel() }
            }
        }
        _isListening.value = false
        _partialTranscript.value = ""
        if (!_isSpeaking.value) {
            resetSpectrumIdle()
            scheduleReleaseSilentListeningMute(
                delayMs = if (wasActive) STOP_RECOGNIZER_BEEP_GUARD_MS else 80L
            )
        }
    }

    fun shutdown() {
        try {
            autoRestartJob?.cancel()
            pendingDelayedSpeakJob?.cancel()
            ttsWatchdogJob?.cancel()
            ttsWaveJob?.cancel()
            releaseMuteTailJob?.cancel()
            isRecognizerSessionActive = false
            audioScope.cancel()
            runOnMain {
                runCatching { speechRecognizer?.destroy() }
                speechRecognizer = null
            }
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        } finally {
            releaseAllSilentListeningMutesImmediate()
        }
    }

    internal fun deliverTranscriptForTest(rawSpeech: String) {
        handleRecognizedText(rawSpeech)
    }

    internal fun finishSpeakingForTest() {
        onTtsUtteranceFinished()
    }

    private fun handleRecognizedText(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            if (isContinuousListeningActive()) {
                scheduleAutoResumeListening(delayMs = 220L)
            } else {
                scheduleReleaseSilentListeningMute(delayMs = STOP_RECOGNIZER_BEEP_GUARD_MS)
            }
            return
        }

        val wakePrefixes = listOf("hey maya", "hello maya", "wake up maya")
        val lower = trimmed.lowercase(Locale.ROOT)
        val matchedPrefix = wakePrefixes.firstOrNull { lower.contains(it) }

        if (_permanentBrainEnabled.value) {
            // Human Brain Mode: No wake-word required! Strip optional wake prefix if Boss happened to say it.
            val naturalInput = if (matchedPrefix != null) {
                val startIdx = lower.indexOf(matchedPrefix)
                trimmed.substring(startIdx + matchedPrefix.length).trim().ifBlank { "Haan Maya, suno" }
            } else {
                trimmed
            }

            // Filter 1-character accidental throat/click noise bursts
            if (naturalInput.length < 2) {
                scheduleAutoResumeListening(delayMs = 220L)
                return
            }

            isAiThinking = true
            _brainLoopPhase.value = BrainLoopPhase.THINKING
            onFinalSpeechResult?.invoke(naturalInput)
            return
        }

        if (_wakeWordModeEnabled.value) {
            if (matchedPrefix != null) {
                val stripped = trimmed.substringAfter(
                    trimmed.substring(
                        lower.indexOf(matchedPrefix),
                        lower.indexOf(matchedPrefix) + matchedPrefix.length
                    )
                ).trim().ifBlank { "Haan Maya, suno" }
                isAiThinking = true
                onFinalSpeechResult?.invoke(stripped)
            } else {
                scheduleAutoResumeListening(delayMs = 220L)
            }
        } else {
            onFinalSpeechResult?.invoke(trimmed)
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                _brainLoopPhase.value = BrainLoopPhase.LISTENING
            }
        }

        override fun onBeginningOfSpeech() {
            updateSpectrumFromNormalizedEnergy(0.32f)
        }

        override fun onRmsChanged(rmsdB: Float) {
            onMicRmsUpdated(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            lastRecognizerToneEventAtMs = SystemClock.uptimeMillis()
            _isListening.value = false
            resetSpectrumIdle()
        }

        override fun onError(error: Int) {
            isRecognizerSessionActive = false
            lastRecognizerToneEventAtMs = SystemClock.uptimeMillis()
            _isListening.value = false
            _partialTranscript.value = ""
            resetSpectrumIdle()

            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    _permanentBrainEnabled.value = false
                    _brainLoopPhase.value = BrainLoopPhase.OFF
                    scheduleReleaseSilentListeningMute(delayMs = STOP_RECOGNIZER_BEEP_GUARD_MS)
                    onSpeechErrorCallback?.invoke("Permanent listening ke liye Mic permission chahiye, Boss.")
                    return
                }
                // Quietly auto-recover in Human Brain Mode while streams stay continuously muted (zero beep!)
                val needsReset = error !in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )
                if (needsReset) {
                    _brainLoopPhase.value = BrainLoopPhase.RECONNECTING
                }
                val backoffMs = if (needsReset) 1100L else 300L
                scheduleAutoResumeListening(delayMs = backoffMs, resetRecognizerInstance = needsReset)
                return
            }

            if (_wakeWordModeEnabled.value && error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )
            ) {
                scheduleAutoResumeListening(delayMs = 280L)
                return
            }

            // Single-shot mode error: keep streams muted past the error chime tail, then restore cleanly
            scheduleReleaseSilentListeningMute(delayMs = STOP_RECOGNIZER_BEEP_GUARD_MS)

            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Awaaz saaf sunai nahi di, Boss. Dobara boliye?"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Koi awaaz nahi aayi, Boss."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission chahiye, Boss."
                else -> "Voice recognition ruk gaya (code $error)."
            }
            onSpeechErrorCallback?.invoke(msg)
        }

        override fun onResults(results: Bundle?) {
            isRecognizerSessionActive = false
            lastRecognizerToneEventAtMs = SystemClock.uptimeMillis()
            _isListening.value = false
            _partialTranscript.value = ""
            resetSpectrumIdle()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val rawText = matches?.firstOrNull()?.trim().orEmpty()
            handleRecognizedText(rawText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val part = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _partialTranscript.value = part
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        @Volatile
        private var sharedInstance: MayaVoiceEngine? = null

        fun getSharedInstance(context: Context): MayaVoiceEngine {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: MayaVoiceEngine(context.applicationContext).also {
                    sharedInstance = it
                }
            }
        }

        fun peekSharedInstance(): MayaVoiceEngine? = sharedInstance
    }
}
