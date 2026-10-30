package com.example.domain

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Master Prompt Section 3.1 — Phone Control (Accessibility Autopilot).
 * Label-first tap (`tap_text`), text entry (`type_text`), screen reader (`read_screen_text`),
 * conditional wait (`wait_for_screen_text`), swipe/scroll, and Home/Back/Recents/Lock actions.
 */
class MayaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive observer; Maya reads/acts ONLY when Boss explicitly commands.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            _isConnected.value = false
        }
        super.onDestroy()
    }

    fun tapText(label: String): ActionOutcome {
        val clean = label.trim()
        if (clean.isBlank()) return ActionOutcome(false, "Tap karne ke liye button ka naam chahiye, Boss.")
        val root = rootInActiveWindow
            ?: return ActionOutcome(false, "Abhi active screen content nahi mil raha, Boss.")

        val directMatches = root.findAccessibilityNodeInfosByText(clean).orEmpty()
        val targetNode = directMatches.firstOrNull { isNodeUsable(it) }
            ?: findNodeByDescriptionRecursive(root, clean.lowercase(Locale.ROOT))

        if (targetNode == null) {
            return ActionOutcome(false, "Screen pe '$clean' button/text nahi dikh raha, Boss.")
        }

        val clickable = findClickableSelfOrParent(targetNode)
        return if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            ActionOutcome(true, "'$clean' pe tap kar diya, Boss.")
        } else {
            ActionOutcome(false, "'$clean' mila par clickable nahi tha, Boss.")
        }
    }

    fun tapCoordinates(x: Float, y: Float): ActionOutcome {
        val path = Path().apply { moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f)) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        return if (dispatched) {
            ActionOutcome(true, "Screen coordinates (${x.toInt()}, ${y.toInt()}) pe tap kar diya, Boss.")
        } else {
            ActionOutcome(false, "Coordinate tap dispatch nahi ho paya, Boss.")
        }
    }

    fun typeTextIntoFocusedField(text: String): ActionOutcome {
        val root = rootInActiveWindow
            ?: return ActionOutcome(false, "Active window nahi mili, Boss.")
        val inputNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditableRecursive(root)
            ?: return ActionOutcome(false, "Screen pe koi text input box open nahi dikh raha, Boss.")

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) {
            ActionOutcome(true, "Text box me likh diya, Boss.")
        } else {
            ActionOutcome(false, "Is input field me text set nahi ho saka, Boss.")
        }
    }

    fun scrollOrSwipe(direction: String): ActionOutcome {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat().coerceAtLeast(360f)
        val h = dm.heightPixels.toFloat().coerceAtLeast(640f)
        val dir = direction.trim().lowercase(Locale.ROOT)

        val (startX, startY, endX, endY) = when (dir) {
            "up", "upar" -> listOf(w * 0.5f, h * 0.30f, w * 0.5f, h * 0.75f)
            "left", "baayein" -> listOf(w * 0.82f, h * 0.5f, w * 0.18f, h * 0.5f)
            "right", "daayein" -> listOf(w * 0.18f, h * 0.5f, w * 0.82f, h * 0.5f)
            else -> listOf(w * 0.5f, h * 0.76f, w * 0.5f, h * 0.26f) // default scroll down
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 260L))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        return if (ok) {
            ActionOutcome(true, "Screen '$dir' scroll kar di, Boss.")
        } else {
            ActionOutcome(false, "Scroll gesture nahi chal saka, Boss.")
        }
    }

    fun readVisibleScreenText(): Pair<Boolean, String> {
        val root = rootInActiveWindow
            ?: return false to "Screen pe abhi kuch readable nahi mila, Boss."
        val items = LinkedHashSet<String>()
        collectVisibleTexts(root, items, maxCount = 70)
        if (items.isEmpty()) {
            return false to "Screen pe koi text label nahi dikh raha, Boss."
        }
        // Privacy Rule 9: Mask raw 10+ digit phone numbers
        val joined = items.joinToString(" • ") { raw ->
            raw.replace(Regex("\\b\\+?\\d{10,13}\\b"), "unknown number")
        }
        return true to joined
    }

    suspend fun waitForScreenText(
        targetText: String,
        shouldAppear: Boolean = true,
        timeoutSeconds: Int = 30
    ): ActionOutcome {
        val clean = targetText.trim()
        if (clean.isBlank()) return ActionOutcome(false, "Wait karne ke liye target text chahiye, Boss.")
        val boundedSec = timeoutSeconds.coerceIn(2, 120) // Golden Rule 10: max 120s
        val lowerTarget = clean.lowercase(Locale.ROOT)

        repeat(boundedSec) {
            val (ok, visible) = readVisibleScreenText()
            val present = ok && visible.lowercase(Locale.ROOT).contains(lowerTarget)
            if ((shouldAppear && present) || (!shouldAppear && !present)) {
                val statusWord = if (shouldAppear) "aa gaya" else "hat gaya"
                return ActionOutcome(true, "Screen pe '$clean' $statusWord, Boss.")
            }
            delay(1_000L)
        }
        return ActionOutcome(false, "${boundedSec}s wait kiya par '$clean' condition poori nahi hui, Boss.")
    }

    fun performGlobalNav(command: String): ActionOutcome {
        val cmd = command.trim().lowercase(Locale.ROOT)
        val (actionConst, label) = when {
            cmd.contains("home") -> GLOBAL_ACTION_HOME to "Home screen pe aa gaye, Boss."
            cmd.contains("recent") -> GLOBAL_ACTION_RECENTS to "Recent apps khol diye, Boss."
            cmd.contains("notif") -> GLOBAL_ACTION_NOTIFICATIONS to "Notifications shade khol diya, Boss."
            cmd.contains("lock") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                GLOBAL_ACTION_LOCK_SCREEN to "Phone lock kar diya, Boss."
            else -> GLOBAL_ACTION_BACK to "Back kar diya, Boss."
        }
        val ok = performGlobalAction(actionConst)
        return if (ok) ActionOutcome(true, label) else ActionOutcome(false, "Navigation action nahi chal paya, Boss.")
    }

    private fun isNodeUsable(node: AccessibilityNodeInfo): Boolean = node.isVisibleToUser

    private fun findClickableSelfOrParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun findNodeByDescriptionRecursive(node: AccessibilityNodeInfo?, queryLower: String): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        val txt = node.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT).orEmpty()
        if (txt.contains(queryLower) || desc.contains(queryLower)) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByDescriptionRecursive(node.getChild(i), queryLower)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditableRecursive(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditableRecursive(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun collectVisibleTexts(node: AccessibilityNodeInfo?, out: LinkedHashSet<String>, maxCount: Int) {
        if (node == null || !node.isVisibleToUser || out.size >= maxCount) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it.take(120)) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { out.add(it.take(120)) }
        for (i in 0 until node.childCount) {
            if (out.size >= maxCount) break
            collectVisibleTexts(node.getChild(i), out, maxCount)
        }
    }

    companion object {
        @Volatile
        var instance: MayaAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
