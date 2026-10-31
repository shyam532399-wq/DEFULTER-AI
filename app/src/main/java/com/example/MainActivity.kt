package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.model.DataUiState
import com.example.data.model.MayaPersona
import com.example.data.model.UserProfile
import com.example.data.repository.MayaRepository
import com.example.ui.MayaViewModel
import com.example.ui.components.DraggableFloatingMiniHud
import com.example.ui.screens.AgentCockpitScreen
import com.example.ui.screens.BoardStudioScreen
import com.example.ui.screens.GuardianMemoryScreen
import com.example.ui.screens.SkillsDeckScreen
import com.example.ui.theme.MayaTheme

enum class AgentTab(
    val routeId: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    MAYA("cockpit", R.string.nav_agent, Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "maya_tab"),
    BOARD("board", R.string.nav_board, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "board_tab"),
    SKILLS("skills", R.string.nav_skills, Icons.Filled.Bolt, Icons.Outlined.Bolt, "skills_tab"),
    GUARDIAN("guardian", R.string.nav_memory, Icons.Filled.Shield, Icons.Outlined.Shield, "guardian_tab")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1 (MANDATORY): Configure App Check with launch intent before any UI or Firebase AI calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            MayaRootApp()
        }
    }
}

@Composable
fun MayaRootApp(
    viewModel: MayaViewModel = viewModel(
        key = MayaRepository.LOCAL_BOSS_ID,
        factory = MayaViewModel.provideFactory(MayaRepository.LOCAL_BOSS_ID, "Boss")
    )
) {
    MayaAppShell(viewModel = viewModel)
}

@Composable
private fun MayaAppShell(viewModel: MayaViewModel) {
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
    val spectrumBands by viewModel.spectrumBands.collectAsStateWithLifecycle()
    val gitHubSkillStoreState by viewModel.gitHubSkillStoreState.collectAsStateWithLifecycle()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsStateWithLifecycle()
    val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsStateWithLifecycle()

    val floatingMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceInput()
    }

    val profile = (profileState as? DataUiState.Success)?.data
        ?: UserProfile(userId = MayaRepository.LOCAL_BOSS_ID, bossName = "Boss")
    val activePersona = MayaPersona.fromId(profile.persona)

    var currentTab by rememberSaveable { mutableStateOf(AgentTab.MAYA) }

    MayaTheme(persona = activePersona, darkTheme = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar {
                            AgentTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
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
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen) {
                        NavigationRail {
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
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (currentTab) {
                            AgentTab.MAYA -> AgentCockpitScreen(
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
                                onNavigateToBoard = { currentTab = AgentTab.BOARD },
                                hudSnapshot = hudSnapshot,
                                hudConfig = hudConfig,
                                audioLevel = audioLevel,
                                spectrumBands = spectrumBands,
                                apiKeyStatus = apiKeyStatus,
                                showApiKeyDialog = showApiKeyDialog,
                                permanentBrainEnabled = permanentBrainEnabled,
                                permanentBrainPaused = permanentBrainPaused,
                                brainLoopPhase = brainLoopPhase
                            )

                            AgentTab.BOARD -> BoardStudioScreen(
                                viewModel = viewModel,
                                documentsState = documentsState,
                                imageStudioState = imageStudioState,
                                businessToolState = businessToolState,
                                pendingConfirmation = pendingConfirmation,
                                onBackToCockpit = { currentTab = AgentTab.MAYA }
                            )

                            AgentTab.SKILLS -> SkillsDeckScreen(
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

                            AgentTab.GUARDIAN -> GuardianMemoryScreen(
                                viewModel = viewModel,
                                profile = profile,
                                memoriesState = memoriesState,
                                remindersState = remindersState,
                                touchGuardAlert = touchGuardAlert,
                                onBackToCockpit = { currentTab = AgentTab.MAYA }
                            )
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
                                onTriggerMic = {
                                    if (isListening) {
                                        viewModel.stopVoiceInput()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) viewModel.startVoiceInput()
                                        else floatingMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onDismiss = { viewModel.setFloatingHudVisible(false) }
                            )
                        }
                    }
                }
            }
        }
    }
}
