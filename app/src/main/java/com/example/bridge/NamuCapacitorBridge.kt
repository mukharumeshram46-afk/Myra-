package com.example.bridge

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.example.service.NamuAccessibilityService
import com.example.service.NamuOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Namu Capacitor & Hybrid Web Bridge
 * Bridges JavaScript (React / Vite / Capacitor / Hybrid APK) to Android 14 native
 * AccessibilityService OS automation, SYSTEM_ALERT_WINDOW floating HUD, and camera/audio sensory pipelines.
 */
class NamuCapacitorBridge(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun click(x: Float, y: Float): String {
        val service = NamuAccessibilityService.instance
            ?: return errorJson("AccessibilityService not bound. Please grant accessibility permissions.")
        service.performClick(x, y)
        logAction("click", "Tapped at ($x, $y)")
        return successJson(mapOf("x" to x, "y" to y))
    }

    @JavascriptInterface
    fun findAndClick(text: String): String {
        val service = NamuAccessibilityService.instance
            ?: return errorJson("AccessibilityService not bound. Please grant accessibility permissions.")
        val success = service.findAndClick(text)
        logAction("findAndClick", "Find & Click '$text': success=$success")
        return if (success) successJson(mapOf("query" to text, "clicked" to true))
        else errorJson("Element matching '$text' not found on current screen.")
    }

    @JavascriptInterface
    fun openApp(packageName: String): String {
        val service = NamuAccessibilityService.instance
        val success = if (service != null) {
            service.openApp(packageName)
        } else {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        }
        logAction("openApp", "Opened app: $packageName (success=$success)")
        return if (success) successJson(mapOf("package" to packageName, "launched" to true))
        else errorJson("Could not launch package: $packageName")
    }

    @JavascriptInterface
    fun scroll(direction: String): String {
        val service = NamuAccessibilityService.instance
            ?: return errorJson("AccessibilityService not bound.")
        val success = service.scroll(direction)
        logAction("scroll", "Scrolled $direction")
        return successJson(mapOf("direction" to direction, "success" to success))
    }

    @JavascriptInterface
    fun inspectTree(): String {
        val service = NamuAccessibilityService.instance
            ?: return errorJson("AccessibilityService not bound.")
        val treeJson = service.inspectNodeTree()
        logAction("inspectTree", "Inspected active screen node tree")
        return treeJson
    }

    @JavascriptInterface
    fun typeText(text: String): String {
        val service = NamuAccessibilityService.instance
            ?: return errorJson("AccessibilityService not bound.")
        val success = service.typeText(text)
        logAction("typeText", "Typed '$text'")
        return successJson(mapOf("typed" to text, "success" to success))
    }

    @JavascriptInterface
    fun readNotifications(): String {
        val items = NamuAccessibilityService.recentNotifications.value
        val array = org.json.JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("packageName", item.packageName)
                put("content", item.content)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        return JSONObject().apply {
            put("status", "success")
            put("count", items.size)
            put("notifications", array)
        }.toString()
    }

    @JavascriptInterface
    fun startFloatingHUD(): String {
        mainHandler.post {
            NamuOverlayService.startOverlay(context)
        }
        logAction("startFloatingHUD", "Started floating 3D avatar HUD")
        return successJson(mapOf("floatingHUD" to "active"))
    }

    @JavascriptInterface
    fun stopFloatingHUD(): String {
        mainHandler.post {
            NamuOverlayService.stopOverlay(context)
        }
        logAction("stopFloatingHUD", "Stopped floating 3D avatar HUD")
        return successJson(mapOf("floatingHUD" to "stopped"))
    }

    @JavascriptInterface
    fun setNamuEmotion(emotion: String): String {
        _namuEmotion.value = emotion
        logAction("setEmotion", "Namu expression changed to: $emotion")
        return successJson(mapOf("emotion" to emotion))
    }

    @JavascriptInterface
    fun speakAndExecute(spokenText: String, actionJson: String): String {
        logAction("executeIntent", "Namu says: \"$spokenText\" | Action: $actionJson")
        // Execute underlying action
        try {
            val action = JSONObject(actionJson)
            val actionType = action.optString("action")
            when (actionType) {
                "openApp" -> openApp(action.optString("packageName"))
                "click" -> click(action.optDouble("x", 0.0).toFloat(), action.optDouble("y", 0.0).toFloat())
                "findAndClick" -> findAndClick(action.optString("text"))
                "scroll" -> scroll(action.optString("direction", "down"))
                "typeText" -> typeText(action.optString("text"))
            }
        } catch (e: Exception) {
            return errorJson("Failed to parse or execute action: ${e.message}")
        }
        return successJson(mapOf("status" to "executed", "speech" to spokenText))
    }

    private fun logAction(action: String, detail: String) {
        val current = _bridgeLogs.value.toMutableList()
        current.add(0, "[${System.currentTimeMillis() % 100000}] $action -> $detail")
        if (current.size > 100) current.removeAt(current.size - 1)
        _bridgeLogs.value = current
    }

    private fun successJson(data: Map<String, Any>): String {
        val obj = JSONObject().apply {
            put("status", "success")
            for ((k, v) in data) put(k, v)
        }
        return obj.toString()
    }

    private fun errorJson(message: String): String {
        val obj = JSONObject().apply {
            put("status", "error")
            put("message", message)
        }
        return obj.toString()
    }

    companion object {
        private val _bridgeLogs = MutableStateFlow<List<String>>(emptyList())
        val bridgeLogs: StateFlow<List<String>> = _bridgeLogs.asStateFlow()

        private val _namuEmotion = MutableStateFlow("idle")
        val namuEmotion: StateFlow<String> = _namuEmotion.asStateFlow()

        fun addLog(entry: String) {
            val current = _bridgeLogs.value.toMutableList()
            current.add(0, entry)
            if (current.size > 100) current.removeAt(current.size - 1)
            _bridgeLogs.value = current
        }
    }
}
