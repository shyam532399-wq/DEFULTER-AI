package com.example.domain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.MayaPersona
import com.example.data.model.MayaTone
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MayaVoiceEngine"

class MayaVoiceEngine(context: Context) {

    private val appContext = context.applicationContext
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

    private var onFinalSpeechResult: ((String) -> Unit)? = null
    private var onSpeechErrorCallback: ((String) -> Unit)? = null

    init {
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
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                ttsReady = true
            }
        }
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

    fun speak(
        text: String,
        persona: MayaPersona,
        tone: MayaTone,
        voiceEnabled: Boolean = true
    ) {
        if (!voiceEnabled || !ttsReady) return
        val clean = sanitizeForVoice(text)
        if (clean.isBlank()) return

        val pitch = (persona.pitch + tone.pitchDelta).coerceIn(0.55f, 1.45f)
        val rate = (persona.speechRate + tone.rateDelta).coerceIn(0.75f, 1.40f)
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "maya_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun setWakeWordLoop(enabled: Boolean) {
        _wakeWordModeEnabled.value = enabled
        if (!enabled) {
            stopListening()
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Is device/emulator pe SpeechRecognizer service uplabdh nahi hai, Boss — aap type karke command de sakte hain.")
            return
        }
        stopSpeaking()
        onFinalSpeechResult = onResult
        onSpeechErrorCallback = onError

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
            }
            _partialTranscript.value = ""
            _isListening.value = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _isListening.value = false
            onError("Mic start nahi ho paya: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _isListening.value = false
        _partialTranscript.value = ""
    }

    fun shutdown() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _partialTranscript.value = ""
            if (_wakeWordModeEnabled.value && error in listOf(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )
            ) {
                // Restart passive listening for wake words ("Hey Maya", "Hello Maya", "Wake up Maya")
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
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val rawText = matches?.firstOrNull()?.trim().orEmpty()
            if (rawText.isNotBlank()) {
                if (_wakeWordModeEnabled.value) {
                    val lower = rawText.lowercase(Locale.ROOT)
                    val wakePrefixes = listOf("hey maya", "hello maya", "wake up maya")
                    val matchedPrefix = wakePrefixes.firstOrNull { lower.contains(it) }
                    if (matchedPrefix != null) {
                        val stripped = rawText.substringAfter(
                            rawText.substring(
                                lower.indexOf(matchedPrefix),
                                lower.indexOf(matchedPrefix) + matchedPrefix.length
                            )
                        ).trim().ifBlank { "Haan Maya, suno" }
                        onFinalSpeechResult?.invoke(stripped)
                    } else {
                        // Continue waiting for wake word
                        onFinalSpeechResult?.let { cb ->
                            startListening(cb, onSpeechErrorCallback ?: {})
                        }
                    }
                } else {
                    onFinalSpeechResult?.invoke(rawText)
                }
            }
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
}
