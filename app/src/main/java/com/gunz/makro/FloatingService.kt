package com.gunz.makro

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // ---------- Tombol sentuh cepat (bubble) : TAHAN untuk aktif ----------
    private lateinit var bubbleView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var bubbleIcon: TextView
    private var autoTapIntervalMs = 150L
    private var isAutoTapping = false

    // Drag hanya berlaku saat mode edit (unlocked dari panel)
    private var bubbleInitialX = 0
    private var bubbleInitialY = 0
    private var bubbleInitialTouchX = 0f
    private var bubbleInitialTouchY = 0f
    private var isBubbleDragging = false
    private val touchSlopPx = 18
    private lateinit var doubleTapDetector: GestureDetector

    // ---------- Bantuan mode edit (konfirmasi / matikan) ----------
    private var isEditMode = false
    private var confirmView: View? = null
    private var confirmParams: WindowManager.LayoutParams? = null
    private var stopView: View? = null
    private var stopParams: WindowManager.LayoutParams? = null

    // ---------- Panel tepi kiri ----------
    private lateinit var edgePanelView: View
    private lateinit var edgePanelParams: WindowManager.LayoutParams
    private var isPanelExpanded = false
    private var collapsedPanelX = 0
    private var panelInitialY = 0
    private var panelInitialTouchX = 0f
    private var panelInitialTouchY = 0f
    private var panelInteracted = false
    private val autoCollapseRunnable = Runnable { collapsePanel() }
    private lateinit var btnCrosshair: TextView

    // ---------- Crosshair ----------
    private var crosshairView: View? = null
    private var crosshairParams: WindowManager.LayoutParams? = null
    private var crosshairInitialX = 0
    private var crosshairInitialY = 0
    private var crosshairInitialTouchX = 0f
    private var crosshairInitialTouchY = 0f
    private var isCrosshairDragging = false

    // ---------- Slider ukuran ----------
    private var sizeSliderView: View? = null
    private var sizeSliderParams: WindowManager.LayoutParams? = null

    private val autoTapRunnable = object : Runnable {
        override fun run() {
            if (!isAutoTapping) return
            val (x, y) = currentTapPoint()
            MacroAccessibilityService.instance?.tap(x, y)
            handler.postDelayed(this, autoTapIntervalMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        autoTapIntervalMs = Prefs.getTapIntervalMs(this).toLong()
        startForegroundServiceNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupBubble()
        setupEdgePanel()

        if (Prefs.isCrosshairEnabled(this)) {
            showCrosshair()
            btnCrosshair.setBackgroundResource(R.drawable.bg_panel_button_active)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        autoTapIntervalMs = Prefs.getTapIntervalMs(this).toLong()
        return START_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun newOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun currentTapPoint(): Pair<Float, Float> {
        val cView = crosshairView
        val cParams = crosshairParams
        return if (cView != null && cParams != null && Prefs.isCrosshairEnabled(this)) {
            Pair(cParams.x + cView.width / 2f, cParams.y + cView.height / 2f)
        } else {
            Pair(bubbleParams.x + bubbleView.width / 2f, bubbleParams.y + bubbleView.height / 2f)
        }
    }

    // ================= NOTIFIKASI =================

    private fun startForegroundServiceNotification() {
        val channelId = "makro_by_gunz_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Makro by Gunz - Mode Mengambang",
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Makro by Gunz aktif")
            .setContentText("Mode mengambang sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Matikan", stopPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    // ================= TOMBOL SENTUH CEPAT (BUBBLE) =================
    // Konsep baru: TAHAN = ketuk cepat berjalan, LEPAS = berhenti.
    // Drag hanya bisa saat mode edit (dibuka dari panel).

    private fun setupBubble() {
        bubbleView = View.inflate(this, R.layout.floating_bubble, null)
        bubbleIcon = bubbleView.findViewById(R.id.bubbleIcon)

        val savedSizePx = dp(Prefs.getBubbleSizeDp(this))
        bubbleIcon.layoutParams = bubbleIcon.layoutParams.apply {
            width = savedSizePx
            height = savedSizePx
        }

        bubbleParams = newOverlayParams()
        bubbleParams.x = Prefs.getBubbleX(this, 100)
        bubbleParams.y = Prefs.getBubbleY(this, 300)
        windowManager.addView(bubbleView, bubbleParams)

        doubleTapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isAutoTapping) stopAutoTap() else startAutoTap()
                return true
            }
        })

        bubbleIcon.setOnTouchListener { _, event ->
            if (isEditMode) {
                handleBubbleDrag(event)
            } else {
                doubleTapDetector.onTouchEvent(event)
            }
            true
        }
    }

    private fun handleBubbleDrag(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                bubbleInitialX = bubbleParams.x
                bubbleInitialY = bubbleParams.y
                bubbleInitialTouchX = event.rawX
                bubbleInitialTouchY = event.rawY
                isBubbleDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - bubbleInitialTouchX
                val dy = event.rawY - bubbleInitialTouchY
                if (!isBubbleDragging && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
                    isBubbleDragging = true
                }
                if (isBubbleDragging) {
                    bubbleParams.x = bubbleInitialX + dx.toInt()
                    bubbleParams.y = bubbleInitialY + dy.toInt()
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    repositionEditButtons()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isBubbleDragging) {
                    Prefs.saveBubblePosition(this, bubbleParams.x, bubbleParams.y)
                }
                isBubbleDragging = false
            }
        }
    }

    private fun startAutoTap() {
        if (isAutoTapping) return
        if (MacroAccessibilityService.instance == null) return
        isAutoTapping = true
        autoTapIntervalMs = Prefs.getTapIntervalMs(this).toLong()
        bubbleIcon.setBackgroundResource(R.drawable.bg_bubble_active)
        handler.post(autoTapRunnable)
    }

    private fun stopAutoTap() {
        if (!isAutoTapping) return
        isAutoTapping = false
        handler.removeCallbacks(autoTapRunnable)
        bubbleIcon.setBackgroundResource(R.drawable.bg_bubble)
    }

    // ================= MODE EDIT (KONFIRMASI / MATIKAN) =================
    // Wajib dibuka lewat panel tepi kiri (tombol ✎). Selama aktif, bubble
    // bisa digeser; begitu dikonfirmasi (✓), bubble terkunci lagi.

    private fun toggleEditMode() {
        stopAutoTap()
        if (isEditMode) {
            confirmPosition()
        } else {
            isEditMode = true
            showEditButtons()
        }
    }

    private fun showEditButtons() {
        confirmView = View.inflate(this, R.layout.floating_confirm, null)
        confirmParams = newOverlayParams()
        windowManager.addView(confirmView, confirmParams)
        confirmView!!.findViewById<View>(R.id.confirmIcon).setOnClickListener { confirmPosition() }

        stopView = View.inflate(this, R.layout.floating_stop, null)
        stopParams = newOverlayParams()
        windowManager.addView(stopView, stopParams)
        stopView!!.findViewById<View>(R.id.stopIcon).setOnClickListener { stopSelf() }

        repositionEditButtons()
    }

    private fun repositionEditButtons() {
        val bubbleSize = bubbleView.width.takeIf { it > 0 } ?: dp(60)
        confirmParams?.let {
            it.x = bubbleParams.x
            it.y = bubbleParams.y + bubbleSize + 16
            confirmView?.let { v -> windowManager.updateViewLayout(v, it) }
        }
        stopParams?.let {
            it.x = bubbleParams.x + bubbleSize + 16
            it.y = bubbleParams.y + bubbleSize + 16
            stopView?.let { v -> windowManager.updateViewLayout(v, it) }
        }
    }

    private fun confirmPosition() {
        Prefs.saveBubblePosition(this, bubbleParams.x, bubbleParams.y)
        isEditMode = false
        hideEditButtons()
    }

    private fun hideEditButtons() {
        confirmView?.let { windowManager.removeView(it) }
        confirmView = null
        confirmParams = null
        stopView?.let { windowManager.removeView(it) }
        stopView = null
        stopParams = null
    }

    // ================= PANEL TEPI KIRI =================

    private fun setupEdgePanel() {
        edgePanelView = View.inflate(this, R.layout.floating_edge_panel, null)
        edgePanelParams = newOverlayParams()

        collapsedPanelX = -(dp(44) + dp(16))

        edgePanelParams.x = collapsedPanelX
        edgePanelParams.y = Prefs.getPanelY(this, 500)
        windowManager.addView(edgePanelView, edgePanelParams)

        val handle: View = edgePanelView.findViewById(R.id.panelHandle)
        btnCrosshair = edgePanelView.findViewById(R.id.btnCrosshair)
        val btnEditPosisi: TextView = edgePanelView.findViewById(R.id.btnEditPosisi)
        val btnUkuran: TextView = edgePanelView.findViewById(R.id.btnUkuran)

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    panelInitialY = edgePanelParams.y
                    panelInitialTouchX = event.rawX
                    panelInitialTouchY = event.rawY
                    panelInteracted = false
                    handler.removeCallbacks(autoCollapseRunnable)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - panelInitialTouchX
                    val dy = event.rawY - panelInitialTouchY

                    // Geser horizontal ke arah mana pun (kiri/kanan) yang cukup jauh -> toggle buka/tutup
                    if (abs(dx) > dp(28) && abs(dx) > abs(dy)) {
                        if (isPanelExpanded) collapsePanel() else expandPanel()
                        panelInteracted = true
                    } else if (abs(dy) > touchSlopPx) {
                        edgePanelParams.y = panelInitialY + dy.toInt()
                        windowManager.updateViewLayout(edgePanelView, edgePanelParams)
                        panelInteracted = true
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (panelInteracted) {
                        Prefs.savePanelY(this, edgePanelParams.y)
                    } else if (!isPanelExpanded) {
                        // Tap singkat pada garis -> buka panel juga
                        expandPanel()
                    }
                    if (isPanelExpanded) {
                        handler.postDelayed(autoCollapseRunnable, 3000L)
                    }
                    true
                }

                else -> false
            }
        }

        btnCrosshair.setOnClickListener {
            handler.removeCallbacks(autoCollapseRunnable)
            toggleCrosshair()
            handler.postDelayed(autoCollapseRunnable, 3000L)
        }

        btnEditPosisi.setOnClickListener {
            handler.removeCallbacks(autoCollapseRunnable)
            toggleEditMode()
            handler.postDelayed(autoCollapseRunnable, 3000L)
        }

        btnUkuran.setOnClickListener {
            handler.removeCallbacks(autoCollapseRunnable)
            toggleSizeSlider()
        }
    }

    private fun expandPanel() {
        if (isPanelExpanded) return
        isPanelExpanded = true
        animatePanelX(edgePanelParams.x, 0)
    }

    private fun collapsePanel() {
        if (!isPanelExpanded) return
        isPanelExpanded = false
        animatePanelX(edgePanelParams.x, collapsedPanelX)
        hideSizeSlider()
    }

    private fun animatePanelX(from: Int, to: Int) {
        val animator = ValueAnimator.ofInt(from, to)
        animator.duration = 200
        animator.addUpdateListener { anim ->
            edgePanelParams.x = anim.animatedValue as Int
            if (::edgePanelView.isInitialized) {
                windowManager.updateViewLayout(edgePanelView, edgePanelParams)
            }
        }
        animator.start()
    }

    private fun toggleCrosshair() {
        val newState = !Prefs.isCrosshairEnabled(this)
        Prefs.setCrosshairEnabled(this, newState)
        if (newState) {
            showCrosshair()
            btnCrosshair.setBackgroundResource(R.drawable.bg_panel_button_active)
        } else {
            hideCrosshair()
            btnCrosshair.setBackgroundResource(R.drawable.bg_panel_button)
        }
    }

    // ================= SLIDER UKURAN =================

    private fun toggleSizeSlider() {
        if (sizeSliderView != null) {
            hideSizeSlider()
        } else {
            showSizeSlider()
        }
    }

    private fun showSizeSlider() {
        sizeSliderView = View.inflate(this, R.layout.floating_size_slider, null)
        sizeSliderParams = newOverlayParams()
        sizeSliderParams!!.x = dp(80)
        sizeSliderParams!!.y = edgePanelParams.y
        windowManager.addView(sizeSliderView, sizeSliderParams)

        val seek: SeekBar = sizeSliderView!!.findViewById(R.id.seekUkuran)
        val minDp = 40
        val maxDp = 100
        val currentDp = Prefs.getBubbleSizeDp(this)
        seek.progress = ((currentDp - minDp) * 100 / (maxDp - minDp)).coerceIn(0, 100)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val newDp = minDp + ((maxDp - minDp) * progress / 100)
                applyBubbleSize(newDp)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val newDp = minDp + ((maxDp - minDp) * seek.progress / 100)
                Prefs.setBubbleSizeDp(this@FloatingService, newDp)
            }
        })

        sizeSliderView!!.findViewById<View>(R.id.btnTutupUkuran).setOnClickListener {
            hideSizeSlider()
        }
    }

    private fun applyBubbleSize(sizeDp: Int) {
        val sizePx = dp(sizeDp)
        bubbleIcon.layoutParams = bubbleIcon.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        bubbleIcon.requestLayout()
        if (isEditMode) repositionEditButtons()
    }

    private fun hideSizeSlider() {
        sizeSliderView?.let { windowManager.removeView(it) }
        sizeSliderView = null
        sizeSliderParams = null
    }

    // ================= CROSSHAIR =================

    private fun showCrosshair() {
        if (crosshairView != null) return

        crosshairView = View.inflate(this, R.layout.floating_crosshair, null)
        crosshairParams = newOverlayParams()
        crosshairParams!!.x = Prefs.getCrosshairX(this, 300)
        crosshairParams!!.y = Prefs.getCrosshairY(this, 600)
        windowManager.addView(crosshairView, crosshairParams)

        val icon: View = crosshairView!!.findViewById(R.id.crosshairIcon)
        icon.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    crosshairInitialX = crosshairParams!!.x
                    crosshairInitialY = crosshairParams!!.y
                    crosshairInitialTouchX = event.rawX
                    crosshairInitialTouchY = event.rawY
                    isCrosshairDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - crosshairInitialTouchX
                    val dy = event.rawY - crosshairInitialTouchY
                    if (!isCrosshairDragging && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
                        isCrosshairDragging = true
                    }
                    if (isCrosshairDragging) {
                        crosshairParams!!.x = crosshairInitialX + dx.toInt()
                        crosshairParams!!.y = crosshairInitialY + dy.toInt()
                        windowManager.updateViewLayout(crosshairView, crosshairParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isCrosshairDragging) {
                        Prefs.saveCrosshairPosition(this, crosshairParams!!.x, crosshairParams!!.y)
                    }
                    isCrosshairDragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun hideCrosshair() {
        crosshairView?.let { windowManager.removeView(it) }
        crosshairView = null
        crosshairParams = null
    }

    // ================= LIFECYCLE =================

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)

        if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
        if (::edgePanelView.isInitialized) windowManager.removeView(edgePanelView)
        confirmView?.let { windowManager.removeView(it) }
        stopView?.let { windowManager.removeView(it) }
        crosshairView?.let { windowManager.removeView(it) }
        sizeSliderView?.let { windowManager.removeView(it) }
    }

    companion object {
        const val ACTION_STOP = "com.gunz.makro.action.STOP"
    }
}
