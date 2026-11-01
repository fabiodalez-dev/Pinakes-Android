package com.pinakes.app.ui.screens.staff.s090.stocktake

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.pinakes.app.data.model.Staff090StocktakeTone

/**
 * Plays the scan beep the web scan page plays (the server's frequency and duration) through a
 * static AudioTrack on the sonification stream. Silent unless the ringer is in normal mode, so
 * a phone on silent or vibrate stays quiet (the haptic tick still says the scan was taken).
 * Any audio failure is swallowed: a missing beep must never stop a stocktake.
 */
class Staff090StocktakeTonePlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var current: AudioTrack? = null

    fun play(tone: Staff090StocktakeTone?) {
        val spec = Staff090StocktakeSound.spec(tone, audio?.ringerMode ?: AudioManager.RINGER_MODE_SILENT) ?: return
        val samples = Staff090StocktakeSound.samples(spec)
        runCatching {
            stop()
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Staff090StocktakeSound.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            current = track
            main.postDelayed({ if (current === track) stop() else runCatching { track.release() } }, spec.durationMs + 150L)
        }
    }

    /** Stop and release the beep in progress (screen leaving, next scan). */
    fun stop() {
        val track = current ?: return
        current = null
        runCatching { track.stop() }
        runCatching { track.release() }
    }
}
