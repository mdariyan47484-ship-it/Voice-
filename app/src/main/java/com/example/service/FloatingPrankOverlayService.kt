package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VoicePrankRepository
import com.example.model.PrankVoiceClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingPrankOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_prank_overlay_channel"
        const val NOTIFICATION_ID = 202
        const val ACTION_START = "ACTION_START_OVERLAY"
        const val ACTION_STOP = "ACTION_STOP_OVERLAY"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingPrankOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingPrankOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var panelView: View? = null

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private lateinit var repository: VoicePrankRepository
    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectClipsJob: Job? = null
    private var collectActiveJob: Job? = null
    private var collectAdbJob: Job? = null

    private var clipsContainer: LinearLayout? = null
    private var volumeLabel: TextView? = null
    private var volumeSeekBar: SeekBar? = null
    private var pairingStatusTv: TextView? = null
    private var pairActivateBtn: Button? = null
    private var statusSubtitleTv: TextView? = null

    override fun onCreate() {
        super.onCreate()
        repository = VoicePrankRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanupOverlayViews()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        }

        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())
        setupOverlayViews()

        return START_STICKY
    }

    private fun setupOverlayViews() {
        if (bubbleView != null) return

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val dp58 = dpToPx(58)
            bubbleParams = WindowManager.LayoutParams(
                dp58,
                dp58,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(20)
                y = dpToPx(180)
            }

            val panelWidth = dpToPx(320)
            val panelHeight = dpToPx(420)
            panelParams = WindowManager.LayoutParams(
                panelWidth,
                panelHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            createBubbleView()
            createPanelView()

            windowManager?.addView(bubbleView, bubbleParams)

            observeRepository()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createBubbleView() {
        val bubble = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_floating_bubble)

            // Inner center icon
            val logoText = TextView(context).apply {
                text = "🎙️"
                textSize = 24f
                gravity = Gravity.CENTER
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            addView(logoText, lp)

            // Drag and click handling
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(this, bubbleParams)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (diffX < 12 && diffY < 12) {
                            showPanel()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        bubbleView = bubble
    }

    private fun createPanelView() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_floating_panel)
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
        }

        // Top Header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTv = TextView(this).apply {
            text = "🎙️ Voice Prank Soundboard"
            setTextColor(Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleTv)

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener { hidePanel() }
        }
        headerRow.addView(closeBtn)

        root.addView(headerRow)

        // Pairing Status Indicator Row
        val pairingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val pairTv = TextView(this).apply {
            val isPaired = repository.adbState.value.isConnected
            text = if (isPaired) "🟢 ACTIVATED (100% Ready for Calls)" else "🔴 NOT PAIRED (Locked)"
            setTextColor(if (isPaired) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pairingRow.addView(pairTv)
        pairingStatusTv = pairTv

        val pairBtn = Button(this).apply {
            val isPaired = repository.adbState.value.isConnected
            visibility = if (isPaired) View.GONE else View.VISIBLE
            text = "⚡ Activate"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(null, Typeface.BOLD)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = dpToPx(6).toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(30))
            setOnClickListener {
                repository.activatePairing { _, _ ->
                    pairTv.text = "🟢 ACTIVATED (100% Ready for Calls)"
                    pairTv.setTextColor(Color.parseColor("#10B981"))
                    visibility = View.GONE
                    populateClips(repository.allClips.value, repository.activePlayingClipId.value)
                }
            }
        }
        pairingRow.addView(pairBtn)
        pairActivateBtn = pairBtn

        root.addView(pairingRow)

        // Volume Controller Section ("সাউন্ড বাড়ানো কমানো")
        val volSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(6), 0, dpToPx(6))
        }

        val currentVolPercent = (repository.myMonitorVolume.value * 100).toInt()
        val volLabel = TextView(this).apply {
            text = "🔊 My Ear Hearing Sound: $currentVolPercent%"
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        volSection.addView(volLabel)
        volumeLabel = volLabel

        val seekBar = SeekBar(this).apply {
            max = 100
            progress = currentVolPercent
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        volLabel.text = "🔊 My Ear Hearing Sound: $progress%"
                        repository.setMyMonitorVolume(progress / 100f)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        volSection.addView(seekBar)
        volumeSeekBar = seekBar

        root.addView(volSection)

        // Subtitle
        val subTv = TextView(this).apply {
            text = "Tap voice to inject directly into microphone:"
            setTextColor(Color.parseColor("#9E9E9E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 0, 0, dpToPx(6))
        }
        root.addView(subTv)
        statusSubtitleTv = subTv

        // Scrollable List of Voice Clips
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val clipsList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        clipsContainer = clipsList
        scrollView.addView(clipsList)

        root.addView(scrollView)

        // Bottom Action Row (Stop & Add from Phone)
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(10), 0, 0)
        }

        val stopBtn = Button(this).apply {
            text = "⏹ Stop"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1f).apply {
                marginEnd = dpToPx(6)
            }
            setOnClickListener {
                repository.stopPlayback()
            }
        }
        bottomRow.addView(stopBtn)

        val addBtn = Button(this).apply {
            text = "➕ Add from Phone"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#00E5FF"))
                cornerRadius = dpToPx(8).toFloat()
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1.5f)
            setOnClickListener {
                hidePanel()
                val openIntent = Intent(this@FloatingPrankOverlayService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("OPEN_ADD_DIALOG", true)
                }
                startActivity(openIntent)
            }
        }
        bottomRow.addView(addBtn)

        root.addView(bottomRow)

        panelView = root
    }

    private fun observeRepository() {
        collectClipsJob?.cancel()
        collectClipsJob = scope.launch {
            repository.allClips.collectLatest { clips ->
                populateClips(clips, repository.activePlayingClipId.value)
            }
        }

        collectActiveJob?.cancel()
        collectActiveJob = scope.launch {
            repository.activePlayingClipId.collectLatest { activeId ->
                populateClips(repository.allClips.value, activeId)
            }
        }

        collectAdbJob?.cancel()
        collectAdbJob = scope.launch {
            repository.adbState.collectLatest { adb ->
                val isPaired = adb.isConnected
                pairingStatusTv?.text = if (isPaired) "🟢 ACTIVATED (100% Ready for Calls)" else "🔴 NOT PAIRED (Locked)"
                pairingStatusTv?.setTextColor(if (isPaired) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                pairActivateBtn?.visibility = if (isPaired) View.GONE else View.VISIBLE
                populateClips(repository.allClips.value, repository.activePlayingClipId.value)
            }
        }
    }

    private fun populateClips(clips: List<PrankVoiceClip>, activeId: String?) {
        val container = clipsContainer ?: return
        container.removeAllViews()

        if (clips.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No clips available. Tap 'Add from Phone' below."
                setTextColor(Color.parseColor("#9E9E9E"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dpToPx(10), dpToPx(20), dpToPx(10), dpToPx(20))
                gravity = Gravity.CENTER
            }
            container.addView(emptyTv)
            return
        }

        val isPaired = repository.adbState.value.isConnected

        clips.forEach { clip ->
            val isPlaying = activeId == clip.id
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(if (isPlaying) R.drawable.bg_clip_item_active else R.drawable.bg_clip_item)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(6))
                }
                isClickable = true
                setOnClickListener {
                    if (!isPaired) {
                        statusSubtitleTv?.text = "⚠️ Not Paired! Tap 'Activate' above to enable voice sending."
                        statusSubtitleTv?.setTextColor(Color.parseColor("#EF4444"))
                        return@setOnClickListener
                    }
                    repository.playClip(clip) { isSuccess, msg ->
                        statusSubtitleTv?.text = msg
                        statusSubtitleTv?.setTextColor(if (isSuccess) Color.parseColor("#00E5FF") else Color.parseColor("#EF4444"))
                    }
                }
            }

            // Emoji icon
            val emojiTv = TextView(this).apply {
                text = clip.iconEmoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(0, 0, dpToPx(8), 0)
            }
            row.addView(emojiTv)

            // Title & duration
            val titleCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val clipTitle = TextView(this).apply {
                text = clip.title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
            }
            titleCol.addView(clipTitle)

            val clipMeta = TextView(this).apply {
                text = "${clip.durationSeconds}s • ${clip.category}"
                setTextColor(Color.parseColor("#A0A0A0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            }
            titleCol.addView(clipMeta)

            row.addView(titleCol)

            // Status label
            val statusTv = TextView(this).apply {
                text = if (isPlaying) "✅ SUCCESS 100%" else if (isPaired) "▶ SEND" else "🔒 LOCKED"
                setTextColor(if (isPlaying) Color.parseColor("#10B981") else if (isPaired) Color.parseColor("#00E5FF") else Color.parseColor("#EF4444"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTypeface(null, Typeface.BOLD)
                setPadding(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))
            }
            row.addView(statusTv)

            container.addView(row)
        }
    }

    private fun showPanel() {
        try {
            if (panelView?.windowToken == null) {
                // Update volume from repository
                val vol = (repository.myMonitorVolume.value * 100).toInt()
                volumeSeekBar?.progress = vol
                volumeLabel?.text = "🔊 My Ear Hearing Sound: $vol%"

                // Hide bubble, show panel
                bubbleView?.let { windowManager?.removeView(it) }
                panelView?.let { windowManager?.addView(it, panelParams) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hidePanel() {
        try {
            if (panelView?.windowToken != null) {
                panelView?.let { windowManager?.removeView(it) }
                bubbleView?.let { windowManager?.addView(it, bubbleParams) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupOverlayViews() {
        try {
            collectClipsJob?.cancel()
            collectActiveJob?.cancel()
            collectAdbJob?.cancel()

            panelView?.let {
                if (it.windowToken != null) windowManager?.removeView(it)
                panelView = null
            }
            bubbleView?.let {
                if (it.windowToken != null) windowManager?.removeView(it)
                bubbleView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingPrankOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Prank Floating Logo Active 🎙️")
            .setContentText("Tap the floating logo on your screen to play sounds over calls")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Prank Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating soundboard bubble over calls and apps"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupOverlayViews()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
