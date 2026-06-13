package com.example.cfdremoteassist.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class RemoteAssistAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }

    override fun onInterrupt() {
        Log.d("AccessibilityService", "Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AccessibilityService", "Service Connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    // Remote Control Implementation
    fun performClick(xPercent: Float, yPercent: Float) {
        val metrics = resources.displayMetrics
        val x = xPercent * metrics.widthPixels
        val y = yPercent * metrics.heightPixels
        
        Log.d("AccessibilityService", "Performing click at $x, $y ($xPercent, $yPercent)")
        
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 100))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun performSwipe(xPercent: Float, yPercent: Float, x2Percent: Float, y2Percent: Float) {
        val metrics = resources.displayMetrics
        val x1 = xPercent * metrics.widthPixels
        val y1 = yPercent * metrics.heightPixels
        val x2 = x2Percent * metrics.widthPixels
        val y2 = yPercent * metrics.heightPixels
        
        Log.d("AccessibilityService", "Performing swipe from $x1,$y1 to $x2,$y2")
        
        val swipePath = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(swipePath, 0, 400))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun performGlobalAction(action: String) {
        Log.d("AccessibilityService", "Performing global action: $action")
        when (action) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "POWER_DIALOG" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }
    }

    companion object {
        var instance: RemoteAssistAccessibilityService? = null
    }
}
