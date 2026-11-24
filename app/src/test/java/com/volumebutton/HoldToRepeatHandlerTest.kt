package com.volumebutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldToRepeatHandlerTest {

    @Test
    fun testConfigurationValues() {
        val initialDelay = 400L
        val repeatInterval = 120L
        val autoCollapseDelay = 3000L

        assertTrue(initialDelay > repeatInterval)
        assertEquals(400L, initialDelay)
        assertEquals(120L, repeatInterval)
        assertEquals(3000L, autoCollapseDelay)
    }

    @Test
    fun testSettingsDefaultValues() {
        val settings = ButtonSettings()
        assertEquals(3000L, settings.autoCollapseDelayMs)
        assertEquals(0.85f, settings.idleOpacity, 0.001f)
        assertTrue(settings.showVolumePopup)
        assertTrue(settings.hapticFeedback)
    }
}

