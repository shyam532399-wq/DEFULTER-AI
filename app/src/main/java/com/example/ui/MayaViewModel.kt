package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
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
import com.example.domain.DeviceTelemetry
import com.example.domain.LiveInfoClient
import com.example.domain.MayaAiBrain
import com.example.domain.MayaVoiceEngine
import com.example.domain.PhoneActionExecutor
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
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

class MayaViewModel(
    private val repository: MayaRepository,
    private val phoneExecutor: PhoneActionExecutor,
    private val voiceEngine: MayaVoiceEngine,
    private val aiBrain: MayaAiBrain,
    private val liveInfoClient: LiveInfoClient,
    private val currentUserId: String,
    private val initialDisplayName: String
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
                text = "Haan $initialDisplayName, Maya ready hai. Boliye kya kaam niptayein — torch, alarm, WhatsApp, live weather, market snapshot, ya koi research Board pe rakhun?",
                badge = "Maya • Ready"
            )
        )
    )
    val messages: StateFlow<List<ChatBubble>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _voiceReplyEnabled = MutableStateFlow(true)
    val voiceReplyEnabled: StateFlow<Boolean> = _voiceReplyEnabled.asStateFlow()

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

    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript
    val wakeWordEnabled: StateFlow<Boolean> = voiceEngine.wakeWordModeEnabled

    init {
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

    fun toggleVoiceReply() {
        val next = !_voiceReplyEnabled.value
        _voiceReplyEnabled.value = next
        if (!next) voiceEngine.stopSpeaking()
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
            onResult = { spokenText ->
                handleVoiceTranscript(spokenText)
            },
            onError = { errMsg ->
                appendMayaBubble(errMsg, badge = "Mic", isError = false, speakOut = false)
            }
        )
    }

    fun stopVoiceInput() {
        voiceEngine.stopListening()
    }

    private fun handleVoiceTranscript(transcript: String) {
        val prof = currentProfile()
        // ListenMode == BOSS_ONLY + Voice Guardian enrolled passphrase check if Guard Armed
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

        // Check if Boss is answering "haan" / "no" to a PendingConfirmation (Golden Rule 5)
        val pending = _pendingConfirmation.value
        if (pending != null) {
            val lower = trimmed.lowercase(Locale.ROOT)
            if (lower in setOf("haan", "ha", "yes", "kar do", "bhej do", "bhej de", "save kar do", "delete kar do", "ok", "confirm")) {
                confirmPendingAction()
                return
            } else if (lower in setOf("nahi", "na", "no", "cancel", "rehne do", "mat bhejo", "ruko")) {
                cancelPendingAction()
                return
            }
        }

        // Quick voice/text listen mode or persona shortcuts
        val lowerCmd = trimmed.lowercase(Locale.ROOT)
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

        viewModelScope.launch {
            _isThinking.value = true
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
                recentTranscript = recent
            )

            _isThinking.value = false
            decisionRes.fold(
                onSuccess = { decision ->
                    executeAgentDecision(decision, memories)
                },
                onFailure = { err ->
                    appendMayaBubble(
                        err.localizedMessage ?: "AI call fail hua, Boss.",
                        badge = "AI Error",
                        isError = true
                    )
                }
            )
        }
    }

    private suspend fun executeAgentDecision(decision: AgentDecision, memories: List<MemoryItem>) {
        // Save Board document automatically if long content was generated (Golden Rule 6)
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

            "OPEN_URL" -> {
                val res = phoneExecutor.openUrl(decision.target)
                appendMayaBubble(res.messageHinglish, badge = "Browser")
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

            // GOLDEN RULE 5: Risky actions ALWAYS gate behind confirmed=true!
            "SEND_WHATSAPP" -> {
                val who = decision.recipient.ifBlank { decision.phone.ifBlank { "Contact" } }
                val msg = decision.messageBody.ifBlank { decision.target }
                val ask = "Boss, $who ko WhatsApp pe '$msg' bhej dun?"
                _pendingConfirmation.value = PendingConfirmation(
                    title = "WhatsApp Message Confirm Karein",
                    previewDetails = "To: $who ${if (decision.phone.isNotBlank()) "(${decision.phone})" else ""}\nMessage: \"$msg\"",
                    promptSpoken = ask,
                    onConfirmExecute = {
                        phoneExecutor.sendWhatsApp(decision.phone, msg).messageHinglish
                    }
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
                    onConfirmExecute = {
                        phoneExecutor.sendSms(decision.phone, msg).messageHinglish
                    }
                )
                appendMayaBubble(ask, badge = "Confirm Required")
            }

            "SEND_EMAIL" -> {
                val to = decision.recipient
                val subj = decision.subject.ifBlank { "Update" }
                val body = decision.messageBody.ifBlank { decision.boardContent }
                // Save single clean email draft on Maya Board first (Golden Rule 6 + Section 5)
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
                    onConfirmExecute = {
                        phoneExecutor.sendEmail(to, subj, body).messageHinglish
                    }
                )
                appendMayaBubble(ask, badge = "Confirm Email", linkedBoardTitle = "Email Draft: $subj")
            }

            "DIAL_CALL" -> {
                val res = phoneExecutor.dialCall(decision.phone.ifBlank { decision.target })
                appendMayaBubble(res.messageHinglish, badge = "Call")
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

            "GET_WEATHER" -> {
                val info = liveInfoClient.fetchWeather(decision.target.ifBlank { "Delhi" })
                if (info.success && info.boardMarkdown != null && info.boardTitle != null) {
                    repository.createDocument(info.boardTitle, info.boardCategory ?: "note", info.spokenSummary, info.boardMarkdown)
                }
                appendMayaBubble(info.spokenSummary, badge = "Live Weather", linkedBoardTitle = info.boardTitle)
            }

            "GET_CRYPTO" -> {
                val info = liveInfoClient.fetchCryptoPrice(decision.target.ifBlank { "bitcoin" })
                if (info.success && info.boardMarkdown != null && info.boardTitle != null) {
                    repository.createDocument(info.boardTitle, "market", info.spokenSummary, info.boardMarkdown)
                }
                appendMayaBubble(info.spokenSummary, badge = "Markets", linkedBoardTitle = info.boardTitle)
            }

            "GET_FOREX" -> {
                val from = decision.target.ifBlank { "USD" }
                val to = decision.phone.ifBlank { "INR" }
                val info = liveInfoClient.fetchForexRate(from, to)
                if (info.success && info.boardMarkdown != null && info.boardTitle != null) {
                    repository.createDocument(info.boardTitle, "market", info.spokenSummary, info.boardMarkdown)
                }
                appendMayaBubble(info.spokenSummary, badge = "Forex", linkedBoardTitle = info.boardTitle)
            }

            "GET_NEWS" -> {
                val info = liveInfoClient.fetchFreshNews()
                if (info.success && info.boardMarkdown != null && info.boardTitle != null) {
                    repository.createDocument(info.boardTitle, "research", info.spokenSummary, info.boardMarkdown)
                }
                appendMayaBubble(info.spokenSummary, badge = "Fresh News", linkedBoardTitle = info.boardTitle)
            }

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

            // Section 4 Skill Creation Rule: Preview playbook & ask "Save kar doon?" first!
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

            else -> {
                appendMayaBubble(
                    decision.spokenReply,
                    badge = if (savedBoardTitle != null) "Board Updated" else null,
                    linkedBoardTitle = savedBoardTitle
                )
            }
        }
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
        voiceEngine.speak(ask, MayaPersona.fromId(currentProfile().persona), MayaTone.fromId(currentProfile().tone), _voiceReplyEnabled.value)
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

    // --- Quick Direct Actions & Profile Mutations ---

    fun switchPersona(persona: MayaPersona) {
        viewModelScope.launch {
            repository.updateProfileFields(mapOf("persona" to persona.id))
        }
    }

    fun switchTone(tone: MayaTone) {
        viewModelScope.launch {
            repository.updateProfileFields(mapOf("tone" to tone.id))
        }
    }

    fun setListenMode(mode: ListenMode) {
        viewModelScope.launch {
            repository.updateProfileFields(mapOf("listenMode" to mode.id))
        }
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
        viewModelScope.launch {
            repository.updateProfileFields(mapOf("guardArmed" to armed))
        }
    }

    fun setTouchGuard(armed: Boolean) {
        _touchGuardAlert.value = null
        viewModelScope.launch {
            repository.updateProfileFields(mapOf("touchGuardArmed" to armed))
        }
    }

    /**
     * Section 3.13 Touch Guard Rule: Immediately alert Boss what happened, run NO tools, ask whether to disarm or keep active.
     */
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
            repository.createDocument(
                title = title,
                category = category,
                summary = content.take(160),
                content = content
            )
        }
    }

    fun togglePinDocument(doc: BoardDocument) {
        viewModelScope.launch {
            repository.updateDocumentPin(doc, !doc.pinned)
        }
    }

    fun addMemoryManual(kind: String, key: String, value: String) {
        viewModelScope.launch {
            repository.saveMemory(kind, key, value)
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
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
    }

    fun toggleSkill(skill: SkillPlaybook) {
        viewModelScope.launch {
            repository.toggleSkillEnabled(skill, !skill.enabled)
        }
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
    }

    fun addReminderManual(kind: String, title: String, detail: String, scheduled: String) {
        viewModelScope.launch {
            repository.createReminder(kind, title, detail, scheduled)
        }
    }

    fun toggleReminder(item: ReminderItem) {
        viewModelScope.launch {
            repository.toggleReminderCompleted(item, !item.completed)
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
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
        if (speakOut) {
            val prof = currentProfile()
            voiceEngine.speak(
                text = text,
                persona = MayaPersona.fromId(prof.persona),
                tone = MayaTone.fromId(prof.tone),
                voiceEnabled = _voiceReplyEnabled.value
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.shutdown()
    }

    companion object {
        fun provideFactory(currentUserId: String, displayName: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    MayaViewModel(
                        repository = MayaRepository(db),
                        phoneExecutor = PhoneActionExecutor(app),
                        voiceEngine = MayaVoiceEngine(app),
                        aiBrain = MayaAiBrain(),
                        liveInfoClient = LiveInfoClient(),
                        currentUserId = currentUserId,
                        initialDisplayName = displayName.ifBlank { "Boss" }
                    )
                }
            }
    }
}
