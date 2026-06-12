package com.example.cfdremoteassist.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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
        Log.d("AccessibilityService", "Service Connected")
    }

    // Example method to perform a click at coordinates
    fun performClickAt(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 100))
        dispatchGesture(gestureBuilder.build(), null, null)
    }
}