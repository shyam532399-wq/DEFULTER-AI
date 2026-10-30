package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DataUiState
import com.example.data.model.MemoryItem
import com.example.data.model.ReminderItem
import com.example.data.model.UserProfile
import com.example.ui.MayaViewModel
import com.example.ui.theme.LocalPersonaAccent

@Composable
fun GuardianMemoryScreen(
    viewModel: MayaViewModel,
    profile: UserProfile,
    memoriesState: DataUiState<List<MemoryItem>>,
    remindersState: DataUiState<List<ReminderItem>>,
    touchGuardAlert: String?,
    onBackToCockpit: () -> Unit
) {
    BackHandler { onBackToCockpit() }

    val accent = LocalPersonaAccent.current
    var bossNameDraft by remember(profile.bossName) { mutableStateOf(profile.bossName) }
    var wikiDraft by remember(profile.personalWiki) { mutableStateOf(profile.personalWiki) }
    var passphraseDraft by remember(profile.voicePassphrase) { mutableStateOf(profile.voicePassphrase) }
    var voiceModelDraft by remember(profile.voiceModel) { mutableStateOf(profile.voiceModel) }

    var memKind by remember { mutableStateOf("fact") }
    var memKey by remember { mutableStateOf("") }
    var memValue by remember { mutableStateOf("") }

    var remKind by remember { mutableStateOf("reminder") }
    var remTitle by remember { mutableStateOf("") }
    var remScheduled by remember { mutableStateOf("") }

    LaunchedEffect(profile.bossName, profile.personalWiki) {
        bossNameDraft = profile.bossName
        wikiDraft = profile.personalWiki
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Voice Guardian & Touch Guard Security Card (Section 3.13)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shield, contentDescription = null, tint = accent.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Voice Guardian & Touch Guard", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "Speaker verification ('Sirf meri suno') aur Away / Touch Guard protection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = voiceModelDraft == "powerful",
                                    onClick = { voiceModelDraft = "powerful" },
                                    label = { Text("Powerful Model (Accurate)") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                                FilterChip(
                                    selected = voiceModelDraft == "medium",
                                    onClick = { voiceModelDraft = "medium" },
                                    label = { Text("Medium Model (Light)") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }

                            OutlinedTextField(
                                value = passphraseDraft,
                                onValueChange = { passphraseDraft = it },
                                label = { Text("Boss Voice Passphrase (jaise: Maya code red)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_passphrase_input")
                            )

                            Button(
                                onClick = { viewModel.enrollVoiceGuardian(passphraseDraft, voiceModelDraft) },
                                enabled = passphraseDraft.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("enroll_voice_button")
                            ) {
                                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (profile.voiceEnrolled) "Update Voice Guardian Profile" else "Enroll Boss Voice")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Away Voice Guard", style = MaterialTheme.typography.titleSmall)
                                    Text("Require Boss passphrase in 'Sirf meri suno'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = profile.guardArmed,
                                    onCheckedChange = viewModel::setAwayGuard,
                                    modifier = Modifier.testTag("away_guard_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Screen Touch Guard", style = MaterialTheme.typography.titleSmall)
                                    Text("Locks screen with touch alert trap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = profile.touchGuardArmed,
                                    onCheckedChange = viewModel::setTouchGuard,
                                    modifier = Modifier.testTag("touch_guard_switch")
                                )
                            }
                        }
                    }
                }

                // 2. Boss Personal Hinglish Wiki (Section 3.9)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Boss Personal Wiki (Hinglish)", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = bossNameDraft,
                                onValueChange = { bossNameDraft = it },
                                label = { Text("Maya aapko kis naam se bulaye?") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("boss_name_input")
                            )
                            OutlinedTextField(
                                value = wikiDraft,
                                onValueChange = { wikiDraft = it },
                                label = { Text("Aapki aadat, kaam aur pasand (Hinglish me)") },
                                minLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("personal_wiki_input")
                            )
                            Button(
                                onClick = { viewModel.saveBossProfileAndWiki(bossNameDraft, wikiDraft) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("save_wiki_button")
                            ) {
                                Text("Save Boss Wiki")
                            }
                        }
                    }
                }

                // 3. Reminders & Market Watchlist
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Reminders, Tasks & Watchlist", style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("reminder", "task", "watchlist").forEach { k ->
                                    FilterChip(
                                        selected = remKind == k,
                                        onClick = { remKind = k },
                                        label = { Text(k.uppercase()) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = remTitle,
                                    onValueChange = { remTitle = it },
                                    label = { Text(if (remKind == "watchlist") "Ticker (e.g. BTC)" else "Title") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = remScheduled,
                                    onValueChange = { remScheduled = it },
                                    label = { Text("Time / Note") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (remTitle.isNotBlank()) {
                                        viewModel.addReminderManual(remKind, remTitle, "", remScheduled)
                                        remTitle = ""
                                        remScheduled = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add ${remKind.replaceFirstChar { it.uppercase() }}")
                            }
                        }
                    }
                }

                if (remindersState is DataUiState.Success) {
                    items(remindersState.data, key = { it.id }) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleReminder(item) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = "Toggle completed",
                                        tint = if (item.completed) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("[${item.kind.uppercase()}] ${item.title}", style = MaterialTheme.typography.titleSmall)
                                    if (item.scheduledInfo.isNotBlank()) {
                                        Text(item.scheduledInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.deleteReminder(item.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete reminder")
                                }
                            }
                        }
                    }
                }

                // 4. Memories & App Nicknames
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Memories & App Nicknames", style = MaterialTheme.typography.titleLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = memKind == "fact",
                                    onClick = { memKind = "fact" },
                                    label = { Text("Fact") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                                FilterChip(
                                    selected = memKind == "app_nickname",
                                    onClick = { memKind = "app_nickname" },
                                    label = { Text("App Nickname") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                            OutlinedTextField(
                                value = memKey,
                                onValueChange = { memKey = it },
                                label = { Text(if (memKind == "app_nickname") "Nickname (jaise: gaana app)" else "Topic") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = memValue,
                                onValueChange = { memValue = it },
                                label = { Text(if (memKind == "app_nickname") "Real App (jaise: Spotify)" else "Yaad rakhne wali baat") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = {
                                    if (memKey.isNotBlank() && memValue.isNotBlank()) {
                                        viewModel.addMemoryManual(memKind, memKey, memValue)
                                        memKey = ""
                                        memValue = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Save Memory")
                            }
                        }
                    }
                }

                if (memoriesState is DataUiState.Success) {
                    items(memoriesState.data, key = { it.id }) { mem ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${mem.key} (${mem.kind})", style = MaterialTheme.typography.titleSmall, color = accent.primary)
                                    Text(mem.value, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = { viewModel.deleteMemory(mem.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Forget memory")
                                }
                            }
                        }
                    }
                }

                // 5. On-Device Private Vault Status (No Login Required)
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_vault_status_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = accent.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "On-Device Local Mode Active • Login Ki Zaroorat Nahi",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Boss ka Wiki, Board documents, Skills aur Memories seedha phone ke private Room vault me safe hain.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Section 3.13 Touch Guard Active Shield Overlay
        if (profile.touchGuardArmed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEB090B12))
                    .clickable { viewModel.onTouchGuardTriggered() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Touch Guard Armed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("Touch Guard Active Hai", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = touchGuardAlert ?: "Screen protected hai. Kahin bhi chhune par Maya bina koi tool chalaye Boss ko alert karegi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.setTouchGuard(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Guard Band Karo")
                            }
                            if (touchGuardAlert != null) {
                                OutlinedButton(
                                    onClick = { viewModel.setTouchGuard(true) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Chalu Rehne Do")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
