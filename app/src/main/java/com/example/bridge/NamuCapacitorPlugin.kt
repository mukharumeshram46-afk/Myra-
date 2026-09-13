package com.example.bridge

import android.content.Context
import android.content.Intent
import com.example.service.NamuAccessibilityService
import com.example.service.NamuOverlayService
import org.json.JSONObject

/**
 * Capacitor Android Plugin: NamuBridge
 * Enables Capacitor.js and React Native hybrid APK applications to trigger
 * native Android 14 AccessibilityService OS actions:
 * - click(x, y)
 * - findAndClick(text)
 * - openApp(packageName)
 * - scroll(direction)
 * - inspectTree()
 * - startFloatingHUD()
 * - setNamuEmotion(emotion)
 */
class NamuCapacitorPlugin(private val context: Context) {

    /**
     * Executes click(x, y) via AccessibilityService gesture dispatch.
     * In Capacitor: @PluginMethod public void click(PluginCall call)
     */
    fun click(x: Float, y: Float, onResult: (Boolean, String?) -> Unit) {
        val service = NamuAccessibilityService.instance
        if (service == null) {
            onResult(false, "AccessibilityService is not enabled. Prompt user in Settings.")
            return
        }
        service.performClick(x, y) { success ->
            NamuCapacitorBridge.addLog("[Capacitor] click($x, $y) -> $success")
            onResult(success, null)
        }
    }

    /**
     * Executes findAndClick(text) by searching on-screen AccessibilityNodeInfo.
     * In Capacitor: @PluginMethod public void findAndClick(PluginCall call)
     */
    fun findAndClick(text: String): Boolean {
        val service = NamuAccessibilityService.instance ?: return false
        val success = service.findAndClick(text)
        NamuCapacitorBridge.addLog("[Capacitor] findAndClick('$text') -> $success")
        return success
    }

    /**
     * Launches external app by package name or common identifier.
     * In Capacitor: @PluginMethod public void openApp(PluginCall call)
     */
    fun openApp(packageName: String): Boolean {
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
        NamuCapacitorBridge.addLog("[Capacitor] openApp('$packageName') -> $success")
        return success
    }

    /**
     * Scrolls screen up/down/left/right via gesture swipe.
     * In Capacitor: @PluginMethod public void scroll(PluginCall call)
     */
    fun scroll(direction: String): Boolean {
        val service = NamuAccessibilityService.instance ?: return false
        val success = service.scroll(direction)
        NamuCapacitorBridge.addLog("[Capacitor] scroll('$direction') -> $success")
        return success
    }

    /**
     * Returns the active window UI hierarchy as a JSON string for Gemini parsing.
     * In Capacitor: @PluginMethod public void inspectTree(PluginCall call)
     */
    fun inspectTree(): String {
        val service = NamuAccessibilityService.instance
            ?: return "{ \"error\": \"AccessibilityService inactive\" }"
        return service.inspectNodeTree()
    }

    /**
     * Starts the SYSTEM_ALERT_WINDOW floating 3D companion avatar HUD.
     * In Capacitor: @PluginMethod public void startFloatingHUD(PluginCall call)
     */
    fun startFloatingHUD() {
        NamuOverlayService.startOverlay(context)
        NamuCapacitorBridge.addLog("[Capacitor] startFloatingHUD()")
    }

    /**
     * Stops the floating avatar HUD.
     * In Capacitor: @PluginMethod public void stopFloatingHUD(PluginCall call)
     */
    fun stopFloatingHUD() {
        NamuOverlayService.stopOverlay(context)
        NamuCapacitorBridge.addLog("[Capacitor] stopFloatingHUD()")
    }
}
