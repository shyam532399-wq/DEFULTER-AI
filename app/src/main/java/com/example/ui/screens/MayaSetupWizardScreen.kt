package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.ApiKeyConnectionStatus
import com.example.domain.MayaSystemSetupHelper
import com.example.domain.SystemAliveStatus

private val WizardBg = Color(0xFF080C17)
private val WizardCardBg = Color(0xFF0E1628)
private val WizardCardBorder = Color(0xFF1D2C4A)
private val WizardBluePrimary = Color(0xFF2F63F5)
private val WizardBlueBright = Color(0xFF5B8DEF)
private val WizardTextPrimary = Color(0xFFF2F6FF)
private val WizardTextMuted = Color(0xFF8A9BB8)
private val WizardPendingBg = Color(0xFF2B2214)
private val WizardPendingText = Color(0xFFF5A524)
private val WizardGrantedBg = Color(0xFF0B221D)
private val WizardGrantedBorder = Color(0xFF1D5E46)
private val WizardGrantedGreen = Color(0xFF34D399)

@Composable
fun MayaColdLaunchSplash(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WizardBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("maya_cold_launch_splash"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFF2554C7),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                color = WizardTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.loading_one_moment),
                color = WizardTextMuted,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator(
                color = WizardBluePrimary,
                trackColor = Color(0xFF16223B),
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun MayaMiniOrbBadge(
    sizeDp: Int = 36,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp * 0.28f).dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1D3566), Color(0xFF0B1326))
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFF2B4B8C),
                shape = RoundedCornerShape((sizeDp * 0.28f).dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((sizeDp * 0.68f).dp)) {
            drawArc(
                color = Color(0xFF67B7FF),
                startAngle = 200f,
                sweepAngle = 220f,
                useCenter = false,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF9B8AFB),
                startAngle = 20f,
                sweepAngle = 150f,
                useCenter = false,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun MayaSetupWizardScreen(
    currentStep: Int,
    apiKeyStatus: ApiKeyConnectionStatus,
    wizardKeyNotice: String?,
    canCloseToApp: Boolean,
    onStepChange: (Int) -> Unit,
    onOpenFreeKeyPortal: () -> Unit,
    onTestKey: (String) -> Unit,
    onSaveAndContinueKey: (String) -> Unit,
    onSkipKeyStep: () -> Unit,
    onFinishSetup: () -> Unit,
    onCloseWizard: () -> Unit
) {
    BackHandler {
        when {
            currentStep > 1 -> onStepChange(currentStep - 1)
            canCloseToApp -> onCloseWizard()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("maya_setup_wizard_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Common Wizard Header: Maya Orb + "Set up Maya" + "Step X of 3"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MayaMiniOrbBadge(sizeDp = 38)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setup_maya_title),
                        color = WizardTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Step ${currentStep.coerceIn(1, 3)} of 3",
                        color = WizardTextMuted,
                        fontSize = 12.sp
                    )
                }
                if (canCloseToApp) {
                    IconButton(
                        onClick = onCloseWizard,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("wizard_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close setup wizard",
                            tint = WizardTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Segment Progress Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (seg in 1..3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (seg <= currentStep) WizardBluePrimary else Color(0xFF1C2538)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (currentStep.coerceIn(1, 3)) {
                1 -> Step1AddGeminiKeyContent(
                    apiKeyStatus = apiKeyStatus,
                    wizardKeyNotice = wizardKeyNotice,
                    onOpenFreeKeyPortal = onOpenFreeKeyPortal,
                    onTestKey = onTestKey,
                    onSaveAndContinueKey = onSaveAndContinueKey,
                    onSkipKeyStep = onSkipKeyStep,
                    modifier = Modifier.weight(1f)
                )

                2 -> Step2PermissionsContent(
                    onContinue = { onStepChange(3) },
                    modifier = Modifier.weight(1f)
                )

                else -> Step3BackgroundAliveContent(
                    onFinish = onFinishSetup,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Step1AddGeminiKeyContent(
    apiKeyStatus: ApiKeyConnectionStatus,
    wizardKeyNotice: String?,
    onOpenFreeKeyPortal: () -> Unit,
    onTestKey: (String) -> Unit,
    onSaveAndContinueKey: (String) -> Unit,
    onSkipKeyStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    var keyInput by rememberSaveable { mutableStateOf("") }
    var revealKey by remember { mutableStateOf(false) }
    val isVerifying = apiKeyStatus is ApiKeyConnectionStatus.Verifying

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Add your Gemini key",
            color = WizardTextPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Maya's voice and brain run on Google's Gemini. Without a key she cannot hear you or answer. The key is free and takes about a minute.",
            color = WizardTextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        WizardNumberedInstructionRow(
            number = "1",
            annotatedText = buildAnnotatedString {
                append("Tap ")
                withStyle(SpanStyle(color = WizardTextPrimary, fontWeight = FontWeight.SemiBold)) {
                    append("\"Get a free key\"")
                }
                append(" — Google AI Studio opens. Sign in with any Google account.")
            }
        )
        WizardNumberedInstructionRow(
            number = "2",
            annotatedText = buildAnnotatedString {
                append("Tap ")
                withStyle(SpanStyle(color = WizardTextPrimary, fontWeight = FontWeight.SemiBold)) {
                    append("\"Create API key\"")
                }
                append(", pick any project, and copy the key. It starts with ")
                withStyle(SpanStyle(color = WizardTextPrimary, fontWeight = FontWeight.SemiBold)) {
                    append("\"AQ.\"")
                }
                append(" (older keys start with \"AIza\").")
            }
        )
        WizardNumberedInstructionRow(
            number = "3",
            annotatedText = buildAnnotatedString {
                append("Come back and paste it below — the key itself, not your name or email.")
            }
        )

        OutlinedButton(
            onClick = onOpenFreeKeyPortal,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WizardCardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = WizardCardBg,
                contentColor = WizardBlueBright
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .testTag("wizard_get_free_key_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Get a free key", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            singleLine = true,
            placeholder = {
                Text("AQ.....", color = WizardTextMuted.copy(alpha = 0.65f))
            },
            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { revealKey = !revealKey },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (revealKey) Icons.Filled.Visibility else Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.cd_toggle_api_key_visibility),
                        tint = Color(0xFFE0A94D)
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WizardCardBg,
                unfocusedContainerColor = WizardCardBg,
                focusedBorderColor = WizardBluePrimary,
                unfocusedBorderColor = WizardCardBorder,
                focusedTextColor = WizardTextPrimary,
                unfocusedTextColor = WizardTextPrimary,
                cursorColor = WizardBlueBright
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_api_key_input")
        )

        OutlinedButton(
            onClick = { onTestKey(keyInput) },
            enabled = !isVerifying,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WizardCardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = WizardCardBg,
                contentColor = WizardTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("wizard_test_key_button")
        ) {
            if (isVerifying) {
                CircularProgressIndicator(
                    color = WizardBlueBright,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testing key…", fontWeight = FontWeight.Medium)
            } else {
                Text("Test key", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }

        if (!wizardKeyNotice.isNullOrBlank()) {
            val isPositive = wizardKeyNotice.startsWith("✓")
            Surface(
                color = if (isPositive) WizardGrantedBg else Color(0xFF2B151B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isPositive) WizardGrantedBorder else Color(0xFF7F2737),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    text = wizardKeyNotice,
                    color = if (isPositive) WizardGrantedGreen else Color(0xFFFCA5A5),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        } else if (apiKeyStatus is ApiKeyConnectionStatus.Connected) {
            Surface(
                color = WizardGrantedBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WizardGrantedBorder, RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = "✓ Connected (${apiKeyStatus.maskedKey} • ${apiKeyStatus.activeModel})",
                    color = WizardGrantedGreen,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }

        Text(
            text = "The key is free. It is stored encrypted and used only to run Maya for you.",
            color = WizardTextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.weight(1f, fill = false))

        Button(
            onClick = { onSaveAndContinueKey(keyInput) },
            enabled = !isVerifying,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WizardBluePrimary,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("wizard_save_continue_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSkipKeyStep)
                .testTag("wizard_skip_key_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = WizardTextMuted)) {
                        append("Added a key on this phone before? ")
                    }
                    withStyle(SpanStyle(color = WizardBlueBright, fontWeight = FontWeight.SemiBold)) {
                        append("Skip")
                    }
                },
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WizardNumberedInstructionRow(
    number: String,
    annotatedText: androidx.compose.ui.text.AnnotatedString
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF142240)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = WizardBlueBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = annotatedText,
            color = WizardTextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class Step2PermissionRowSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val runtimePermissions: List<String>
)

@Composable
private fun Step2PermissionsContent(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val setupPrefs = remember(context) {
        context.applicationContext.getSharedPreferences("maya_setup_prefs", Context.MODE_PRIVATE)
    }

    var refreshTick by remember { mutableIntStateOf(0) }
    var isAskingAll by remember { mutableStateOf(false) }

    val rows = remember {
        val notifPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
        listOf(
            Step2PermissionRowSpec(
                id = "mic",
                title = "Microphone",
                subtitle = "So you can talk to Maya. Required.",
                runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO)
            ),
            Step2PermissionRowSpec(
                id = "notifications",
                title = "Notifications",
                subtitle = "Maya's own notification, which keeps her running in the background.",
                runtimePermissions = notifPerms
            ),
            Step2PermissionRowSpec(
                id = "camera",
                title = "Camera",
                subtitle = "So she can take a photo or look at what you point the phone at.",
                runtimePermissions = listOf(Manifest.permission.CAMERA)
            ),
            Step2PermissionRowSpec(
                id = "calls",
                title = "Phone calls",
                subtitle = "So she can place a call for you.",
                runtimePermissions = emptyList()
            ),
            Step2PermissionRowSpec(
                id = "contacts",
                title = "Contacts",
                subtitle = "So a name is enough — she looks up the number.",
                runtimePermissions = listOf(Manifest.permission.READ_CONTACTS)
            ),
            Step2PermissionRowSpec(
                id = "sms",
                title = "SMS",
                subtitle = "So she can send a text message.",
                runtimePermissions = emptyList()
            ),
            Step2PermissionRowSpec(
                id = "location",
                title = "Location",
                subtitle = "Weather, navigation, and where you are.",
                runtimePermissions = listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ),
            Step2PermissionRowSpec(
                id = "gallery",
                title = "Gallery & files",
                subtitle = "So she can find a photo or file and send it.",
                runtimePermissions = emptyList()
            )
        )
    }

    fun isRowGranted(spec: Step2PermissionRowSpec): Boolean {
        if (spec.runtimePermissions.isEmpty()) {
            return if (spec.id == "notifications") {
                true
            } else {
                setupPrefs.getBoolean("intent_cap_${spec.id}", false)
            }
        }
        return spec.runtimePermissions.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isAskingAll = false
        refreshTick++
    }

    // Re-check permissions when returning from system dialog
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micGranted = remember(refreshTick) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Let Maya use your phone",
                color = WizardTextPrimary,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "One tap — Android asks for each in turn. Only the microphone is required; the rest make her useful.",
                color = WizardTextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))

            rows.forEach { spec ->
                val granted = remember(refreshTick, spec.id) { isRowGranted(spec) }
                WizardCapabilityStatusCard(
                    title = spec.title,
                    subtitle = spec.subtitle,
                    isGranted = granted,
                    tag = "wizard_perm_row_${spec.id}",
                    onClick = {
                        if (spec.runtimePermissions.isEmpty()) {
                            setupPrefs.edit().putBoolean("intent_cap_${spec.id}", true).apply()
                            refreshTick++
                        } else {
                            multiPermissionLauncher.launch(spec.runtimePermissions.toTypedArray())
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                isAskingAll = true
                setupPrefs.edit()
                    .putBoolean("intent_cap_calls", true)
                    .putBoolean("intent_cap_sms", true)
                    .putBoolean("intent_cap_gallery", true)
                    .apply()
                val runtimeToAsk = rows
                    .flatMap { it.runtimePermissions }
                    .distinct()
                    .filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                if (runtimeToAsk.isEmpty()) {
                    isAskingAll = false
                    refreshTick++
                } else {
                    multiPermissionLauncher.launch(runtimeToAsk.toTypedArray())
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF162544),
                contentColor = WizardTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .border(1.dp, WizardCardBorder, RoundedCornerShape(14.dp))
                .testTag("wizard_allow_all_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = WizardBlueBright,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAskingAll) "Asking…" else "Allow all",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onContinue,
            enabled = micGranted,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WizardBluePrimary,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF17223B),
                disabledContentColor = WizardTextMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("wizard_step2_continue_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        if (!micGranted) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Continue unlocks once the microphone is allowed.",
                color = WizardTextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Step3BackgroundAliveContent(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(MayaSystemSetupHelper.readStatus(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = MayaSystemSetupHelper.readStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Keep Maya alive in the background",
                color = WizardTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Battery is required — it is what stops her going quiet when the screen is off. The rest are optional but make her far more useful.",
                color = WizardTextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))

            WizardCapabilityStatusCard(
                title = "Battery — no optimisation",
                subtitle = "Required. Without it the phone kills Maya in the background and she goes quiet. Tap, then choose Allow.",
                isGranted = status.batteryUnrestricted,
                tag = "wizard_sys_battery",
                onClick = { MayaSystemSetupHelper.openBatteryOptimizationSettings(context) }
            )

            WizardCapabilityStatusCard(
                title = "Display over other apps",
                subtitle = "So her orb can float on top of whatever you are doing.",
                isGranted = status.drawOverlaysAllowed,
                tag = "wizard_sys_overlay",
                onClick = { MayaSystemSetupHelper.openOverlayPermissionSettings(context) }
            )

            WizardCapabilityStatusCard(
                title = "Notification access",
                subtitle = "To read WhatsApp messages and know who is calling. Turn on \"Maya\" in the list.",
                isGranted = status.notificationAccessEnabled,
                tag = "wizard_sys_notifications",
                onClick = { MayaSystemSetupHelper.openNotificationListenerSettings(context) }
            )

            WizardCapabilityStatusCard(
                title = "Accessibility service",
                subtitle = "So she can open apps, tap, and read the screen for you. Turn on \"Maya\" in the list.",
                isGranted = status.accessibilityEnabled,
                tag = "wizard_sys_accessibility",
                onClick = { MayaSystemSetupHelper.openAccessibilitySettings(context) }
            )

            WizardCapabilityStatusCard(
                title = "Default assistant",
                subtitle = "Long-press the power button or swipe from a corner to call her, even on the lock screen. Pick \"MAYA\".",
                isGranted = status.defaultAssistantSelected,
                tag = "wizard_sys_assistant",
                onClick = { MayaSystemSetupHelper.openDefaultAssistantSettings(context) }
            )

            Text(
                text = "Tap a Pending row to open its system screen — come back here when you are done. You can change any of them later in Settings → Advanced → Permissions.",
                color = WizardTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onFinish,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WizardBluePrimary,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("wizard_finish_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Finish", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun WizardCapabilityStatusCard(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val bgColor = if (isGranted) WizardGrantedBg else WizardCardBg
    val borderColor = if (isGranted) WizardGrantedBorder else WizardCardBorder

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = WizardTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = WizardTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (isGranted) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(WizardGrantedGreen.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Allowed",
                    tint = WizardGrantedGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(WizardPendingBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Pending",
                    color = WizardPendingText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
