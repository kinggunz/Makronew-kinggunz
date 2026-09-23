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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // ---------- Tombol sentuh otomatis (bubble) ----------
    private lateinit var bubbleView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var autoTapIntervalMs = 150L
    private var isAutoTapping = false

    private var bubbleInitialX = 0
    private var bubbleInitialY = 0
    private var bubbleInitialTouchX = 0f
    private var bubbleInitialTouchY = 0f
    private var isBubbleDragging = false
    private val touchSlopPx = 18
    private val longPressThresholdMs = 600L
    private var longPressRunnable: Runnable? = null

    // ---------- Bantuan mode edit (konfirmasi / matikan), dipicu dari panel ----------
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

    // ---------- Crosshair (penanda titik ketuk) ----------
    private var crosshairView: View? = null
    private var crosshairParams: WindowManager.LayoutParams? = null
    private var crosshairInitialX = 0
    private var crosshairInitialY = 0
    private var crosshairInitialTouchX = 0f
    private var crosshairInitialTouchY = 0f
    private var isCrosshairDragging = false

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

    // ================= TOMBOL SENTUH OTOMATIS (BUBBLE) =================

    private fun setupBubble() {
        bubbleView = View.inflate(this, R.layout.floating_bubble, null)
        bubbleParams = newOverlayParams()
        bubbleParams.x = Prefs.getBubbleX(this, 100)
        bubbleParams.y = Prefs.getBubbleY(this, 300)
        windowManager.addView(bubbleView, bubbleParams)

        val icon: TextView = bubbleView.findViewById(R.id.bubbleIcon)

        icon.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleInitialX = bubbleParams.x
                    bubbleInitialY = bubbleParams.y
                    bubbleInitialTouchX = event.rawX
                    bubbleInitialTouchY = event.rawY
                    isBubbleDragging = false

                    // Tahan lama hanya berfungsi toggle saat TIDAK sedang mode edit.
                    // Saat mode edit, sentuhan dipakai murni untuk menggeser posisi.
                    if (!isEditMode) {
                        longPressRunnable = Runnable {
                            if (!isBubbleDragging) toggleAutoTap()
                        }
                        handler.postDelayed(longPressRunnable!!, longPressThresholdMs)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Bubble WAJIB dalam mode edit (dibuka dari panel kiri) baru bisa digeser.
                    if (!isEditMode) return@setOnTouchListener true

                    val dx = event.rawX - bubbleInitialTouchX
                    val dy = event.rawY - bubbleInitialTouchY

                    if (!isBubbleDragging && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
                        isBubbleDragging = true
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                    }

                    if (isBubbleDragging) {
                        bubbleParams.x = bubbleInitialX + dx.toInt()
                        bubbleParams.y = bubbleInitialY + dy.toInt()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                        repositionEditButtons()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    if (isBubbleDragging) {
                        Prefs.saveBubblePosition(this, bubbleParams.x, bubbleParams.y)
                    }
                    isBubbleDragging = false
                    true
                }

                else -> false
            }
        }

        updateBubbleColor()
    }

    private fun toggleAutoTap() {
        isAutoTapping = !isAutoTapping
        if (isAutoTapping) {
            if (MacroAccessibilityService.instance == null) {
                isAutoTapping = false
                return
            }
            autoTapIntervalMs = Prefs.getTapIntervalMs(this).toLong()
            handler.post(autoTapRunnable)
        } else {
            handler.removeCallbacks(autoTapRunnable)
        }
        updateBubbleColor()
    }

    private fun updateBubbleColor() {
        val icon: TextView = bubbleView.findViewById(R.id.bubbleIcon)
        icon.text = if (isAutoTapping) "●" else "M"
    }

    // ================= MODE EDIT (KONFIRMASI / MATIKAN) =================
    // Wajib dibuka lewat panel tepi kiri. Selama mode ini aktif, bubble bisa
    // digeser bebas; begitu dikonfirmasi (✓), bubble terkunci lagi (tidak bisa digeser).

    private fun toggleEditMode() {
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

        val buttonsColumn: View = edgePanelView.findViewById(R.id.panelButtonsColumn)
        collapsedPanelX = -(dp(44) + dp(16)) // lebar tombol + padding kolom

        edgePanelParams.x = collapsedPanelX
        edgePanelParams.y = Prefs.getPanelY(this, 500)
        windowManager.addView(edgePanelView, edgePanelParams)

        val handle: View = edgePanelView.findViewById(R.id.panelHandle)
        btnCrosshair = edgePanelView.findViewById(R.id.btnCrosshair)
        val btnEditPosisi: TextView = edgePanelView.findViewById(R.id.btnEditPosisi)

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

                    if (!isPanelExpanded && dx > dp(28) && abs(dx) > abs(dy)) {
                        expandPanel()
                        panelInteracted = true
                    } else if (isPanelExpanded && dx < -dp(28) && abs(dx) > abs(dy)) {
                        collapsePanel()
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
    }

    companion object {
        const val ACTION_STOP = "com.gunz.makro.action.STOP"
    }
}
