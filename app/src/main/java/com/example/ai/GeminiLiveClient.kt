package com.example.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.bridge.NamuCapacitorBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live Bidirectional Audio & Multimodal WebSocket Client
 * Connects to Gemini Live BidiGenerateContent WebSocket endpoint for sub-second
 * real-time voice conversations and live screen frame multimodal analysis.
 */
class GeminiLiveClient {

    private val clientScope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var isRecordingAudio = false

    private val _connectionState = MutableStateFlow(LiveState.DISCONNECTED)
    val connectionState: StateFlow<LiveState> = _connectionState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _connectionState.value = LiveState.ERROR_NO_KEY
            Log.w(TAG, "GEMINI_API_KEY placeholder or empty.")
            return
        }

        _connectionState.value = LiveState.CONNECTING
        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Gemini Live WebSocket opened successfully.")
                _connectionState.value = LiveState.CONNECTED
                sendInitialSetup(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Gemini Live WebSocket failure: ${t.message}")
                _connectionState.value = LiveState.DISCONNECTED
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = LiveState.DISCONNECTED
            }
        })
    }

    private fun sendInitialSetup(ws: WebSocket) {
        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray(listOf("AUDIO", "TEXT")))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede") // Energetic, bright feminine voice
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are Namu, a sassy, hyper-intelligent, loyal female companion on Android 14. Keep responses brief, conversational, and charismatic. Speak then control the device!")
                        })
                    })
                })
            })
        }
        ws.send(setupPayload.toString())
        Log.d(TAG, "Sent Gemini Live setup packet.")
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)
            val serverContent = root.optJSONObject("serverContent")
            val modelTurn = serverContent?.optJSONObject("modelTurn")
            val parts = modelTurn?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val text = part.optString("text")
                    if (text.isNotBlank()) {
                        _liveTranscript.value = text
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing live ws message", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLiveMicStreaming() {
        if (_connectionState.value != LiveState.CONNECTED) return
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()
            isRecordingAudio = true

            clientScope.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecordingAudio) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val base64Pcm = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        sendAudioChunk(base64Pcm)
                    }
                }
            }
            Log.i(TAG, "Live mic streaming initiated.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
        }
    }

    private fun sendAudioChunk(base64Pcm: String) {
        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Pcm)
                    })
                })
            })
        }
        webSocket?.send(payload.toString())
    }

    fun sendScreenFrame(base64Jpeg: String) {
        if (_connectionState.value != LiveState.CONNECTED) return
        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Jpeg)
                    })
                })
            })
        }
        webSocket?.send(payload.toString())
        Log.d(TAG, "Sent live video/screen frame to Gemini Live.")
    }

    fun disconnect() {
        isRecordingAudio = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = LiveState.DISCONNECTED
    }

    enum class LiveState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR_NO_KEY
    }

    companion object {
        private const val TAG = "GeminiLiveClient"
    }
}
