package com.lyriva.ninfinity.core

object TextUtils {
    private val ARTIST_SPLIT = Regex("\\s*,\\s*|\\s+[-–—]\\s+")
    private val BAD_FILE_CHARS = Regex("[\\\\/:*?\"<>|]")
    private val WS = Regex("\\s+")

    /**
     * Nhiều nghệ sĩ: dấu phẩy hoặc dấu gạch có space hai bên → "•".
     * Gạch dính liền (Jay-Z, M-TP) giữ nguyên.
     */
    fun artistLine(s: String): String =
        s.split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" • ").uppercase()

    /** Tên file an toàn từ tên bài. */
    fun fileStem(title: String, fallback: String = "karaoke"): String {
        val cleaned = title.trim().replace(BAD_FILE_CHARS, "").replace(WS, "_")
        return cleaned.ifEmpty { fallback }
    }
}
