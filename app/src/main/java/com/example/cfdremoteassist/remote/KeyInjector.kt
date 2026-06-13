package com.example.cfdremoteassist.remote

import android.util.Log
import android.view.KeyEvent

interface KeyInjector {
    fun inject(event: KeyEvent): Boolean
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
            Runtime.getRuntime().exec(cmd).waitFor() == 0
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
