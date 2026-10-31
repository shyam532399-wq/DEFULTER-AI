package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.MayaPersona
import com.example.data.model.MayaTone
import com.example.data.model.ReminderItem
import com.example.data.model.UserProfile
import com.example.domain.ApiKeyConnectionStatus
import com.example.domain.BrainLoopPhase
import com.example.domain.HomeWeatherSnapshot
import com.example.domain.HudVisualConfig
import com.example.domain.MayaNotificationListenerService
import com.example.domain.MayaSystemSetupHelper
import com.example.domain.SystemHudSnapshot
import com.example.ui.ChatBubble
import com.example.ui.MayaViewModel
import com.example.ui.PendingConfirmation
import com.example.ui.components.MayaParticleEngineOverlay
import com.example.ui.components.MayaWaveMicVisualizer
import com.example.ui.components.RememberLiveFpsReporter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val HomeBg = Color(0xFF080C17)
private val HomeCardBg = Color(0xFF0E1628)
private val HomeCardBorder = Color(0xFF1D2C4A)
private val HomePillBg = Color(0xFF10192D)
private val HomePillBorder = Color(0xFF1F3052)
private val HomeBlue = Color(0xFF2F63F5)
private val HomeBlueSoft = Color(0xFF6EA2FF)
private val HomeLavender = Color(0xFF9B8AFB)
private val HomeTextWhite = Color(0xFFF4F7FF)
private val HomeTextMuted = Color(0xFF7F93B8)

@Composable
fun MayaHomeDashboardScreen(
    viewModel: MayaViewModel,
    profile: UserProfile,
    messages: List<ChatBubble>,
    reminders: List<ReminderItem>,
    homeWeather: HomeWeatherSnapshot,
    isThinking: Boolean,
    isListening: Boolean,
    isSpeaking: Boolean,
    partialTranscript: String,
    permanentBrainEnabled: Boolean,
    permanentBrainPaused: Boolean,
    brainLoopPhase: BrainLoopPhase,
    silentListeningEnabled: Boolean,
    pendingConfirmation: PendingConfirmation?,
    hudSnapshot: SystemHudSnapshot,
    hudConfig: HudVisualConfig,
    audioLevel: Float,
    micLevel: Float,
    speakerAmplitude: Float,
    apiKeyStatus: ApiKeyConnectionStatus,
    onOpenChatTab: () -> Unit,
    onOpenBoardStudio: () -> Unit,
    onOpenMemoriesTab: () -> Unit,
    onOpenSetupWizardStep: (Int) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var inputText by rememberSaveable { mutableStateOf("") }
    var showDrawerSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val recentNotifications by MayaNotificationListenerService.recentItems.collectAsStateWithLifecycle()
    val notifConnected by MayaNotificationListenerService.isConnected.collectAsStateWithLifecycle()
    val needsBrainPermission by viewModel.needsBrainMicPermissionEvent.collectAsStateWithLifecycle()

    RememberLiveFpsReporter(onFpsMeasured = viewModel::reportLiveFps)

    LaunchedEffect(Unit) {
        viewModel.refreshHomeWeather(force = false)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceInput()
    }

    val permanentBrainPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val micOk = grantMap[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk) {
            viewModel.setPermanentBrainMode(enabled = true, hasAudioPermission = true)
        }
    }

    fun requestPermanentBrainPerms() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        permanentBrainPermissionLauncher.launch(perms)
    }

    LaunchedEffect(needsBrainPermission) {
        if (needsBrainPermission) {
            viewModel.clearBrainPermissionRequest()
            requestPermanentBrainPerms()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap != null) {
                val q = inputText
                inputText = ""
                viewModel.analyzeImageAttachment(bitmap, q)
            }
        }
    }

    val now = remember { LocalDateTime.now() }
    val greetingFirstLine = remember(now.hour) {
        when (now.hour) {
            in 5..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            else -> "Good evening,"
        }
    }
    val greetingSecondLine = remember(profile.bossName) {
        val clean = profile.bossName.trim()
        if (clean.isBlank() || clean.equals("Boss", ignoreCase = true)) "there" else clean
    }
    val dayOfMonthStr = remember(now.dayOfMonth) { now.dayOfMonth.toString() }
    val dayMonthSubStr = remember(now.dayOfYear) {
        now.format(DateTimeFormatter.ofPattern("EEE, MMM", Locale.ENGLISH))
    }

    val activeTone = MayaTone.fromId(profile.tone)
    val moodTitle = when (activeTone) {
        MayaTone.CALM -> "Warm"
        MayaTone.EXCITED -> "Bright"
        MayaTone.SOFT -> "Calm"
    }

    val statusSubtitle = when {
        partialTranscript.isNotBlank() -> "\"$partialTranscript\""
        isListening -> "Maya is listening silently…"
        isThinking -> "Maya is thinking…"
        isSpeaking -> "Maya is speaking…"
        permanentBrainEnabled && !permanentBrainPaused -> "Maya is listening in the background."
        else -> "Maya is ready to help you."
    }

    fun triggerVoiceToggle() {
        when {
            isListening -> viewModel.stopVoiceInput()
            isSpeaking -> viewModel.toggleVoiceReply()
            else -> {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.startVoiceInput()
                else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun submitTextCommand() {
        val clean = inputText.trim()
        if (clean.isNotBlank()) {
            inputText = ""
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.sendCommand(clean)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBg),
        contentAlignment = Alignment.TopCenter
    ) {
        if (hudConfig.particlesEnabled) {
            MayaParticleEngineOverlay(
                particleCount = 38,
                maxConnections = 28,
                showScanlines = false,
                testTagName = "cockpit_particle_engine",
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // 1. Video Top Bar: Hamburger | Maya | Bell (orange dot) + Circular MAYA Orb
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                IconButton(
                    onClick = { showDrawerSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.cd_home_menu),
                        tint = HomeTextWhite
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = stringResource(R.string.app_name),
                    color = HomeTextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_notifications_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.cd_home_notifications),
                            tint = HomeTextWhite
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-11).dp, y = 11.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            viewModel.togglePermanentBrainMode(
                                hasAudioPermission = hasMic,
                                onNeedPermission = { requestPermanentBrainPerms() }
                            )
                        }
                        .testTag("permanent_brain_chip"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (permanentBrainEnabled) {
                                        listOf(Color(0xFF2B6CB0), Color(0xFF102A4C))
                                    } else {
                                        listOf(Color(0xFF1E325C), Color(0xFF0C152B))
                                    }
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (permanentBrainEnabled) Color(0xFF34D399) else Color(0xFF4C7DFF),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MAYA",
                            color = HomeTextWhite,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Scrollable middle dashboard body so both compact and tall phones render cleanly
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 2. Mode / License Banner ("Free mode • 10:00 min left today" + "Activate")
                HomeLicenseModeBanner(
                    apiKeyStatus = apiKeyStatus,
                    onActivateClick = viewModel::openApiKeyDialog
                )

                // Conditional Permanent Brain Control Bar (only when Brain Mode is active)
                if (permanentBrainEnabled) {
                    HomePermanentBrainBanner(
                        paused = permanentBrainPaused,
                        phase = brainLoopPhase,
                        silentMode = silentListeningEnabled,
                        onTogglePause = viewModel::togglePermanentBrainPause,
                        onStop = { viewModel.setPermanentBrainMode(enabled = false) }
                    )
                }

                // Conditional Risky Action Confirmation Gate (Golden Rule 5)
                if (pendingConfirmation != null) {
                    HomeConfirmationGateCard(
                        confirmation = pendingConfirmation,
                        onConfirm = viewModel::confirmPendingAction,
                        onCancel = viewModel::cancelPendingAction
                    )
                }

                // 3. Greeting ("Good morning,\nthere") + "⚡ 1 Energy" HUD Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$greetingFirstLine\n$greetingSecondLine",
                            color = HomeTextWhite,
                            fontSize = 27.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusSubtitle,
                            color = HomeTextMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(HomePillBg)
                            .border(1.dp, HomePillBorder, RoundedCornerShape(16.dp))
                            .clickable { viewModel.setFullHudVisible(true) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("header_open_full_hud_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = stringResource(R.string.cd_open_full_maya_hud),
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "1",
                                color = HomeTextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Energy",
                            color = HomeTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // 4. Centerpiece Circular Glowing Sci-Fi HUD Ring ("AI ASSISTANT" / "M.A.Y.A" / "HOW CAN I HELP YOU?")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    MayaCircularSciFiDial(
                        audioLevel = maxOf(audioLevel, speakerAmplitude),
                        micLevel = micLevel,
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        isThinking = isThinking,
                        onTapDial = ::triggerVoiceToggle
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MayaWaveMicVisualizer(
                        micLevel = micLevel,
                        isListening = isListening,
                        isSpeaking = isSpeaking,
                        waveHeightDp = 16,
                        showHeaderLabel = false,
                        testTagName = "maya_wave_mic_visualizer",
                        modifier = Modifier.width(136.dp)
                    )
                }

                // 5. Quick Action Pills: Music | Study | Journal
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HomeQuickActionPill(
                        icon = Icons.Filled.MusicNote,
                        iconTint = Color(0xFFEC4899),
                        label = "Music",
                        tag = "home_pill_music",
                        onClick = { viewModel.sendCommand("Play chill focus music on Spotify") },
                        modifier = Modifier.weight(1f)
                    )
                    HomeQuickActionPill(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        iconTint = Color(0xFF60A5FA),
                        label = "Study",
                        tag = "home_pill_study",
                        onClick = { viewModel.sendCommand("Create a quick study plan note on Maya Board") },
                        modifier = Modifier.weight(1f)
                    )
                    HomeQuickActionPill(
                        icon = Icons.Filled.Edit,
                        iconTint = Color(0xFFFBBF24),
                        label = "Journal",
                        tag = "home_pill_journal",
                        onClick = { viewModel.sendCommand("Save a daily reflection journal entry on Maya Board") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 6. Live Context Info Cards: Weather | Today | Mood
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HomeContextInfoCard(
                        icon = Icons.Filled.WbSunny,
                        iconTint = Color(0xFFFBBF24),
                        headerLabel = "Weather",
                        primaryValue = if (homeWeather.isLoaded) "${homeWeather.tempCelsius}°" else "—",
                        secondaryLine1 = if (homeWeather.isLoaded) homeWeather.conditionLabel else "No data",
                        secondaryLine2 = if (homeWeather.isLoaded) "💧 ${homeWeather.humidityPercent}%" else null,
                        tag = "home_card_weather",
                        onClick = { viewModel.refreshHomeWeather(force = true) },
                        modifier = Modifier.weight(1f)
                    )
                    HomeContextInfoCard(
                        icon = Icons.Filled.CalendarToday,
                        iconTint = Color(0xFF60A5FA),
                        headerLabel = "Today",
                        primaryValue = dayOfMonthStr,
                        secondaryLine1 = dayMonthSubStr,
                        secondaryLine2 = null,
                        tag = "home_card_today",
                        onClick = onOpenMemoriesTab,
                        modifier = Modifier.weight(1f)
                    )
                    HomeContextInfoCard(
                        icon = Icons.Filled.FavoriteBorder,
                        iconTint = Color(0xFFE2E8F0),
                        headerLabel = "Mood",
                        primaryValue = moodTitle,
                        secondaryLine1 = "All good",
                        secondaryLine2 = null,
                        tag = "home_card_mood",
                        onClick = viewModel::cycleMoodTone,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Compact System Telemetry Strip (preserves live CPU/RAM/GPU/Disk visibility & HUD access)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B1222))
                        .border(1.dp, Color(0xFF17243F), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "CPU ${hudSnapshot.cpuLoadPercent}%",
                        color = HomeTextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "RAM ${hudSnapshot.ramUsedPercent}%",
                        color = HomeTextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "GPU ${hudSnapshot.gpuLoadPercent}%",
                        color = HomeTextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.testTag("hud_gauge_gpu")
                    )
                    Text(
                        text = "DISK ${hudSnapshot.storageUsedPercent}%",
                        color = HomeTextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.testTag("hud_gauge_disk")
                    )
                }

                // Live Latest Reply Bubble when Boss interacts directly from Home
                val latestMayaReply = messages.lastOrNull { !it.isFromBoss }
                if (messages.size > 1 && latestMayaReply != null) {
                    Surface(
                        color = HomeCardBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, HomeCardBorder, RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenChatTab)
                            .testTag("home_latest_reply_card")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = latestMayaReply.badge ?: "Maya",
                                    color = HomeBlueSoft,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Open Chat →",
                                    color = HomeTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = latestMayaReply.text,
                                color = HomeTextWhite,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 7. Bottom Docked "Ask Maya anything..." Pill Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(HomeCardBg)
                    .border(1.dp, HomeCardBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = stringResource(R.string.cd_attach_image),
                        tint = HomeTextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ask_maya_placeholder),
                            color = HomeTextMuted,
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = HomeTextWhite,
                            fontSize = 14.sp
                        ),
                        cursorBrush = SolidColor(HomeBlueSoft),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitTextCommand() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .testTag("command_input")
                    )
                }

                IconButton(
                    onClick = { submitTextCommand() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_command_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(HomeBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.cd_send_button),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (showDrawerSheet) {
            MayaHomeQuickDrawerSheet(
                activePersona = MayaPersona.fromId(profile.persona),
                silentListeningEnabled = silentListeningEnabled,
                floatingHudVisible = hudConfig.floatingHudVisible,
                onDismiss = { showDrawerSheet = false },
                onOpenSetupWizardStep = onOpenSetupWizardStep,
                onOpenApiKeyDialog = viewModel::openApiKeyDialog,
                onOpenFullHud = { viewModel.setFullHudVisible(true) },
                onToggleFloatingHud = viewModel::toggleFloatingHud,
                onToggleSilentListening = viewModel::toggleSilentListening,
                onSelectPersona = viewModel::switchPersona,
                onOpenBoardStudio = onOpenBoardStudio
            )
        }

        if (showNotificationsSheet) {
            MayaHomeNotificationsSheet(
                recentNotifications = recentNotifications,
                reminders = reminders,
                notificationReaderConnected = notifConnected,
                onOpenNotificationAccessSetup = { MayaSystemSetupHelper.openNotificationListenerSettings(context) },
                onDismiss = { showNotificationsSheet = false }
            )
        }
    }
}

@Composable
private fun HomeLicenseModeBanner(
    apiKeyStatus: ApiKeyConnectionStatus,
    onActivateClick: () -> Unit
) {
    val isConnected = apiKeyStatus is ApiKeyConnectionStatus.Connected
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HomeCardBg)
            .border(1.dp, HomeCardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.Lock,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF34D399) else Color(0xFFF5A524),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (apiKeyStatus is ApiKeyConnectionStatus.Connected) {
                    "Connected • ${apiKeyStatus.provider.shortBadge} unlimited talk"
                } else {
                    "Free mode • 10:00 min left today"
                },
                color = HomeTextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (apiKeyStatus is ApiKeyConnectionStatus.Connected) {
                    "Model: ${apiKeyStatus.activeModel} • ${apiKeyStatus.maskedKey}"
                } else {
                    "Activate a license for tools, PC link and unlimited talk"
                },
                color = HomeTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onActivateClick,
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF172749),
                contentColor = HomeBlueSoft
            ),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .border(1.dp, Color(0xFF2B4B8C), RoundedCornerShape(999.dp))
                .testTag("api_key_status_chip")
        ) {
            Text(
                text = if (isConnected) "Manage" else "Activate",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MayaCircularSciFiDial(
    audioLevel: Float,
    micLevel: Float,
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    onTapDial: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "maya_home_dial")
    val orbitAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dial_orbit"
    )

    Box(
        modifier = Modifier
            .size(216.dp)
            .clip(CircleShape)
            .clickable(onClick = onTapDial)
            .testTag("maya_reactive_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val maxR = minOf(w, h) / 2f
            val boost = (audioLevel * 0.35f).coerceIn(0f, 0.35f)

            // Outer ambient glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3B6EF6).copy(alpha = 0.24f + boost * 0.4f),
                        Color(0xFF1C3570).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR,
                center = center
            )

            // Precision 60-tick outer ring
            val tickOuterR = maxR * 0.94f
            val tickInnerR = maxR * 0.90f
            for (i in 0 until 60) {
                val rad = Math.toRadians((i * 6.0) + orbitAngle * 0.15)
                val sx = center.x + (tickInnerR * cos(rad)).toFloat()
                val sy = center.y + (tickInnerR * sin(rad)).toFloat()
                val ex = center.x + (tickOuterR * cos(rad)).toFloat()
                val ey = center.y + (tickOuterR * sin(rad)).toFloat()
                drawLine(
                    color = Color(0xFF4F7AD6).copy(alpha = if (i % 5 == 0) 0.45f else 0.18f),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = if (i % 5 == 0) 1.6f else 1f
                )
            }

            // Sweeping glowing cyan-white & royal-blue arcs (matching video 01:11 centerpiece)
            val arcR = maxR * 0.83f
            val arcTopLeft = Offset(center.x - arcR, center.y - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)

            drawArc(
                color = Color(0xFF1A2D57),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 3.dp.toPx())
            )

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF3B6EF6),
                        Color(0xFFA5F3FC),
                        Color.White,
                        Color(0xFF6366F1),
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = orbitAngle,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )

            val innerArcR = maxR * 0.76f
            drawArc(
                color = if (isListening) Color(0xFF34D399) else Color(0xFF60A5FA).copy(alpha = 0.65f),
                startAngle = -orbitAngle * 1.2f + 40f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - innerArcR, center.y - innerArcR),
                size = Size(innerArcR * 2f, innerArcR * 2f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )

            // Deep dark inner sphere
            val coreR = maxR * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF13203D),
                        Color(0xFF0A1122),
                        Color(0xFF060A14)
                    ),
                    center = Offset(center.x, center.y - coreR * 0.2f),
                    radius = coreR * 1.15f
                ),
                radius = coreR,
                center = center
            )
            drawCircle(
                color = Color(0xFF29467E).copy(alpha = 0.55f),
                radius = coreR,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = when {
                    isListening -> "L I S T E N I N G"
                    isThinking -> "T H I N K I N G"
                    isSpeaking -> "S P E A K I N G"
                    else -> "A I   A S S I S T A N T"
                },
                color = HomeTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) {
                        append("M.A.Y.")
                    }
                    withStyle(SpanStyle(color = HomeLavender)) {
                        append("A")
                    }
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "H O W   C A N   I   H E L P   Y O U ?",
                color = Color(0xFF6B7FA6),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
private fun HomeQuickActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(HomePillBg)
            .border(1.dp, HomePillBorder, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = HomeTextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun HomeContextInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    headerLabel: String,
    primaryValue: String,
    secondaryLine1: String,
    secondaryLine2: String?,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HomeCardBg)
            .border(1.dp, HomeCardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = headerLabel,
                color = HomeTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = primaryValue,
            color = HomeTextWhite,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        Column {
            Text(
                text = secondaryLine1,
                color = HomeTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!secondaryLine2.isNullOrBlank()) {
                Text(
                    text = secondaryLine2,
                    color = Color(0xFF60A5FA),
                    fontSize = 10.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HomePermanentBrainBanner(
    paused: Boolean,
    phase: BrainLoopPhase,
    silentMode: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D2038))
            .border(1.dp, Color(0xFF24548C), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("permanent_brain_banner")
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (paused) "Brain Mode Paused" else "Silent Brain LIVE • ${phase.name}",
                color = HomeTextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (silentMode) "No-Beep background listener active" else "Background listener active",
                color = HomeTextMuted,
                fontSize = 11.sp
            )
        }
        IconButton(
            onClick = onTogglePause,
            modifier = Modifier
                .size(48.dp)
                .testTag("permanent_brain_pause_button")
        ) {
            Icon(
                imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(R.string.cd_permanent_brain_pause),
                tint = HomeBlueSoft
            )
        }
        IconButton(
            onClick = onStop,
            modifier = Modifier
                .size(48.dp)
                .testTag("permanent_brain_stop_button")
        ) {
            Icon(
                imageVector = Icons.Filled.StopCircle,
                contentDescription = stringResource(R.string.cd_permanent_brain_stop),
                tint = Color(0xFFF87171)
            )
        }
    }
}

@Composable
private fun HomeConfirmationGateCard(
    confirmation: PendingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF211829))
            .border(1.dp, Color(0xFF7C3AED), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = confirmation.title,
            color = HomeTextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = confirmation.previewDetails,
            color = HomeTextMuted,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = HomeBlue),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("confirm_yes_button")
            ) {
                Text(stringResource(R.string.confirm_action_yes))
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("confirm_no_button")
            ) {
                Text(stringResource(R.string.confirm_action_no), color = HomeTextWhite)
            }
        }
    }
}
