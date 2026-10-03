package com.lyriva.ninfinity.core

class LrcWord(val t: Double, val i: Int, val l: Int, val e: Double)

/** Một dòng lời. [w] khác null khi là LRC nâng cao (có mốc từng chữ). */
class LrcLine(val t: Double, val s: String, val w: List<LrcWord>?)

class LrcResult(val items: List<LrcLine>, val meta: Map<String, String>)

/**
 * Đọc LRC thường và LRC nâng cao: nhiều mốc trên một dòng [t1][t2],
 * thẻ từ <mm:ss.xx>, các thẻ [ti:] [ar:] [offset:].
 * Port nguyên logic parseLRC của bản web.
 */
object LrcParser {
    private val META = Regex("^\\s*\\[([a-z]+):(.*)\\]\\s*$", RegexOption.IGNORE_CASE)
    private val LINE = Regex("^((?:\\s*\\[\\d+:\\d+(?:\\.\\d+)?\\])+)(.*)$")
    private val STAMP = Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]")
    private val TAG_SEP = Regex("[<\\[](\\d+):(\\d+(?:\\.\\d+)?)[>\\]]")
    private val TAG_FULL = Regex("^[<\\[](\\d+):(\\d+(?:\\.\\d+)?)[>\\]]$")
    private val WS = Regex("\\s+")
    private val NEWLINE = Regex("\\r?\\n")

    private class RawWord(var t: Double, val i: Int, val l: Int)
    private class RawLine(var t: Double, val s: String, val w: List<RawWord>?)

    fun parse(text: String): LrcResult {
        val out = ArrayList<RawLine>()
        val meta = LinkedHashMap<String, String>()

        for (line in text.split(NEWLINE)) {
            val mm = META.find(line)
            if (mm != null) {
                meta[mm.groupValues[1].lowercase()] = mm.groupValues[2].trim()
                continue
            }
            val m = LINE.find(line) ?: continue
            val ts = STAMP.findAll(m.groupValues[1]).map {
                it.groupValues[1].toDouble() * 60 + it.groupValues[2].toDouble()
            }.toList()
            if (ts.isEmpty()) continue
            val raw = m.groupValues[2]

            // tách theo thẻ thời gian nhưng giữ lại chính thẻ (giống String.split có nhóm bắt)
            val parts = ArrayList<String>()
            var last = 0
            for (sep in TAG_SEP.findAll(raw)) {
                parts.add(raw.substring(last, sep.range.first))
                parts.add(sep.value)
                last = sep.range.last + 1
            }
            parts.add(raw.substring(last))

            var cur: Double? = null
            val words = ArrayList<Triple<Double, Int, Int>>()
            val sb = StringBuilder()
            for (p in parts) {
                val q = TAG_FULL.matchEntire(p)
                if (q != null) {
                    cur = q.groupValues[1].toDouble() * 60 + q.groupValues[2].toDouble()
                } else if (p.isNotEmpty()) {
                    val txt2 = WS.replace(p, " ")
                    val c = cur
                    if (c != null && txt2.isNotBlank()) {
                        val t1 = txt2.removePrefix(" ")
                        val sp0 = if (txt2.startsWith(" ") && sb.isNotEmpty() && !sb.endsWith(" ")) " " else ""
                        sb.append(sp0)
                        val idx = sb.length
                        val core = t1.removeSuffix(" ")
                        sb.append(core)
                        words.add(Triple(c, idx, core.length))
                        if (t1.endsWith(" ")) sb.append(' ')
                        cur = null
                    } else {
                        if (txt2.startsWith(" ")) {
                            sb.append(if (sb.isNotEmpty() && !sb.endsWith(" ")) " " else "")
                            sb.append(txt2.substring(1))
                        } else {
                            sb.append(txt2)
                        }
                    }
                }
            }
            val s = WS.replace(sb.toString(), " ").trim()
            for (t in ts) {
                out.add(
                    RawLine(
                        t, s,
                        if (words.isEmpty()) null
                        else words.map { RawWord(it.first + t - ts[0], it.second, it.third) }
                    )
                )
            }
        }

        val off = (meta["offset"]?.toDoubleOrNull() ?: 0.0) / 1000.0
        if (off != 0.0) {
            for (x in out) {
                x.t -= off
                x.w?.forEach { it.t -= off }
            }
        }
        out.sortBy { it.t } // sắp xếp ổn định, giống Array.sort của JS

        val result = out.mapIndexed { k, x ->
            val w = x.w
            if (w == null) {
                LrcLine(x.t, x.s, null)
            } else {
                val end = if (k + 1 < out.size) out[k + 1].t else x.t + 4
                LrcLine(
                    x.t, x.s,
                    w.mapIndexed { q, rw ->
                        LrcWord(rw.t, rw.i, rw.l, if (q + 1 < w.size) w[q + 1].t else minOf(end, rw.t + 1.5))
                    }
                )
            }
        }
        return LrcResult(result, meta)
    }

    /** Số ký tự đã tô màu của dòng tại thời điểm [t] (theo thẻ từ). */
    fun highlightChars(line: LrcLine, t: Double): Double {
        val ws = line.w ?: return 0.0
        var d = 0.0
        for (w in ws) {
            if (t >= w.t) {
                d = w.i + ((t - w.t) / maxOf(0.05, w.e - w.t)).coerceIn(0.0, 1.0) * w.l
            }
        }
        return d
    }
}
