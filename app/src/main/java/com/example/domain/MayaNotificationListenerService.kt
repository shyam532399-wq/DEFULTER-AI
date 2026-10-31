package com.example.domain

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecentNotificationSummary(
    val packageName: String,
    val appLabel: String,
    val senderOrTitle: String,
    val previewSafe: String,
    val timestampMs: Long
)

/**
 * Notification Listener registered as "Maya WhatsApp Reader" in Android Settings
 * (Step 3 of 3 in "Set up Maya" -> Notification access).
 * Strictly obeys Privacy Rule 9: keeps only sanitized ephemeral summaries in RAM,
 * never logs or reads OTPs, bank codes, or password notifications.
 */
class MayaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
    }

    override fun onListenerDisconnected() {
        _isConnected.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return

        val extras = posted.notification?.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (rawTitle.isBlank() && rawText.isBlank()) return

        // Privacy Rule 9: Never capture OTPs, verification codes, passwords, or banking alerts
        val combinedLower = "$rawTitle $rawText".lowercase()
        val sensitiveKeywords = listOf("otp", "verification code", "passcode", "password", "cvv", "pin is", "one time")
        if (sensitiveKeywords.any { combinedLower.contains(it) }) return

        val pkg = posted.packageName
        val friendlyApp = when {
            pkg.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            pkg.contains("telegram", ignoreCase = true) -> "Telegram"
            pkg.contains("dialer", ignoreCase = true) || pkg.contains("incallui", ignoreCase = true) -> "Phone Call"
            pkg.contains("messaging", ignoreCase = true) || pkg.contains("mms", ignoreCase = true) -> "Messages"
            else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

        val item = RecentNotificationSummary(
            packageName = pkg,
            appLabel = friendlyApp,
            senderOrTitle = rawTitle.take(60).ifBlank { friendlyApp },
            previewSafe = rawText.take(120),
            timestampMs = System.currentTimeMillis()
        )
        _recentItems.value = (listOf(item) + _recentItems.value).take(8)
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _recentItems = MutableStateFlow<List<RecentNotificationSummary>>(emptyList())
        val recentItems: StateFlow<List<RecentNotificationSummary>> = _recentItems.asStateFlow()
    }
}

data class SystemAliveStatus(
    val batteryUnrestricted: Boolean = false,
    val drawOverlaysAllowed: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val defaultAssistantSelected: Boolean = false
)

object MayaSystemSetupHelper {

    fun readStatus(context: Context): SystemAliveStatus {
        val appCtx = context.applicationContext
        val pkg = appCtx.packageName

        val batteryOk = runCatching {
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(pkg) == true
        }.getOrDefault(false)

        val overlayOk = runCatching {
            Settings.canDrawOverlays(appCtx)
        }.getOrDefault(false)

        val notifAccessOk = runCatching {
            MayaNotificationListenerService.isConnected.value ||
                NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(pkg)
        }.getOrDefault(false)

        val accessibilityOk = runCatching {
            if (MayaAccessibilityService.isConnected.value) {
                true
            } else {
                val expected = ComponentName(appCtx, MayaAccessibilityService::class.java).flattenToString()
                val enabledServices = Settings.Secure.getString(
                    appCtx.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                enabledServices.split(':').any {
                    it.equals(expected, ignoreCase = true) || it.contains(pkg, ignoreCase = true)
                }
            }
        }.getOrDefault(false)

        val assistantOk = runCatching {
            val currentAssistant = Settings.Secure.getString(appCtx.contentResolver, "assistant").orEmpty()
            val voiceInteraction = Settings.Secure.getString(appCtx.contentResolver, "voice_interaction_service").orEmpty()
            currentAssistant.contains(pkg, ignoreCase = true) ||
                voiceInteraction.contains(pkg, ignoreCase = true)
        }.getOrDefault(false)

        return SystemAliveStatus(
            batteryUnrestricted = batteryOk,
            drawOverlaysAllowed = overlayOk,
            notificationAccessEnabled = notifAccessOk,
            accessibilityEnabled = accessibilityOk,
            defaultAssistantSelected = assistantOk
        )
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val pkg = context.packageName
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$pkg")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(directIntent) }
            .recoverCatching { context.startActivity(fallbackIntent) }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val pkg = context.packageName
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$pkg")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(intent) }
            .recoverCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openDefaultAssistantSettings(context: Context) {
        val assistSettings = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val defaultAppsSettings = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(assistSettings) }
            .recoverCatching { context.startActivity(defaultAppsSettings) }
    }
}
