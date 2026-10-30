package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BoardDocument
import com.example.data.model.DataUiState
import com.example.data.model.SkillPlaybook
import com.example.data.model.UserProfile
import com.example.data.repository.MayaRepository
import com.example.domain.LiveInfoClient
import com.example.domain.MayaAiBrain
import com.example.domain.MayaVoiceEngine
import com.example.domain.PhoneActionExecutor
import com.example.ui.BusinessToolState
import com.example.ui.ImageStudioState
import com.example.ui.MayaViewModel
import com.example.ui.MissionState
import com.example.ui.PendingConfirmation
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AgentCockpitScreen
import com.example.ui.screens.BoardStudioScreen
import com.example.ui.screens.GuardianMemoryScreen
import com.example.ui.screens.SkillsDeckScreen
import com.example.ui.theme.MayaTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h1000dp")
class MayaCriticalUserJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseId = context.getString(R.string.firestore_database_id)
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            val newApp = FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:536299701809:android:5e208c8af770ef9e85f710")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
            val fs = FirebaseFirestore.getInstance(newApp, databaseId)
            fs.useEmulator("127.0.0.1", 8085)
            fs.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            val fa = FirebaseAuth.getInstance(newApp)
            fa.useEmulator("127.0.0.1", 9099)
            newApp
        } else {
            FirebaseApp.getInstance()
        }
        firestore = FirebaseFirestore.getInstance(app, databaseId)
        auth = FirebaseAuth.getInstance(app)
    }

    private fun buildTestViewModel(uid: String = "test_boss_uid"): MayaViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return MayaViewModel(
            repository = MayaRepository(firestore, auth),
            phoneExecutor = PhoneActionExecutor(context),
            voiceEngine = MayaVoiceEngine(context),
            aiBrain = MayaAiBrain(),
            liveInfoClient = LiveInfoClient(),
            currentUserId = uid,
            initialDisplayName = "Boss"
        )
    }

    @Test
    fun cuj1_authScreen_displaysHinglishGreetingAndGoogleSignInButton() {
        composeTestRule.setContent {
            MayaTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Namaste Boss, main Maya hoon.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cuj2_agentCockpit_enforcesRiskyConfirmationGate_andPersonaChips() {
        val uid = "cuj2_boss"
        val vm = buildTestViewModel(uid)
        val executed = AtomicBoolean(false)

        val pending = PendingConfirmation(
            title = "WhatsApp Message Confirm Karein",
            previewDetails = "To: Rahul\nMessage: \"main late hoon\"",
            promptSpoken = "Boss, Rahul ko WhatsApp pe 'main late hoon' bhej dun?",
            onConfirmExecute = {
                executed.set(true)
                "Bhej diya, Boss."
            }
        )

        // Pause infinite orb pulse clock so Compose UI assertions settle deterministically
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MayaTheme {
                AgentCockpitScreen(
                    viewModel = vm,
                    profile = UserProfile(userId = uid, bossName = "Boss", persona = "maya"),
                    messages = vm.messages.value,
                    isThinking = false,
                    isListening = false,
                    isSpeaking = false,
                    partialTranscript = "",
                    voiceReplyEnabled = false,
                    wakeWordEnabled = false,
                    pendingConfirmation = pending,
                    activeMission = MissionState(
                        title = "Morning Prep",
                        steps = listOf("Check battery", "Read weather", "Set 7 AM alarm"),
                        currentStepIndex = 1
                    ),
                    onNavigateToBoard = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(150)

        // Verify Persona chips & Confirmation Gate UI
        composeTestRule.onNodeWithTag("persona_chip_maya").assertIsDisplayed()
        composeTestRule.onNodeWithTag("persona_chip_friday").assertIsDisplayed()
        composeTestRule.onNodeWithTag("persona_chip_venom").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_yes_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_no_button").assertIsDisplayed()

        // Action must NOT have executed before confirmation
        assertFalse(executed.get())
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun cuj3_boardStudio_switchesSections_andRequiresConfirmBeforeDelete() {
        val uid = "cuj3_boss"
        val vm = buildTestViewModel(uid)

        val sampleDoc = BoardDocument(
            id = "doc_abc",
            userId = uid,
            title = "Q4 Marketing Plan",
            category = "marketing",
            summary = "Instagram Reels + WhatsApp Drip",
            content = "# Marketing Plan\n- Week 1: Teasers",
            pinned = true
        )

        composeTestRule.setContent {
            MayaTheme {
                BoardStudioScreen(
                    viewModel = vm,
                    documentsState = DataUiState.Success(listOf(sampleDoc)),
                    imageStudioState = ImageStudioState.Idle,
                    businessToolState = BusinessToolState.Idle,
                    pendingConfirmation = null,
                    onBackToCockpit = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Q4 Marketing Plan").assertIsDisplayed()

        // Requesting delete must create a PendingConfirmation instead of deleting without asking (Golden Rule 5)
        assertNull(vm.pendingConfirmation.value)
        vm.requestDeleteBoardDoc(sampleDoc)
        assertTrue(vm.pendingConfirmation.value != null)
        vm.cancelPendingAction()
        assertNull(vm.pendingConfirmation.value)

        // Switch to Gemini Image Macro studio
        composeTestRule.onNodeWithTag("section_image_studio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("image_prompt_input").performTextInput("Sunset on a beach")
        composeTestRule.onNodeWithTag("generate_image_button").assertIsDisplayed()

        // Switch to Business Suite
        composeTestRule.onNodeWithTag("section_business_suite").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("business_tool_input").assertIsDisplayed()
    }

    @Test
    fun cuj4_guardianScreen_triggersTouchGuardAlertWithoutRunningTools() {
        val uid = "cuj4_boss"
        val vm = buildTestViewModel(uid)

        composeTestRule.setContent {
            MayaTheme {
                GuardianMemoryScreen(
                    viewModel = vm,
                    profile = UserProfile(userId = uid, bossName = "Boss", touchGuardArmed = true),
                    memoriesState = DataUiState.Success(emptyList()),
                    remindersState = DataUiState.Success(emptyList()),
                    touchGuardAlert = vm.touchGuardAlert.value,
                    userEmail = "boss@test.com",
                    onBackToCockpit = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Touch Guard Active Hai").assertIsDisplayed()
        vm.onTouchGuardTriggered()
        assertTrue(vm.touchGuardAlert.value?.contains("unauthorized touch") == true)
    }

    @Test
    fun cuj5_skillsDeck_displaysTelemetryAndRequiresConfirmationBeforeSavingSkill() {
        val uid = "cuj5_boss"
        val vm = buildTestViewModel(uid)

        composeTestRule.setContent {
            MayaTheme {
                SkillsDeckScreen(
                    viewModel = vm,
                    telemetry = vm.telemetry.value,
                    skillsState = DataUiState.Success(
                        listOf(
                            SkillPlaybook(
                                id = "skill_1",
                                userId = uid,
                                kind = "skill",
                                name = "Raat Mode",
                                trigger = "Good night Maya",
                                stepsText = "1. Torch off\n2. Silent mode"
                            )
                        )
                    ),
                    pendingConfirmation = null,
                    onBackToCockpit = {},
                    onRunMacroInCockpit = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Phone Deck, Skills & Macros").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_skill_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("torch_quick_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("autopilot_quick_card").assertIsDisplayed()

        // Section 4.1: Saving a new skill must ask confirmation first
        assertNull(vm.pendingConfirmation.value)
        vm.previewAndConfirmSkill("skill", "Subah Routine", "Good morning", "1. Weather\n2. News")
        assertTrue(vm.pendingConfirmation.value != null)
        vm.cancelPendingAction()
        assertNull(vm.pendingConfirmation.value)
    }

    @Test
    fun cuj6_phoneActionExecutor_readsRealBatteryAudioClipboardAndTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = PhoneActionExecutor(context)

        val batteryOutcome = executor.getBatteryStatusText()
        assertTrue(batteryOutcome.success)
        assertTrue(batteryOutcome.messageHinglish.contains("battery"))

        val volOutcome = executor.setMediaVolumePercent(50)
        assertTrue(volOutcome.success)

        val clipWrite = executor.writeClipboard("MayaTest", "HinglishClipboardCheck")
        assertTrue(clipWrite.success)
        assertEquals("HinglishClipboardCheck", executor.readClipboard())

        val timeStr = executor.getCurrentTimeText("Asia/Kolkata")
        assertTrue(timeStr.contains("Asia/Kolkata"))

        // Verify ListenMode voice shortcuts ("sirf meri suno" / "sabki suno")
        val vm = buildTestViewModel("cuj6_boss")
        vm.sendCommand("Sabki suno")
        assertTrue(vm.messages.value.last().text.contains("sabki awaaz"))
        vm.sendCommand("Sirf meri suno")
        assertTrue(vm.messages.value.last().text.contains("sirf aapki awaaz"))
    }
}
