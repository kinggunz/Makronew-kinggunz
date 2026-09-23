package com.gunz.makro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Layanan aksesibilitas nyata yang mengirim gesture "tap" ke sistem
 * lewat dispatchGesture(). Tidak ada simulasi/dummy: ini benar-benar
 * mengirim event sentuh ke layar pada koordinat yang diberikan.
 */
class MacroAccessibilityService : AccessibilityService() {

    companion object {
        // Instance aktif agar FloatingService bisa memanggil tap()
        var instance: MacroAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Tidak perlu memproses event UI untuk fitur auto-tap ini.
    }

    override fun onInterrupt() {
        // Tidak ada aksi khusus saat layanan diinterupsi.
    }

    /**
     * Mengirim satu ketukan nyata pada koordinat (x, y) di layar.
     */
    fun tap(x: Float, y: Float, durationMs: Long = 40L) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
