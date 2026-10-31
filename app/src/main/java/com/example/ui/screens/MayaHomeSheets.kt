package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MayaPersona
import com.example.data.model.ReminderItem
import com.example.domain.RecentNotificationSummary

private val SheetBg = Color(0xFF0C1426)
private val SheetCardBg = Color(0xFF121D35)
private val SheetBorder = Color(0xFF22355C)
private val SheetTextMain = Color(0xFFF2F6FF)
private val SheetTextSub = Color(0xFF8A9BB8)
private val SheetBlue = Color(0xFF3B6EF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MayaHomeQuickDrawerSheet(
    activePersona: MayaPersona,
    silentListeningEnabled: Boolean,
    floatingHudVisible: Boolean,
    onDismiss: () -> Unit,
    onOpenSetupWizardStep: (Int) -> Unit,
    onOpenApiKeyDialog: () -> Unit,
    onOpenFullHud: () -> Unit,
    onToggleFloatingHud: () -> Unit,
    onToggleSilentListening: () -> Unit,
    onSelectPersona: (MayaPersona) -> Unit,
    onOpenBoardStudio: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        contentColor = SheetTextMain
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("home_quick_drawer_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MayaMiniOrbBadge(sizeDp = 34)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Maya Control Center",
                        color = SheetTextMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Setup wizard, permissions, HUD & persona",
                        color = SheetTextSub,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            DrawerActionRow(
                icon = Icons.Filled.Key,
                title = "Set up Maya (3-Step Wizard)",
                subtitle = "Gemini key, phone permissions & background alive setup",
                tag = "drawer_open_setup_wizard",
                onClick = {
                    onDismiss()
                    onOpenSetupWizardStep(1)
                }
            )

            DrawerActionRow(
                icon = Icons.Filled.SettingsSuggest,
                title = "Advanced → Permissions & Background",
                subtitle = "Battery no-optimisation, WhatsApp reader, Accessibility & Assistant",
                tag = "drawer_open_step3_permissions",
                onClick = {
                    onDismiss()
                    onOpenSetupWizardStep(3)
                }
            )

            DrawerActionRow(
                icon = Icons.Filled.Bolt,
                title = "API Key Vault (Multi-Provider)",
                subtitle = "Connect Gemini (AQ. / AIza), OpenAI, Groq, OpenRouter or DeepSeek",
                tag = "drawer_open_api_vault",
                onClick = {
                    onDismiss()
                    onOpenApiKeyDialog()
                }
            )

            DrawerActionRow(
                icon = Icons.Filled.Fullscreen,
                title = "Full-Screen Maya AI HUD",
                subtitle = "Launch Quantum HUD with live CPU/GPU/RAM/Disk & AI Status",
                tag = "drawer_open_full_hud",
                onClick = {
                    onDismiss()
                    onOpenFullHud()
                }
            )

            DrawerActionRow(
                icon = Icons.Filled.PictureInPictureAlt,
                title = if (floatingHudVisible) "Hide Floating Mini-HUD" else "Show Floating Mini-HUD",
                subtitle = "Draggable mini orb overlay across tabs",
                tag = "drawer_toggle_floating_hud",
                onClick = {
                    onToggleFloatingHud()
                    onDismiss()
                }
            )

            DrawerActionRow(
                icon = Icons.Filled.Dashboard,
                title = "Maya Board & Creative Studio",
                subtitle = "Research notes, Gemini Image Studio & Business Suite",
                tag = "drawer_open_board_studio",
                onClick = {
                    onDismiss()
                    onOpenBoardStudio()
                }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SheetCardBg)
                    .border(1.dp, SheetBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Hearing,
                    contentDescription = null,
                    tint = SheetBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silent Listening (Zero Beep)",
                        color = SheetTextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Suppresses mic start/stop beep sounds",
                        color = SheetTextSub,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = silentListeningEnabled,
                    onCheckedChange = { onToggleSilentListening() }
                )
            }

            Text(
                text = "Persona",
                color = SheetTextSub,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MayaPersona.entries.forEach { persona ->
                    FilterChip(
                        selected = activePersona == persona,
                        onClick = { onSelectPersona(persona) },
                        label = { Text(persona.displayName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SheetCardBg)
            .border(1.dp, SheetBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SheetBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF6EA2FF),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SheetTextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = SheetTextSub,
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MayaHomeNotificationsSheet(
    recentNotifications: List<RecentNotificationSummary>,
    reminders: List<ReminderItem>,
    notificationReaderConnected: Boolean,
    onOpenNotificationAccessSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SheetBg,
        contentColor = SheetTextMain
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("home_notifications_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Maya Notifications & Reader",
                        color = SheetTextMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (notificationReaderConnected) {
                            "Maya WhatsApp Reader is active"
                        } else {
                            "Turn on Notification access so Maya can read WhatsApp & calls"
                        },
                        color = SheetTextSub,
                        fontSize = 12.sp
                    )
                }
            }

            if (!notificationReaderConnected) {
                DrawerActionRow(
                    icon = Icons.Filled.Notifications,
                    title = "Enable Notification Access",
                    subtitle = "Open system settings and turn on \"Maya WhatsApp Reader\"",
                    tag = "sheet_enable_notif_access",
                    onClick = {
                        onDismiss()
                        onOpenNotificationAccessSetup()
                    }
                )
            }

            if (recentNotifications.isEmpty() && reminders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SheetCardBg)
                        .border(1.dp, SheetBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "All caught up — no unread alerts or active reminders right now.",
                        color = SheetTextSub,
                        fontSize = 13.sp
                    )
                }
            } else {
                recentNotifications.forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SheetCardBg)
                            .border(1.dp, SheetBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${item.appLabel} • ${item.senderOrTitle}",
                            color = SheetTextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.previewSafe.isNotBlank()) {
                            Text(
                                text = item.previewSafe,
                                color = SheetTextSub,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                reminders.filter { !it.completed }.take(4).forEach { rem ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SheetCardBg)
                            .border(1.dp, SheetBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "⏰ ${rem.title}",
                            color = SheetTextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${rem.scheduledInfo} • ${rem.detail}",
                            color = SheetTextSub,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
