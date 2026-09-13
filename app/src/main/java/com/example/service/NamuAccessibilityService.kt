package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Namu Accessibility Service: Autonomous Mobile OS Control Engine
 * Supports automated clicking, swiping, scrolling, node inspection, text entry,
 * app launching, and notification intercept on Android 14 / Realme UI 5.0.
 */
class NamuAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceBound.value = true
        Log.i(TAG, "Namu Accessibility Service Connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val texts = event.text.joinToString(" ")
                val pkg = event.packageName?.toString() ?: "unknown"
                val notificationRecord = NotificationItem(
                    packageName = pkg,
                    content = texts,
                    timestamp = System.currentTimeMillis()
                )
                val current = _recentNotifications.value.toMutableList()
                current.add(0, notificationRecord)
                if (current.size > 50) current.removeAt(current.size - 1)
                _recentNotifications.value = current
                Log.d(TAG, "Notification intercepted: [$pkg] $texts")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.let { _currentForegroundApp.value = it.toString() }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Namu Accessibility Service Interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceBound.value = false
        Log.i(TAG, "Namu Accessibility Service Destroyed.")
    }

    /**
     * Taps at coordinate (x, y) using gesture dispatch.
     */
    fun performClick(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click gesture completed at ($x, $y)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Click gesture cancelled at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Swipes from (startX, startY) to (endX, endY) over durationMs.
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(50))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe gesture completed: ($startX,$startY) -> ($endX,$endY)")
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe gesture cancelled")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Finds an on-screen UI element matching text, contentDescription, or viewId and clicks it.
     */
    fun findAndClick(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matchedNode = findMatchingNode(root, query.trim().lowercase())
        if (matchedNode != null) {
            // Try standard accessibility click
            var current: AccessibilityNodeInfo? = matchedNode
            while (current != null) {
                if (current.isClickable) {
                    val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        Log.d(TAG, "findAndClick clicked via node action on: $query")
                        return true
                    }
                }
                current = current.parent
            }

            // Fallback: Dispatch physical tap at node's screen center
            val bounds = Rect()
            matchedNode.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                val cx = bounds.centerX().toFloat()
                val cy = bounds.centerY().toFloat()
                performClick(cx, cy)
                Log.d(TAG, "findAndClick dispatched tap at center ($cx, $cy) for: $query")
                return true
            }
        }
        return false
    }

    private fun findMatchingNode(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (text.contains(query) || desc.contains(query) || viewId.contains(query)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findMatchingNode(child, query)
            if (result != null) return result
        }
        return null
    }

    /**
     * Scrolls the screen in the given direction (up, down, left, right).
     */
    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow
        val dir = direction.trim().lowercase()

        // Attempt scroll action on scrollable node
        if (root != null) {
            val scrollable = findScrollableNode(root)
            if (scrollable != null) {
                val action = if (dir == "up" || dir == "backward") {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                val success = scrollable.performAction(action)
                if (success) {
                    Log.d(TAG, "Scrolled via node action: $direction")
                    return true
                }
            }
        }

        // Fallback: Gesture swipe based on display metrics
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f

        when (dir) {
            "down", "forward" -> performSwipe(centerX, centerY + 350f, centerX, centerY - 350f, 300)
            "up", "backward" -> performSwipe(centerX, centerY - 350f, centerX, centerY + 350f, 300)
            "left" -> performSwipe(centerX + 350f, centerY, centerX - 350f, centerY, 300)
            "right" -> performSwipe(centerX - 350f, centerY, centerX + 350f, centerY, 300)
            else -> performSwipe(centerX, centerY + 350f, centerX, centerY - 350f, 300)
        }
        return true
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Types text into the currently active or focused input field.
     */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
        if (focusedNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Opens an Android application by its package name or common app alias.
     */
    fun openApp(packageNameOrName: String): Boolean {
        val query = packageNameOrName.trim().lowercase()
        val pkg = resolvePackageName(query)
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.i(TAG, "Launched application package: $pkg")
            true
        } else {
            Log.w(TAG, "Could not resolve launch intent for: $pkg")
            false
        }
    }

    private fun resolvePackageName(query: String): String {
        return when {
            query.contains("whatsapp") -> "com.whatsapp"
            query.contains("chrome") -> "com.android.chrome"
            query.contains("youtube") -> "com.google.android.youtube"
            query.contains("settings") -> "com.android.settings"
            query.contains("instagram") -> "com.instagram.android"
            query.contains("telegram") -> "org.telegram.messenger"
            query.contains("maps") -> "com.google.android.apps.maps"
            query.contains("spotify") -> "com.spotify.music"
            query.contains("camera") -> "com.android.camera"
            query.contains("phone") || query.contains("dialer") -> "com.google.android.dialer"
            query.contains("message") || query.contains("sms") -> "com.google.android.apps.messaging"
            query.contains(".") -> query // already package name
            else -> query
        }
    }

    /**
     * Inspects active on-screen window node hierarchy and returns a structured JSON summary.
     */
    fun inspectNodeTree(): String {
        val root = rootInActiveWindow ?: return "{ \"error\": \"No active window root available\" }"
        val jsonArray = JSONArray()
        collectNodes(root, jsonArray, 0)
        val obj = JSONObject().apply {
            put("foregroundApp", _currentForegroundApp.value)
            put("nodeCount", jsonArray.length())
            put("elements", jsonArray)
        }
        return obj.toString(2)
    }

    private fun collectNodes(node: AccessibilityNodeInfo, array: JSONArray, depth: Int) {
        if (depth > 12) return // prevent excessive recursion
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName

        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val item = JSONObject().apply {
                put("className", node.className?.toString() ?: "")
                if (!text.isNullOrBlank()) put("text", text)
                if (!desc.isNullOrBlank()) put("desc", desc)
                if (!viewId.isNullOrBlank()) put("id", viewId)
                put("clickable", node.isClickable)
                put("editable", node.isEditable)
                put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            }
            array.put(item)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodes(child, array, depth + 1)
        }
    }

    // Global navigation convenience
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    companion object {
        private const val TAG = "NamuAccessibility"
        var instance: NamuAccessibilityService? = null
            private set

        private val _isServiceBound = MutableStateFlow(false)
        val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

        private val _recentNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
        val recentNotifications: StateFlow<List<NotificationItem>> = _recentNotifications.asStateFlow()

        private val _currentForegroundApp = MutableStateFlow("unknown")
        val currentForegroundApp: StateFlow<String> = _currentForegroundApp.asStateFlow()
    }
}

data class NotificationItem(
    val packageName: String,
    val content: String,
    val timestamp: Long
)
