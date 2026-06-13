package com.example.cfdremoteassist.remote

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.KeyEvent

import android.app.Instrumentation

interface KeyInjector {
    fun inject(event: KeyEvent): Boolean
}

class InstrumentationKeyInjector : KeyInjector {
    private val instrumentation = Instrumentation()
    
    override fun inject(event: KeyEvent): Boolean {
        return try {
            instrumentation.sendKeySync(event)
            true
        } catch (e: Exception) {
            Log.w("KeyInjector", "Instrumentation inject failed", e)
            false
        }
    }
}

class ShellKeyInjector : KeyInjector {
    override fun inject(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true // one shot per key
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
