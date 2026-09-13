package com.example.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.BuildConfig
import com.example.bridge.NamuCapacitorBridge
import com.example.service.NamuAccessibilityService
import com.example.service.NamuOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Namu Persona & Autonomous Execution Brain
 * Handles sassy conversation, speech synthesis (TTS), multimodal intent extraction,
 * and immediate device actuation through the Accessibility & Capacitor bridge.
 */
class NamuPersonaBrain(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "Namu",
                text = "Hey there! Namu is online. What are we conquering today? Need me to open WhatsApp, scroll through feeds, or check your notifications?",
                isUser = false,
                actionExecuted = null
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(1.15f) // Slightly higher, lively feminine tone
            tts?.setSpeechRate(1.05f) // Snappy pace
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    NamuCapacitorBridge(context).setNamuEmotion("speaking")
                }
                override fun onDone(utteranceId: String?) {
                    NamuCapacitorBridge(context).setNamuEmotion("idle")
                }
                override fun onError(utteranceId: String?) {
                    NamuCapacitorBridge(context).setNamuEmotion("idle")
                }
            })
            isTtsReady = true
            Log.d(TAG, "Namu TTS initialized successfully.")
        } else {
            Log.w(TAG, "TTS initialization failed: status=$status")
        }
    }

    fun speak(text: String) {
        if (isTtsReady && text.isNotBlank()) {
            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "namu_${System.currentTimeMillis()}")
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "namu_utterance")
            NamuOverlayService.say(context, text)
        }
    }

    /**
     * Sends user instruction to Namu.
     * Namu generates a sassy spoken reply and immediately triggers the requested device action.
     */
    fun processUserInput(userInput: String, screenContext: String? = null, screenshotBase64: String? = null) {
        val userMsg = ChatMessage(sender = "User", text = userInput, isUser = true)
        val current = _messages.value.toMutableList()
        current.add(userMsg)
        _messages.value = current

        _isThinking.value = true
        NamuCapacitorBridge(context).setNamuEmotion("thinking")

        scope.launch {
            val result = queryGeminiForAction(userInput, screenContext, screenshotBase64)
            withContext(Dispatchers.Main) {
                _isThinking.value = false

                // Add Namu's response
                val updated = _messages.value.toMutableList()
                updated.add(
                    ChatMessage(
                        sender = "Namu",
                        text = result.speech,
                        isUser = false,
                        actionExecuted = result.actionSummary
                    )
                )
                _messages.value = updated

                // Speak reply with audio
                speak(result.speech)

                // Execute action immediately via Accessibility bridge
                result.action?.let { action ->
                    executeDeviceAction(action)
                }
            }
        }
    }

    private suspend fun queryGeminiForAction(
        userInput: String,
        screenContext: String?,
        screenshotBase64: String?
    ): AssistantResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Intelligent local autonomous rule engine if API key is not yet configured
            return@withContext fallbackIntentParser(userInput)
        }

        try {
            val systemPrompt = """
                You are "Namu", a sassy, hyper-intelligent, loyal female companion and autonomous mobile OS agent on Android 14 / Realme UI 5.0 (Realme 12 Pro 5G).
                You are bold, witty, playful, proactive, and never robotic.
                When the user asks you to do something on their phone:
                1. "speech": Speak naturally, playfully, and decisively in a sassy female companion tone (e.g. "Opening WhatsApp and pinging Rahul right now. I've got your back!").
                2. "action": An object indicating the immediate OS action to execute:
                   - { "type": "openApp", "package": "com.whatsapp" }
                   - { "type": "click", "x": 540, "y": 960 }
                   - { "type": "findAndClick", "query": "Send" }
                   - { "type": "scroll", "direction": "down" }
                   - { "type": "typeText", "text": "Hello there" }
                   - { "type": "inspectTree" }
                   - { "type": "readNotifications" }
                Output strictly JSON matching:
                {
                   "speech": "string",
                   "action": { "type": "...", ... } or null
                }
            """.trimIndent()

            val contentsArray = JSONArray()
            val partsArray = JSONArray()

            var promptText = "User says: \"$userInput\""
            if (!screenContext.isNullOrBlank()) {
                promptText += "\n\nCurrent Screen Hierarchy Node Tree:\n$screenContext"
            }
            partsArray.put(JSONObject().put("text", promptText))

            if (!screenshotBase64.isNullOrBlank()) {
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", screenshotBase64)
                }
                partsArray.put(JSONObject().put("inlineData", inlineData))
            }

            contentsArray.put(JSONObject().put("parts", partsArray))

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                val rootObj = JSONObject(bodyString)
                val textCandidate = rootObj.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val parsed = JSONObject(textCandidate)
                val speech = parsed.optString("speech", "On it right away, boss!")
                val actionObj = parsed.optJSONObject("action")
                val action = actionObj?.let { parseActionFromJsonObject(it) }

                return@withContext AssistantResult(
                    speech = speech,
                    action = action,
                    actionSummary = action?.description ?: "Analyzed & responded"
                )
            } else {
                Log.w(TAG, "Gemini call failed (${response.code}), falling back to local engine")
                return@withContext fallbackIntentParser(userInput)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Gemini API", e)
            return@withContext fallbackIntentParser(userInput)
        }
    }

    private fun parseActionFromJsonObject(obj: JSONObject): DeviceAction {
        val type = obj.optString("type")
        return when (type) {
            "openApp" -> DeviceAction.OpenApp(obj.optString("package"))
            "click" -> DeviceAction.Click(obj.optDouble("x", 500.0).toFloat(), obj.optDouble("y", 500.0).toFloat())
            "findAndClick" -> DeviceAction.FindAndClick(obj.optString("query"))
            "scroll" -> DeviceAction.Scroll(obj.optString("direction", "down"))
            "typeText" -> DeviceAction.TypeText(obj.optString("text"))
            "inspectTree" -> DeviceAction.InspectTree
            "readNotifications" -> DeviceAction.ReadNotifications
            else -> DeviceAction.InspectTree
        }
    }

    private fun fallbackIntentParser(input: String): AssistantResult {
        val lower = input.lowercase()
        return when {
            lower.contains("whatsapp") -> {
                AssistantResult(
                    speech = "Opening WhatsApp right now. Let's see who's trying to get your attention!",
                    action = DeviceAction.OpenApp("com.whatsapp"),
                    actionSummary = "Launched WhatsApp"
                )
            }
            lower.contains("youtube") -> {
                AssistantResult(
                    speech = "Firing up YouTube for you. Sit back and enjoy the show!",
                    action = DeviceAction.OpenApp("com.google.android.youtube"),
                    actionSummary = "Launched YouTube"
                )
            }
            lower.contains("chrome") || lower.contains("browser") -> {
                AssistantResult(
                    speech = "Opening Chrome. What rabbit hole are we exploring today?",
                    action = DeviceAction.OpenApp("com.android.chrome"),
                    actionSummary = "Launched Chrome"
                )
            }
            lower.contains("settings") -> {
                AssistantResult(
                    speech = "Popping open system settings. Don't go breaking anything!",
                    action = DeviceAction.OpenApp("com.android.settings"),
                    actionSummary = "Opened System Settings"
                )
            }
            lower.contains("scroll down") || lower.contains("scroll") -> {
                AssistantResult(
                    speech = "Scrolling down for you. Look at that smooth slide.",
                    action = DeviceAction.Scroll("down"),
                    actionSummary = "Scrolled Screen Down"
                )
            }
            lower.contains("scroll up") -> {
                AssistantResult(
                    speech = "Scrolling back up. Found what you were looking for?",
                    action = DeviceAction.Scroll("up"),
                    actionSummary = "Scrolled Screen Up"
                )
            }
            lower.contains("click") || lower.contains("tap") -> {
                val query = input.substringAfter("click").substringAfter("tap").trim()
                AssistantResult(
                    speech = "Target acquired: tapping '$query' on your screen right now.",
                    action = DeviceAction.FindAndClick(query),
                    actionSummary = "Tapped element '$query'"
                )
            }
            lower.contains("notification") -> {
                AssistantResult(
                    speech = "Checking your recent notification feed. Let's see what arrived.",
                    action = DeviceAction.ReadNotifications,
                    actionSummary = "Intercepted Notifications"
                )
            }
            lower.contains("inspect") || lower.contains("scan screen") || lower.contains("look at") -> {
                AssistantResult(
                    speech = "Scanning every UI element on your screen. My multimodal eyes are wide open.",
                    action = DeviceAction.InspectTree,
                    actionSummary = "Inspected UI Node Tree"
                )
            }
            else -> {
                AssistantResult(
                    speech = "Consider it done! Namu is scanning your screen and ready to automate any tap, swipe, or task you throw at me.",
                    action = DeviceAction.InspectTree,
                    actionSummary = "Autonomous Scan Active"
                )
            }
        }
    }

    private fun executeDeviceAction(action: DeviceAction) {
        val service = NamuAccessibilityService.instance
        NamuCapacitorBridge(context).setNamuEmotion("executing")

        scope.launch {
            delay(400) // Realistic assistant execution cadence
            when (action) {
                is DeviceAction.OpenApp -> {
                    val ok = service?.openApp(action.packageName)
                        ?: run {
                            val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (intent != null) { context.startActivity(intent); true } else false
                        }
                    NamuCapacitorBridge.addLog("[EXEC] Opened App: ${action.packageName} (success=$ok)")
                }
                is DeviceAction.Click -> {
                    service?.performClick(action.x, action.y)
                    NamuCapacitorBridge.addLog("[EXEC] Tapped screen at (${action.x}, ${action.y})")
                }
                is DeviceAction.FindAndClick -> {
                    val clicked = service?.findAndClick(action.query) ?: false
                    NamuCapacitorBridge.addLog("[EXEC] findAndClick('${action.query}'): success=$clicked")
                }
                is DeviceAction.Scroll -> {
                    service?.scroll(action.direction)
                    NamuCapacitorBridge.addLog("[EXEC] Scrolled ${action.direction}")
                }
                is DeviceAction.TypeText -> {
                    service?.typeText(action.text)
                    NamuCapacitorBridge.addLog("[EXEC] Typed text: '${action.text}'")
                }
                is DeviceAction.InspectTree -> {
                    val tree = service?.inspectNodeTree() ?: "{}"
                    NamuCapacitorBridge.addLog("[EXEC] Scanned active screen hierarchy (${tree.length} chars)")
                }
                is DeviceAction.ReadNotifications -> {
                    val count = NamuAccessibilityService.recentNotifications.value.size
                    NamuCapacitorBridge.addLog("[EXEC] Read $count captured notifications")
                }
            }
            delay(1000)
            NamuCapacitorBridge(context).setNamuEmotion("idle")
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        private const val TAG = "NamuPersonaBrain"
    }
}

data class ChatMessage(
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val actionExecuted: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AssistantResult(
    val speech: String,
    val action: DeviceAction?,
    val actionSummary: String
)

sealed class DeviceAction(val description: String) {
    data class OpenApp(val packageName: String) : DeviceAction("Launch $packageName")
    data class Click(val x: Float, val y: Float) : DeviceAction("Click ($x, $y)")
    data class FindAndClick(val query: String) : DeviceAction("Find & Tap '$query'")
    data class Scroll(val direction: String) : DeviceAction("Scroll $direction")
    data class TypeText(val text: String) : DeviceAction("Type '$text'")
    object InspectTree : DeviceAction("Inspect On-Screen Node Tree")
    object ReadNotifications : DeviceAction("Read Incoming Notifications")
}
