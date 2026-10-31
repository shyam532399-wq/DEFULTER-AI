package com.example.domain

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ListenMode
import com.example.data.model.MayaPersona
import com.example.data.model.MayaTone
import com.example.data.repository.MayaRepository
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "MayaPermanentBrainSvc"

data class BackgroundBrainTurn(
    val id: String = UUID.randomUUID().toString(),
    val bossSpeech: String,
    val mayaReply: String,
    val badge: String = "Human Brain • Background"
)

/**
 * Shared coordinator connecting [MayaPermanentBrainService], [MayaVoiceEngine], and [MayaViewModel]
 * so Maya maintains one unified voice/mic pipeline and syncs background turns into Cockpit chat.
 */
object MayaBrainBus {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _lastHeardPreview = MutableStateFlow("")
    val lastHeardPreview: StateFlow<String> = _lastHeardPreview.asStateFlow()

    private val _pendingBackgroundTurns = MutableStateFlow<List<BackgroundBrainTurn>>(emptyList())
    val pendingBackgroundTurns: StateFlow<List<BackgroundBrainTurn>> = _pendingBackgroundTurns.asStateFlow()

    @Volatile
    private var uiTranscriptDelegate: ((String) -> Unit)? = null

    @Volatile
    private var uiStopNotifyDelegate: (() -> Unit)? = null

    @Volatile
    private var uiBackgroundTurnDelegate: ((BackgroundBrainTurn) -> Unit)? = null

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun updateLastHeard(text: String) {
        _lastHeardPreview.value = text
    }

    fun attachUiDelegate(
        onTranscript: (String) -> Unit,
        onStoppedFromNotification: () -> Unit,
        onBackgroundTurn: ((BackgroundBrainTurn) -> Unit)? = null
    ) {
        uiTranscriptDelegate = onTranscript
        uiStopNotifyDelegate = onStoppedFromNotification
        uiBackgroundTurnDelegate = onBackgroundTurn
        if (onBackgroundTurn != null) {
            drainBackgroundTurns().forEach(onBackgroundTurn)
        }
    }

    fun detachUiDelegate(ownerHandler: (String) -> Unit) {
        if (uiTranscriptDelegate === ownerHandler) {
            uiTranscriptDelegate = null
            uiStopNotifyDelegate = null
            uiBackgroundTurnDelegate = null
        }
    }

    fun dispatchToUiIfAttached(transcript: String): Boolean {
        val delegate = uiTranscriptDelegate ?: return false
        delegate.invoke(transcript)
        return true
    }

    fun notifyStoppedFromService() {
        uiStopNotifyDelegate?.invoke()
    }

    fun enqueueBackgroundTurn(turn: BackgroundBrainTurn) {
        val turnDelegate = uiBackgroundTurnDelegate
        if (turnDelegate != null) {
            turnDelegate.invoke(turn)
        } else {
            _pendingBackgroundTurns.value = (_pendingBackgroundTurns.value + turn).takeLast(30)
        }
    }

    fun drainBackgroundTurns(): List<BackgroundBrainTurn> {
        val current = _pendingBackgroundTurns.value
        if (current.isNotEmpty()) {
            _pendingBackgroundTurns.value = emptyList()
        }
        return current
    }
}

/**
 * Foreground Service (`foregroundServiceType="microphone"`) enabling Maya's Always-On
 * Human-Brain Background Listening & Spoken Conversational Reply loop.
 */
class MayaPermanentBrainService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var phaseObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var voiceEngine: MayaVoiceEngine
    private lateinit var repository: MayaRepository
    private lateinit var apiKeyStore: ApiKeyConfigStore
    private lateinit var aiBrain: MayaAiBrain
    private lateinit var phoneExecutor: PhoneActionExecutor
    private lateinit var hudMonitor: SystemHudMonitor
    private val liveInfoClient = LiveInfoClient()

    private val headlessHistory = ArrayDeque<Pair<String, String>>()
    private var latestSubtitle: String = "Silent listening active (no beep) — seedha boliye, Boss"

    override fun onCreate() {
        super.onCreate()
        voiceEngine = MayaVoiceEngine.getSharedInstance(applicationContext)
        repository = MayaRepository(applicationContext, MayaRepository.LOCAL_BOSS_ID)
        apiKeyStore = ApiKeyConfigStore(applicationContext)
        aiBrain = MayaAiBrain(apiKeyStore = apiKeyStore)
        phoneExecutor = PhoneActionExecutor(applicationContext)
        hudMonitor = SystemHudMonitor(applicationContext)
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_BRAIN

        if (action == ACTION_STOP_BRAIN) {
            stopBrainModeCleanly(speakFarewell = false)
            return START_NOT_STICKY
        }

        if (!hasRecordAudioPermission(this)) {
            Log.w(TAG, "RECORD_AUDIO permission missing; stopping MayaPermanentBrainService")
            voiceEngine.setPermanentBrainLoop(false)
            MayaBrainBus.setServiceRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_TOGGLE_PAUSE) {
            val nextPaused = !voiceEngine.permanentBrainPaused.value
            voiceEngine.setPermanentBrainPaused(nextPaused)
            latestSubtitle = if (nextPaused) {
                "Brain paused hai — Resume dabayein jab baat karni ho"
            } else {
                "Wapas sun rahi hoon, Boss — seedha boliye"
            }
            updateOngoingNotification(voiceEngine.brainLoopPhase.value)
            return START_STICKY
        }

        // Start or refresh foreground Human Brain session
        acquireSafeWakeLock()
        MayaBrainBus.setServiceRunning(true)

        val currentPhase = if (voiceEngine.permanentBrainPaused.value) {
            BrainLoopPhase.PAUSED
        } else {
            BrainLoopPhase.LISTENING
        }
        promoteToForeground(currentPhase)

        voiceEngine.setPermanentBrainLoop(
            enabled = true,
            onTranscript = ::onContinuousSpeechHeard,
            onError = { errMsg ->
                latestSubtitle = errMsg
                updateOngoingNotification(voiceEngine.brainLoopPhase.value)
            }
        )

        observePhaseForNotification()
        return START_STICKY
    }

    private fun observePhaseForNotification() {
        if (phaseObserverJob?.isActive == true) return
        phaseObserverJob = serviceScope.launch {
            voiceEngine.brainLoopPhase.collect { phase ->
                if (MayaBrainBus.serviceRunning.value) {
                    updateOngoingNotification(phase)
                }
            }
        }
    }

    private fun onContinuousSpeechHeard(spokenText: String) {
        val cleaned = spokenText.trim()
        if (cleaned.isBlank()) {
            voiceEngine.notifyTurnCompletedWithoutSpeech()
            return
        }

        MayaBrainBus.updateLastHeard(cleaned)
        val lower = cleaned.lowercase(Locale.ROOT)

        // Instant voice stop/pause/silent commands that work even when phone screen is off or in another app
        if (lower in setOf(
                "permanent listening off",
                "permanent listening band",
                "permanent listening band karo",
                "background listening band karo",
                "brain mode off",
                "brain mode band karo",
                "stop permanent listening"
            )
        ) {
            stopBrainModeCleanly(speakFarewell = false)
            return
        }

        if (lower in setOf("pause listening", "thodi der mat suno", "brain pause karo")) {
            voiceEngine.setPermanentBrainPaused(true)
            latestSubtitle = "Brain paused — Resume dabayein"
            updateOngoingNotification(BrainLoopPhase.PAUSED)
            return
        }

        if (lower in setOf(
                "beep band karo",
                "bip band karo",
                "beep sound band karo",
                "silent listening",
                "silent listening on",
                "silent listening on karo"
            )
        ) {
            voiceEngine.setSilentListeningEnabled(true)
            latestSubtitle = "Silent Listening ON — koi beep sound nahi aayega"
            updateOngoingNotification(BrainLoopPhase.LISTENING)
            voiceEngine.notifyTurnCompletedWithoutSpeech()
            return
        }

        if (lower in setOf(
                "kaam karne ke baad sound band karo",
                "kaam ke baad sound band karo",
                "voice reply off",
                "voice reply band karo",
                "awaaz band karo",
                "bolna band karo",
                "chupchap kaam karo"
            )
        ) {
            voiceEngine.setSilentListeningEnabled(true)
            voiceEngine.setVoiceReplyEnabled(false)
            latestSubtitle = "100% Silent Mode ON — bina beep aur bina bole kaam hoga"
            updateOngoingNotification(BrainLoopPhase.LISTENING)
            voiceEngine.notifyTurnCompletedWithoutSpeech()
            return
        }

        if (lower in setOf("voice reply on", "awaaz chalu karo", "bolkar jawab do")) {
            voiceEngine.setVoiceReplyEnabled(true)
            latestSubtitle = "Voice reply wapas ON (Silent no-beep mic active)"
            updateOngoingNotification(BrainLoopPhase.LISTENING)
            voiceEngine.notifyTurnCompletedWithoutSpeech()
            return
        }

        // Delegate to MayaViewModel when Cockpit/App UI is alive; otherwise run autonomous background brain!
        val handledByUi = MayaBrainBus.dispatchToUiIfAttached(cleaned)
        if (!handledByUi) {
            processAutonomousBackgroundTurn(cleaned)
        }
    }

    private fun processAutonomousBackgroundTurn(userInput: String) {
        serviceScope.launch {
            voiceEngine.notifyThinkingStarted()
            latestSubtitle = "Suna: \"${userInput.take(60)}\""
            updateOngoingNotification(BrainLoopPhase.THINKING)

            try {
                val prof = repository.ensureDefaultProfile(MayaRepository.LOCAL_BOSS_ID, "Boss")
                    .getOrNull()
                val bossName = prof?.bossName ?: "Boss"
                val persona = MayaPersona.fromId(prof?.persona ?: "maya")
                val tone = MayaTone.fromId(prof?.tone ?: "calm")
                val listenMode = ListenMode.fromId(prof?.listenMode ?: "boss_only")

                // Respect Voice Guardian passphrase in Boss-only mode when armed
                if (listenMode == ListenMode.BOSS_ONLY &&
                    prof?.guardArmed == true &&
                    prof.voiceEnrolled &&
                    prof.voicePassphrase.isNotBlank()
                ) {
                    val expected = prof.voicePassphrase.lowercase(Locale.ROOT)
                    if (!userInput.lowercase(Locale.ROOT).contains(expected) &&
                        !userInput.lowercase(Locale.ROOT).contains(bossName.lowercase(Locale.ROOT))
                    ) {
                        val guardMsg = "Voice Guardian active hai, $bossName. Passphrase bolkar verify karein."
                        latestSubtitle = guardMsg
                        val spokeGuard = voiceEngine.speak(
                            guardMsg,
                            persona,
                            tone,
                            voiceEnabled = voiceEngine.voiceReplyEnabled.value
                        )
                        if (!spokeGuard) {
                            voiceEngine.notifyTurnCompletedWithoutSpeech()
                        }
                        return@launch
                    }
                }

                val memories = runCatching { repository.observeMemories().first() }.getOrDefault(emptyList())
                val skills = runCatching { repository.observeSkills().first() }.getOrDefault(emptyList())
                val telemetry = phoneExecutor.readTelemetry()
                val hudSnap = hudMonitor.readSnapshot()

                val decisionRes = aiBrain.processBossCommand(
                    userInput = userInput,
                    bossName = bossName,
                    persona = persona,
                    tone = tone,
                    listenMode = listenMode,
                    personalWiki = prof?.personalWiki.orEmpty(),
                    memories = memories,
                    skills = skills,
                    telemetry = telemetry,
                    autopilotConnected = MayaAccessibilityService.isConnected.value,
                    recentTranscript = headlessHistory.toList(),
                    hudSnapshot = hudSnap
                )

                val replyText = decisionRes.fold(
                    onSuccess = { decision ->
                        executeSafeBackgroundAction(decision)
                    },
                    onFailure = { err ->
                        err.localizedMessage ?: "Abhi network ya AI response me dikkat aayi, $bossName."
                    }
                )

                headlessHistory.addLast(bossName to userInput)
                headlessHistory.addLast(persona.displayName to replyText)
                while (headlessHistory.size > 10) {
                    headlessHistory.removeFirst()
                }

                MayaBrainBus.enqueueBackgroundTurn(
                    BackgroundBrainTurn(
                        bossSpeech = userInput,
                        mayaReply = replyText
                    )
                )

                latestSubtitle = replyText.take(90)
                val spoke = voiceEngine.speak(
                    text = replyText,
                    persona = persona,
                    tone = tone,
                    voiceEnabled = voiceEngine.voiceReplyEnabled.value
                )
                if (!spoke) {
                    voiceEngine.notifyTurnCompletedWithoutSpeech()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Autonomous background brain error", e)
                voiceEngine.notifyTurnCompletedWithoutSpeech()
            }
        }
    }

    private suspend fun executeSafeBackgroundAction(decision: AgentDecision): String {
        if (decision.boardContent.isNotBlank() && decision.actionType != "SEND_EMAIL") {
            repository.createDocument(
                title = decision.boardTitle.ifBlank { "Maya Background Note" },
                category = decision.boardCategory,
                summary = decision.spokenReply,
                content = decision.boardContent
            )
        }

        return when (decision.actionType) {
            "SET_TORCH_ON" -> phoneExecutor.setTorch(true).messageHinglish
            "SET_TORCH_OFF" -> phoneExecutor.setTorch(false).messageHinglish
            "GET_BATTERY" -> phoneExecutor.getBatteryStatusText().messageHinglish
            "SET_VOLUME" -> phoneExecutor.setMediaVolumePercent(decision.numberVal).messageHinglish
            "SET_RINGER" -> phoneExecutor.setRingerMode(decision.target.ifBlank { "vibrate" }).messageHinglish
            "MEDIA_CONTROL" -> phoneExecutor.mediaControl(decision.target.ifBlank { "play_pause" }).messageHinglish
            "GET_TIME" -> phoneExecutor.getCurrentTimeText(decision.target.takeIf { it.isNotBlank() })
            "REMEMBER_FACT" -> {
                val key = decision.subject.ifBlank { decision.target.ifBlank { "Boss Fact" } }
                val value = decision.messageBody.ifBlank { decision.spokenReply }
                repository.saveMemory("fact", key, value)
                decision.spokenReply
            }
            "ADD_REMINDER" -> {
                val title = decision.target.ifBlank { decision.subject.ifBlank { "Reminder" } }
                repository.createReminder("reminder", title, decision.messageBody, decision.phone)
                decision.spokenReply
            }
            "GET_WEATHER" -> liveInfoClient.fetchWeather(decision.target.ifBlank { "Delhi" }).spokenSummary
            "GET_STOCK" -> liveInfoClient.fetchStockQuote(decision.target.ifBlank { "RELIANCE.NS" }).spokenSummary
            "GET_CRYPTO" -> liveInfoClient.fetchCryptoPrice(decision.target.ifBlank { "bitcoin" }).spokenSummary
            "GET_NEWS" -> liveInfoClient.fetchFreshNews().spokenSummary
            "SEARCH_WEB" -> liveInfoClient.searchWebLive(decision.target.ifBlank { decision.messageBody }).spokenSummary
            "SEND_WHATSAPP", "SEND_TELEGRAM", "SEND_SMS", "SEND_EMAIL", "DELETE_BOARD_DOC", "DELETE_SKILL", "TRIGGER_SOS" -> {
                // Golden Rule 5: Never execute risky/irreversible actions silently in background without screen confirmation
                "Boss, safety rule ke hisaab se ye action confirm karne ke liye Maya screen kholkar 'Haan' bol dijiye."
            }
            else -> decision.spokenReply
        }
    }

    private fun stopBrainModeCleanly(speakFarewell: Boolean) {
        MayaBrainBus.setServiceRunning(false)
        voiceEngine.setPermanentBrainLoop(false)
        MayaBrainBus.notifyStoppedFromService()
        if (speakFarewell && voiceEngine.voiceReplyEnabled.value) {
            runCatching {
                voiceEngine.speak(
                    text = "Permanent background listening band kar diya hai, Boss.",
                    persona = MayaPersona.MAYA,
                    tone = MayaTone.CALM,
                    voiceEnabled = true
                )
            }
        }
        releaseSafeWakeLock()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun acquireSafeWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Maya:PermanentBrainWakeLock").apply {
                setReferenceCounted(false)
                // Acquire up to 4 hours per start cycle (auto-renewed on service commands, never leaks forever)
                acquire(4 * 60 * 60 * 1000L)
            }
        }.onFailure { e ->
            Log.w(TAG, "WakeLock acquire skipped", e)
        }
    }

    private fun releaseSafeWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    private fun promoteToForeground(phase: BrainLoopPhase) {
        val notification = buildNotification(phase)
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgType)
        }.onFailure { e ->
            Log.e(TAG, "startForeground failed", e)
        }
    }

    private fun updateOngoingNotification(phase: BrainLoopPhase) {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.notify(NOTIFICATION_ID, buildNotification(phase))
        }
    }

    private fun buildNotification(phase: BrainLoopPhase): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePauseIntent = Intent(this, MayaPermanentBrainService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val togglePausePending = PendingIntent.getService(
            this,
            101,
            togglePauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MayaPermanentBrainService::class.java).apply {
            action = ACTION_STOP_BRAIN
        }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPaused = phase == BrainLoopPhase.PAUSED || voiceEngine.permanentBrainPaused.value
        val title = when (phase) {
            BrainLoopPhase.LISTENING -> "Maya Silent Brain • Sun rahi hoon, Boss"
            BrainLoopPhase.THINKING -> "Maya Brain • Kaam kar rahi hoon…"
            BrainLoopPhase.SPEAKING -> "Maya Brain • Jawab de rahi hoon…"
            BrainLoopPhase.PAUSED -> "Maya Brain • Paused"
            BrainLoopPhase.RECONNECTING -> "Maya Silent Brain • Mic ready ho raha hai…"
            BrainLoopPhase.OFF -> "Maya Brain"
        }

        val pauseActionLabel = getString(
            if (isPaused) R.string.brain_action_resume else R.string.brain_action_pause
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(latestSubtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(latestSubtitle))
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setDefaults(0)
            .setVibrate(null)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, pauseActionLabel, togglePausePending)
            .addAction(0, getString(R.string.brain_action_stop), stopPending)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            runCatching { nm.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.brain_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.brain_notif_channel_desc)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    override fun onDestroy() {
        MayaBrainBus.setServiceRunning(false)
        phaseObserverJob?.cancel()
        serviceScope.cancel()
        releaseSafeWakeLock()
        super.onDestroy()
    }

    companion object {
        private const val LEGACY_CHANNEL_ID = "maya_human_brain_channel"
        const val CHANNEL_ID = "maya_silent_brain_channel_v2"
        const val NOTIFICATION_ID = 4108

        const val ACTION_START_BRAIN = "com.example.action.START_BRAIN"
        const val ACTION_TOGGLE_PAUSE = "com.example.action.TOGGLE_PAUSE_BRAIN"
        const val ACTION_STOP_BRAIN = "com.example.action.STOP_BRAIN"

        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun start(context: Context): Boolean {
            if (!hasRecordAudioPermission(context)) return false
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, MayaPermanentBrainService::class.java).apply {
                action = ACTION_START_BRAIN
            }
            return runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
                true
            }.getOrElse { e ->
                Log.e(TAG, "Failed to start MayaPermanentBrainService", e)
                false
            }
        }

        fun togglePause(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, MayaPermanentBrainService::class.java).apply {
                action = ACTION_TOGGLE_PAUSE
            }
            runCatching { appCtx.startService(intent) }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            MayaBrainBus.setServiceRunning(false)
            MayaVoiceEngine.peekSharedInstance()?.setPermanentBrainLoop(false)
            runCatching {
                appCtx.stopService(Intent(appCtx, MayaPermanentBrainService::class.java))
            }
        }
    }
}
