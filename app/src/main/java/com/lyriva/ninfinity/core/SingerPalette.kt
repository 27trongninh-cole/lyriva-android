package com.lyriva.ninfinity.core

import java.text.Normalizer

/** Một nhóm nhạc với bảng màu cố định cho từng thành viên. */
class SingerGroup(val name: String) {
    /** tên đã chuẩn hóa (không dấu, chữ thường) → màu RGB */
    val members = LinkedHashMap<String, Int>()
}

/**
 * Bảng màu người hát. Định dạng nhập tay:
 *
 *     Tên nhóm: CORTIS
 *     James: #123456
 *     Martin: #234567
 *
 * Chỉ dùng cho nhãn tên người hát, không đụng tới màu theo quốc gia hay màu khác.
 */
object SingerPalette {
    private val HEX = Regex("^#?([0-9a-fA-F]{6})$")
    private val MARKS = Regex("\\p{Mn}+")
    private val NEWLINE = Regex("\\r?\\n")
    private val SPLIT = Regex("\\s*[,&/+]\\s*")
    private val HEADERS = setOf("ten nhom", "nhom", "group", "group name", "team", "ten team")

    /**
     * Màu tự gán cho tên chưa có trong bảng, theo thứ tự xuất hiện. Cố ý không dùng các màu của
     * ngôn ngữ (tím, vàng, xanh dương, hồng, đỏ) để nhãn người hát không bao giờ giống màu theo quốc gia.
     */
    val AUTO = intArrayOf(0x5EEAD4, 0xFF9F68, 0xBEF264, 0x22D3EE, 0xE879F9, 0x86EFAC)

    fun norm(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(MARKS, "").replace('đ', 'd')

    /** "James, Martin" hoặc "James & Martin" → ["James", "Martin"] */
    fun split(names: String): List<String> = names.split(SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

    fun parse(text: String): List<SingerGroup> {
        val out = ArrayList<SingerGroup>()
        var cur: SingerGroup? = null
        for (raw in text.split(NEWLINE)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            var i = line.indexOf(':')
            if (i < 0) i = line.indexOf('=')
            if (i <= 0) continue
            val key = line.substring(0, i).trim()
            val value = line.substring(i + 1).trim()
            if (norm(key) in HEADERS) {
                val g = SingerGroup(value)
                out.add(g)
                cur = g
                continue
            }
            val m = HEX.matchEntire(value) ?: continue
            var g = cur
            if (g == null) {
                g = SingerGroup("")
                out.add(g)
                cur = g
            }
            g.members[norm(key)] = m.groupValues[1].toInt(16)
        }
        return out
    }

    /** Màu RGB của [name]; ưu tiên nhóm [active], rồi tìm ở mọi nhóm. Không có thì trả null. */
    fun resolve(name: String, groups: List<SingerGroup>, active: String): Int? {
        val k = norm(name)
        if (active.isNotEmpty()) {
            val a = norm(active)
            groups.firstOrNull { norm(it.name) == a }?.members?.get(k)?.let { return it }
        }
        for (g in groups) g.members[k]?.let { return it }
        return null
    }
}
