package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.bridge.NamuCapacitorBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Namu 24/7 Companion & Floating 3D Avatar HUD Foreground Service
 * Manages SYSTEM_ALERT_WINDOW draggable overlay, persistent notification,
 * and low-latency interaction trigger over any app on Android 14.
 */
class NamuOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Namu is active & listening 24/7"))
        _isOverlayRunning.value = true
        Log.i(TAG, "NamuOverlayService created")

        if (Settings.canDrawOverlays(this)) {
            setupFloatingBubble()
        } else {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission missing; overlay not drawn")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_SPEECH -> {
                val speech = intent.getStringExtra(EXTRA_SPEECH) ?: ""
                updateSpeechBubble(speech)
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 300
        }

        // Programmatically assemble the sleek floating avatar bubble
        val container = FrameLayout(this).apply {
            setPadding(16, 16, 16, 16)
        }

        // Circular glowing companion head
        val avatarBubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(160, 160)
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
        }

        val avatarImg = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(140, 140).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_namu_avatar_1789292386605)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        avatarBubble.addView(avatarImg)

        // Status badge
        val statusBadge = TextView(this).apply {
            text = "AI"
            textSize = 10f
            setTextColor(0xFF00FFCC.toInt())
            setPadding(8, 2, 8, 2)
            gravity = Gravity.CENTER
        }
        avatarBubble.addView(statusBadge)

        // Speech balloon preview
        val speechBubble = TextView(this).apply {
            id = ID_SPEECH_BUBBLE
            text = "Hey! Namu's ready."
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC0D1B2A.toInt())
            setPadding(20, 10, 20, 10)
            visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(0, 160, 0, 0)
            }
        }

        container.addView(avatarBubble)
        container.addView(speechBubble)

        // Touch listener for dragging & tapping
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasMoved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved) {
                        toggleExpandedHUD(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = container
        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "Floating avatar bubble added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding view to WindowManager", e)
        }

        // Listen for emotion updates
        serviceScope.launch {
            NamuCapacitorBridge.namuEmotion.collect { emotion ->
                statusBadge.text = when (emotion) {
                    "listening" -> "🎤 LISTENING"
                    "thinking" -> "⚡ THINKING"
                    "executing" -> "⚡ ACTION"
                    "speaking" -> "💬 TALKING"
                    else -> "NAMU"
                }
            }
        }
    }

    private fun toggleExpandedHUD(bubbleX: Int, bubbleY: Int) {
        if (isExpanded) {
            expandedView?.let { windowManager?.removeView(it) }
            expandedView = null
            isExpanded = false
        } else {
            showMiniHUD(bubbleX, bubbleY)
        }
    }

    private fun showMiniHUD(bubbleX: Int, bubbleY: Int) {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val hudParams = WindowManager.LayoutParams(
            720,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleX - 100).coerceAtLeast(40)
            y = (bubbleY + 180).coerceAtLeast(100)
        }

        val hudLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0B132B.toInt())
            setPadding(32, 24, 32, 24)
        }

        val title = TextView(this).apply {
            text = "⚡ Namu Multimodal HUD"
            textSize = 16f
            setTextColor(0xFF00FFCC.toInt())
        }
        hudLayout.addView(title)

        val quickActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }

        // Quick OS Action: Inspect Screen
        val inspectBtn = TextView(this).apply {
            text = "🔍 Inspect Screen"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1C2541.toInt())
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                val service = NamuAccessibilityService.instance
                val tree = service?.inspectNodeTree() ?: "Accessibility not active"
                updateSpeechBubble("Screen scanned: ${NamuAccessibilityService.currentForegroundApp.value}")
                NamuCapacitorBridge.addLog("[HUD] Inspected active screen node tree")
            }
        }
        quickActions.addView(inspectBtn)

        // Quick Action: Scroll Down
        val scrollBtn = TextView(this).apply {
            text = "⬇ Scroll"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1C2541.toInt())
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                NamuAccessibilityService.instance?.scroll("down")
            }
        }
        quickActions.addView(scrollBtn)

        hudLayout.addView(quickActions)

        // Launch main app
        val openAppBtn = TextView(this).apply {
            text = "Open Namu Console"
            setTextColor(0xFF00E5FF.toInt())
            setPadding(0, 8, 0, 8)
            setOnClickListener {
                val appIntent = Intent(this@NamuOverlayService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(appIntent)
                toggleExpandedHUD(0, 0)
            }
        }
        hudLayout.addView(openAppBtn)

        expandedView = hudLayout
        try {
            windowManager?.addView(expandedView, hudParams)
            isExpanded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add HUD overlay view", e)
        }
    }

    private fun updateSpeechBubble(text: String) {
        val bubble = floatingView?.findViewById<TextView>(ID_SPEECH_BUBBLE)
        bubble?.text = text
        bubble?.visibility = if (text.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun buildNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, NamuOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Namu Mobile Assistant (Online)")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_namu_avatar_1789292386605)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop HUD", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Namu 24/7 Companion Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Namu active for wake-words, screen analysis, and OS control."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        expandedView?.let { windowManager?.removeView(it) }
        _isOverlayRunning.value = false
        Log.i(TAG, "NamuOverlayService destroyed")
    }

    companion object {
        private const val TAG = "NamuOverlayService"
        const val CHANNEL_ID = "namu_assistant_channel"
        const val NOTIFICATION_ID = 2026
        const val ID_SPEECH_BUBBLE = 9912

        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_UPDATE_SPEECH = "com.example.action.UPDATE_SPEECH"
        const val EXTRA_SPEECH = "extra_speech"

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        fun startOverlay(context: Context) {
            val intent = Intent(context, NamuOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopOverlay(context: Context) {
            val intent = Intent(context, NamuOverlayService::class.java)
            context.stopService(intent)
        }

        fun say(context: Context, speech: String) {
            val intent = Intent(context, NamuOverlayService::class.java).apply {
                action = ACTION_UPDATE_SPEECH
                putExtra(EXTRA_SPEECH, speech)
            }
            context.startService(intent)
        }
    }
}
