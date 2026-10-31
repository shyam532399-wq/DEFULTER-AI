package com.example.domain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeviceTelemetry(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val torchEnabled: Boolean,
    val mediaVolumePercent: Int,
    val ringerModeLabel: String
)

data class ActionOutcome(
    val success: Boolean,
    val messageHinglish: String
)

class PhoneActionExecutor(private val context: Context) {

    internal val appContext = context.applicationContext
    @Volatile
    var isTorchOn: Boolean = false
        private set

    fun readTelemetry(): DeviceTelemetry {
        val batteryStatus = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 0
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = MayaVoiceEngine.peekSharedInstance()?.getEffectiveMusicVolume(audio)
            ?: audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPct = ((curVol * 100f) / maxVol).toInt()

        val ringer = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Ring"
        }

        return DeviceTelemetry(
            batteryPercent = pct,
            isCharging = charging,
            torchEnabled = isTorchOn,
            mediaVolumePercent = volPct,
            ringerModeLabel = ringer
        )
    }

    fun setTorch(enabled: Boolean): ActionOutcome {
        return try {
            val camManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val flashCamId = camManager.cameraIdList.firstOrNull { id ->
                camManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (flashCamId == null) {
                ActionOutcome(false, "Boss, is device pe camera flash hardware nahi mila.")
            } else {
                camManager.setTorchMode(flashCamId, enabled)
                isTorchOn = enabled
                ActionOutcome(
                    true,
                    if (enabled) "Torch on ho gayi, Boss." else "Torch band kar di, Boss."
                )
            }
        } catch (e: Exception) {
            ActionOutcome(false, "Torch badalne me dikkat aayi: ${e.localizedMessage ?: "hardware busy"}")
        }
    }

    fun getBatteryStatusText(): ActionOutcome {
        val t = readTelemetry()
        val stateWord = if (t.isCharging) "charging pe hai" else "charging pe nahi hai"
        return ActionOutcome(true, "Boss, battery ${t.batteryPercent}% hai aur abhi $stateWord.")
    }

    fun setMediaVolumePercent(percent: Int): ActionOutcome {
        return try {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val bounded = percent.coerceIn(0, 100)
            val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetIndex = ((bounded / 100f) * maxVol).toInt()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
            MayaVoiceEngine.peekSharedInstance()?.onExternalMediaVolumeChanged(targetIndex)
            ActionOutcome(true, "Media volume $bounded% kar diya, Boss.")
        } catch (e: Exception) {
            ActionOutcome(false, "Volume set nahi ho paya: ${e.localizedMessage}")
        }
    }

    fun setRingerMode(mode: String): ActionOutcome {
        return try {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (mode.lowercase(Locale.ROOT)) {
                "vibrate" -> {
                    audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    ActionOutcome(true, "Phone vibrate mode pe kar diya, Boss.")
                }
                "normal", "ring" -> {
                    audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    ActionOutcome(true, "Phone normal ring mode pe aa gaya, Boss.")
                }
                "silent" -> {
                    // Android requires DND access for true silent; fallback to vibrate if restricted
                    try {
                        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                        ActionOutcome(true, "Phone silent kar diya, Boss.")
                    } catch (_: SecurityException) {
                        audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                        ActionOutcome(true, "DND permission nahi thi isliye vibrate mode laga diya, Boss.")
                    }
                }
                else -> ActionOutcome(false, "Ringer mode samajh nahi aaya ($mode).")
            }
        } catch (e: Exception) {
            ActionOutcome(false, "Ringer badalne me dikkat aayi: ${e.localizedMessage}")
        }
    }

    fun mediaControl(command: String): ActionOutcome {
        return try {
            val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val keyCode = when (command.lowercase(Locale.ROOT)) {
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "pause", "stop" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            ActionOutcome(true, "Media '$command' bhej diya, Boss.")
        } catch (e: Exception) {
            ActionOutcome(false, "Media control nahi chala: ${e.localizedMessage}")
        }
    }

    fun openSettingsPanel(panel: String): ActionOutcome {
        val lower = panel.lowercase(Locale.ROOT)
        if (lower == "notification" || lower == "notifications") {
            val notifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            }
            return launchSafeIntent(notifIntent, "Notification settings khol di, Boss.")
        }
        val action = when (lower) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "airplane", "network", "hotspot" -> Settings.ACTION_WIRELESS_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "accessibility", "autopilot" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "dnd" -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            "security", "lock" -> Settings.ACTION_SECURITY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return launchSafeIntent(Intent(action), "$panel settings khol di, Boss.")
    }

    fun openAccessibilitySettings(): ActionOutcome {
        return launchSafeIntent(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            "Accessibility settings khol di Boss — wahan 'Maya' on kar dijiye."
        )
    }

    fun listInstalledApps(filterQuery: String = ""): ActionOutcome {
        return try {
            val pm = appContext.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(mainIntent, 0)
                .map { it.loadLabel(pm).toString().trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val matched = if (filterQuery.isNotBlank()) {
                apps.filter { it.contains(filterQuery.trim(), ignoreCase = true) }
            } else {
                apps
            }

            if (matched.isEmpty()) {
                ActionOutcome(false, "Boss, '$filterQuery' se milta koi installed app nahi mila.")
            } else {
                val preview = matched.take(10).joinToString(", ")
                ActionOutcome(
                    true,
                    "Phone pe ${matched.size} apps mile, jaise: $preview."
                )
            }
        } catch (e: Exception) {
            ActionOutcome(false, "Installed apps padhne me dikkat aayi: ${e.localizedMessage}")
        }
    }

    fun openAppByName(query: String, appNicknames: Map<String, String> = emptyMap()): ActionOutcome {
        val normalized = query.trim().lowercase(Locale.ROOT)
        val resolvedName = appNicknames.entries
            .firstOrNull { it.key.lowercase(Locale.ROOT) == normalized }
            ?.value ?: query.trim()

        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val match = apps.firstOrNull { info ->
            val label = info.loadLabel(pm).toString()
            label.equals(resolvedName, ignoreCase = true) ||
                label.contains(resolvedName, ignoreCase = true) ||
                info.activityInfo.packageName.contains(resolvedName, ignoreCase = true)
        }
        return if (match != null) {
            val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launch != null) {
                launchSafeIntent(launch, "${match.loadLabel(pm)} khol diya, Boss.")
            } else {
                ActionOutcome(false, "${match.loadLabel(pm)} launch nahi ho saka.")
            }
        } else {
            ActionOutcome(false, "Boss, '$resolvedName' naam ka installed app nahi mila.")
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): ActionOutcome {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, h)
            putExtra(AlarmClock.EXTRA_MINUTES, m)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Maya Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        val formatted = String.format(Locale.US, "%02d:%02d", h, m)
        return launchSafeIntent(intent, "$formatted ka alarm khol diya, Boss.")
    }

    fun showAlarms(): ActionOutcome {
        return launchSafeIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Alarms list khol di, Boss.")
    }

    fun setTimer(seconds: Int, label: String): ActionOutcome {
        val secs = seconds.coerceAtLeast(5)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, secs)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Maya Timer" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchSafeIntent(intent, "${secs}s ka timer laga diya, Boss.")
    }

    fun addCalendarEvent(title: String, detail: String): ActionOutcome {
        val startMillis = System.currentTimeMillis() + 3_600_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, detail)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3_600_000L)
        }
        return launchSafeIntent(intent, "Calendar event '$title' ready kar diya, Boss.")
    }

    fun sendWhatsApp(phone: String, message: String): ActionOutcome {
        val encoded = URLEncoder.encode(message, "UTF-8")
        val cleanDigits = phone.filter { it.isDigit() }
        val url = if (cleanDigits.isNotEmpty()) {
            "https://wa.me/$cleanDigits?text=$encoded"
        } else {
            "https://wa.me/?text=$encoded"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return launchSafeIntent(intent, "WhatsApp pe message ready kar diya, Boss.")
    }

    fun sendTelegram(handleOrPhone: String, message: String): ActionOutcome {
        val encodedMsg = URLEncoder.encode(message, "UTF-8")
        val tgNative = Intent(Intent.ACTION_VIEW, Uri.parse("tg://msg?text=$encodedMsg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tgNative.resolveActivity(appContext.packageManager) != null) {
            launchSafeIntent(tgNative, "Telegram me message ready kar diya, Boss.")
        } else {
            val cleanHandle = handleOrPhone.trim().removePrefix("@")
            val webUrl = if (cleanHandle.isNotBlank() && cleanHandle.none { it.isWhitespace() }) {
                "https://t.me/$cleanHandle?text=$encodedMsg"
            } else {
                "https://t.me/share/url?url=&text=$encodedMsg"
            }
            launchSafeIntent(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)), "Telegram share khol diya, Boss.")
        }
    }

    fun saveContactIntent(name: String, phone: String, email: String = ""): ActionOutcome {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name.trim())
            if (phone.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, phone.trim())
            if (email.isNotBlank()) putExtra(ContactsContract.Intents.Insert.EMAIL, email.trim())
        }
        return launchSafeIntent(intent, "Contact '${name.ifBlank { phone }}' save screen khol di, Boss.")
    }

    fun shareText(title: String, body: String): ActionOutcome {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        val chooser = Intent.createChooser(sendIntent, title.ifBlank { "Share via Maya" })
        return launchSafeIntent(chooser, "Share sheet khol di, Boss.")
    }

    fun sendSms(phone: String, message: String): ActionOutcome {
        val cleanPhone = phone.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanPhone")).apply {
            putExtra("sms_body", message)
        }
        return launchSafeIntent(intent, "SMS compose khol diya, Boss.")
    }

    fun sendEmail(to: String, subject: String, body: String): ActionOutcome {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${to.trim()}")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return launchSafeIntent(intent, "Email app me draft khol diya, Boss.")
    }

    fun dialCall(phone: String): ActionOutcome {
        val clean = phone.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
        return launchSafeIntent(intent, "Dialer khol diya, Boss.")
    }

    fun playYouTube(query: String): ActionOutcome {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
        return launchSafeIntent(intent, "YouTube pe '$query' chala diya, Boss.")
    }

    fun playSpotify(query: String): ActionOutcome {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val spotifyUri = Uri.parse("spotify:search:$encoded")
        val spotifyIntent = Intent(Intent.ACTION_VIEW, spotifyUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (spotifyIntent.resolveActivity(appContext.packageManager) != null) {
            launchSafeIntent(spotifyIntent, "Spotify pe '$query' search khol diya, Boss.")
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$encoded"))
            launchSafeIntent(webIntent, "Spotify web pe '$query' khol diya, Boss.")
        }
    }

    fun navigateTo(destination: String): ActionOutcome {
        val encoded = URLEncoder.encode(destination, "UTF-8")
        val gmmUri = Uri.parse("geo:0,0?q=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, gmmUri)
        return launchSafeIntent(intent, "'$destination' ka map route khol diya, Boss.")
    }

    fun openUrl(rawUrl: String): ActionOutcome {
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        return launchSafeIntent(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)), "Link browser me khol diya, Boss.")
    }

    fun writeClipboard(label: String, text: String): ActionOutcome {
        return try {
            val cb = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText(label, text))
            ActionOutcome(true, "Clipboard me copy kar diya, Boss.")
        } catch (e: Exception) {
            ActionOutcome(false, "Clipboard write fail hua: ${e.localizedMessage}")
        }
    }

    fun readClipboard(): String {
        return try {
            val cb = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun getCurrentTimeText(zoneQuery: String? = null): String {
        return try {
            val zone = if (!zoneQuery.isNullOrBlank()) {
                ZoneId.getAvailableZoneIds()
                    .firstOrNull { it.contains(zoneQuery.trim().replace(" ", "_"), ignoreCase = true) }
                    ?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
            } else {
                ZoneId.systemDefault()
            }
            val now = ZonedDateTime.now(zone)
            val fmt = DateTimeFormatter.ofPattern("hh:mm a, dd MMM yyyy", Locale.ENGLISH)
            "Abhi ${zone.id} me ${now.format(fmt)} ho rahe hain, Boss."
        } catch (_: Exception) {
            "Abhi local time chal raha hai, Boss."
        }
    }

    fun saveBitmapToGallery(bitmap: Bitmap, title: String): ActionOutcome {
        return try {
            val cleanName = "Maya_${title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)}_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Maya")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return ActionOutcome(false, "Gallery entry create nahi ho saki.")

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            ActionOutcome(true, "Gallery me save ho gayi, Boss.")
        } catch (e: Exception) {
            ActionOutcome(false, "Gallery save fail hua: ${e.localizedMessage}")
        }
    }

    private fun launchSafeIntent(intent: Intent, successMessage: String): ActionOutcome {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            ActionOutcome(true, successMessage)
        } catch (e: Exception) {
            ActionOutcome(false, "Is kaam ke liye phone pe app available nahi mila, Boss.")
        }
    }
}
