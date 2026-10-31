package com.example.domain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

enum class BrainLoopPhase(val statusHinglish: String) {
    OFF("Brain Mode Off"),
    LISTENING("Sun rahi hoon, Boss…"),
    THINKING("Soch rahi hoon…"),
    SPEAKING("Jawab de rahi hoon…"),
    PAUSED("Brain Paused"),
    RECONNECTING("Mic reconnect ho raha hai…")
}

class MayaVoiceEngine(
    context: Context,
    private val recognitionAvailableOverride: Boolean? = null
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var ttsWaveJob: Job? = null
    private var autoRestartJob: Job? = null
    private var ttsWatchdogJob: Job? = null

    @Volatile
    private var isAiThinking = false

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
     * Normalized 0f..1f audio energy & 24-band spectrum mirroring `maya-ui` (`audio_worker.py` + `mic_visualizer.py`),
     * reacting to real microphone RMS dB during speech recognition and TTS output cadence when Maya speaks.
     */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _spectrumBands = MutableStateFlow(List(SPECTRUM_BAND_COUNT) { 0.08f })
    val spectrumBands: StateFlow<List<Float>> = _spectrumBands.asStateFlow()

    private var phaseTick: Double = 0.0

    private var onFinalSpeechResult: ((String) -> Unit)? = null
    private var onSpeechErrorCallback: ((String) -> Unit)? = null

    init {
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
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = null
        _isSpeaking.value = false
        stopTtsOutputSpectrum()
        if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
            // Short 380ms acoustic tail guard so room echo of Maya's own voice fades before mic reopens
            scheduleAutoResumeListening(delayMs = 380L)
        }
    }

    private fun startTtsOutputSpectrum() {
        ttsWaveJob?.cancel()
        ttsWaveJob = audioScope.launch {
            var step = 0
            while (isActive && _isSpeaking.value) {
                val syllablePulse = 0.35f + 0.45f * abs(sin(step * 0.42)).toFloat()
                updateSpectrumFromNormalizedEnergy(syllablePulse)
                step++
                delay(55L)
            }
            resetSpectrumIdle()
        }
    }

    private fun stopTtsOutputSpectrum() {
        ttsWaveJob?.cancel()
        ttsWaveJob = null
        if (!_isListening.value) {
            resetSpectrumIdle()
        }
    }

    internal fun onMicRmsUpdated(rmsdB: Float) {
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
     * Speaks Maya's reply with Self-Echo Guard:
     * Automatically pauses microphone recognition while speaking and resumes listening when finished.
     */
    fun speak(
        text: String,
        persona: MayaPersona,
        tone: MayaTone,
        voiceEnabled: Boolean = true
    ): Boolean {
        isAiThinking = false
        val clean = sanitizeForVoice(text)
        if (!voiceEnabled || clean.isBlank()) {
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                scheduleAutoResumeListening(delayMs = 240L)
            }
            return false
        }

        // Self-Echo Prevention: Pause mic BEFORE Maya's voice comes out of speaker
        pauseMicForSpeechOutput()

        if (!ttsReady || tts == null) {
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                scheduleAutoResumeListening(delayMs = 300L)
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

        val result = runCatching {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ?: TextToSpeech.ERROR
        }.getOrDefault(TextToSpeech.ERROR)

        if (result == TextToSpeech.ERROR) {
            onTtsUtteranceFinished()
            return false
        }

        // Safety watchdog in case OEM/headless TTS engine drops onDone callback
        val estimatedMaxMs = (clean.length * 90L + 2600L).coerceIn(3000L, 24000L)
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = audioScope.launch {
            delay(estimatedMaxMs)
            if (_isSpeaking.value) {
                onTtsUtteranceFinished()
            }
        }
        return true
    }

    fun stopSpeaking() {
        ttsWatchdogJob?.cancel()
        ttsWatchdogJob = null
        runCatching { tts?.stop() }
        _isSpeaking.value = false
        stopTtsOutputSpectrum()
        if (_permanentBrainEnabled.value && !_permanentBrainPaused.value && !_isListening.value) {
            scheduleAutoResumeListening(delayMs = 280L)
        }
    }

    private fun pauseMicForSpeechOutput() {
        autoRestartJob?.cancel()
        autoRestartJob = null
        runOnMain {
            runCatching { speechRecognizer?.cancel() }
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
                val cb = onFinalSpeechResult
                if (cb != null) {
                    startListening(cb, onSpeechErrorCallback ?: {})
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
        if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
            isAiThinking = true
            pauseMicForSpeechOutput()
            _brainLoopPhase.value = BrainLoopPhase.THINKING
        }
    }

    fun notifyTurnCompletedWithoutSpeech() {
        isAiThinking = false
        if (_permanentBrainEnabled.value && !_permanentBrainPaused.value && !_isSpeaking.value) {
            scheduleAutoResumeListening(delayMs = 260L)
        }
    }

    private fun scheduleAutoResumeListening(delayMs: Long, resetRecognizerInstance: Boolean = false) {
        if (!_permanentBrainEnabled.value || _permanentBrainPaused.value) return
        autoRestartJob?.cancel()
        autoRestartJob = audioScope.launch {
            delay(delayMs)
            if (!isActive || !_permanentBrainEnabled.value || _permanentBrainPaused.value || _isSpeaking.value) {
                return@launch
            }
            if (isAiThinking) {
                return@launch
            }
            runOnMain {
                if (!_permanentBrainEnabled.value || _permanentBrainPaused.value || _isSpeaking.value || isAiThinking) {
                    return@runOnMain
                }
                if (resetRecognizerInstance) {
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
        if (!isAutoRestart) {
            stopSpeaking()
        }
        onFinalSpeechResult = onResult
        onSpeechErrorCallback = onError

        if (!isRecognitionSupported()) {
            if (_permanentBrainEnabled.value) {
                _brainLoopPhase.value = BrainLoopPhase.LISTENING
                _isListening.value = true
                if (!isAutoRestart) {
                    onError("Is device/emulator pe SpeechRecognizer uplabdh nahi hai, Boss — real phone pe Maya lagatar sunegi.")
                }
            } else {
                onError("Is device/emulator pe SpeechRecognizer service uplabdh nahi hai, Boss — aap type karke command de sakte hain.")
            }
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                if (_permanentBrainEnabled.value) {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1450L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1150L)
                }
            }
            _partialTranscript.value = ""
            _isListening.value = true
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                _brainLoopPhase.value = BrainLoopPhase.LISTENING
            }
            updateSpectrumFromNormalizedEnergy(0.18f)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _isListening.value = false
            resetSpectrumIdle()
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                _brainLoopPhase.value = BrainLoopPhase.RECONNECTING
                scheduleAutoResumeListening(delayMs = 1200L, resetRecognizerInstance = true)
            } else {
                onError("Mic start nahi ho paya: ${e.localizedMessage ?: "unknown error"}")
            }
        }
    }

    fun stopListening() {
        autoRestartJob?.cancel()
        autoRestartJob = null
        runOnMain {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
        }
        _isListening.value = false
        _partialTranscript.value = ""
        if (!_isSpeaking.value) {
            resetSpectrumIdle()
        }
    }

    fun shutdown() {
        try {
            autoRestartJob?.cancel()
            ttsWatchdogJob?.cancel()
            ttsWaveJob?.cancel()
            audioScope.cancel()
            runOnMain {
                runCatching { speechRecognizer?.destroy() }
                speechRecognizer = null
            }
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
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
            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                scheduleAutoResumeListening(delayMs = 220L)
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
                onFinalSpeechResult?.invoke(stripped)
            } else {
                onFinalSpeechResult?.let { cb ->
                    startListening(cb, onSpeechErrorCallback ?: {})
                }
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
            _isListening.value = false
            resetSpectrumIdle()
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _partialTranscript.value = ""
            resetSpectrumIdle()

            if (_permanentBrainEnabled.value && !_permanentBrainPaused.value) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    _permanentBrainEnabled.value = false
                    _brainLoopPhase.value = BrainLoopPhase.OFF
                    onSpeechErrorCallback?.invoke("Permanent listening ke liye Mic permission chahiye, Boss.")
                    return
                }
                // Quietly auto-recover in Human Brain Mode without spamming chat on natural silence
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
                onFinalSpeechResult?.let { cb ->
                    startListening(cb, onSpeechErrorCallback ?: {})
                }
                return
            }

            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "Awaaz saaf sunai nahi di, Boss. Dobara boliye?"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Koi awaaz nahi aayi, Boss."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission chahiye, Boss."
                else -> "Voice recognition ruk gaya (code $error)."
            }
            onSpeechErrorCallback?.invoke(msg)
        }

        override fun onResults(results: Bundle?) {
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
