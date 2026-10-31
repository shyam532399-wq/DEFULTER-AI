package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.domain.ApiKeyConnectionStatus
import com.example.domain.ApiProvider
import com.example.ui.theme.LocalPersonaAccent

@Composable
fun ApiKeyConnectDialog(
    status: ApiKeyConnectionStatus,
    onConnect: (rawKey: String, provider: ApiProvider, customModel: String) -> Unit,
    onDisconnect: () -> Unit,
    onPasteFromClipboard: () -> String,
    onOpenGeminiKeyPortal: () -> Unit,
    onTestLiveReply: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("api_key_connect_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ApiKeyConnectionCard(
                    status = status,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onPasteFromClipboard = onPasteFromClipboard,
                    onOpenGeminiKeyPortal = onOpenGeminiKeyPortal,
                    onTestLiveReply = onTestLiveReply,
                    onCloseDialog = onDismiss
                )
            }
        }
    }
}

@Composable
fun ApiKeyConnectionCard(
    status: ApiKeyConnectionStatus,
    onConnect: (rawKey: String, provider: ApiProvider, customModel: String) -> Unit,
    onDisconnect: () -> Unit,
    onPasteFromClipboard: () -> String,
    onOpenGeminiKeyPortal: () -> Unit,
    onTestLiveReply: (() -> Unit)? = null,
    onCloseDialog: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalPersonaAccent.current

    val initialProvider = when (status) {
        is ApiKeyConnectionStatus.Connected -> status.provider
        is ApiKeyConnectionStatus.Verifying -> status.provider
        is ApiKeyConnectionStatus.Error -> status.previousConnected?.provider ?: ApiProvider.GEMINI
        ApiKeyConnectionStatus.BuiltIn -> ApiProvider.GEMINI
    }

    var selectedProviderId by rememberSaveable(initialProvider.id) { mutableStateOf(initialProvider.id) }
    val selectedProvider = ApiProvider.fromId(selectedProviderId)

    var keyInput by rememberSaveable { mutableStateOf("") }
    var customModelInput by rememberSaveable { mutableStateOf("") }
    var showRawKey by remember { mutableStateOf(false) }

    val connectedInfo = when (status) {
        is ApiKeyConnectionStatus.Connected -> status
        is ApiKeyConnectionStatus.Error -> status.previousConnected
        else -> null
    }
    val isVerifying = status is ApiKeyConnectionStatus.Verifying

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (connectedInfo != null) accent.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("api_key_connection_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = accent.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.api_key_card_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(R.string.api_key_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onCloseDialog != null) {
                    IconButton(
                        onClick = onCloseDialog,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("api_key_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close_api_dialog)
                        )
                    }
                }
            }

            // Active Connection Status Pill
            if (connectedInfo != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = accent.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("api_key_connected_banner")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = accent.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connected • ${connectedInfo.provider.displayName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Key: ${connectedInfo.maskedKey}  •  Model: ${connectedInfo.activeModel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Mode: Built-in AI (Apni key connect karne ke liye neeche paste karein)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Error Feedback Banner
            if (status is ApiKeyConnectionStatus.Error) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("api_key_error_banner")
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Provider Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApiProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = { selectedProviderId = provider.id },
                        label = { Text(provider.displayName) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("api_key_provider_${provider.id}")
                    )
                }
            }

            // Masked API Key Field + Show/Hide Toggle
            OutlinedTextField(
                value = keyInput,
                onValueChange = { next ->
                    keyInput = next
                    ApiProvider.detectFromKey(next, selectedProvider)?.let { detected ->
                        selectedProviderId = detected.id
                    }
                },
                label = {
                    Text("${stringResource(R.string.api_key_input_label)} (${selectedProvider.keyPrefixHint})")
                },
                singleLine = true,
                visualTransformation = if (showRawKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { showRawKey = !showRawKey },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("api_key_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (showRawKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.cd_toggle_api_key_visibility)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input")
            )

            // Quick Paste + Get Gemini Key Helpers
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        val clip = onPasteFromClipboard().trim()
                        if (clip.isNotBlank()) {
                            keyInput = clip
                            ApiProvider.detectFromKey(clip, selectedProvider)?.let { detected ->
                                selectedProviderId = detected.id
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("api_key_paste_button")
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.api_key_paste))
                }

                OutlinedButton(
                    onClick = onOpenGeminiKeyPortal,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("api_key_get_key_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.api_key_get_gemini))
                }
            }

            // Optional Model Override Field
            OutlinedTextField(
                value = customModelInput,
                onValueChange = { customModelInput = it },
                label = {
                    Text("${stringResource(R.string.api_key_model_label)}: ${selectedProvider.defaultModel}")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_custom_model_input")
            )

            // Primary Verify & Connect Button + Disconnect Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        onConnect(keyInput, selectedProvider, customModelInput)
                        keyInput = ""
                    },
                    enabled = keyInput.isNotBlank() && !isVerifying,
                    colors = ButtonDefaults.buttonColors(containerColor = accent.primary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("api_key_connect_button")
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying…")
                    } else {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.api_key_verify_connect))
                    }
                }

                if (connectedInfo != null) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = !isVerifying,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("api_key_disconnect_button")
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.api_key_disconnect))
                    }
                }
            }

            if (connectedInfo != null && onTestLiveReply != null) {
                OutlinedButton(
                    onClick = onTestLiveReply,
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("api_key_test_reply_button")
                ) {
                    Text("Test Live AI Reply")
                }
            }
        }
    }
}
