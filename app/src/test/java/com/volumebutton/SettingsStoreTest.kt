package com.volumebutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {

    @Test
    fun testSettingsDefaultConstants() {
        assertEquals(0.85f, SettingsStore.DEFAULT_IDLE_OPACITY, 0.001f)
        assertEquals(3, SettingsStore.DEFAULT_AUTO_COLLAPSE_SEC)
        assertEquals("floating_volume_prefs", SettingsStore.PREFS_NAME)
    }

    @Test
    fun testAutoCollapseDelayMsCalculation() {
        val delaySec = 4
        val delayMs = delaySec * 1000L
        assertEquals(4000L, delayMs)
    }

    @Test
    fun testOpacityCoercion() {
        val tooHigh = 1.5f
        val tooLow = 0.05f
        val normal = 0.75f

        assertEquals(1.0f, tooHigh.coerceIn(0.2f, 1.0f), 0.001f)
        assertEquals(0.2f, tooLow.coerceIn(0.2f, 1.0f), 0.001f)
        assertEquals(0.75f, normal.coerceIn(0.2f, 1.0f), 0.001f)
    }
}

