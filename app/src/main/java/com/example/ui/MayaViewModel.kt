package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.BoardDocument
import com.example.data.model.DataUiState
import com.example.data.model.ListenMode
import com.example.data.model.MayaPersona
import com.example.data.model.MayaTone
import com.example.data.model.MemoryItem
import com.example.data.model.ReminderItem
import com.example.data.model.SkillPlaybook
import com.example.data.model.UserProfile
import com.example.data.repository.MayaRepository
import com.example.domain.AgentDecision
import com.example.domain.ApiKeyConfigStore
import com.example.domain.ApiKeyConnectionStatus
import com.example.domain.ApiProvider
import com.example.domain.BrainLoopPhase
import com.example.domain.DeviceTelemetry
import com.example.domain.GitHubRemoteSkill
import com.example.domain.GitHubSkillStoreClient
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.domain.HomeWeatherSnapshot
import com.example.domain.HudEffectTheme
import com.example.domain.HudVisualConfig
import com.example.domain.LiveInfoClient
import com.example.domain.LiveInfoResult
import com.example.domain.MayaAccessibilityService
import com.example.domain.MayaAiBrain
import com.example.domain.MayaBrainBus
import com.example.domain.MayaPermanentBrainService
import com.example.domain.MayaVoiceEngine
import com.example.domain.PhoneActionExecutor
import com.example.domain.SystemHudMonitor
import com.example.domain.SystemHudSnapshot
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "MayaViewModel"
private const val STOP_TIMEOUT_MS = 5000L

data class ChatBubble(
    val id: String = UUID.randomUUID().toString(),
    val isFromBoss: Boolean,
    val text: String,
    val badge: String? = null,
    val linkedBoardTitle: String? = null,
    val attachedBitmap: Bitmap? = null,
    val isError: Boolean = false
)

/**
 * Golden Rule 5: Risky actions require explicit confirmation (`confirmed = true`) before execution.
 */
data class PendingConfirmation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val previewDetails: String,
    val promptSpoken: String,
    val onConfirmExecute: suspend () -> String
)

/**
 * Golden Rule 4: Multi-step missions (2–8 steps) with undo_last_step and cancel_mission.
 */
data class MissionState(
    val title: String,
    val steps: List<String>,
    val currentStepIndex: Int = 0,
    val isCancelled: Boolean = false
)

sealed interface ImageStudioState {
    data object Idle : ImageStudioState
    data object Generating : ImageStudioState
    data class Ready(val prompt: String, val bitmap: Bitmap, val savedMessage: String? = null) : ImageStudioState
    data class Error(val message: String) : ImageStudioState
}

sealed interface BusinessToolState {
    data object Idle : BusinessToolState
    data object Loading : BusinessToolState
    data class Success(val mode: String, val output: String, val savedToBoard: Boolean) : BusinessToolState
    data class Error(val message: String) : BusinessToolState
}

sealed interface GitHubSkillStoreState {
    data object Idle : GitHubSkillStoreState
    data class Loading(val repoQuery: String) : GitHubSkillStoreState
    data class Loaded(val repoSlug: String, val skills: List<GitHubRemoteSkill>) : GitHubSkillStoreState
    data class Error(val message: String) : GitHubSkillStoreState
}

class MayaViewModel(
    private val repository: MayaRepository,
    private val phoneExecutor: PhoneActionExecutor,
    private val voiceEngine: MayaVoiceEngine,
    private val aiBrain: MayaAiBrain,
    private val liveInfoClient: LiveInfoClient,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val systemHudMonitor: SystemHudMonitor = SystemHudMonitor(phoneExecutor.appContext),
    private val gitHubSkillStoreClient: GitHubSkillStoreClient = GitHubSkillStoreClient(),
    private val apiKeyStore: ApiKeyConfigStore = ApiKeyConfigStore(phoneExecutor.appContext)
) : ViewModel() {

    val profileState: StateFlow<DataUiState<UserProfile>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, DataUiState<UserProfile>> { profile ->
            DataUiState.Success(profile ?: UserProfile(userId = currentUserId, bossName = initialDisplayName))
        }
        .catch { err ->
            Log.w(TAG, "Profile stream error", err)
            emit(DataUiState.Error(err.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataUiState.Loading)

    val documentsState: StateFlow<DataUiState<List<BoardDocument>>> = repository.observeDocuments(currentUserId)
        .map<List<BoardDocument>, DataUiState<List<BoardDocument>>> { DataUiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Documents stream error", err)
            emit(DataUiState.Error(err.localizedMessage ?: "Failed to load Maya Board"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataUiState.Loading)

    val memoriesState: StateFlow<DataUiState<List<MemoryItem>>> = repository.observeMemories(currentUserId)
        .map<List<MemoryItem>, DataUiState<List<MemoryItem>>> { DataUiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Memories stream error", err)
            emit(DataUiState.Error(err.localizedMessage ?: "Failed to load memories"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataUiState.Loading)

    val skillsState: StateFlow<DataUiState<List<SkillPlaybook>>> = repository.observeSkills(currentUserId)
        .map<List<SkillPlaybook>, DataUiState<List<SkillPlaybook>>> { DataUiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Skills stream error", err)
            emit(DataUiState.Error(err.localizedMessage ?: "Failed to load skills"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataUiState.Loading)

    val remindersState: StateFlow<DataUiState<List<ReminderItem>>> = repository.observeReminders(currentUserId)
        .map<List<ReminderItem>, DataUiState<List<ReminderItem>>> { DataUiState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Reminders stream error", err)
            emit(DataUiState.Error(err.localizedMessage ?: "Failed to load reminders"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataUiState.Loading)

    private val _messages = MutableStateFlow(
        listOf(
            ChatBubble(
                isFromBoss = false,
                text = "Haan $initialDisplayName, Maya ready hai. Boliye kya kaam niptayein — torch, alarm, WhatsApp/Telegram, stocks, GitHub check, ya koi research Board pe rakhun?",
                badge = "Maya • Ready"
            )
        )
    )
    val messages: StateFlow<List<ChatBubble>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    val voiceReplyEnabled: StateFlow<Boolean> = voiceEngine.voiceReplyEnabled
    val silentListeningEnabled: StateFlow<Boolean> = voiceEngine.silentListeningEnabled

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _activeMission = MutableStateFlow<MissionState?>(null)
    val activeMission: StateFlow<MissionState?> = _activeMission.asStateFlow()

    private val _telemetry = MutableStateFlow(phoneExecutor.readTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _imageStudioState = MutableStateFlow<ImageStudioState>(ImageStudioState.Idle)
    val imageStudioState: StateFlow<ImageStudioState> = _imageStudioState.asStateFlow()

    private val _businessToolState = MutableStateFlow<BusinessToolState>(BusinessToolState.Idle)
    val businessToolState: StateFlow<BusinessToolState> = _businessToolState.asStateFlow()

    private val _touchGuardAlert = MutableStateFlow<String?>(null)
    val touchGuardAlert: StateFlow<String?> = _touchGuardAlert.asStateFlow()

    val hudSnapshot: StateFlow<SystemHudSnapshot> = systemHudMonitor.observeLiveHud()
        .catch { err -> Log.w(TAG, "System HUD monitor error", err) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            systemHudMonitor.readSnapshot()
        )

    private val _hudConfig = MutableStateFlow(systemHudMonitor.loadVisualConfig())
    val hudConfig: StateFlow<HudVisualConfig> = _hudConfig.asStateFlow()

    private val _gitHubSkillStoreState = MutableStateFlow<GitHubSkillStoreState>(GitHubSkillStoreState.Idle)
    val gitHubSkillStoreState: StateFlow<GitHubSkillStoreState> = _gitHubSkillStoreState.asStateFlow()

    val apiKeyStatus: StateFlow<ApiKeyConnectionStatus> = apiKeyStore.status

    private val _showApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = _showApiKeyDialog.asStateFlow()

    private val setupPrefs = phoneExecutor.appContext.getSharedPreferences("maya_setup_prefs", Context.MODE_PRIVATE)

    private val _setupCompleted = MutableStateFlow(
        setupPrefs.getBoolean("setup_completed", false) ||
            apiKeyStore.status.value is ApiKeyConnectionStatus.Connected
    )
    val setupCompleted: StateFlow<Boolean> = _setupCompleted.asStateFlow()

    private val _showSetupWizard = MutableStateFlow(false)
    val showSetupWizard: StateFlow<Boolean> = _showSetupWizard.asStateFlow()

    private val _setupWizardStep = MutableStateFlow(1)
    val setupWizardStep: StateFlow<Int> = _setupWizardStep.asStateFlow()

    private val _wizardKeyNotice = MutableStateFlow<String?>(null)
    val wizardKeyNotice: StateFlow<String?> = _wizardKeyNotice.asStateFlow()

    private val _homeWeather = MutableStateFlow(HomeWeatherSnapshot())
    val homeWeather: StateFlow<HomeWeatherSnapshot> = _homeWeather.asStateFlow()

    private val _needsBrainMicPermissionEvent = MutableStateFlow(false)
    val needsBrainMicPermissionEvent: StateFlow<Boolean> = _needsBrainMicPermissionEvent.asStateFlow()

    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val wakeWordEnabled: StateFlow<Boolean> = voiceEngine.wakeWordModeEnabled
    val permanentBrainEnabled: StateFlow<Boolean> = voiceEngine.permanentBrainEnabled
    val permanentBrainPaused: StateFlow<Boolean> = voiceEngine.permanentBrainPaused
    val brainLoopPhase: StateFlow<BrainLoopPhase> = voiceEngine.brainLoopPhase
    val audioLevel: StateFlow<Float> = voiceEngine.audioLevel
    val micLevel: StateFlow<Float> = voiceEngine.micLevel
    val speakerAmplitude: StateFlow<Float> = voiceEngine.speakerAmplitude
    val spectrumBands: StateFlow<List<Float>> = voiceEngine.spectrumBands
    val autopilotConnected: StateFlow<Boolean> = MayaAccessibilityService.isConnected

    private val uiBrainTranscriptHandler: (String) -> Unit = { spoken ->
        handleVoiceTranscript(spoken)
    }

    init {
        MayaBrainBus.attachUiDelegate(
            onTranscript = uiBrainTranscriptHandler,
            onStoppedFromNotification = {
                appendMayaBubble(
                    "Notification se Permanent Background Listening band kar diya gaya, ${currentProfile().bossName}.",
                    badge = "Brain Mode OFF",
                    speakOut = false
                )
            },
            onBackgroundTurn = { turn ->
                _messages.value = _messages.value + listOf(
                    ChatBubble(isFromBoss = true, text = turn.bossSpeech),
                    ChatBubble(isFromBoss = false, text = turn.mayaReply, badge = turn.badge)
                )
            }
        )
        if (voiceEngine.permanentBrainEnabled.value) {
            voiceEngine.bindBrainCallbacks(
                onTranscript = uiBrainTranscriptHandler,
                onError = { errMsg ->
                    appendMayaBubble(errMsg, badge = "Brain Mic", isError = false, speakOut = false)
                }
            )
        }
        viewModelScope.launch {
            repository.ensureDefaultProfile(currentUserId, initialDisplayName)
            refreshTelemetry()
        }
    }

    private fun currentProfile(): UserProfile {
        return (profileState.value as? DataUiState.Success)?.data
            ?: UserProfile(userId = currentUserId, bossName = initialDisplayName)
    }

    fun refreshTelemetry() {
        _telemetry.value = phoneExecutor.readTelemetry()
    }

    fun openAccessibilityAutopilotSettings() {
        val outcome = phoneExecutor.openAccessibilitySettings()
        appendMayaBubble(outcome.messageHinglish, badge = "Autopilot Settings")
    }

    fun openApiKeyDialog() {
        _showApiKeyDialog.value = true
    }

    fun dismissApiKeyDialog() {
        _showApiKeyDialog.value = false
    }

    fun readClipboardForApiKey(): String = phoneExecutor.readClipboard()

    fun openGeminiApiKeyPortal() {
        phoneExecutor.openUrl("https://aistudio.google.com/apikey")
    }

    fun connectApiKey(rawKey: String, provider: ApiProvider, customModel: String = "") {
        viewModelScope.launch {
            val res = apiKeyStore.verifyAndConnect(
                rawKey = rawKey,
                selectedProvider = provider,
                customModel = customModel
            )
            res.onSuccess { connected ->
                _showApiKeyDialog.value = false
                appendMayaBubble(
                    "${connected.provider.displayName} API key connect ho gayi ${currentProfile().bossName} (Model: ${connected.activeModel}). Ab aapki key se kaam chalega.",
                    badge = "API Connected • ${connected.provider.shortBadge}"
                )
            }
        }
    }

    fun disconnectApiKey() {
        apiKeyStore.disconnect()
        appendMayaBubble(
            "Custom API key disconnect kar di hai, ${currentProfile().bossName}. Built-in mode active hai.",
            badge = "Built-in Mode"
        )
    }

    fun testConnectedApiKey() {
        _showApiKeyDialog.value = false
        sendCommand("Maya, ek chhoti line me confirm karo ki meri API key live kaam kar rahi hai.")
    }

    fun openSetupWizard(step: Int = 1) {
        _wizardKeyNotice.value = null
        _setupWizardStep.value = step.coerceIn(1, 3)
        _showSetupWizard.value = true
    }

    fun setSetupWizardStep(step: Int) {
        _wizardKeyNotice.value = null
        _setupWizardStep.value = step.coerceIn(1, 3)
    }

    fun skipApiKeyStepInWizard() {
        _wizardKeyNotice.value = null
        _setupWizardStep.value = 2
    }

    fun completeSetupWizard() {
        setupPrefs.edit().putBoolean("setup_completed", true).apply()
        _setupCompleted.value = true
        _showSetupWizard.value = false
        refreshTelemetry()
        refreshHomeWeather(force = true)
    }

    fun connectApiKeyFromWizard(rawKey: String, testOnly: Boolean) {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) {
            if (!testOnly && apiKeyStatus.value is ApiKeyConnectionStatus.Connected) {
                _setupWizardStep.value = 2
                return
            }
            _wizardKeyNotice.value = "Please paste your Gemini API key (starts with AQ. or AIza)."
            return
        }
        val detected = ApiProvider.detectFromKey(trimmed, ApiProvider.GEMINI) ?: ApiProvider.GEMINI
        _wizardKeyNotice.value = null
        viewModelScope.launch {
            val res = apiKeyStore.verifyAndConnect(
                rawKey = trimmed,
                selectedProvider = detected
            )
            res.fold(
                onSuccess = { connected ->
                    if (testOnly) {
                        _wizardKeyNotice.value = "✓ Key verified (${connected.activeModel}) — ready to save & continue."
                    } else {
                        _wizardKeyNotice.value = null
                        _setupWizardStep.value = 2
                    }
                },
                onFailure = { err ->
                    _wizardKeyNotice.value = err.localizedMessage ?: "Could not verify key. Check your internet and key."
                }
            )
        }
    }

    fun refreshHomeWeather(force: Boolean = false) {
        if (!force && _homeWeather.value.isLoaded) return
        viewModelScope.launch {
            val ctx = phoneExecutor.appContext
            val hasCoarse = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasFine = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            var lat: Double? = null
            var lon: Double? = null
            if (hasCoarse || hasFine) {
                runCatching {
                    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val loc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                    }
                }
            }
            val snap = liveInfoClient.fetchHomeWeatherCard(latitude = lat, longitude = lon)
            if (snap.isLoaded) {
                _homeWeather.value = snap
            }
        }
    }

    fun cycleMoodTone() {
        val current = MayaTone.fromId(currentProfile().tone)
        val next = when (current) {
            MayaTone.CALM -> MayaTone.EXCITED
            MayaTone.EXCITED -> MayaTone.SOFT
            MayaTone.SOFT -> MayaTone.CALM
        }
        switchTone(next)
    }

    fun toggleVoiceReply() {
        voiceEngine.setVoiceReplyEnabled(!voiceReplyEnabled.value)
    }

    fun setVoiceReplyEnabled(enabled: Boolean) {
        voiceEngine.setVoiceReplyEnabled(enabled)
    }

    fun setSilentListeningEnabled(enabled: Boolean) {
        voiceEngine.setSilentListeningEnabled(enabled)
    }

    fun toggleSilentListening() {
        voiceEngine.setSilentListeningEnabled(!silentListeningEnabled.value)
    }

    fun clearBrainPermissionRequest() {
        _needsBrainMicPermissionEvent.value = false
    }

    fun setPermanentBrainMode(
        enabled: Boolean,
        hasAudioPermission: Boolean = MayaPermanentBrainService.hasRecordAudioPermission(phoneExecutor.appContext),
        onNeedPermission: (() -> Unit)? = null
    ) {
        if (enabled) {
            if (!hasAudioPermission) {
                if (onNeedPermission != null) {
                    onNeedPermission()
                } else {
                    _needsBrainMicPermissionEvent.value = true
                    appendMayaBubble(
                        "Boss, Permanent Background Listening chalu karne ke liye Mic permission Allow kar dijiye.",
                        badge = "Mic Permission Needed",
                        speakOut = false
                    )
                }
                return
            }
            _needsBrainMicPermissionEvent.value = false
            voiceEngine.setPermanentBrainLoop(
                enabled = true,
                onTranscript = uiBrainTranscriptHandler,
                onError = { errMsg ->
                    appendMayaBubble(errMsg, badge = "Brain Mic", isError = false, speakOut = false)
                }
            )
            MayaPermanentBrainService.start(phoneExecutor.appContext)
            appendMayaBubble(
                "Silent Human Brain Listening chalu ho gaya hai, ${currentProfile().bossName}! Bina kisi beep sound ke background me hamesha chupchap sunungi — seedha boliye.",
                badge = "Silent Brain LIVE",
                speakOut = false
            )
        } else {
            _needsBrainMicPermissionEvent.value = false
            voiceEngine.setPermanentBrainLoop(false)
            MayaPermanentBrainService.stop(phoneExecutor.appContext)
            appendMayaBubble(
                "Permanent background listening band kar diya hai, ${currentProfile().bossName}.",
                badge = "Brain Mode OFF",
                speakOut = false
            )
        }
    }

    fun togglePermanentBrainMode(
        hasAudioPermission: Boolean = MayaPermanentBrainService.hasRecordAudioPermission(phoneExecutor.appContext),
        onNeedPermission: (() -> Unit)? = null
    ) {
        setPermanentBrainMode(
            enabled = !permanentBrainEnabled.value,
            hasAudioPermission = hasAudioPermission,
            onNeedPermission = onNeedPermission
        )
    }

    fun togglePermanentBrainPause() {
        if (!permanentBrainEnabled.value) return
        val nextPaused = !permanentBrainPaused.value
        voiceEngine.setPermanentBrainPaused(nextPaused)
        if (MayaBrainBus.serviceRunning.value) {
            MayaPermanentBrainService.togglePause(phoneExecutor.appContext)
        }
    }

    fun simulatePermanentBrainSpeechForTest(spokenText: String) {
        voiceEngine.deliverTranscriptForTest(spokenText)
    }

    internal fun finishSpeakingForTest() {
        voiceEngine.finishSpeakingForTest()
    }

    fun toggleWakeWordMode(hasAudioPermission: Boolean, onNeedPermission: () -> Unit) {
        if (!hasAudioPermission) {
            onNeedPermission()
            return
        }
        val next = !wakeWordEnabled.value
        voiceEngine.setWakeWordLoop(next)
        if (next) {
            startVoiceInput()
        }
    }

    fun startVoiceInput() {
        voiceEngine.startListening(
            onResult = { spokenText -> handleVoiceTranscript(spokenText) },
            onError = { errMsg ->
                appendMayaBubble(errMsg, badge = "Mic", isError = false, speakOut = false)
            }
        )
    }

    fun stopVoiceInput() {
        if (permanentBrainEnabled.value) {
            voiceEngine.setPermanentBrainPaused(true)
        } else {
            voiceEngine.stopListening()
        }
    }

    private fun handleVoiceTranscript(transcript: String) {
        val prof = currentProfile()
        if (prof.listenMode == ListenMode.BOSS_ONLY.id && prof.guardArmed && prof.voiceEnrolled && prof.voicePassphrase.isNotBlank()) {
            val expected = prof.voicePassphrase.lowercase(Locale.ROOT)
            if (!transcript.lowercase(Locale.ROOT).contains(expected) &&
                !transcript.lowercase(Locale.ROOT).contains(prof.bossName.lowercase(Locale.ROOT))
            ) {
                val warn = "Voice Guardian active hai ('Sirf meri suno'). Passphrase '${prof.voicePassphrase}' bolkar verify karein, Boss."
                appendMayaBubble(warn, badge = "Voice Guardian Blocked", isError = true)
                return
            }
        }
        sendCommand(transcript)
    }

    fun sendCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return

        _messages.value = _messages.value + ChatBubble(isFromBoss = true, text = trimmed)
        val lowerCmd = trimmed.lowercase(Locale.ROOT)

        // 1. Check if Boss is answering "haan" / "no" to a PendingConfirmation (Golden Rule 5)
        val pending = _pendingConfirmation.value
        if (pending != null) {
            if (lowerCmd in setOf("haan", "ha", "yes", "kar do", "bhej do", "bhej de", "save kar do", "delete kar do", "ok", "confirm", "haan kar do")) {
                confirmPendingAction()
                return
            } else if (lowerCmd in setOf("nahi", "na", "no", "cancel", "rehne do", "mat bhejo", "ruko", "mat karo")) {
                cancelPendingAction()
                return
            }
        }

        // 2. Mission Undo / Cancel voice & text shortcuts (Golden Rule 4)
        if (_activeMission.value != null) {
            if (lowerCmd in setOf("undo", "undo step", "undo last step", "pichhla step wapas")) {
                undoLastMissionStep()
                return
            }
            if (lowerCmd in setOf("cancel mission", "mission roko", "mission band karo")) {
                cancelMission()
                return
            }
        }

        // 3. Listen mode shortcuts (Section 1)
        if (lowerCmd.contains("sirf meri suno")) {
            setListenMode(ListenMode.BOSS_ONLY)
            appendMayaBubble("Done ${currentProfile().bossName}, ab sirf aapki awaaz sunungi.", badge = "Listen: Boss Only")
            return
        }
        if (lowerCmd.contains("sabki suno")) {
            setListenMode(ListenMode.EVERYONE)
            appendMayaBubble("Theek hai ${currentProfile().bossName}, ab sabki awaaz sun rahi hoon.", badge = "Listen: Everyone")
            return
        }

        // 4. Instant Maya AI HUD shortcuts (HunterisLive-1/maya-ui)
        if (lowerCmd in setOf("floating hud on", "floating hud on karo", "float hud on", "floating hud dikhao")) {
            setFloatingHudVisible(true)
            appendMayaBubble("Floating Mini-HUD screen pe on kar diya hai ${currentProfile().bossName} — kahin bhi drag kar lijiye.", badge = "Floating HUD ON")
            return
        }
        if (lowerCmd in setOf("floating hud off", "floating hud band", "floating hud band karo", "float hud off")) {
            setFloatingHudVisible(false)
            appendMayaBubble("Floating Mini-HUD band kar diya, ${currentProfile().bossName}.", badge = "Floating HUD OFF")
            return
        }
        if (lowerCmd in setOf("quantum hud", "quantum mode", "quantum mode on karo", "quantum hud on karo")) {
            setHudEffectTheme(HudEffectTheme.QUANTUM_GRID)
            setHudExpanded(true)
            appendMayaBubble("Quantum Grid HUD active hai, ${currentProfile().bossName}.", badge = "Quantum HUD")
            return
        }
        if (lowerCmd in setOf("holographic hud", "holographic mode", "holographic mode on karo", "holographic hud on karo")) {
            setHudEffectTheme(HudEffectTheme.HOLOGRAPHIC_SCAN)
            setHudExpanded(true)
            appendMayaBubble("Holographic Scanline HUD lag gaya, ${currentProfile().bossName}.", badge = "Holographic HUD")
            return
        }
        if (lowerCmd in setOf(
                "full hud",
                "full hud on",
                "full hud on karo",
                "full hud kholo",
                "maya hud",
                "maya hud on",
                "maya hud kholo",
                "maya ui",
                "maya ui mode",
                "hud screen kholo"
            )
        ) {
            setFullHudVisible(true)
            appendMayaBubble(
                "Full Maya AI HUD (System Metrics, AI Status, Wave Mic aur Particle Network) khol diya hai, ${currentProfile().bossName}.",
                badge = "Full Maya HUD"
            )
            return
        }
        if (lowerCmd in setOf("full hud off", "full hud band", "full hud band karo", "maya hud band karo", "exit hud")) {
            setFullHudVisible(false)
            appendMayaBubble("Full Maya HUD band kar diya, ${currentProfile().bossName}.", badge = "HUD Closed")
            return
        }
        if (lowerCmd in setOf("particles on", "particles on karo", "ai particles on", "particle engine on")) {
            setParticlesEnabled(true)
            appendMayaBubble("AI Particle Network aur scanlines active hain, ${currentProfile().bossName}.", badge = "Particles ON")
            return
        }
        if (lowerCmd in setOf("particles off", "particles band", "particles band karo", "ai particles off")) {
            setParticlesEnabled(false)
            appendMayaBubble("Background AI Particles band kar diye hain, ${currentProfile().bossName}.", badge = "Particles OFF")
            return
        }
        if (lowerCmd in setOf("wave visualizer", "wave mode", "wave mic", "wave mode on karo")) {
            setWaveVisualizerMode(true)
            setHudExpanded(true)
            appendMayaBubble("3-Layer Flowing Wave Mic Visualizer lag gaya, ${currentProfile().bossName}.", badge = "Wave Mic")
            return
        }
        if (lowerCmd in setOf("spectrum mode", "24 bar mode", "bars visualizer", "bar mode on karo")) {
            setWaveVisualizerMode(false)
            setHudExpanded(true)
            appendMayaBubble("24-Bar Neon Spectrum Visualizer lag gaya, ${currentProfile().bossName}.", badge = "24-Bar Spectrum")
            return
        }

        // 5. Instant Permanent Human-Brain Listening voice & text shortcuts
        if (lowerCmd in setOf(
                "permanent listening on",
                "permanent listening on karo",
                "permanent listening chalu karo",
                "permanent listening start karo",
                "always listen",
                "always listen on",
                "background me suno",
                "hamesha suno",
                "human brain mode",
                "human brain mode on",
                "brain mode on",
                "brain mode on karo",
                "brain mode chalu karo"
            )
        ) {
            setPermanentBrainMode(enabled = true)
            return
        }
        if (lowerCmd in setOf(
                "permanent listening off",
                "permanent listening band",
                "permanent listening band karo",
                "background listening off",
                "background listening band",
                "background listening band karo",
                "human brain mode off",
                "brain mode off",
                "brain mode band karo"
            )
        ) {
            setPermanentBrainMode(enabled = false)
            return
        }
        if (lowerCmd in setOf("pause listening", "brain pause", "brain pause karo", "thodi der mat suno")) {
            if (permanentBrainEnabled.value) {
                voiceEngine.setPermanentBrainPaused(true)
                appendMayaBubble("Permanent listening pause kar diya hai, ${currentProfile().bossName}.", badge = "Brain Paused", speakOut = false)
            } else {
                stopVoiceInput()
            }
            return
        }
        if (lowerCmd in setOf("resume listening", "brain resume", "brain resume karo", "wapas suno")) {
            if (permanentBrainEnabled.value) {
                voiceEngine.setPermanentBrainPaused(false)
                appendMayaBubble("Wapas silent sun rahi hoon, ${currentProfile().bossName}.", badge = "Silent Brain LIVE", speakOut = false)
            } else {
                setPermanentBrainMode(enabled = true)
            }
            return
        }

        // 5.5 Instant Zero-Beep Silent Listening & Post-Work Sound Control shortcuts
        if (lowerCmd in setOf(
                "silent listening",
                "silent listening on",
                "silent listening on karo",
                "silent listening chalu karo",
                "beep band",
                "beep band karo",
                "bip band karo",
                "beep sound off",
                "beep sound band karo",
                "mic beep band karo"
            )
        ) {
            voiceEngine.setSilentListeningEnabled(true)
            appendMayaBubble(
                "Silent Listening active hai ${currentProfile().bossName} — listening ke waqt aur kaam poora hone ke baad koi beep sound nahi aayega.",
                badge = "Silent • No Beep",
                speakOut = false
            )
            return
        }
        if (lowerCmd in setOf(
                "kaam karne ke baad sound band karo",
                "kaam ke baad sound band karo",
                "kaam ke baad sound off",
                "sound band karo",
                "sound band kar do",
                "voice reply off",
                "voice reply band",
                "voice reply band karo",
                "awaaz band karo",
                "bolna band karo",
                "chupchap kaam karo",
                "silent mode on"
            )
        ) {
            voiceEngine.setSilentListeningEnabled(true)
            voiceEngine.setVoiceReplyEnabled(false)
            appendMayaBubble(
                "Done ${currentProfile().bossName}, 100% Silent Mode active hai — na listening me beep aayega aur na kaam poora hone ke baad koi sound bajega.",
                badge = "100% Silent Mode",
                speakOut = false
            )
            return
        }
        if (lowerCmd in setOf("voice reply on", "voice reply chalu karo", "awaaz chalu karo", "bolkar jawab do")) {
            voiceEngine.setSilentListeningEnabled(true)
            voiceEngine.setVoiceReplyEnabled(true)
            appendMayaBubble(
                "Voice reply wapas chalu kar diya hai ${currentProfile().bossName} (mic beep abhi bhi poori tarah silent rahega).",
                badge = "Voice Reply ON"
            )
            return
        }

        // 6. Instant API Key Connect & Status shortcuts
        if (lowerCmd in setOf("api key", "api key connect", "api key connect karo", "connect api key", "api key dalo", "api key set karo", "api connect karo")) {
            _showApiKeyDialog.value = true
            appendMayaBubble(
                "Ji ${currentProfile().bossName}, API Key Connect box khol diya hai — apni Gemini, OpenAI ya Groq key paste karke Connect daba dijiye.",
                badge = "API Key Connect"
            )
            return
        }
        if (lowerCmd in setOf("api status", "api key status", "kaunsi api connected hai")) {
            val st = apiKeyStatus.value
            val msg = if (st is ApiKeyConnectionStatus.Connected) {
                "${st.provider.displayName} API key connected hai, ${currentProfile().bossName} (Model: ${st.activeModel})."
            } else {
                "Abhi Built-in AI mode active hai, ${currentProfile().bossName}. 'API key connect karo' bolkar apni key jod sakte hain."
            }
            appendMayaBubble(msg, badge = "API Status")
            return
        }

        viewModelScope.launch {
            _isThinking.value = true
            voiceEngine.notifyThinkingStarted()
            try {
                refreshTelemetry()
                val prof = currentProfile()
                val persona = MayaPersona.fromId(prof.persona)
                val tone = MayaTone.fromId(prof.tone)
                val listenMode = ListenMode.fromId(prof.listenMode)
                val memories = (memoriesState.value as? DataUiState.Success)?.data.orEmpty()
                val skills = (skillsState.value as? DataUiState.Success)?.data.orEmpty()
                val recent = _messages.value.takeLast(8).map {
                    (if (it.isFromBoss) prof.bossName else persona.displayName) to it.text
                }
                val latestHud = systemHudMonitor.readSnapshot()

                val decisionRes = aiBrain.processBossCommand(
                    userInput = trimmed,
                    bossName = prof.bossName,
                    persona = persona,
                    tone = tone,
                    listenMode = listenMode,
                    personalWiki = prof.personalWiki,
                    memories = memories,
                    skills = skills,
                    telemetry = _telemetry.value,
                    autopilotConnected = autopilotConnected.value,
                    recentTranscript = recent,
                    hudSnapshot = latestHud
                )

                _isThinking.value = false
                decisionRes.fold(
                    onSuccess = { decision -> executeAgentDecision(decision, memories) },
                    onFailure = { err ->
                        appendMayaBubble(
                            err.localizedMessage ?: "AI call fail hua, Boss.",
                            badge = "AI Error",
                            isError = true
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in sendCommand", e)
                appendMayaBubble(
                    e.localizedMessage ?: "Response generate karne me error aaya, Boss.",
                    badge = "AI Error",
                    isError = true
                )
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun executeAgentDecision(decision: AgentDecision, memories: List<MemoryItem>) {
        var savedBoardTitle: String? = null
        if (decision.boardContent.isNotBlank() && decision.actionType != "SEND_EMAIL") {
            val title = decision.boardTitle.ifBlank { "Maya Board Report" }
            repository.createDocument(
                title = title,
                category = decision.boardCategory,
                summary = decision.spokenReply,
                content = decision.boardContent
            )
            savedBoardTitle = title
        }

        val nicknameMap = memories
            .filter { it.kind == "app_nickname" }
            .associate { it.key to it.value }

        when (decision.actionType) {
            // --- Section 3.1 Accessibility & Screen Autopilot ---
            "SCREEN_TAP_TEXT" -> {
                val svc = MayaAccessibilityService.instance
                if (svc == null) {
                    phoneExecutor.openAccessibilitySettings()
                    appendMayaBubble("Boss, screen pe button tap karne ke liye Accessibility me 'Maya' on kar dijiye — settings khol di hain.", badge = "Enable Autopilot")
                } else {
                    val res = svc.tapText(decision.target.ifBlank { decision.messageBody })
                    appendMayaBubble(res.messageHinglish, badge = "Autopilot Tap")
                }
            }

            "SCREEN_TYPE_TEXT" -> {
                val svc = MayaAccessibilityService.instance
                if (svc == null) {
                    phoneExecutor.openAccessibilitySettings()
                    appendMayaBubble("Boss, screen pe type karne ke liye Accessibility me 'Maya' on kar dijiye.", badge = "Enable Autopilot")
                } else {
                    val res = svc.typeTextIntoFocusedField(decision.messageBody.ifBlank { decision.target })
                    appendMayaBubble(res.messageHinglish, badge = "Autopilot Type")
                }
            }

            "SCREEN_SCROLL" -> {
                val svc = MayaAccessibilityService.instance
                if (svc == null) {
                    phoneExecutor.openAccessibilitySettings()
                    appendMayaBubble("Boss, scroll karne ke liye Accessibility me 'Maya' on kar dijiye.", badge = "Enable Autopilot")
                } else {
                    val res = svc.scrollOrSwipe(decision.target.ifBlank { "down" })
                    appendMayaBubble(res.messageHinglish, badge = "Autopilot Scroll")
                }
            }

            "SCREEN_READ" -> {
                val svc = MayaAccessibilityService.instance
                if (svc == null) {
                    phoneExecutor.openAccessibilitySettings()
                    appendMayaBubble("Boss, screen text padhne ke liye Accessibility me 'Maya' on kar dijiye.", badge = "Enable Autopilot")
                } else {
                    val (ok, visible) = svc.readVisibleScreenText()
                    if (ok) {
                        val docTitle = "Screen Snapshot"
                        repository.createDocument(docTitle, "note", visible.take(200), "# Screen Text\n\n$visible")
                        appendMayaBubble("Screen pe dikh raha hai: ${visible.take(180)}.", badge = "Screen Read", linkedBoardTitle = docTitle)
                    } else {
                        appendMayaBubble(visible, badge = "Screen Read")
                    }
                }
            }

            "WAIT_FOR_TEXT" -> {
                val svc = MayaAccessibilityService.instance
                if (svc == null) {
                    phoneExecutor.openAccessibilitySettings()
                    appendMayaBubble("Boss, wait_for_screen_text ke liye Accessibility me 'Maya' on chahiye.", badge = "Enable Autopilot")
                } else {
                    val secs = decision.numberVal.coerceIn(3, 120)
                    appendMayaBubble("'${decision.target}' aane ka wait kar rahi hoon (max ${secs}s)…", badge = "Waiting")
                    val res = svc.waitForScreenText(decision.target, shouldAppear = true, timeoutSeconds = secs)
                    appendMayaBubble(res.messageHinglish, badge = "Wait Complete")
                }
            }

            "GLOBAL_NAV" -> {
                val svc = MayaAccessibilityService.instance
                if (svc == null) {
                    phoneExecutor.openAccessibilitySettings()
                    appendMayaBubble("Boss, Home/Back/Recents autopilot ke liye Accessibility me 'Maya' on kar dijiye.", badge = "Enable Autopilot")
                } else {
                    val res = svc.performGlobalNav(decision.target.ifBlank { "back" })
                    appendMayaBubble(res.messageHinglish, badge = "Navigation")
                }
            }

            "WAIT_DELAY" -> {
                val secs = decision.numberVal.coerceIn(1, 120) // Golden Rule 10: max 120s
                appendMayaBubble("${secs}s ruk rahi hoon, Boss…", badge = "Timer Wait")
                delay(secs * 1000L)
                appendMayaBubble("${secs}s ka wait poora ho gaya, Boss.", badge = "Wait Done")
            }

            // --- Section 3.1 & 3.2 Hardware, Settings & OS ---
            "SET_TORCH_ON" -> {
                val res = phoneExecutor.setTorch(true)
                refreshTelemetry()
                appendMayaBubble(res.messageHinglish, badge = "Torch")
            }

            "SET_TORCH_OFF" -> {
                val res = phoneExecutor.setTorch(false)
                refreshTelemetry()
                appendMayaBubble(res.messageHinglish, badge = "Torch")
            }

            "GET_BATTERY" -> {
                val res = phoneExecutor.getBatteryStatusText()
                refreshTelemetry()
                appendMayaBubble(res.messageHinglish, badge = "Battery")
            }

            "SET_VOLUME" -> {
                val res = phoneExecutor.setMediaVolumePercent(decision.numberVal)
                refreshTelemetry()
                appendMayaBubble(res.messageHinglish, badge = "Volume")
            }

            "SET_RINGER" -> {
                val res = phoneExecutor.setRingerMode(decision.target.ifBlank { "vibrate" })
                refreshTelemetry()
                appendMayaBubble(res.messageHinglish, badge = "Ringer")
            }

            "MEDIA_CONTROL" -> {
                val res = phoneExecutor.mediaControl(decision.target.ifBlank { "play_pause" })
                appendMayaBubble(res.messageHinglish, badge = "Media")
            }

            "OPEN_SETTINGS" -> {
                val res = phoneExecutor.openSettingsPanel(decision.target.ifBlank { "wifi" })
                appendMayaBubble(res.messageHinglish, badge = "Settings")
            }

            "OPEN_APP" -> {
                val res = phoneExecutor.openAppByName(decision.target, nicknameMap)
                appendMayaBubble(res.messageHinglish, badge = "Open App")
            }

            "LIST_APPS" -> {
                val res = phoneExecutor.listInstalledApps(decision.target)
                appendMayaBubble(res.messageHinglish, badge = "Installed Apps")
            }

            "OPEN_URL" -> {
                val res = phoneExecutor.openUrl(decision.target)
                appendMayaBubble(res.messageHinglish, badge = "Browser")
            }

            "GET_TIME" -> {
                val txt = phoneExecutor.getCurrentTimeText(decision.target.takeIf { it.isNotBlank() })
                appendMayaBubble(txt, badge = "Time")
            }

            "SHOW_ALARMS" -> {
                val res = phoneExecutor.showAlarms()
                appendMayaBubble(res.messageHinglish, badge = "Alarms")
            }

            "READ_CLIPBOARD" -> {
                val clip = phoneExecutor.readClipboard()
                val msg = if (clip.isBlank()) "Clipboard abhi khaali hai, Boss." else "Clipboard me likha hai: \"${clip.take(200)}\""
                appendMayaBubble(msg, badge = "Clipboard")
            }

            "WRITE_CLIPBOARD" -> {
                val text = decision.messageBody.ifBlank { decision.target }
                val res = phoneExecutor.writeClipboard("Maya", text)
                appendMayaBubble(res.messageHinglish, badge = "Clipboard")
            }

            "SHARE_TEXT" -> {
                val res = phoneExecutor.shareText(
                    decision.subject.ifBlank { "Maya Share" },
                    decision.messageBody.ifBlank { decision.spokenReply }
                )
                appendMayaBubble(res.messageHinglish, badge = "Share")
            }

            "SET_ALARM" -> {
                val label = decision.messageBody.ifBlank { decision.target.ifBlank { "Maya Alarm" } }
                val res = phoneExecutor.setAlarm(decision.hour, decision.minute, label)
                val timeStr = String.format(Locale.US, "%02d:%02d", decision.hour.coerceIn(0, 23), decision.minute.coerceIn(0, 59))
                repository.createReminder("reminder", label, "System Alarm", timeStr)
                appendMayaBubble(res.messageHinglish, badge = "Alarm $timeStr")
            }

            "SET_TIMER" -> {
                val secs = decision.numberVal.coerceAtLeast(10)
                val res = phoneExecutor.setTimer(secs, decision.target.ifBlank { "Timer" })
                appendMayaBubble(res.messageHinglish, badge = "Timer")
            }

            "ADD_CALENDAR" -> {
                val res = phoneExecutor.addCalendarEvent(
                    decision.target.ifBlank { "Meeting" },
                    decision.messageBody
                )
                appendMayaBubble(res.messageHinglish, badge = "Calendar")
            }

            // --- Section 3.3 Comms + Golden Rule 5 Confirmation Gate ---
            "SEND_WHATSAPP" -> {
                val who = decision.recipient.ifBlank { decision.phone.ifBlank { "Contact" } }
                val msg = decision.messageBody.ifBlank { decision.target }
                val ask = "Boss, $who ko WhatsApp pe '$msg' bhej dun?"
                _pendingConfirmation.value = PendingConfirmation(
                    title = "WhatsApp Message Confirm Karein",
                    previewDetails = "To: $who ${if (decision.phone.isNotBlank()) "(${decision.phone})" else ""}\nMessage: \"$msg\"",
                    promptSpoken = ask,
                    onConfirmExecute = { phoneExecutor.sendWhatsApp(decision.phone, msg).messageHinglish }
                )
                appendMayaBubble(ask, badge = "Confirm Required")
            }

            "SEND_TELEGRAM" -> {
                val who = decision.recipient.ifBlank { decision.phone.ifBlank { "Telegram" } }
                val msg = decision.messageBody.ifBlank { decision.target }
                val ask = "Boss, $who ko Telegram pe '$msg' bhej dun?"
                _pendingConfirmation.value = PendingConfirmation(
                    title = "Telegram Message Confirm Karein",
                    previewDetails = "To: $who\nTelegram: \"$msg\"",
                    promptSpoken = ask,
                    onConfirmExecute = { phoneExecutor.sendTelegram(who, msg).messageHinglish }
                )
                appendMayaBubble(ask, badge = "Confirm Required")
            }

            "SEND_SMS" -> {
                val who = decision.recipient.ifBlank { decision.phone.ifBlank { "Number" } }
                val msg = decision.messageBody.ifBlank { decision.target }
                val ask = "Boss, $who ko SMS '$msg' bhej dun?"
                _pendingConfirmation.value = PendingConfirmation(
                    title = "SMS Confirm Karein",
                    previewDetails = "To: $who\nSMS: \"$msg\"",
                    promptSpoken = ask,
                    onConfirmExecute = { phoneExecutor.sendSms(decision.phone, msg).messageHinglish }
                )
                appendMayaBubble(ask, badge = "Confirm Required")
            }

            "SEND_EMAIL" -> {
                val to = decision.recipient
                val subj = decision.subject.ifBlank { "Update" }
                val body = decision.messageBody.ifBlank { decision.boardContent }
                repository.createDocument(
                    title = "Email Draft: $subj",
                    category = "email",
                    summary = "To: ${to.ifBlank { "(recipient)" }} • Subject: $subj",
                    content = "To: $to\nSubject: $subj\n\n$body"
                )
                val ask = "Email draft Board pe rakh diya hai (Subject: '$subj'). Email app me bhej dun, Boss?"
                _pendingConfirmation.value = PendingConfirmation(
                    title = "Email Send Confirm Karein",
                    previewDetails = "To: ${to.ifBlank { "(select in mail app)" }}\nSubject: $subj\n\n$body",
                    promptSpoken = ask,
                    onConfirmExecute = { phoneExecutor.sendEmail(to, subj, body).messageHinglish }
                )
                appendMayaBubble(ask, badge = "Confirm Email", linkedBoardTitle = "Email Draft: $subj")
            }

            "DIAL_CALL" -> {
                val res = phoneExecutor.dialCall(decision.phone.ifBlank { decision.target })
                appendMayaBubble(res.messageHinglish, badge = "Call")
            }

            "SAVE_CONTACT" -> {
                val name = decision.recipient.ifBlank { decision.target }
                val res = phoneExecutor.saveContactIntent(name, decision.phone, decision.subject)
                appendMayaBubble(res.messageHinglish, badge = "Contacts")
            }

            "PLAY_YOUTUBE" -> {
                val res = phoneExecutor.playYouTube(decision.target.ifBlank { decision.messageBody })
                appendMayaBubble(res.messageHinglish, badge = "YouTube")
            }

            "PLAY_SPOTIFY" -> {
                val res = phoneExecutor.playSpotify(decision.target.ifBlank { decision.messageBody })
                appendMayaBubble(res.messageHinglish, badge = "Spotify")
            }

            "NAVIGATE_TO" -> {
                val res = phoneExecutor.navigateTo(decision.target.ifBlank { decision.messageBody })
                appendMayaBubble(res.messageHinglish, badge = "Maps")
            }

            // --- Section 3.6, 3.7, 3.10 Live Web, URL Reader, Markets & GitHub ---
            "SEARCH_WEB" -> {
                saveAndSpeakLiveInfo(
                    liveInfoClient.searchWebLive(decision.target.ifBlank { decision.messageBody }),
                    "Web Search"
                )
            }

            "READ_URL" -> {
                val rawUrl = decision.target.ifBlank { decision.messageBody }
                val pageRes = liveInfoClient.fetchUrlCleanText(rawUrl)
                pageRes.fold(
                    onSuccess = { (pageTitle, cleanBody) ->
                        val sumRes = aiBrain.summarizeUntrustedWebPage(pageTitle, cleanBody, currentProfile().bossName)
                        sumRes.fold(
                            onSuccess = { (spoken, md) ->
                                val title = "URL Summary: ${pageTitle.take(80)}"
                                repository.createDocument(title, "research", spoken, md)
                                appendMayaBubble(spoken, badge = "Page Read", linkedBoardTitle = title)
                            },
                            onFailure = { e ->
                                appendMayaBubble(e.localizedMessage ?: "URL summarize nahi ho saka, Boss.", badge = "URL Error", isError = true)
                            }
                        )
                    },
                    onFailure = {
                        appendMayaBubble("Boss, wo webpage abhi khul ya padha nahi ja saka.", badge = "URL Error", isError = true)
                    }
                )
            }

            "GET_WEATHER" -> {
                saveAndSpeakLiveInfo(liveInfoClient.fetchWeather(decision.target.ifBlank { "Delhi" }), "Live Weather")
            }

            "GET_STOCK" -> {
                saveAndSpeakLiveInfo(liveInfoClient.fetchStockQuote(decision.target.ifBlank { "RELIANCE.NS" }), "Stocks")
            }

            "GET_CRYPTO" -> {
                saveAndSpeakLiveInfo(liveInfoClient.fetchCryptoPrice(decision.target.ifBlank { "bitcoin" }), "Crypto")
            }

            "GET_FOREX" -> {
                val from = decision.target.ifBlank { "USD" }
                val to = decision.phone.ifBlank { "INR" }
                saveAndSpeakLiveInfo(liveInfoClient.fetchForexRate(from, to), "Forex")
            }

            "GET_NEWS" -> {
                saveAndSpeakLiveInfo(liveInfoClient.fetchFreshNews(), "Fresh News")
            }

            "GITHUB_LOOKUP" -> {
                saveAndSpeakLiveInfo(
                    liveInfoClient.fetchGitHubInfo(decision.target.ifBlank { decision.messageBody }),
                    "GitHub"
                )
            }

            "SCAN_WATCHLIST" -> {
                val watchItems = (remindersState.value as? DataUiState.Success)?.data
                    .orEmpty()
                    .filter { it.kind == "watchlist" }
                    .take(4)
                if (watchItems.isEmpty()) {
                    appendMayaBubble("Boss, aapki market watchlist abhi khaali hai. Pehle koi stock ya coin add karein?", badge = "Watchlist")
                } else {
                    val lines = mutableListOf<String>()
                    for (item in watchItems) {
                        val stockRes = liveInfoClient.fetchStockQuote(item.title)
                        if (stockRes.success) {
                            lines.add("- **${item.title}:** ${stockRes.spokenSummary}")
                        } else {
                            val cryptoRes = liveInfoClient.fetchCryptoPrice(item.title)
                            lines.add("- **${item.title}:** ${cryptoRes.spokenSummary}")
                        }
                    }
                    val docTitle = "Watchlist Market Scan"
                    val md = "# Watchlist Live Scan\n\n" + lines.joinToString("\n") + "\n\n_Sirf live data, koi buy/sell salah nahi._"
                    repository.createDocument(docTitle, "market", "Watchlist (${watchItems.size} items) scan ready.", md)
                    appendMayaBubble("Watchlist ke ${watchItems.size} tickers scan karke Board pe rakh diye hain, Boss.", badge = "Watchlist Scan", linkedBoardTitle = docTitle)
                }
            }

            // --- Memory, Board, Skills, Personas & Security ---
            "REMEMBER_FACT" -> {
                val key = decision.subject.ifBlank { decision.target.ifBlank { "Boss Preference" } }
                val value = decision.messageBody.ifBlank { decision.spokenReply }
                repository.saveMemory("fact", key, value)
                appendMayaBubble(decision.spokenReply, badge = "Memory Saved")
            }

            "REMEMBER_APP_NICKNAME" -> {
                val nick = decision.subject.ifBlank { "shortcut" }
                val app = decision.target.ifBlank { decision.messageBody }
                repository.saveMemory("app_nickname", nick, app)
                appendMayaBubble("Yaad rakh liya Boss — '$nick' matlab $app.", badge = "App Nickname")
            }

            "ADD_REMINDER" -> {
                val title = decision.target.ifBlank { decision.subject.ifBlank { "Reminder" } }
                repository.createReminder("reminder", title, decision.messageBody, decision.phone)
                appendMayaBubble(decision.spokenReply, badge = "Reminder Saved")
            }

            "ADD_WATCHLIST" -> {
                val ticker = decision.target.uppercase(Locale.ROOT).ifBlank { "BTC" }
                repository.createReminder("watchlist", ticker, decision.messageBody.ifBlank { "Market Watchlist" }, "Live Watch")
                appendMayaBubble("$ticker aapki watchlist me jod diya, Boss.", badge = "Watchlist")
            }

            "CREATE_BOARD_DOC" -> {
                val title = decision.boardTitle.ifBlank { decision.target.ifBlank { "Maya Document" } }
                if (savedBoardTitle == null) {
                    repository.createDocument(
                        title = title,
                        category = decision.boardCategory,
                        summary = decision.spokenReply,
                        content = decision.boardContent.ifBlank { decision.messageBody.ifBlank { decision.spokenReply } }
                    )
                }
                appendMayaBubble(decision.spokenReply, badge = "Saved to Board", linkedBoardTitle = title)
            }

            "DELETE_BOARD_DOC" -> {
                val docs = (documentsState.value as? DataUiState.Success)?.data.orEmpty()
                val query = decision.target.trim()
                val match = if (query.isNotBlank()) {
                    docs.firstOrNull { it.title.contains(query, ignoreCase = true) }
                } else docs.firstOrNull()

                if (match == null) {
                    appendMayaBubble("Boss, Board pe '$query' naam ka koi document nahi mila.", badge = "Board")
                } else {
                    requestDeleteBoardDoc(match)
                }
            }

            "CREATE_SKILL_PREVIEW" -> {
                val name = decision.target.ifBlank { "Custom Playbook" }
                val trigger = decision.subject.ifBlank { name }
                val steps = decision.messageBody.ifBlank { "1. Command suno\n2. Action execute karo" }
                val ask = "Boss, '$name' skill ka preview ready hai. Save kar doon?"
                _pendingConfirmation.value = PendingConfirmation(
                    title = "Nayi Skill Save Karein?",
                    previewDetails = "Skill: $name\nTrigger: \"$trigger\"\nSteps:\n$steps",
                    promptSpoken = ask,
                    onConfirmExecute = {
                        repository.createSkillPlaybook("skill", name, trigger, steps)
                        "'$name' skill save ho gayi, Boss."
                    }
                )
                appendMayaBubble(ask, badge = "Skill Preview")
            }

            "DELETE_SKILL" -> {
                val list = (skillsState.value as? DataUiState.Success)?.data.orEmpty()
                val match = list.firstOrNull { it.name.contains(decision.target.trim(), ignoreCase = true) }
                if (match == null) {
                    appendMayaBubble("Boss, '${decision.target}' naam ki koi saved skill nahi mili.", badge = "Skills")
                } else {
                    requestDeleteSkill(match)
                }
            }

            "SWITCH_PERSONA" -> {
                val newPersona = MayaPersona.fromId(decision.target)
                switchPersona(newPersona)
                appendMayaBubble(decision.spokenReply, badge = "Persona: ${newPersona.displayName}")
            }

            "SET_LISTEN_MODE" -> {
                val mode = ListenMode.fromId(decision.target)
                setListenMode(mode)
                appendMayaBubble(decision.spokenReply, badge = mode.label)
            }

            "SET_PERMANENT_LISTEN" -> {
                val targetCmd = decision.target.trim().lowercase(Locale.ROOT)
                when {
                    targetCmd in setOf("off", "stop", "false", "disable", "band") -> {
                        setPermanentBrainMode(enabled = false)
                    }
                    targetCmd == "pause" -> {
                        if (permanentBrainEnabled.value) {
                            voiceEngine.setPermanentBrainPaused(true)
                        }
                        appendMayaBubble(decision.spokenReply, badge = "Brain Paused", speakOut = false)
                    }
                    targetCmd == "resume" -> {
                        if (permanentBrainEnabled.value) {
                            voiceEngine.setPermanentBrainPaused(false)
                            appendMayaBubble(decision.spokenReply, badge = "Brain LIVE")
                        } else {
                            setPermanentBrainMode(enabled = true)
                        }
                    }
                    else -> {
                        setPermanentBrainMode(enabled = true)
                    }
                }
            }

            "ARM_GUARD" -> {
                if (decision.target.contains("touch", ignoreCase = true)) {
                    setTouchGuard(true)
                    appendMayaBubble("Touch Guard chalu kar diya hai, Boss.", badge = "Touch Guard Armed")
                } else {
                    setAwayGuard(true)
                    appendMayaBubble("Away Voice Guard chalu kar diya hai, Boss.", badge = "Voice Guard Armed")
                }
            }

            "DISARM_GUARD" -> {
                setTouchGuard(false)
                setAwayGuard(false)
                appendMayaBubble("Security guards band kar diye hain, Boss.", badge = "Guard Off")
            }

            "TRIGGER_SOS" -> {
                val sosNum = decision.phone.ifBlank { "112" }
                val ask = "Boss, Emergency SOS dialer ($sosNum) abhi khol dun?"
                _pendingConfirmation.value = PendingConfirmation(
                    title = "Emergency SOS Confirm Karein",
                    previewDetails = "Emergency Number: $sosNum",
                    promptSpoken = ask,
                    onConfirmExecute = { phoneExecutor.dialCall(sosNum).messageHinglish }
                )
                appendMayaBubble(ask, badge = "SOS Confirm")
            }

            "GENERATE_IMAGE" -> {
                val prompt = decision.messageBody.ifBlank { decision.target }
                appendMayaBubble("Image bana rahi hoon Boss (30–60s lag sakte hain)…", badge = "Gemini Image Macro")
                generateRuntimeImage(prompt, autoSaveToGallery = true)
            }

            "START_MISSION" -> {
                val steps = decision.missionSteps.ifEmpty {
                    listOf("Plan verify karna", "Screen/action step chalana", "Result confirm karna")
                }.take(8)
                _activeMission.value = MissionState(
                    title = decision.target.ifBlank { "Autopilot Mission" },
                    steps = steps,
                    currentStepIndex = 0
                )
                appendMayaBubble(decision.spokenReply, badge = "Mission Plan (${steps.size} steps)")
            }

            "GET_SYSTEM_HUD" -> {
                val snap = systemHudMonitor.readSnapshot()
                val summary = snap.toHinglishSpokenSummary(currentProfile().bossName)
                val shouldPinBoard = decision.target.contains("board", ignoreCase = true) || savedBoardTitle == null
                val title = "Maya AI HUD — System Diagnostic"
                if (shouldPinBoard) {
                    repository.createDocument(
                        title = title,
                        category = "dashboard",
                        summary = summary,
                        content = snap.toBoardDiagnosticMarkdown()
                    )
                }
                appendMayaBubble(
                    summary,
                    badge = "System HUD • ${snap.healthLevel.label}",
                    linkedBoardTitle = if (shouldPinBoard) title else savedBoardTitle
                )
            }

            "CONTROL_HUD" -> {
                val badge = applyHudControlTarget(decision.target)
                appendMayaBubble(decision.spokenReply, badge = badge)
            }

            "SYNC_GITHUB_SKILLS" -> {
                val repoTarget = decision.target.ifBlank { "HunterisLive-1/maya-ui" }
                val res = gitHubSkillStoreClient.fetchSkillsFromRepo(repoTarget)
                res.fold(
                    onSuccess = { (slug, remoteSkills) ->
                        _gitHubSkillStoreState.value = GitHubSkillStoreState.Loaded(slug, remoteSkills)
                        appendMayaBubble(
                            "${currentProfile().bossName}, '$slug' se ${remoteSkills.size} Maya skills fetch ho gayi hain — Skills tab me preview karke install kar lijiye.",
                            badge = "GitHub Skills (${remoteSkills.size})"
                        )
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: "GitHub skills sync nahi ho saka."
                        _gitHubSkillStoreState.value = GitHubSkillStoreState.Error(msg)
                        appendMayaBubble(msg, badge = "Skill Sync Error", isError = true)
                    }
                )
            }

            else -> {
                appendMayaBubble(
                    decision.spokenReply,
                    badge = if (savedBoardTitle != null) "Board Updated" else null,
                    linkedBoardTitle = savedBoardTitle
                )
            }
        }
    }

    private suspend fun saveAndSpeakLiveInfo(info: LiveInfoResult, badge: String) {
        if (info.success && info.boardMarkdown != null && info.boardTitle != null) {
            repository.createDocument(
                info.boardTitle,
                info.boardCategory ?: "research",
                info.spokenSummary,
                info.boardMarkdown
            )
        }
        appendMayaBubble(info.spokenSummary, badge = badge, linkedBoardTitle = info.boardTitle)
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            try {
                val outcomeText = pending.onConfirmExecute()
                appendMayaBubble(outcomeText, badge = "Confirmed ✓")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendMayaBubble("Action poora nahi ho saka: ${e.localizedMessage}", badge = "Action Error", isError = true)
            }
        }
    }

    fun cancelPendingAction() {
        if (_pendingConfirmation.value != null) {
            _pendingConfirmation.value = null
            appendMayaBubble("Theek hai Boss, cancel kar diya.", badge = "Cancelled")
        }
    }

    fun requestDeleteBoardDoc(doc: BoardDocument) {
        val ask = "Boss, '${doc.title}' ko Board se delete kar dun? Ye wapas nahi aayega."
        _pendingConfirmation.value = PendingConfirmation(
            title = "Document Delete Confirm Karein",
            previewDetails = "Title: ${doc.title}\nCategory: ${doc.category}",
            promptSpoken = ask,
            onConfirmExecute = {
                repository.deleteDocument(doc.id).getOrThrow()
                "'${doc.title}' delete kar diya, Boss."
            }
        )
        val spoke = voiceEngine.speak(
            ask,
            MayaPersona.fromId(currentProfile().persona),
            MayaTone.fromId(currentProfile().tone),
            voiceReplyEnabled.value
        )
        if (!spoke) {
            voiceEngine.notifyTurnCompletedWithoutSpeech()
        }
    }

    fun advanceMissionStep() {
        val m = _activeMission.value ?: return
        if (m.currentStepIndex + 1 < m.steps.size) {
            _activeMission.value = m.copy(currentStepIndex = m.currentStepIndex + 1)
        } else {
            _activeMission.value = null
            appendMayaBubble("Mission ke saare steps poore ho gaye, Boss.", badge = "Mission Done")
        }
    }

    fun undoLastMissionStep() {
        val m = _activeMission.value ?: return
        val prev = (m.currentStepIndex - 1).coerceAtLeast(0)
        _activeMission.value = m.copy(currentStepIndex = prev)
        appendMayaBubble("Pichhla step undo kar diya (ab Step ${prev + 1} pe hain), Boss.", badge = "Undo Step")
    }

    fun cancelMission() {
        if (_activeMission.value != null) {
            _activeMission.value = null
            appendMayaBubble("Mission rok diya gaya, Boss.", badge = "Mission Cancelled")
        }
    }

    fun analyzeImageAttachment(bitmap: Bitmap, question: String) {
        val label = question.trim().ifBlank { "Is photo me kya dikh raha hai?" }
        _messages.value = _messages.value + ChatBubble(
            isFromBoss = true,
            text = label,
            attachedBitmap = bitmap
        )
        viewModelScope.launch {
            _isThinking.value = true
            val prof = currentProfile()
            val res = aiBrain.analyzeVision(bitmap, label, prof.bossName)
            _isThinking.value = false
            res.fold(
                onSuccess = { reply -> appendMayaBubble(reply, badge = "Vision Check") },
                onFailure = { err ->
                    appendMayaBubble(err.localizedMessage ?: "Vision check fail hua.", badge = "Vision Error", isError = true)
                }
            )
        }
    }

    fun generateRuntimeImage(prompt: String, autoSaveToGallery: Boolean = false) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _imageStudioState.value = ImageStudioState.Generating
            val res = aiBrain.generateStudioImage(clean)
            res.fold(
                onSuccess = { bitmap ->
                    val savedMsg = if (autoSaveToGallery) {
                        val saveOutcome = phoneExecutor.saveBitmapToGallery(bitmap, clean)
                        appendMayaBubble(saveOutcome.messageHinglish, badge = "Gallery Saved", attachedBitmap = bitmap)
                        saveOutcome.messageHinglish
                    } else null
                    _imageStudioState.value = ImageStudioState.Ready(clean, bitmap, savedMsg)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Image generation failed."
                    _imageStudioState.value = ImageStudioState.Error(msg)
                    if (autoSaveToGallery) {
                        appendMayaBubble(msg, badge = "Image Error", isError = true)
                    }
                }
            )
        }
    }

    fun saveStudioBitmapToGallery(bitmap: Bitmap, prompt: String) {
        val outcome = phoneExecutor.saveBitmapToGallery(bitmap, prompt)
        _imageStudioState.value = ImageStudioState.Ready(prompt, bitmap, outcome.messageHinglish)
        appendMayaBubble(outcome.messageHinglish, badge = "Gallery")
    }

    fun runBusinessGenerator(mode: String, input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _businessToolState.value = BusinessToolState.Loading
            val res = aiBrain.runBusinessTool(mode, input)
            res.fold(
                onSuccess = { output ->
                    val (title, category) = when (mode) {
                        "review_reply" -> "Customer Review Reply" to "reviews"
                        "review_themes" -> "Customer Review Themes" to "reviews"
                        "dashboard" -> "Dashboard Summary" to "dashboard"
                        else -> "Marketing & Campaign Plan" to "marketing"
                    }
                    repository.createDocument(
                        title = title,
                        category = category,
                        summary = output.lineSequence().take(2).joinToString(" ").take(200),
                        content = output
                    )
                    _businessToolState.value = BusinessToolState.Success(mode, output, savedToBoard = true)
                },
                onFailure = { err ->
                    _businessToolState.value = BusinessToolState.Error(err.localizedMessage ?: "Generation failed.")
                }
            )
        }
    }

    fun switchPersona(persona: MayaPersona) {
        viewModelScope.launch { repository.updateProfileFields(mapOf("persona" to persona.id)) }
    }

    fun switchTone(tone: MayaTone) {
        viewModelScope.launch { repository.updateProfileFields(mapOf("tone" to tone.id)) }
    }

    fun setListenMode(mode: ListenMode) {
        viewModelScope.launch { repository.updateProfileFields(mapOf("listenMode" to mode.id)) }
    }

    fun saveBossProfileAndWiki(bossName: String, wiki: String) {
        viewModelScope.launch {
            repository.updateProfileFields(
                mapOf(
                    "bossName" to bossName.trim().ifBlank { "Boss" }.take(60),
                    "personalWiki" to wiki.trim().take(4000)
                )
            )
        }
    }

    fun enrollVoiceGuardian(passphrase: String, model: String) {
        val clean = passphrase.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.updateProfileFields(
                mapOf(
                    "voiceEnrolled" to true,
                    "voicePassphrase" to clean.take(200),
                    "voiceModel" to if (model == "medium") "medium" else "powerful"
                )
            )
            appendMayaBubble("Voice Guardian me aapki passphrase enroll ho gayi, Boss.", badge = "Voice Guardian")
        }
    }

    fun setAwayGuard(armed: Boolean) {
        viewModelScope.launch { repository.updateProfileFields(mapOf("guardArmed" to armed)) }
    }

    fun setTouchGuard(armed: Boolean) {
        _touchGuardAlert.value = null
        viewModelScope.launch { repository.updateProfileFields(mapOf("touchGuardArmed" to armed)) }
    }

    fun onTouchGuardTriggered() {
        val alert = "Boss, abhi screen pe unauthorized touch detect hua! Maine koi action nahi chalaya. Touch guard band karun ya chalu rehne dun?"
        _touchGuardAlert.value = alert
        appendMayaBubble(alert, badge = "Touch Guard Alert!", isError = true)
    }

    fun quickToggleTorch() {
        val next = !_telemetry.value.torchEnabled
        val res = phoneExecutor.setTorch(next)
        refreshTelemetry()
        appendMayaBubble(res.messageHinglish, badge = "Torch")
    }

    fun addManualBoardNote(title: String, category: String, content: String) {
        viewModelScope.launch {
            repository.createDocument(title = title, category = category, summary = content.take(160), content = content)
        }
    }

    fun togglePinDocument(doc: BoardDocument) {
        viewModelScope.launch { repository.updateDocumentPin(doc, !doc.pinned) }
    }

    fun addMemoryManual(kind: String, key: String, value: String) {
        viewModelScope.launch { repository.saveMemory(kind, key, value) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun previewAndConfirmSkill(kind: String, name: String, trigger: String, steps: String) {
        val ask = "Boss, '$name' ($kind) preview ready hai. Save kar doon?"
        _pendingConfirmation.value = PendingConfirmation(
            title = "Save ${kind.replaceFirstChar { it.uppercase() }} '$name'?",
            previewDetails = "Trigger: $trigger\nSteps:\n$steps",
            promptSpoken = ask,
            onConfirmExecute = {
                repository.createSkillPlaybook(kind, name, trigger, steps).getOrThrow()
                "'$name' save ho gaya, Boss."
            }
        )
        voiceEngine.notifyTurnCompletedWithoutSpeech()
    }

    fun toggleSkill(skill: SkillPlaybook) {
        viewModelScope.launch { repository.toggleSkillEnabled(skill, !skill.enabled) }
    }

    fun requestDeleteSkill(skill: SkillPlaybook) {
        val ask = "Boss, '${skill.name}' ko delete kar dun?"
        _pendingConfirmation.value = PendingConfirmation(
            title = "Delete '${skill.name}'?",
            previewDetails = "Trigger: ${skill.trigger}",
            promptSpoken = ask,
            onConfirmExecute = {
                repository.deleteSkill(skill.id).getOrThrow()
                "'${skill.name}' hata diya, Boss."
            }
        )
        voiceEngine.notifyTurnCompletedWithoutSpeech()
    }

    fun addReminderManual(kind: String, title: String, detail: String, scheduled: String) {
        viewModelScope.launch { repository.createReminder(kind, title, detail, scheduled) }
    }

    fun toggleReminder(item: ReminderItem) {
        viewModelScope.launch { repository.toggleReminderCompleted(item, !item.completed) }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    // --- HunterisLive-1/maya-ui HUD Controls & GitHub Skill Store ---

    fun reportLiveFps(fps: Int) {
        systemHudMonitor.reportMeasuredFps(fps)
    }

    fun setHudExpanded(expanded: Boolean) {
        val updated = _hudConfig.value.copy(hudExpanded = expanded)
        _hudConfig.value = updated
        systemHudMonitor.saveVisualConfig(updated)
    }

    fun toggleHudExpanded() {
        setHudExpanded(!_hudConfig.value.hudExpanded)
    }

    fun setHudEffectTheme(theme: HudEffectTheme) {
        val updated = _hudConfig.value.copy(effectTheme = theme)
        _hudConfig.value = updated
        systemHudMonitor.saveVisualConfig(updated)
    }

    fun setFloatingHudVisible(visible: Boolean) {
        val updated = _hudConfig.value.copy(floatingHudVisible = visible)
        _hudConfig.value = updated
        systemHudMonitor.saveVisualConfig(updated)
    }

    fun toggleFloatingHud() {
        setFloatingHudVisible(!_hudConfig.value.floatingHudVisible)
    }

    fun updateFloatingHudOffset(x: Float, y: Float) {
        val updated = _hudConfig.value.copy(floatingOffsetX = x, floatingOffsetY = y)
        _hudConfig.value = updated
        systemHudMonitor.saveVisualConfig(updated)
    }

    fun setFullHudVisible(visible: Boolean) {
        val updated = _hudConfig.value.copy(fullHudVisible = visible)
        _hudConfig.value = updated
    }

    fun toggleFullHud() {
        setFullHudVisible(!_hudConfig.value.fullHudVisible)
    }

    fun setParticlesEnabled(enabled: Boolean) {
        val updated = _hudConfig.value.copy(particlesEnabled = enabled)
        _hudConfig.value = updated
        systemHudMonitor.saveVisualConfig(updated)
    }

    fun toggleParticlesEnabled() {
        setParticlesEnabled(!_hudConfig.value.particlesEnabled)
    }

    fun setWaveVisualizerMode(waveMode: Boolean) {
        val updated = _hudConfig.value.copy(waveVisualizerMode = waveMode)
        _hudConfig.value = updated
        systemHudMonitor.saveVisualConfig(updated)
    }

    fun toggleVisualizerMode() {
        setWaveVisualizerMode(!_hudConfig.value.waveVisualizerMode)
    }

    private fun applyHudControlTarget(rawTarget: String): String {
        val t = rawTarget.trim().lowercase(Locale.ROOT)
        return when {
            t.contains("full_off") || t.contains("exit_hud") -> {
                setFullHudVisible(false)
                "Full Maya HUD OFF"
            }
            t.contains("full") || t.contains("maya_ui") -> {
                setFullHudVisible(true)
                "Full Maya HUD ON"
            }
            t.contains("particle_off") -> {
                setParticlesEnabled(false)
                "Particles OFF"
            }
            t.contains("particle") -> {
                setParticlesEnabled(true)
                "Particles ON"
            }
            t.contains("wave") -> {
                setWaveVisualizerMode(true)
                setHudExpanded(true)
                "Wave Visualizer"
            }
            t.contains("float_off") || t.contains("floating_off") -> {
                setFloatingHudVisible(false)
                "Floating HUD OFF"
            }
            t.contains("float") -> {
                setFloatingHudVisible(true)
                "Floating HUD ON"
            }
            t.contains("collapse") || t.contains("hud_off") || t.contains("hide") -> {
                setHudExpanded(false)
                "HUD Collapsed"
            }
            t.contains("holographic") -> {
                setHudEffectTheme(HudEffectTheme.HOLOGRAPHIC_SCAN)
                setHudExpanded(true)
                "Holographic HUD"
            }
            t.contains("minimal") -> {
                setHudEffectTheme(HudEffectTheme.MINIMAL)
                "Minimal HUD"
            }
            else -> {
                setHudEffectTheme(HudEffectTheme.QUANTUM_GRID)
                setHudExpanded(true)
                "Quantum HUD"
            }
        }
    }

    fun pinSystemHudReportToBoard() {
        viewModelScope.launch {
            val snap = systemHudMonitor.readSnapshot()
            val title = "Maya AI HUD — System Diagnostic"
            val summary = snap.toHinglishSpokenSummary(currentProfile().bossName)
            repository.createDocument(
                title = title,
                category = "dashboard",
                summary = summary,
                content = snap.toBoardDiagnosticMarkdown()
            )
            appendMayaBubble(
                "System HUD report Maya Board pe pin kar di hai, ${currentProfile().bossName}.",
                badge = "HUD Report Pinned",
                linkedBoardTitle = title
            )
        }
    }

    fun builtInMayaHudSkills(): List<GitHubRemoteSkill> = gitHubSkillStoreClient.builtInMayaUiHudSkills()

    fun fetchGitHubSkills(repoInput: String) {
        val query = repoInput.trim().ifBlank { "HunterisLive-1/maya-ui" }
        viewModelScope.launch {
            _gitHubSkillStoreState.value = GitHubSkillStoreState.Loading(query)
            val res = gitHubSkillStoreClient.fetchSkillsFromRepo(query)
            res.fold(
                onSuccess = { (slug, skills) ->
                    _gitHubSkillStoreState.value = GitHubSkillStoreState.Loaded(slug, skills)
                },
                onFailure = { err ->
                    _gitHubSkillStoreState.value = GitHubSkillStoreState.Error(
                        err.localizedMessage ?: "GitHub repo se skills fetch nahi ho sakein."
                    )
                }
            )
        }
    }

    fun requestInstallGitHubSkill(skill: GitHubRemoteSkill) {
        val existing = (skillsState.value as? DataUiState.Success)?.data.orEmpty()
        if (existing.size >= 25) {
            appendMayaBubble("Boss, max 25 skills limit poori ho chuki hai — pehle koi purani skill delete karein.", badge = "Skills Limit (25)", isError = true)
            return
        }
        val ask = "Boss, '${skill.name}' skill (${skill.sourceRepo}) Maya me save kar doon?"
        _pendingConfirmation.value = PendingConfirmation(
            title = "Install GitHub Skill '${skill.name}'?",
            previewDetails = "Source: ${skill.sourceRepo}\nTrigger: ${skill.trigger}\nSteps:\n${skill.stepsText.take(400)}",
            promptSpoken = ask,
            onConfirmExecute = {
                repository.createSkillPlaybook("skill", skill.name, skill.trigger, skill.stepsText).getOrThrow()
                "'${skill.name}' skill Maya me jud gayi, Boss."
            }
        )
    }

    fun requestInstallMayaHudSkillPack() {
        val pack = gitHubSkillStoreClient.builtInMayaUiHudSkills()
        val existingNames = (skillsState.value as? DataUiState.Success)?.data
            .orEmpty()
            .map { it.name.lowercase(Locale.ROOT) }
            .toSet()
        val toInstall = pack.filter { it.name.lowercase(Locale.ROOT) !in existingNames }
        if (toInstall.isEmpty()) {
            appendMayaBubble("Boss, maya-ui ke saare HUD skills pehle se installed hain!", badge = "HUD Pack Active")
            return
        }
        val ask = "Boss, HunterisLive-1/maya-ui ke ${toInstall.size} HUD skills ek saath Maya me add kar doon?"
        _pendingConfirmation.value = PendingConfirmation(
            title = "Install maya-ui HUD Skill Pack (${toInstall.size} skills)?",
            previewDetails = toInstall.joinToString("\n") { "• ${it.name} — \"${it.trigger}\"" },
            promptSpoken = ask,
            onConfirmExecute = {
                val currentCount = (skillsState.value as? DataUiState.Success)?.data?.size ?: 0
                val allowed = (25 - currentCount).coerceAtLeast(0)
                val installing = toInstall.take(allowed)
                for (item in installing) {
                    repository.createSkillPlaybook("skill", item.name, item.trigger, item.stepsText).getOrThrow()
                }
                "maya-ui ke ${installing.size} HUD skills save ho gaye, Boss."
            }
        )
    }

    private fun appendMayaBubble(
        text: String,
        badge: String? = null,
        linkedBoardTitle: String? = null,
        attachedBitmap: Bitmap? = null,
        isError: Boolean = false,
        speakOut: Boolean = true
    ) {
        _messages.value = _messages.value + ChatBubble(
            isFromBoss = false,
            text = text,
            badge = badge,
            linkedBoardTitle = linkedBoardTitle,
            attachedBitmap = attachedBitmap,
            isError = isError
        )
        var spokeViaTts = false
        if (speakOut) {
            runCatching {
                val prof = currentProfile()
                spokeViaTts = voiceEngine.speak(
                    text = text,
                    persona = MayaPersona.fromId(prof.persona),
                    tone = MayaTone.fromId(prof.tone),
                    voiceEnabled = voiceReplyEnabled.value
                )
            }.onFailure { e ->
                Log.w(TAG, "TTS speak failed", e)
            }
        }
        if (!spokeViaTts) {
            voiceEngine.notifyTurnCompletedWithoutSpeech()
        }
    }

    override fun onCleared() {
        super.onCleared()
        MayaBrainBus.detachUiDelegate(uiBrainTranscriptHandler)
        if (voiceEngine !== MayaVoiceEngine.peekSharedInstance()) {
            voiceEngine.shutdown()
        }
    }

    companion object {
        fun provideFactory(
            currentUserId: String = MayaRepository.LOCAL_BOSS_ID,
            displayName: String = "Boss"
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val keyStore = ApiKeyConfigStore(app)
                    MayaViewModel(
                        repository = MayaRepository(app, currentUserId),
                        phoneExecutor = PhoneActionExecutor(app),
                        voiceEngine = MayaVoiceEngine.getSharedInstance(app),
                        aiBrain = MayaAiBrain(apiKeyStore = keyStore),
                        liveInfoClient = LiveInfoClient(),
                        currentUserId = currentUserId,
                        initialDisplayName = displayName.ifBlank { "Boss" },
                        apiKeyStore = keyStore
                    )
                }
            }
    }
}
