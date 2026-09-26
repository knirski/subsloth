package net.subsloth.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/**
 * System media volume, so the vertical volume swipe behaves like the phone's
 * volume keys rather than only changing the in-app player volume.
 */
class AndroidVolumeControl(context: Context) : PlayerValueControl {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    override val isSupported: Boolean get() = audioManager != null

    override fun get(): Float {
        val audio = audioManager ?: return 0f
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) {
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        } else {
            0f
        }
    }

    override fun set(value: Float) {
        val audio = audioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        audio.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (value.coerceIn(0f, 1f) * max).roundToInt(),
            0,
        )
    }
}

/**
 * Window brightness for the player's activity. The first gesture sets an
 * absolute value; before that the system default (-1) is approximated.
 */
class AndroidBrightnessControl(private val activity: Activity) : PlayerValueControl {
    override val isSupported: Boolean get() = true

    override fun get(): Float {
        val value = activity.window.attributes.screenBrightness
        return if (value < 0f) DEFAULT_BRIGHTNESS else value
    }

    override fun set(value: Float) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = value.coerceIn(MIN_BRIGHTNESS, 1f)
        }
    }

    private companion object {
        const val DEFAULT_BRIGHTNESS = 0.5f

        /** Never let the gesture black the screen out completely. */
        const val MIN_BRIGHTNESS = 0.01f
    }
}
