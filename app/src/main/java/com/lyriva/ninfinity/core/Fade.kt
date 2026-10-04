package com.lyriva.ninfinity.core

import kotlin.math.min

/** Nhạc vào dần / ra dần (giây). */
object Fade {
    fun gain(t: Double, total: Double, fadeIn: Double, fadeOut: Double): Float {
        var g = 1.0
        if (fadeIn > 0.0 && t < fadeIn) g = (t / fadeIn).coerceIn(0.0, 1.0)
        if (fadeOut > 0.0 && t > total - fadeOut) g = min(g, ((total - t) / fadeOut).coerceIn(0.0, 1.0))
        return g.toFloat()
    }

    /** Nhân hệ số vào PCM 16-bit xen kẽ, sửa trực tiếp trên [samples]. */
    fun apply(samples: ShortArray, channels: Int, sampleRate: Int, fadeIn: Double, fadeOut: Double) {
        if (fadeIn <= 0.0 && fadeOut <= 0.0) return
        val frames = samples.size / channels
        val total = frames.toDouble() / sampleRate
        val inEnd = minOf(frames, (fadeIn * sampleRate).toInt())
        val outStart = maxOf(0, ((total - fadeOut) * sampleRate).toInt())
        fun scale(f: Int) {
            val g = gain(f.toDouble() / sampleRate, total, fadeIn, fadeOut)
            if (g >= 1f) return
            for (c in 0 until channels) {
                val i = f * channels + c
                samples[i] = (samples[i] * g).toInt().toShort()
            }
        }
        for (f in 0 until inEnd) scale(f)
        for (f in maxOf(outStart, inEnd) until frames) scale(f)
    }
}
