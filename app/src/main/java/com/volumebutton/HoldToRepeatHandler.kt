package com.volumebutton

import android.os.Handler
import android.os.Looper

/**
 * Manages hold-to-repeat events for buttons.
 * Dispatches an initial trigger, waits for initialDelayMs, then fires repeatedly at repeatIntervalMs.
 * Completely event-driven and runs only while a touch is actively held down.
 */
class HoldToRepeatHandler(
    private val initialDelayMs: Long = 400L,
    private val repeatIntervalMs: Long = 120L,
    private val onTrigger: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                onTrigger()
                handler.postDelayed(this, repeatIntervalMs)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        onTrigger()
        handler.postDelayed(repeatRunnable, initialDelayMs)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(repeatRunnable)
    }
}

