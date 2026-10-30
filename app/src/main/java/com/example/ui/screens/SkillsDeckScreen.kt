package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DataUiState
import com.example.data.model.SkillPlaybook
import com.example.domain.DeviceTelemetry
import com.example.domain.GitHubRemoteSkill
import com.example.domain.SystemHudMonitor
import com.example.domain.SystemHudSnapshot
import com.example.ui.GitHubSkillStoreState
import com.example.ui.MayaViewModel
import com.example.ui.PendingConfirmation
import com.example.ui.theme.LocalPersonaAccent
import java.util.Locale

@Composable
fun SkillsDeckScreen(
    viewModel: MayaViewModel,
    telemetry: DeviceTelemetry,
    skillsState: DataUiState<List<SkillPlaybook>>,
    pendingConfirmation: PendingConfirmation?,
    onBackToCockpit: () -> Unit,
    onRunMacroInCockpit: (String) -> Unit,
    hudSnapshot: SystemHudSnapshot = SystemHudSnapshot(),
    gitHubSkillStoreState: GitHubSkillStoreState = GitHubSkillStoreState.Idle
) {
    BackHandler { onBackToCockpit() }

    LaunchedEffect(Unit) {
        viewModel.refreshTelemetry()
    }

    val autopilotActive by viewModel.autopilotConnected.collectAsStateWithLifecycle()
    var showCreateSkillDialog by remember { mutableStateOf(false) }
    var repoUrlInput by rememberSaveable { mutableStateOf("https://github.com/HunterisLive-1/maya-ui.git") }
    val accent = LocalPersonaAccent.current

    val builtInMacros = remember {
        listOf(
            Triple("maya system hud", "Maya AI HUD System Diagnostic", "System HUD status aur network speed Board pe rakho"),
            Triple("floating hud", "Floating Mini-HUD Overlay", "Floating HUD on karo"),
            Triple("gemini image", "Gemini Image Macro", "Gemini se image banao: Sunset on a beach in Goa, cinematic"),
            Triple("chatgpt image", "ChatGPT Image Flow", "ChatGPT kholkar image prompt bhejne ka mission plan banao"),
            Triple("gemini video", "Gemini Video Flow", "Gemini app me video generation ka step-by-step mission plan banao"),
            Triple("chatgpt se poochho", "ChatGPT Se Poochho", "ChatGPT app kholkar sawaal puchhne aur jawab laane ka mission plan banao"),
            Triple("gemini se poochho", "Gemini Se Poochho (Research)", "EV charging business India 2026 par short research Board pe rakho")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Phone Deck, Skills & Macros",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Real hardware + maya-ui HUD + GitHub Skill Store (max 25)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showCreateSkillDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("create_skill_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Iski Skill Banao")
                    }
                }
            }

            if (pendingConfirmation != null) {
                item {
                    Surface(
                        color = accent.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(pendingConfirmation.title, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(pendingConfirmation.previewDetails, style = MaterialTheme.typography.labelMedium)
                            Row {
                                TextButton(
                                    onClick = viewModel::confirmPendingAction,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("skills_confirm_save_button")
                                ) {
                                    Text("Haan, Save Kar Do")
                                }
                                TextButton(
                                    onClick = viewModel::cancelPendingAction,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Rehne Do")
                                }
                            }
                        }
                    }
                }
            }

            // 1. Live Hardware, Autopilot & HUD Telemetry Deck
            item {
                Text(
                    text = "Live Phone Hardware & Autopilot",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HardwareTile(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("torch_quick_card"),
                        icon = if (telemetry.torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        title = if (telemetry.torchEnabled) "Torch ON" else "Torch OFF",
                        subtitle = "Tap to toggle flash",
                        active = telemetry.torchEnabled,
                        onClick = viewModel::quickToggleTorch
                    )
                    HardwareTile(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("autopilot_quick_card"),
                        icon = Icons.Filled.TouchApp,
                        title = if (autopilotActive) "Autopilot ON" else "Autopilot Setup",
                        subtitle = if (autopilotActive) "tap_text & screen ready" else "Enable in Accessibility",
                        active = autopilotActive,
                        onClick = viewModel::openAccessibilityAutopilotSettings
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HardwareTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.BatteryChargingFull,
                        title = "${telemetry.batteryPercent}% Battery",
                        subtitle = String.format(Locale.US, "%.1f°C • %s", hudSnapshot.temperatureCelsius, hudSnapshot.thermalStatusLabel),
                        active = telemetry.isCharging,
                        onClick = { viewModel.refreshTelemetry() }
                    )
                    HardwareTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Memory,
                        title = "CPU ${hudSnapshot.cpuLoadPercent}%",
                        subtitle = String.format(Locale.US, "RAM %.1f/%.1fG", hudSnapshot.ramUsedGb, hudSnapshot.ramTotalGb),
                        active = false,
                        onClick = { viewModel.pinSystemHudReportToBoard() }
                    )
                    HardwareTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "Vol ${telemetry.mediaVolumePercent}%",
                        subtitle = "↓${SystemHudMonitor.formatNetRate(hudSnapshot.netDownloadKbps)}",
                        active = false,
                        onClick = { onRunMacroInCockpit("Sound settings kholo") }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                HardwareTile(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Alarm,
                    title = "Quick 7:00 AM System Alarm",
                    subtitle = "Tap to set morning wake-up alarm via Maya",
                    active = false,
                    onClick = { onRunMacroInCockpit("Mujhe kal subah 7 baje jagana") }
                )
            }

            // 2. GitHub Skill Store & HunterisLive-1/maya-ui Sync Card
            item {
                GitHubSkillStoreCard(
                    repoUrlInput = repoUrlInput,
                    onRepoUrlChange = { repoUrlInput = it },
                    storeState = gitHubSkillStoreState,
                    builtInHudPack = viewModel.builtInMayaHudSkills(),
                    onFetchRepo = { viewModel.fetchGitHubSkills(repoUrlInput) },
                    onInstallHudPack = viewModel::requestInstallMayaHudSkillPack,
                    onInstallSingleSkill = viewModel::requestInstallGitHubSkill
                )
            }

            // 3. Built-in Macros
            item {
                Text(
                    text = "Built-in Macros",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent.primary
                )
            }

            items(builtInMacros) { (code, title, command) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = accent.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text("Macro: $code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { onRunMacroInCockpit(command) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Run $title", tint = accent.primary)
                        }
                    }
                }
            }

            // 4. Boss's Custom Skills, Macros & Event Triggers in Firestore
            item {
                Text(
                    text = "Boss's Saved Skills & Triggers",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent.primary
                )
            }

            when (skillsState) {
                is DataUiState.Loading -> {}
                is DataUiState.Error -> item {
                    Text(skillsState.message, color = MaterialTheme.colorScheme.error)
                }
                is DataUiState.Success -> {
                    if (skillsState.data.isEmpty()) {
                        item {
                            Text(
                                text = "Abhi koi custom skill save nahi hai. Upar 'maya-ui HUD Pack' ya 'Iski Skill Banao' dabayein!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(skillsState.data, key = { it.id }) { skill ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${skill.name} (${skill.kind.uppercase()})", style = MaterialTheme.typography.titleSmall)
                                            Text("Trigger: ${skill.trigger}", style = MaterialTheme.typography.labelSmall, color = accent.secondary)
                                        }
                                        Switch(
                                            checked = skill.enabled,
                                            onCheckedChange = { viewModel.toggleSkill(skill) }
                                        )
                                        IconButton(
                                            onClick = { viewModel.requestDeleteSkill(skill) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete skill")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = skill.stepsText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSkillDialog) {
        NewSkillDialog(
            onDismiss = { showCreateSkillDialog = false },
            onPreview = { kind, name, trigger, steps ->
                viewModel.previewAndConfirmSkill(kind, name, trigger, steps)
                showCreateSkillDialog = false
            }
        )
    }
}

@Composable
private fun GitHubSkillStoreCard(
    repoUrlInput: String,
    onRepoUrlChange: (String) -> Unit,
    storeState: GitHubSkillStoreState,
    builtInHudPack: List<GitHubRemoteSkill>,
    onFetchRepo: () -> Unit,
    onInstallHudPack: () -> Unit,
    onInstallSingleSkill: (GitHubRemoteSkill) -> Unit
) {
    val accent = LocalPersonaAccent.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_skill_store_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.github_store_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = accent.primary
                    )
                    Text(
                        text = "HunterisLive-1/maya-ui aur GitHub playbooks (SKILL.md) seedha Maya me jodein",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onInstallHudPack,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("install_hud_pack_button")
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HUD Pack")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onRepoUrlChange("https://github.com/HunterisLive-1/maya-ui.git") },
                    label = { Text("HunterisLive-1/maya-ui") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("preset_repo_maya_ui")
                )
                AssistChip(
                    onClick = { onRepoUrlChange("https://github.com/HunterisLive-1/mobile-maya-skills") },
                    label = { Text("HunterisLive-1/mobile-maya-skills") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("preset_repo_mobile_skills")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = repoUrlInput,
                    onValueChange = onRepoUrlChange,
                    label = { Text("GitHub Repo URL ya owner/repo") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("github_skill_repo_input")
                )
                Button(
                    onClick = onFetchRepo,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("fetch_github_skills_button")
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync")
                }
            }

            when (storeState) {
                is GitHubSkillStoreState.Idle -> {
                    Text(
                        text = "Built-in HunterisLive-1/maya-ui Skills (${builtInHudPack.size} ready):",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent.secondary
                    )
                    builtInHudPack.forEach { skill ->
                        RemoteSkillCandidateRow(skill = skill, onInstall = { onInstallSingleSkill(skill) })
                    }
                }

                is GitHubSkillStoreState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "'${storeState.repoQuery}' se skills fetch ho rahi hain…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                is GitHubSkillStoreState.Error -> {
                    Text(
                        text = storeState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                is GitHubSkillStoreState.Loaded -> {
                    Text(
                        text = "${storeState.repoSlug} — ${storeState.skills.size} skills mili:",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent.secondary
                    )
                    storeState.skills.forEach { skill ->
                        RemoteSkillCandidateRow(skill = skill, onInstall = { onInstallSingleSkill(skill) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteSkillCandidateRow(
    skill: GitHubRemoteSkill,
    onInstall: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onInstall,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("install_remote_skill_${skill.id}")
            ) {
                Text("Add +")
            }
        }
    }
}

@Composable
private fun HardwareTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalPersonaAccent.current
    Surface(
        color = if (active) accent.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .heightIn(min = 84.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NewSkillDialog(
    onDismiss: () -> Unit,
    onPreview: (String, String, String, String) -> Unit
) {
    var kind by remember { mutableStateOf("skill") }
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nayi Hinglish Skill / Trigger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("skill", "macro", "rule").forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.uppercase()) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Skill Naam (jaise: Subah Briefing)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Trigger (jaise: Good morning Maya / Battery < 20%)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Hinglish Steps Playbook") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPreview(kind, name, trigger, steps) },
                enabled = name.isNotBlank() && steps.isNotBlank()
            ) {
                Text("Preview & Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
