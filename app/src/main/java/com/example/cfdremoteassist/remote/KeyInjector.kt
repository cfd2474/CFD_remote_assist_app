package com.example.cfdremoteassist.remote

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo

interface KeyInjector {
    fun inject(event: KeyEvent): Boolean
}

/**
 * Injects keys using AccessibilityNodeInfo actions.
 * Good for typing and basic navigation in non-root/non-system environments.
 */
class AccessibilityKeyInjector(
    private val service: AccessibilityService
) : KeyInjector {
    override fun inject(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true

        val node = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (node != null && node.className?.contains("EditText") == true) {
                    val currentText = (node.text ?: "").toString()
                    if (currentText.isNotEmpty()) {
                        val bundle = Bundle()
                        bundle.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            currentText.dropLast(1)
                        )
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    } else true
                } else false
            }
            KeyEvent.KEYCODE_ENTER -> {
                node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            }
            else -> {
                val unicodeChar = event.getUnicodeChar(event.metaState)
                if (unicodeChar != 0 && node != null && node.className?.contains("EditText") == true) {
                    val char = unicodeChar.toChar()
                    val currentText = (node.text ?: "").toString()
                    val bundle = Bundle()
                    bundle.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        currentText + char
                    )
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                } else false
            }
        }
    }
}

/**
 * Fallback injector that uses the 'input keyevent' shell command.
 * Requires device-owner or privileged shell permissions on managed devices.
 */
class ShellKeyInjector : KeyInjector {
    override fun inject(event: KeyEvent): Boolean {
        // We only need to inject one event per key press for shell commands
        if (event.action != KeyEvent.ACTION_DOWN) return true 

        return try {
            val cmd = arrayOf("sh", "-c", "input keyevent ${event.keyCode}")
            val process = Runtime.getRuntime().exec(cmd)
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w("KeyInjector", "shell input keyevent failed with exit code $exitCode")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.w("KeyInjector", "shell input keyevent failed", e)
            false
        }
    }
}

class ChainedKeyInjector(
    private vararg val injectors: KeyInjector,
) : KeyInjector {
    override fun inject(event: KeyEvent): Boolean {
        for (injector in injectors) {
            if (injector.inject(event)) return true
        }
        return false
    }
}
