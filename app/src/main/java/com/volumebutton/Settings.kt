package com.volumebutton

enum class ScreenEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM
}

data class ButtonSettings(
    val autoCollapseDelayMs: Long = 3000L,
    val idleOpacity: Float = 0.85f,
    val showVolumePopup: Boolean = true,
    val hapticFeedback: Boolean = true
)

