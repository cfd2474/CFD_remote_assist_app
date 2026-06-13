package com.example.cfdremoteassist.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.os.Bundle
import android.view.InputDevice
import android.view.View
import android.app.Instrumentation

class RemoteAssistAccessibilityService : AccessibilityService() {

    private val instrumentation = Instrumentation()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Auto-accept Screen Share / Media Projection dialog
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString() ?: ""
            
            // System UI or Android System is usually responsible for this dialog
            if (packageName.contains("systemui") || packageName.contains("android") || packageName.isEmpty()) {
                // Try multiple times as the window might still be loading
                serviceHandler.removeCallbacks(autoAcceptRunnable)
                serviceHandler.postDelayed(autoAcceptRunnable, 200)
                serviceHandler.postDelayed(autoAcceptRunnable, 500)
                serviceHandler.postDelayed(autoAcceptRunnable, 1000)
            }
        }
    }

    private val serviceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoAcceptRunnable = Runnable {
        findAndClickMediaProjectionButtons(rootInActiveWindow)
    }

    private fun findAndClickMediaProjectionButtons(node: AccessibilityNodeInfo?) {
        if (node == null) return

        // 1. Look for specific system button IDs and text patterns
        val textToFind = listOf("Start now", "Allow", "Entire screen", "Start recording", "START NOW", "ALLOW")
        val idsToFind = listOf(
            "android:id/button1", // Standard "OK/Positive" button ID
            "com.android.systemui:id/remember_checkbox",
            "com.android.systemui:id/button_start_now",
            "com.android.systemui:id/start_button"
        )
        
        // Try IDs first (more reliable)
        for (id in idsToFind) {
            val nodes = node.findAccessibilityNodeInfosByViewId(id)
            for (foundNode in nodes) {
                if (foundNode.isClickable || foundNode.isCheckable) {
                    Log.d("AccessibilityService", "Auto-acting on ID: $id")
                    foundNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (foundNode.isCheckable) continue 
                }
            }
        }

        // Try Text patterns
        for (text in textToFind) {
            val nodes = node.findAccessibilityNodeInfosByText(text)
            for (foundNode in nodes) {
                if (foundNode.isClickable) {
                    Log.d("AccessibilityService", "Auto-clicking text: $text")
                    foundNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }

        // 2. Recursive search for children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            findAndClickMediaProjectionButtons(child)
        }
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
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 50))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun performLongPress(xPercent: Float, yPercent: Float) {
        val metrics = resources.displayMetrics
        val x = xPercent * metrics.widthPixels
        val y = yPercent * metrics.heightPixels
        
        Log.d("AccessibilityService", "Performing long press at $x, $y")
        
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, 600))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun performSwipe(x1Percent: Float, y1Percent: Float, x2Percent: Float, y2Percent: Float, durationMs: Long = 350) {
        val metrics = resources.displayMetrics
        val x1 = x1Percent * metrics.widthPixels
        val y1 = y1Percent * metrics.heightPixels
        val x2 = x2Percent * metrics.widthPixels
        val y2 = y2Percent * metrics.heightPixels
        
        Log.d("AccessibilityService", "Performing swipe from $x1,$y1 to $x2,$y2 over ${durationMs}ms")
        
        val swipePath = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(swipePath, 0, durationMs))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    fun handleKeyAction(key: String, inputMethod: String?) {
        Log.d("AccessibilityService", "Key Action: $key (method: $inputMethod)")
        
        // 1. Navigation / Global Actions (Priority)
        when (key) {
            "BACK", "KEYCODE_BACK" -> { performGlobalAction(GLOBAL_ACTION_BACK); return }
            "HOME", "KEYCODE_HOME" -> { performGlobalAction(GLOBAL_ACTION_HOME); return }
            "RECENTS", "KEYCODE_APP_SWITCH" -> { performGlobalAction(GLOBAL_ACTION_RECENTS); return }
            "NOTIFICATIONS" -> { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); return }
            "QUICK_SETTINGS" -> { performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); return }
            "POWER_DIALOG" -> { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); return }
        }

        // 2. Hardware Keyboard Injection (Preferred if requested)
        if (inputMethod == "hardware_keyboard") {
            injectHardwareKey(key)
            return
        }

        // 3. Fallback to global action mapping or IME-style injection
        performGlobalActionByName(key)
    }

    private fun injectHardwareKey(key: String) {
        val (keyCode, metaState) = parseKeyAndModifiers(key)
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            injectKeyEvent(keyCode, metaState)
        } else if (key.length == 1) {
            // Fallback for single characters
            injectChar(key[0])
        }
    }

    private fun parseKeyAndModifiers(key: String): Pair<Int, Int> {
        var metaState = 0
        var keyPart = key

        if (key.contains("+")) {
            val parts = key.split("+")
            parts.dropLast(1).forEach { mod ->
                when (mod.lowercase()) {
                    "ctrl" -> metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                    "shift" -> metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                    "alt" -> metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
                    "meta", "win", "cmd" -> metaState = metaState or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
                }
            }
            keyPart = parts.last()
        }

        val normalized = if (keyPart.startsWith("KEYCODE_")) keyPart.uppercase() else "KEYCODE_${keyPart.uppercase()}"
        val keyCode = try {
            KeyEvent::class.java.getField(normalized).getInt(null)
        } catch (e: Exception) {
            when (keyPart.uppercase()) {
                "ENTER" -> KeyEvent.KEYCODE_ENTER
                "DEL", "BACKSPACE" -> KeyEvent.KEYCODE_DEL
                "SPACE" -> KeyEvent.KEYCODE_SPACE
                "TAB" -> KeyEvent.KEYCODE_TAB
                "UP" -> KeyEvent.KEYCODE_DPAD_UP
                "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                "ESC", "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
                else -> KeyEvent.KEYCODE_UNKNOWN
            }
        }
        return Pair(keyCode, metaState)
    }

    private fun injectKeyEvent(keyCode: Int, metaState: Int = 0) {
        Thread {
            try {
                val downTime = android.os.SystemClock.uptimeMillis()
                val eventDown = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState, 
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
                instrumentation.sendKeySync(eventDown)
                
                val eventUp = KeyEvent(downTime, android.os.SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, metaState, 
                    android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
                instrumentation.sendKeySync(eventUp)
                Log.d("AccessibilityService", "Injected hardware key $keyCode (meta $metaState)")
            } catch (e: Exception) {
                Log.e("AccessibilityService", "Hardware injection failed: ${e.message}")
                handler.post { injectLegacyKey(keyCode) }
            }
        }.start()
    }

    private fun injectLegacyKey(keyCode: Int) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (node != null && node.className?.contains("EditText") == true) {
                    val currentText = (node.text ?: "").toString()
                    if (currentText.isNotEmpty()) {
                        val bundle = Bundle()
                        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentText.dropLast(1))
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    }
                }
            }
            KeyEvent.KEYCODE_ENTER -> node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            KeyEvent.KEYCODE_DPAD_UP -> performSwipe(0.5f, 0.6f, 0.5f, 0.4f, 100)
            KeyEvent.KEYCODE_DPAD_DOWN -> performSwipe(0.5f, 0.4f, 0.5f, 0.6f, 100)
            KeyEvent.KEYCODE_DPAD_LEFT -> performSwipe(0.6f, 0.5f, 0.4f, 0.5f, 100)
            KeyEvent.KEYCODE_DPAD_RIGHT -> performSwipe(0.4f, 0.5f, 0.6f, 0.5f, 100)
        }
    }

    private fun performGlobalActionByName(action: String) {
        val (keyCode, _) = parseKeyAndModifiers(action)
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            injectLegacyKey(keyCode)
        } else if (action.length == 1) {
            injectChar(action[0])
        }
    }

    private fun injectChar(char: Char) {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node != null) {
            val bundle = Bundle()
            val newText = (node.text ?: "").toString() + char
            bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            
            // Move cursor to end
            val cursorBundle = Bundle()
            cursorBundle.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newText.length)
            cursorBundle.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newText.length)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorBundle)
        }
    }

    companion object {
        var instance: RemoteAssistAccessibilityService? = null
    }
}
