package com.lyriva.ninfinity.core

/**
 * Ký tự nằm giữa cặp //…// vẫn giữ mốc thời gian nhưng bị ẩn khỏi video.
 * Cặp nằm trong cùng một dòng; dòng còn dư một // thì ghép với // dư ở dòng sau
 * (ví dụ chế độ từng chữ, mỗi từ một dòng LRC). Port từ hideMasks/hideApply của bản web.
 */
object Hide {
    class Applied(val s: String, val w: List<LrcWord>)

    fun masks(a: List<String>): Array<BooleanArray?> {
        val m = arrayOfNulls<BooleanArray>(a.size)
        val lone = ArrayList<Pair<Int, Int>>()
        fun get(k: Int): BooleanArray {
            var x = m[k]
            if (x == null) {
                x = BooleanArray(a[k].length)
                m[k] = x
            }
            return x
        }
        a.forEachIndexed { k, t ->
            val p = ArrayList<Int>()
            var i = t.indexOf("//", 0)
            while (i >= 0) {
                p.add(i)
                i = t.indexOf("//", i + 2)
            }
            var j = 0
            while (j + 1 < p.size) {
                get(k).fill(true, p[j], minOf(t.length, p[j + 1] + 2))
                j += 2
            }
            if (p.size % 2 == 1) lone.add(k to p[p.size - 1])
        }
        var j = 0
        while (j + 1 < lone.size) {
            val (k1, p1) = lone[j]
            val (k2, p2) = lone[j + 1]
            get(k1).fill(true, p1, a[k1].length)
            for (k in k1 + 1 until k2) get(k).fill(true)
            get(k2).fill(true, 0, minOf(a[k2].length, p2 + 2))
            j += 2
        }
        return m
    }

    fun apply(t: String, m: BooleanArray, ws: List<LrcWord>?): Applied {
        val out = StringBuilder()
        val map = IntArray(t.length + 1)
        val kept = BooleanArray(t.length)
        for (k in 0 until t.length) {
            map[k] = out.length
            if (m[k]) continue
            val sp = t[k].isWhitespace()
            if (sp && (out.isEmpty() || out.endsWith(" "))) continue
            out.append(if (sp) ' ' else t[k])
            kept[k] = true
        }
        map[t.length] = out.length
        val s = out.toString().trimEnd()
        val w2 = ArrayList<LrcWord>()
        for (w in ws ?: emptyList()) {
            var f = -1
            var n = 0
            var k = w.i
            while (k < w.i + w.l && k < t.length) {
                if (kept[k]) {
                    if (f < 0) f = k
                    n++
                }
                k++
            }
            if (f < 0) continue
            val i = map[f]
            if (i >= s.length) continue
            w2.add(LrcWord(w.t, i, minOf(n, s.length - i), w.e))
        }
        return Applied(s, w2)
    }

    /** Dùng cho lời gốc / Vietsub: bỏ phần nằm trong //…//. */
    fun lines(a: List<String>): List<String> {
        val m = masks(a)
        return a.mapIndexed { k, t -> val x = m[k]; if (x != null) apply(t, x, null).s else t }
    }
}
