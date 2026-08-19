package com.damsan.green.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/** Short, non-intrusive feedback tones. No audio permission or bundled files are required. */
object SoundFeedback {
    fun photoCaptured(context: Context) {
        play(context, ToneGenerator.TONE_PROP_BEEP, 90)
    }

    fun success(context: Context) {
        play(context, ToneGenerator.TONE_PROP_ACK, 140)
    }

    fun error(context: Context) {
        play(context, ToneGenerator.TONE_PROP_NACK, 180)
    }

    private fun play(context: Context, tone: Int, durationMs: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            generator.startTone(tone, durationMs)
            android.os.Handler(context.mainLooper).postDelayed({ generator.release() }, durationMs + 80L)
        }
    }
}
