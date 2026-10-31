package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.model.DataUiState
import com.example.data.model.MayaPersona
import com.example.data.model.UserProfile
import com.example.data.repository.MayaRepository
import com.example.ui.MayaViewModel
import com.example.ui.components.ApiKeyConnectDialog
import com.example.ui.components.DraggableFloatingMiniHud
import com.example.ui.components.MayaFullAiHudOverlay
import com.example.ui.screens.AgentCockpitScreen
import com.example.ui.screens.BoardStudioScreen
import com.example.ui.screens.GuardianMemoryScreen
import com.example.ui.screens.MayaColdLaunchSplash
import com.example.ui.screens.MayaHomeDashboardScreen
import com.example.ui.screens.MayaSetupWizardScreen
import com.example.ui.screens.SkillsDeckScreen
import com.example.ui.theme.MayaTheme
import kotlinx.coroutines.delay

enum class AgentTab(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    MAYA("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "maya_tab"),
    SKILLS("scan", R.string.nav_scan, Icons.Filled.QrCodeScanner, Icons.Outlined.QrCodeScanner, "skills_tab"),
    GUARDIAN("memories", R.string.nav_memories, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, "guardian_tab"),
    BOARD("chat", R.string.nav_chat, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "board_tab")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1 (MANDATORY): Configure App Check with launch intent before any UI or Firebase AI calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            MayaRootApp(enableColdLaunchOnboarding = true)
        }
    }
}

@Composable
fun MayaRootApp(
    viewModel: MayaViewModel = viewModel(
        key = MayaRepository.LOCAL_BOSS_ID,
        factory = MayaViewModel.provideFactory(MayaRepository.LOCAL_BOSS_ID, "Boss")
    ),
    enableColdLaunchOnboarding: Boolean = false
) {
    MayaAppShell(
        viewModel = viewModel,
        enableColdLaunchOnboarding = enableColdLaunchOnboarding
    )
}

@Composable
private fun MayaAppShell(
    viewModel: MayaViewModel,
    enableColdLaunchOnboarding: Boolean
) {
    val context = LocalContext.current

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val skillsState by viewModel.skillsState.collectAsStateWithLifecycle()
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val voiceReplyEnabled by viewModel.voiceReplyEnabled.collectAsStateWithLifecycle()
    val silentListeningEnabled by viewModel.silentListeningEnabled.collectAsStateWithLifecycle()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val permanentBrainEnabled by viewModel.permanentBrainEnabled.collectAsStateWithLifecycle()
    val permanentBrainPaused by viewModel.permanentBrainPaused.collectAsStateWithLifecycle()
    val brainLoopPhase by viewModel.brainLoopPhase.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val activeMission by viewModel.activeMission.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val imageStudioState by viewModel.imageStudioState.collectAsStateWithLifecycle()
    val businessToolState by viewModel.businessToolState.collectAsStateWithLifecycle()
    val touchGuardAlert by viewModel.touchGuardAlert.collectAsStateWithLifecycle()
    val hudSnapshot by viewModel.hudSnapshot.collectAsStateWithLifecycle()
    val hudConfig by viewModel.hudConfig.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val speakerAmplitude by viewModel.speakerAmplitude.collectAsStateWithLifecycle()
    val spectrumBands by viewModel.spectrumBands.collectAsStateWithLifecycle()
    val gitHubSkillStoreState by viewModel.gitHubSkillStoreState.collectAsStateWithLifecycle()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsStateWithLifecycle()
    val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsStateWithLifecycle()

    val setupCompleted by viewModel.setupCompleted.collectAsStateWithLifecycle()
    val showSetupWizard by viewModel.showSetupWizard.collectAsStateWithLifecycle()
    val setupWizardStep by viewModel.setupWizardStep.collectAsStateWithLifecycle()
    val wizardKeyNotice by viewModel.wizardKeyNotice.collectAsStateWithLifecycle()
    val homeWeather by viewModel.homeWeather.collectAsStateWithLifecycle()

    var showColdSplash by rememberSaveable { mutableStateOf(enableColdLaunchOnboarding) }
    var checkedInitialWizard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(enableColdLaunchOnboarding) {
        if (enableColdLaunchOnboarding) {
            if (showColdSplash) {
                delay(750)
                showColdSplash = false
            }
            if (!checkedInitialWizard) {
                checkedInitialWizard = true
                if (!setupCompleted) {
                    viewModel.openSetupWizard(step = 1)
                }
            }
        }
    }

    val floatingMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceInput()
    }

    val scanCameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.analyzeImageAttachment(bitmap, "Describe what is in front of the camera and help me with it.")
        }
    }

    val scanCameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanCameraPreviewLauncher.launch(null)
    }

    val scanPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val decoded = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (decoded != null) {
                viewModel.analyzeImageAttachment(decoded, "Analyze this scanned image and save key details if helpful.")
            }
        }
    }

    fun toggleGlobalVoiceMic() {
        when {
            isListening -> viewModel.stopVoiceInput()
            isSpeaking -> viewModel.toggleVoiceReply()
            else -> {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) viewModel.startVoiceInput()
                else floatingMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val profile = (profileState as? DataUiState.Success)?.data
        ?: UserProfile(userId = MayaRepository.LOCAL_BOSS_ID, bossName = "Boss")
    val activePersona = MayaPersona.fromId(profile.persona)
    val remindersList = (remindersState as? DataUiState.Success)?.data.orEmpty()

    var currentTab by rememberSaveable { mutableStateOf(AgentTab.MAYA) }
    var showBoardInChatTab by rememberSaveable { mutableStateOf(false) }

    MayaTheme(persona = activePersona, darkTheme = true) {
        if (showColdSplash) {
            MayaColdLaunchSplash()
            return@MayaTheme
        }

        if (showSetupWizard) {
            MayaSetupWizardScreen(
                currentStep = setupWizardStep,
                apiKeyStatus = apiKeyStatus,
                wizardKeyNotice = wizardKeyNotice,
                canCloseToApp = setupCompleted,
                onStepChange = viewModel::setSetupWizardStep,
                onOpenFreeKeyPortal = viewModel::openGeminiApiKeyPortal,
                onTestKey = { raw -> viewModel.connectApiKeyFromWizard(raw, testOnly = true) },
                onSaveAndContinueKey = { raw -> viewModel.connectApiKeyFromWizard(raw, testOnly = false) },
                onSkipKeyStep = viewModel::skipApiKeyStepInWizard,
                onFinishSetup = viewModel::completeSetupWizard,
                onCloseWizard = viewModel::completeSetupWizard
            )
            return@MayaTheme
        }

        if (currentTab != AgentTab.MAYA) {
            BackHandler {
                if (currentTab == AgentTab.BOARD && showBoardInChatTab) {
                    showBoardInChatTab = false
                } else {
                    currentTab = AgentTab.MAYA
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color(0xFF080C17),
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!isWideScreen) {
                        MayaFiveItemBottomBar(
                            currentTab = currentTab,
                            isListening = isListening,
                            onSelectTab = { tab ->
                                if (tab != AgentTab.BOARD) showBoardInChatTab = false
                                currentTab = tab
                            },
                            onCenterMicClick = ::toggleGlobalVoiceMic
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen) {
                        NavigationRail(
                            containerColor = Color(0xFF0A101E)
                        ) {
                            AgentTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            NavigationRailItem(
                                selected = isListening,
                                onClick = ::toggleGlobalVoiceMic,
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = stringResource(R.string.cd_center_mic_fab)
                                    )
                                },
                                label = { Text("Mic") },
                                modifier = Modifier.testTag("bottom_center_mic_fab")
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (currentTab) {
                            AgentTab.MAYA -> MayaHomeDashboardScreen(
                                viewModel = viewModel,
                                profile = profile,
                                messages = messages,
                                reminders = remindersList,
                                homeWeather = homeWeather,
                                isThinking = isThinking,
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                partialTranscript = partialTranscript,
                                permanentBrainEnabled = permanentBrainEnabled,
                                permanentBrainPaused = permanentBrainPaused,
                                brainLoopPhase = brainLoopPhase,
                                silentListeningEnabled = silentListeningEnabled,
                                pendingConfirmation = pendingConfirmation,
                                hudSnapshot = hudSnapshot,
                                hudConfig = hudConfig,
                                audioLevel = audioLevel,
                                micLevel = micLevel,
                                speakerAmplitude = speakerAmplitude,
                                apiKeyStatus = apiKeyStatus,
                                onOpenChatTab = {
                                    showBoardInChatTab = false
                                    currentTab = AgentTab.BOARD
                                },
                                onOpenBoardStudio = {
                                    showBoardInChatTab = true
                                    currentTab = AgentTab.BOARD
                                },
                                onOpenMemoriesTab = { currentTab = AgentTab.GUARDIAN },
                                onOpenSetupWizardStep = { step -> viewModel.openSetupWizard(step) }
                            )

                            AgentTab.SKILLS -> Column(modifier = Modifier.fillMaxSize()) {
                                ScanVisionQuickHeader(
                                    onScanWithCamera = {
                                        val hasCam = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasCam) scanCameraPreviewLauncher.launch(null)
                                        else scanCameraPermLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    onPickImageToScan = {
                                        scanPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    SkillsDeckScreen(
                                        viewModel = viewModel,
                                        telemetry = telemetry,
                                        skillsState = skillsState,
                                        pendingConfirmation = pendingConfirmation,
                                        onBackToCockpit = { currentTab = AgentTab.MAYA },
                                        onRunMacroInCockpit = { cmd ->
                                            currentTab = AgentTab.MAYA
                                            viewModel.sendCommand(cmd)
                                        },
                                        hudSnapshot = hudSnapshot,
                                        gitHubSkillStoreState = gitHubSkillStoreState
                                    )
                                }
                            }

                            AgentTab.GUARDIAN -> Column(modifier = Modifier.fillMaxSize()) {
                                MemoriesSetupQuickBar(
                                    onOpenSetupWizard = { viewModel.openSetupWizard(step = 2) }
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    GuardianMemoryScreen(
                                        viewModel = viewModel,
                                        profile = profile,
                                        memoriesState = memoriesState,
                                        remindersState = remindersState,
                                        touchGuardAlert = touchGuardAlert,
                                        onBackToCockpit = { currentTab = AgentTab.MAYA }
                                    )
                                }
                            }

                            AgentTab.BOARD -> Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0A101E))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    FilterChip(
                                        selected = !showBoardInChatTab,
                                        onClick = { showBoardInChatTab = false },
                                        label = { Text("Chat") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                    FilterChip(
                                        selected = showBoardInChatTab,
                                        onClick = { showBoardInChatTab = true },
                                        label = { Text("Maya Board & Studio") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Dashboard,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    if (showBoardInChatTab) {
                                        BoardStudioScreen(
                                            viewModel = viewModel,
                                            documentsState = documentsState,
                                            imageStudioState = imageStudioState,
                                            businessToolState = businessToolState,
                                            pendingConfirmation = pendingConfirmation,
                                            onBackToCockpit = { showBoardInChatTab = false }
                                        )
                                    } else {
                                        AgentCockpitScreen(
                                            viewModel = viewModel,
                                            profile = profile,
                                            messages = messages,
                                            isThinking = isThinking,
                                            isListening = isListening,
                                            isSpeaking = isSpeaking,
                                            partialTranscript = partialTranscript,
                                            voiceReplyEnabled = voiceReplyEnabled,
                                            wakeWordEnabled = wakeWordEnabled,
                                            pendingConfirmation = pendingConfirmation,
                                            activeMission = activeMission,
                                            onNavigateToBoard = { showBoardInChatTab = true },
                                            hudSnapshot = hudSnapshot,
                                            hudConfig = hudConfig,
                                            audioLevel = audioLevel,
                                            micLevel = micLevel,
                                            speakerAmplitude = speakerAmplitude,
                                            spectrumBands = spectrumBands,
                                            apiKeyStatus = apiKeyStatus,
                                            showApiKeyDialog = false,
                                            permanentBrainEnabled = permanentBrainEnabled,
                                            permanentBrainPaused = permanentBrainPaused,
                                            brainLoopPhase = brainLoopPhase,
                                            silentListeningEnabled = silentListeningEnabled
                                        )
                                    }
                                }
                            }
                        }

                        // Cross-tab Draggable Floating Mini-HUD Overlay (`HunterisLive-1/maya-ui`)
                        if (hudConfig.floatingHudVisible) {
                            DraggableFloatingMiniHud(
                                snapshot = hudSnapshot,
                                audioLevel = audioLevel,
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                initialOffsetX = hudConfig.floatingOffsetX,
                                initialOffsetY = hudConfig.floatingOffsetY,
                                onOffsetCommitted = viewModel::updateFloatingHudOffset,
                                onTriggerMic = ::toggleGlobalVoiceMic,
                                onDismiss = { viewModel.setFloatingHudVisible(false) }
                            )
                        }
                    }
                }
            }

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

            // Full-Screen Frameless Maya AI HUD Mode (`ui/main_window.py` in `HunterisLive-1/maya-ui`)
            if (hudConfig.fullHudVisible) {
                MayaFullAiHudOverlay(
                    persona = activePersona,
                    snapshot = hudSnapshot,
                    apiKeyStatus = apiKeyStatus,
                    micLevel = micLevel,
                    speakerAmplitude = speakerAmplitude,
                    audioLevel = audioLevel,
                    isListening = isListening,
                    isThinking = isThinking,
                    isSpeaking = isSpeaking,
                    guardArmed = profile.guardArmed || profile.touchGuardArmed,
                    permanentBrainEnabled = permanentBrainEnabled,
                    particlesEnabled = hudConfig.particlesEnabled,
                    floatingHudVisible = hudConfig.floatingHudVisible,
                    onTapOrbOrMic = ::toggleGlobalVoiceMic,
                    onToggleParticles = viewModel::toggleParticlesEnabled,
                    onTogglePermanentBrain = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        viewModel.togglePermanentBrainMode(
                            hasAudioPermission = granted,
                            onNeedPermission = { floatingMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        )
                    },
                    onSwitchToFloatingMiniHud = {
                        viewModel.setFullHudVisible(false)
                        viewModel.setFloatingHudVisible(true)
                    },
                    onDismiss = { viewModel.setFullHudVisible(false) }
                )
            }
        }
    }
}

@Composable
private fun MayaFiveItemBottomBar(
    currentTab: AgentTab,
    isListening: Boolean,
    onSelectTab: (AgentTab) -> Unit,
    onCenterMicClick: () -> Unit
) {
    Surface(
        color = Color(0xFF0A101E),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0xFF16223C))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            BottomNavTabButton(
                tab = AgentTab.MAYA,
                selected = currentTab == AgentTab.MAYA,
                onClick = { onSelectTab(AgentTab.MAYA) },
                modifier = Modifier.weight(1f)
            )
            BottomNavTabButton(
                tab = AgentTab.SKILLS,
                selected = currentTab == AgentTab.SKILLS,
                onClick = { onSelectTab(AgentTab.SKILLS) },
                modifier = Modifier.weight(1f)
            )

            // Center Glowing Royal-Blue Circle Mic FAB (matches video 01:11-01:12)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = Color(0xFF3B6EF6),
                        spotColor = Color(0xFF3B6EF6)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isListening) {
                                listOf(Color(0xFF34D399), Color(0xFF059669))
                            } else {
                                listOf(Color(0xFF4F82FF), Color(0xFF2858E8))
                            }
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = 0.28f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onCenterMicClick)
                    .testTag("bottom_center_mic_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.cd_center_mic_fab),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            BottomNavTabButton(
                tab = AgentTab.GUARDIAN,
                selected = currentTab == AgentTab.GUARDIAN,
                onClick = { onSelectTab(AgentTab.GUARDIAN) },
                modifier = Modifier.weight(1f)
            )
            BottomNavTabButton(
                tab = AgentTab.BOARD,
                selected = currentTab == AgentTab.BOARD,
                onClick = { onSelectTab(AgentTab.BOARD) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavTabButton(
    tab: AgentTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFF6EA2FF)
    val inactiveColor = Color(0xFF7D90B2)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tab.tag)
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = stringResource(tab.labelRes),
            tint = if (selected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = stringResource(tab.labelRes),
            color = if (selected) activeColor else inactiveColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun ScanVisionQuickHeader(
    onScanWithCamera: () -> Unit,
    onPickImageToScan: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1426))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Button(
            onClick = onScanWithCamera,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F63F5)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("scan_camera_button")
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Live Camera Scan", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onPickImageToScan,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("scan_gallery_button")
        ) {
            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Scan Photo", fontSize = 13.sp, color = Color(0xFFF2F6FF))
        }
    }
}

@Composable
private fun MemoriesSetupQuickBar(
    onOpenSetupWizard: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C1426))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Permissions & Background Alive",
            color = Color(0xFF8A9BB8),
            fontSize = 12.sp
        )
        OutlinedButton(
            onClick = onOpenSetupWizard,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("memories_open_setup_wizard_button")
        ) {
            Text("Set up Maya", fontSize = 12.sp, color = Color(0xFF6EA2FF))
        }
    }
}
