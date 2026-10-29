package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.model.DataUiState
import com.example.data.model.MayaPersona
import com.example.data.model.UserProfile
import com.example.ui.MayaViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AgentCockpitScreen
import com.example.ui.screens.BoardStudioScreen
import com.example.ui.screens.GuardianMemoryScreen
import com.example.ui.screens.SkillsDeckScreen
import com.example.ui.theme.MayaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // STEP 1 (MANDATORY): Configure App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            MayaRootApp()
        }
    }
}

@Composable
fun MayaRootApp(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser

    if (user == null) {
        MayaTheme(persona = MayaPersona.MAYA, darkTheme = true) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AuthScreen(
                    onAuthSuccess = {
                        currentUser = auth.currentUser
                    }
                )
            }
        }
    } else {
        AuthenticatedMayaShell(user = user)
    }
}

@Composable
private fun AuthenticatedMayaShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val displayName = user.displayName?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: "Boss"
    val viewModel: MayaViewModel = viewModel(
        key = user.uid,
        factory = MayaViewModel.provideFactory(user.uid, displayName)
    )

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
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val activeMission by viewModel.activeMission.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val imageStudioState by viewModel.imageStudioState.collectAsStateWithLifecycle()
    val businessToolState by viewModel.businessToolState.collectAsStateWithLifecycle()
    val touchGuardAlert by viewModel.touchGuardAlert.collectAsStateWithLifecycle()

    val profile = (profileState as? DataUiState.Success)?.data
        ?: UserProfile(userId = user.uid, bossName = displayName)
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
                                onNavigateToBoard = { currentTab = AgentTab.BOARD }
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
                                }
                            )

                            AgentTab.GUARDIAN -> GuardianMemoryScreen(
                                viewModel = viewModel,
                                profile = profile,
                                memoriesState = memoriesState,
                                remindersState = remindersState,
                                touchGuardAlert = touchGuardAlert,
                                userEmail = user.email,
                                onBackToCockpit = { currentTab = AgentTab.MAYA },
                                onSignOut = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        onSignOutComplete = { currentTab = AgentTab.MAYA },
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
