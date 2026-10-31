package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ListenMode
import com.example.data.model.MayaPersona
import com.example.data.model.MayaTone
import com.example.data.model.UserProfile
import com.example.domain.ApiKeyConnectionStatus
import com.example.domain.BrainLoopPhase
import com.example.domain.HudEffectTheme
import com.example.domain.HudVisualConfig
import com.example.domain.SystemHudSnapshot
import com.example.ui.ChatBubble
import com.example.ui.MayaViewModel
import com.example.ui.MissionState
import com.example.ui.PendingConfirmation
import com.example.ui.components.ApiKeyConnectDialog
import com.example.ui.components.MayaReactiveOrb
import com.example.ui.components.NeonAudioSpectrumVisualizer
import com.example.ui.components.QuantumHudContainer
import com.example.ui.components.RememberLiveFpsReporter
import com.example.ui.components.SystemMonitoringHudPanel
import com.example.ui.theme.LocalPersonaAccent

@Composable
fun AgentCockpitScreen(
    viewModel: MayaViewModel,
    profile: UserProfile,
    messages: List<ChatBubble>,
    isThinking: Boolean,
    isListening: Boolean,
    isSpeaking: Boolean,
    partialTranscript: String,
    voiceReplyEnabled: Boolean,
    wakeWordEnabled: Boolean,
    pendingConfirmation: PendingConfirmation?,
    activeMission: MissionState?,
    onNavigateToBoard: () -> Unit,
    hudSnapshot: SystemHudSnapshot = SystemHudSnapshot(),
    hudConfig: HudVisualConfig = HudVisualConfig(),
    audioLevel: Float = 0f,
    spectrumBands: List<Float> = List(24) { 0.08f },
    apiKeyStatus: ApiKeyConnectionStatus = ApiKeyConnectionStatus.BuiltIn,
    showApiKeyDialog: Boolean = false,
    permanentBrainEnabled: Boolean = false,
    permanentBrainPaused: Boolean = false,
    brainLoopPhase: BrainLoopPhase = BrainLoopPhase.OFF
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val accent = LocalPersonaAccent.current
    val listState = rememberLazyListState()
    var inputText by rememberSaveable { mutableStateOf("") }
    val needsBrainPermission by viewModel.needsBrainMicPermissionEvent.collectAsStateWithLifecycle()

    RememberLiveFpsReporter(onFpsMeasured = viewModel::reportLiveFps)

    val activePersona = MayaPersona.fromId(profile.persona)
    val activeTone = MayaTone.fromId(profile.tone)
    val activeListenMode = ListenMode.fromId(profile.listenMode)

    // Scroll to the true bottom item (QuickChips at index 0 + messages + optional thinking indicator)
    LaunchedEffect(messages.size, isThinking, partialTranscript.isNotBlank(), pendingConfirmation) {
        val lastItemIndex = messages.size + (if (isThinking || partialTranscript.isNotBlank()) 1 else 0)
        if (lastItemIndex >= 0) {
            runCatching { listState.animateScrollToItem(lastItemIndex) }
        }
    }

    // Runtime permission launchers
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput()
        }
    }

    val permanentBrainPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val micGranted = grantMap[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.setPermanentBrainMode(enabled = true, hasAudioPermission = true)
        }
    }

    fun requestPermissionsForPermanentBrain() {
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
            requestPermissionsForPermanentBrain()
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val question = inputText
            inputText = ""
            viewModel.analyzeImageAttachment(bitmap, question)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPreviewLauncher.launch(null)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val decoded = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
            if (decoded != null) {
                val question = inputText
                inputText = ""
                viewModel.analyzeImageAttachment(decoded, question)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val compactViewport = maxHeight < 560.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
        ) {
            // 1. Persona Aura + Quantum HUD Cockpit Header (`HunterisLive-1/maya-ui`)
            PersonaCockpitHeader(
                bossName = profile.bossName,
                activePersona = activePersona,
                activeTone = activeTone,
                listenMode = activeListenMode,
                isListening = isListening,
                isThinking = isThinking,
                isSpeaking = isSpeaking,
                compactViewport = compactViewport,
                guardArmed = profile.guardArmed || profile.touchGuardArmed,
                voiceReplyEnabled = voiceReplyEnabled,
                wakeWordEnabled = wakeWordEnabled,
                permanentBrainEnabled = permanentBrainEnabled,
                permanentBrainPaused = permanentBrainPaused,
                brainLoopPhase = brainLoopPhase,
                hudSnapshot = hudSnapshot,
                hudConfig = hudConfig,
                audioLevel = audioLevel,
                spectrumBands = spectrumBands,
                onSelectPersona = viewModel::switchPersona,
                onSelectTone = viewModel::switchTone,
                onToggleListenMode = {
                    val next = if (activeListenMode == ListenMode.BOSS_ONLY) ListenMode.EVERYONE else ListenMode.BOSS_ONLY
                    viewModel.setListenMode(next)
                },
                onToggleVoiceReply = viewModel::toggleVoiceReply,
                onToggleWakeWord = {
                    val hasAudio = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.toggleWakeWordMode(
                        hasAudioPermission = hasAudio,
                        onNeedPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                },
                onTogglePermanentBrain = {
                    val hasAudio = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.togglePermanentBrainMode(
                        hasAudioPermission = hasAudio,
                        onNeedPermission = { requestPermissionsForPermanentBrain() }
                    )
                },
                onTogglePermanentBrainPause = viewModel::togglePermanentBrainPause,
                onStopPermanentBrain = { viewModel.setPermanentBrainMode(enabled = false) },
                onToggleHudExpanded = viewModel::toggleHudExpanded,
                onSelectHudTheme = viewModel::setHudEffectTheme,
                onToggleFloatingHud = viewModel::toggleFloatingHud,
                onPinHudReport = viewModel::pinSystemHudReportToBoard,
                apiKeyStatus = apiKeyStatus,
                onOpenApiKeyDialog = viewModel::openApiKeyDialog,
                onTapCenterpieceOrb = {
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
            )

            if (showApiKeyDialog) {
                ApiKeyConnectDialog(
                    status = apiKeyStatus,
                    onConnect = viewModel::connectApiKey,
                    onDisconnect = viewModel::disconnectApiKey,
                    onPasteFromClipboard = viewModel::readClipboardForApiKey,
                    onOpenGeminiKeyPortal = viewModel::openGeminiApiKeyPortal,
                    onTestLiveReply = viewModel::testConnectedApiKey,
                    onDismiss = viewModel::dismissApiKeyDialog
                )
            }

            // 2. Golden Rule 5: Risky Action Confirmation Gate Banner
            AnimatedVisibility(visible = pendingConfirmation != null) {
                pendingConfirmation?.let { pending ->
                    ConfirmationSafetyCard(
                        pending = pending,
                        onConfirm = viewModel::confirmPendingAction,
                        onCancel = viewModel::cancelPendingAction
                    )
                }
            }

            // 3. Golden Rule 4: Multi-Step Mission Plan Card (with undo_last_step)
            AnimatedVisibility(visible = activeMission != null) {
                activeMission?.let { mission ->
                    MissionStepperCard(
                        mission = mission,
                        onNextStep = viewModel::advanceMissionStep,
                        onUndoStep = viewModel::undoLastMissionStep,
                        onCancel = viewModel::cancelMission
                    )
                }
            }

            // 4. Conversation Stream + Quick Prompt Chips
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    QuickHinglishChipsRow(
                        onPickPrompt = { prompt -> viewModel.sendCommand(prompt) }
                    )
                }

                items(messages, key = { it.id }) { bubble ->
                    ChatBubbleCard(
                        bubble = bubble,
                        personaName = activePersona.displayName,
                        onOpenBoard = onNavigateToBoard
                    )
                }

                if (isThinking || partialTranscript.isNotBlank()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = accent.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (partialTranscript.isNotBlank()) {
                                        "Sun rahi hoon: \"$partialTranscript\""
                                    } else {
                                        "${activePersona.displayName} kaam kar rahi hai…"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 5. Multimodal Voice + Vision + Text Command Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                val hasCam = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCam) cameraPreviewLauncher.launch(null)
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("camera_vision_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = stringResource(R.string.cd_camera_capture),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("photo_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = stringResource(R.string.cd_attach_image),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.input_placeholder_maya),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val toSend = inputText.trim()
                                    if (toSend.isNotBlank()) {
                                        inputText = ""
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        viewModel.sendCommand(toSend)
                                    }
                                }
                            ),
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("command_input")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        if (inputText.isBlank()) {
                            IconButton(
                                onClick = {
                                    if (isListening) {
                                        viewModel.stopVoiceInput()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) viewModel.startVoiceInput()
                                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isListening) MaterialTheme.colorScheme.error else accent.primary)
                                    .testTag("mic_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = stringResource(R.string.cd_mic_button),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val toSend = inputText
                                    inputText = ""
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    viewModel.sendCommand(toSend)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(accent.primary)
                                    .testTag("send_command_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.cd_send_button),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaCockpitHeader(
    bossName: String,
    activePersona: MayaPersona,
    activeTone: MayaTone,
    listenMode: ListenMode,
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    compactViewport: Boolean,
    guardArmed: Boolean,
    voiceReplyEnabled: Boolean,
    wakeWordEnabled: Boolean,
    permanentBrainEnabled: Boolean,
    permanentBrainPaused: Boolean,
    brainLoopPhase: BrainLoopPhase,
    hudSnapshot: SystemHudSnapshot,
    hudConfig: HudVisualConfig,
    audioLevel: Float,
    spectrumBands: List<Float>,
    onSelectPersona: (MayaPersona) -> Unit,
    onSelectTone: (MayaTone) -> Unit,
    onToggleListenMode: () -> Unit,
    onToggleVoiceReply: () -> Unit,
    onToggleWakeWord: () -> Unit,
    onTogglePermanentBrain: () -> Unit,
    onTogglePermanentBrainPause: () -> Unit,
    onStopPermanentBrain: () -> Unit,
    onToggleHudExpanded: () -> Unit,
    onSelectHudTheme: (HudEffectTheme) -> Unit,
    onToggleFloatingHud: () -> Unit,
    onPinHudReport: () -> Unit,
    apiKeyStatus: ApiKeyConnectionStatus,
    onOpenApiKeyDialog: () -> Unit,
    onTapCenterpieceOrb: () -> Unit
) {
    val accent = LocalPersonaAccent.current
    val pulseTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening || isSpeaking || permanentBrainEnabled) 650 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    QuantumHudContainer(
        effectTheme = hudConfig.effectTheme,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accent.primary.copy(alpha = 0.42f),
                                        accent.secondary.copy(alpha = 0.0f)
                                    )
                                ),
                                radius = (size.minDimension / 2f) * pulseScale
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_maya_avatar),
                        contentDescription = stringResource(R.string.cd_maya_avatar),
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(2.dp, accent.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${activePersona.displayName} • $bossName",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            permanentBrainEnabled -> "Brain Mode • ${brainLoopPhase.statusHinglish}"
                            isListening -> "Sun rahi hoon… ('Hey Maya' ready)"
                            isSpeaking -> "Bol rahi hoon…"
                            else -> activePersona.tagline
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (permanentBrainEnabled && !permanentBrainPaused) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleHudExpanded,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_hud_deck_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = stringResource(R.string.cd_toggle_hud_deck),
                        tint = if (hudConfig.hudExpanded) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleWakeWord,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("wake_word_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Hearing,
                        contentDescription = stringResource(R.string.cd_wake_word_toggle),
                        tint = if (wakeWordEnabled) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleVoiceReply,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_reply_toggle")
                ) {
                    Icon(
                        imageVector = if (voiceReplyEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = stringResource(R.string.cd_speak_toggle),
                        tint = if (voiceReplyEnabled) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable HunterisLive-1/maya-ui Centerpiece Orb + Neon Spectrum + System Telemetry HUD
            AnimatedVisibility(visible = hudConfig.hudExpanded && !compactViewport) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MayaReactiveOrb(
                            audioLevel = audioLevel,
                            isListening = isListening,
                            isThinking = isThinking,
                            isSpeaking = isSpeaking,
                            guardArmed = guardArmed,
                            onTapOrb = onTapCenterpieceOrb,
                            modifier = Modifier.size(86.dp)
                        )
                        NeonAudioSpectrumVisualizer(
                            spectrumBands = spectrumBands,
                            isListening = isListening,
                            isSpeaking = isSpeaking,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SystemMonitoringHudPanel(
                        snapshot = hudSnapshot,
                        effectTheme = hudConfig.effectTheme,
                        floatingHudVisible = hudConfig.floatingHudVisible,
                        onSelectTheme = onSelectHudTheme,
                        onToggleFloatingHud = onToggleFloatingHud,
                        onPinDiagnosticToBoard = onPinHudReport
                    )
                }
            }

            // Live Human Brain Background Listening Status Strip
            AnimatedVisibility(visible = permanentBrainEnabled && !compactViewport) {
                Surface(
                    color = accent.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .border(1.dp, accent.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .testTag("permanent_brain_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = accent.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (permanentBrainPaused) {
                                    "Human Brain Paused"
                                } else {
                                    "Background Brain LIVE • ${brainLoopPhase.statusHinglish}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Bina mic dabaye seedha boliye — background me bhi sunke jawab degi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onTogglePermanentBrainPause,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("permanent_brain_pause_button")
                        ) {
                            Icon(
                                imageVector = if (permanentBrainPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.cd_permanent_brain_pause),
                                tint = accent.primary
                            )
                        }
                        IconButton(
                            onClick = onStopPermanentBrain,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("permanent_brain_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.StopCircle,
                                contentDescription = stringResource(R.string.cd_permanent_brain_stop),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Permanent Brain + API Key + Persona + Listen Mode + Tone Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = permanentBrainEnabled,
                    onClick = onTogglePermanentBrain,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = stringResource(R.string.cd_permanent_brain_toggle),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            when {
                                !permanentBrainEnabled -> stringResource(R.string.brain_chip_off)
                                permanentBrainPaused -> stringResource(R.string.brain_chip_paused)
                                else -> stringResource(R.string.brain_chip_live)
                            }
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("permanent_brain_chip")
                )

                val connectedKey = apiKeyStatus as? ApiKeyConnectionStatus.Connected
                FilterChip(
                    selected = connectedKey != null,
                    onClick = onOpenApiKeyDialog,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            if (connectedKey != null) "${connectedKey.provider.shortBadge} Key ✓"
                            else stringResource(R.string.api_key_chip_connect)
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("api_key_status_chip")
                )

                MayaPersona.entries.forEach { p ->
                    FilterChip(
                        selected = p == activePersona,
                        onClick = { onSelectPersona(p) },
                        label = { Text(p.displayName) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("persona_chip_${p.id}")
                    )
                }

                AssistChip(
                    onClick = onToggleListenMode,
                    label = { Text(listenMode.label) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("listen_mode_chip")
                )

                MayaTone.entries.forEach { t ->
                    FilterChip(
                        selected = t == activeTone,
                        onClick = { onSelectTone(t) },
                        label = { Text(t.label) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tone_chip_${t.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmationSafetyCard(
    pending: PendingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "Confirmation Required",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pending.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = pending.previewDetails,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_yes_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.confirm_action_yes))
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_no_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.confirm_action_no))
                }
            }
        }
    }
}

@Composable
private fun MissionStepperCard(
    mission: MissionState,
    onNextStep: () -> Unit,
    onUndoStep: () -> Unit,
    onCancel: () -> Unit
) {
    val accent = LocalPersonaAccent.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Mission: ${mission.title} (${mission.currentStepIndex + 1}/${mission.steps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent.primary
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel mission")
                }
            }
            mission.steps.forEachIndexed { idx, step ->
                val prefix = when {
                    idx < mission.currentStepIndex -> "✓"
                    idx == mission.currentStepIndex -> "▶"
                    else -> "○"
                }
                Text(
                    text = "$prefix ${idx + 1}. $step",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (idx == mission.currentStepIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNextStep,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Next Step")
                }
                OutlinedButton(
                    onClick = onUndoStep,
                    enabled = mission.currentStepIndex > 0,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo Step")
                }
            }
        }
    }
}

@Composable
private fun QuickHinglishChipsRow(onPickPrompt: (String) -> Unit) {
    val suggestions = listOf(
        "Permanent listening on karo",
        "System HUD aur net speed batao",
        "Floating HUD on karo",
        "Holographic mode on karo",
        "HunterisLive-1/maya-ui skills sync karo",
        "Torch on karo",
        "Battery kitni hai?",
        "Reliance stock ka haal?",
        "Bitcoin ka haal?",
        "Mumbai ka mausam batao",
        "Kal subah 7 baje jagana",
        "Rahul ko WhatsApp pe bol do main late hoon"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEachIndexed { idx, text ->
            AssistChip(
                onClick = { onPickPrompt(text) },
                label = { Text(text, style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("quick_prompt_$idx")
            )
        }
    }
}

@Composable
private fun ChatBubbleCard(
    bubble: ChatBubble,
    personaName: String,
    onOpenBoard: () -> Unit
) {
    val accent = LocalPersonaAccent.current
    val alignment = if (bubble.isFromBoss) Alignment.End else Alignment.Start
    val container = when {
        bubble.isError -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        bubble.isFromBoss -> accent.primary.copy(alpha = 0.20f)
        else -> MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (bubble.isFromBoss) 20.dp else 6.dp,
                bottomEnd = if (bubble.isFromBoss) 6.dp else 20.dp
            ),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .border(
                    width = 1.dp,
                    color = if (bubble.isFromBoss) accent.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (bubble.isFromBoss) 20.dp else 6.dp,
                        bottomEnd = if (bubble.isFromBoss) 6.dp else 20.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (bubble.isFromBoss) "Boss" else personaName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bubble.isFromBoss) accent.secondary else accent.primary
                    )
                    bubble.badge?.let { tag ->
                        Surface(
                            color = accent.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                bubble.attachedBitmap?.let { bmp ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Attached visual",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = bubble.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                bubble.linkedBoardTitle?.let { docTitle ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBoard)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = "Open Maya Board",
                                tint = accent.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Board pe dekhein: $docTitle",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
