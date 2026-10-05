package com.lyriva.ninfinity.core

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Câu hook đầu video: [n] cụm chữ hiện lần lượt, mỗi cụm kèm một tiếng chuông. */
class HookSpec(
    val words: List<String>,
    val hi: BooleanArray,
    val t0: Double,
    val gap: Double,
    val hold: Double,
    val sound: Int,
    val seed: Int,
    val volume: Float
) {
    val n: Int get() = words.size
    fun wordTime(i: Int): Double = t0 + i * gap

    /** Lúc bắt đầu mờ dần (sau chữ cuối [hold] giây). */
    val endTime: Double get() = t0 + (n - 1) * gap + hold
}

/**
 * Hook: tách chữ, chọn cao độ ngẫu nhiên trong thang ngũ cung (luôn hòa hợp), tổng hợp tiếng chuông
 * bằng cộng các họa âm tắt dần, rồi trộn vào nhạc (hạ nhạc nhỏ lại trong lúc chuông kêu).
 */
object Hook {
    const val START = 0.4
    const val HOLD = 1.5
    const val FADE = 0.35
    const val SR = 44100
    val SOUNDS = listOf("Chuông", "Thủy tinh", "Gõ")

    private val DEGREES = intArrayOf(0, 2, 4, 7, 9) // ngũ cung trưởng
    private val WS = Regex("\\s+")

    /** Mỗi cụm cách nhau bằng dấu cách là một chữ/một tiếng chuông. *chữ* hoặc *nhiều chữ* được bôi màu. */
    fun parse(text: String): Pair<List<String>, BooleanArray> {
        val words = ArrayList<String>()
        val hi = ArrayList<Boolean>()
        var inHi = false
        for (raw in text.trim().split(WS)) {
            if (raw.isEmpty()) continue
            var tok = raw
            if (tok.startsWith("*")) {
                inHi = true
                tok = tok.trimStart('*')
            }
            val closes = tok.endsWith("*")
            tok = tok.trimEnd('*')
            if (tok.isNotEmpty()) {
                words.add(tok)
                hi.add(inHi)
            }
            if (closes) inHi = false
        }
        return words to hi.toBooleanArray()
    }

    fun spec(on: Boolean, text: String, gap: Double, sound: Int, seed: Int, volume: Float): HookSpec? {
        if (!on) return null
        val (w, h) = parse(text)
        if (w.isEmpty()) return null
        return HookSpec(w, h, START, gap.coerceIn(0.15, 0.8), HOLD, sound.coerceIn(0, 2), seed, volume.coerceIn(0f, 1f))
    }

    /** Số hiệu nốt MIDI cho từng chữ. Không lặp liền một nốt; nốt cuối nằm ở quãng cao cho cảm giác "xong câu". */
    fun notes(n: Int, seed: Int): IntArray {
        val rnd = Random(seed.toLong() * 7919L + 13L)
        val out = IntArray(n)
        var prev = -1
        for (i in 0 until n) {
            val last = i == n - 1 && n > 1
            var m: Int
            var tries = 0
            do {
                val octave = if (last) 1 else rnd.nextInt(2)
                m = 72 + octave * 12 + DEGREES[rnd.nextInt(DEGREES.size)]
                tries++
            } while (m == prev && tries < 8)
            out[i] = m
            prev = m
        }
        return out
    }

    private class Timbre(
        val ratios: DoubleArray, val amps: DoubleArray, val taus: DoubleArray,
        val attack: Double, val dur: Double
    )

    private val TIMBRES = arrayOf(
        Timbre(doubleArrayOf(1.0, 2.0, 2.76, 5.4), doubleArrayOf(1.0, 0.55, 0.28, 0.12), doubleArrayOf(0.9, 0.55, 0.3, 0.15), 0.003, 1.4),
        Timbre(doubleArrayOf(1.0, 2.32, 4.25, 6.63), doubleArrayOf(1.0, 0.35, 0.18, 0.08), doubleArrayOf(0.8, 0.4, 0.25, 0.12), 0.008, 1.3),
        Timbre(doubleArrayOf(1.0, 3.9, 9.2), doubleArrayOf(1.0, 0.35, 0.1), doubleArrayOf(0.22, 0.1, 0.05), 0.001, 0.6)
    )

    private fun addNote(buf: FloatArray, start: Int, freq: Double, kind: Int, sr: Int) {
        val tb = TIMBRES[kind]
        val len = (tb.dur * sr).toInt()
        val tail = (0.02 * sr).toInt().coerceAtLeast(1)
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= buf.size) break
            if (idx < 0) continue
            val t = i.toDouble() / sr
            var v = 0.0
            for (p in tb.ratios.indices) {
                val f = freq * tb.ratios[p]
                if (f >= sr / 2.0) continue
                v += tb.amps[p] * exp(-t / tb.taus[p]) * sin(2.0 * PI * f * t)
            }
            val att = min(1.0, t / tb.attack)
            val rel = min(1.0, (len - i).toDouble() / tail)
            buf[idx] += (v * att * rel * 0.35).toFloat()
        }
    }

    /** Âm thanh mono 16-bit của cả đoạn hook, bắt đầu từ giây 0 của video (phần đầu là im lặng). */
    fun synth(spec: HookSpec, sampleRate: Int = SR): ShortArray {
        val total = ((spec.endTime + 1.6) * sampleRate).toInt()
        val buf = FloatArray(total)
        val notes = notes(spec.n, spec.seed)
        for (i in 0 until spec.n) {
            val f = 440.0 * 2.0.pow((notes[i] - 69) / 12.0)
            addNote(buf, (spec.wordTime(i) * sampleRate).toInt(), f, spec.sound, sampleRate)
        }
        var peak = 0f
        for (v in buf) peak = max(peak, abs(v))
        val g = if (peak > 0f) 0.85f / peak * spec.volume else 0f
        return ShortArray(total) { (buf[it] * g * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }

    /** Hệ số âm lượng nhạc tại [t] giây: hạ còn 50% trong lúc chuông kêu, vào/ra mượt 0,15s. */
    fun duck(t: Double, spec: HookSpec): Float {
        val a = spec.t0 - 0.05
        val b = spec.wordTime(spec.n - 1) + 1.0
        val r = 0.15
        val w = when {
            t < a - r -> 0.0
            t < a -> (t - (a - r)) / r
            t <= b -> 1.0
            t < b + r -> 1.0 - (t - b) / r
            else -> 0.0
        }
        return (1.0 - 0.5 * w).toFloat()
    }

    /** Trộn chuông vào PCM 16-bit xen kẽ (sửa trực tiếp), đồng thời hạ nhạc trong lúc chuông kêu. */
    fun mixInto(samples: ShortArray, channels: Int, sampleRate: Int, spec: HookSpec) {
        val chime = synth(spec, sampleRate)
        val frames = samples.size / channels
        val n = min(frames, chime.size)
        for (f in 0 until n) {
            val g = duck(f.toDouble() / sampleRate, spec)
            for (c in 0 until channels) {
                val i = f * channels + c
                val v = samples[i] * g + chime[f]
                samples[i] = v.toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }
}
