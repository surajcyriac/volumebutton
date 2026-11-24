package com.volumebutton

import android.content.Context
import android.media.AudioManager

class VolumeController(context: Context) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volumeUp(showUi: Boolean = true) {
        adjustVolume(AudioManager.ADJUST_RAISE, showUi)
    }

    fun volumeDown(showUi: Boolean = true) {
        adjustVolume(AudioManager.ADJUST_LOWER, showUi)
    }

    fun showVolumeUi() {
        adjustVolume(AudioManager.ADJUST_SAME, showUi = true)
    }

    private fun adjustVolume(direction: Int, showUi: Boolean) {
        val flags = if (showUi) {
            AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
        } else {
            0
        }
        try {
            audioManager.adjustSuggestedStreamVolume(
                direction,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                flags
            )
        } catch (e: SecurityException) {
            // Under Do Not Disturb or restricted audio policies, changing ring/notification stream can throw SecurityException
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
