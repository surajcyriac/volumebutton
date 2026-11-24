package com.volumebutton

import android.content.Context
import android.content.SharedPreferences

class PositionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savePosition(edge: ScreenEdge, fraction: Float) {
        prefs.edit()
            .putString(KEY_EDGE, edge.name)
            .putFloat(KEY_FRACTION, fraction.coerceIn(0f, 1f))
            .apply()
    }

    fun getEdge(): ScreenEdge {
        val raw = prefs.getString(KEY_EDGE, ScreenEdge.RIGHT.name) ?: ScreenEdge.RIGHT.name
        return try {
            ScreenEdge.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            ScreenEdge.RIGHT
        }
    }

    fun getFraction(): Float {
        return prefs.getFloat(KEY_FRACTION, DEFAULT_FRACTION).coerceIn(0f, 1f)
    }

    companion object {
        const val PREFS_NAME = "floating_volume_prefs"
        private const val KEY_EDGE = "screen_edge"
        private const val KEY_FRACTION = "edge_fraction"
        private const val DEFAULT_FRACTION = 0.5f // center
    }
}

