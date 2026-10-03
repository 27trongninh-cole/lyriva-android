package com.lyriva.ninfinity.core

import java.util.Locale
import kotlin.math.floor

/** Định dạng và đọc thời gian, giữ nguyên hành vi của bản web. */
object TimeUtils {
    private fun fixed2(v: Double): String = String.format(Locale.US, "%.2f", v)

    /** m:ss.xx (dùng trong giao diện đồng bộ) */
    fun fmt(t: Double): String {
        val x = maxOf(0.0, t)
        val m = floor(x / 60).toInt()
        return "$m:" + fixed2(x - m * 60).padStart(5, '0')
    }

    /** m:ss (dùng cho thanh phát) */
    fun fmtShort(t: Double): String {
        val x = maxOf(0.0, if (t.isNaN()) 0.0 else t)
        val m = floor(x / 60).toInt()
        val s = floor(x % 60).toInt()
        return "$m:" + s.toString().padStart(2, '0')
    }

    /** mm:ss.xx (mốc trong file LRC) */
    fun lrcStamp(t: Double): String {
        val m = floor(t / 60).toInt()
        return m.toString().padStart(2, '0') + ":" + fixed2(t - m * 60).padStart(5, '0')
    }

    /** hh:mm:ss,mmm (file SRT) */
    fun srtStamp(t: Double): String {
        val ms = Math.round(t * 1000)
        val h = ms / 3600000
        val m = (ms / 60000) % 60
        val s = (ms / 1000) % 60
        val r = ms % 1000
        return listOf(h, m, s).joinToString(":") { it.toString().padStart(2, '0') } +
            "," + r.toString().padStart(3, '0')
    }

    /** Đọc "1:05.50", "65.5", "1:05,5" → giây. Sai định dạng trả về null. */
    fun parse(input: String): Double? {
        val s = input.trim().replace(',', '.')
        if (s.isEmpty()) return null
        val parts = s.split(':').map { it.toDoubleOrNull() ?: return null }
        return parts.fold(0.0) { a, b -> a * 60 + b }
    }
}
