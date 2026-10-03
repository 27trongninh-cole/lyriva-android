package com.lyriva.ninfinity.core

class TimedLine(val s: Double, val e: Double, val text: String)

/** Xuất LRC / LRC nâng cao / SRT từ các mốc đã đánh dấu. Port từ bản web. */
object LrcWriter {

    /** [marks] là thời điểm (giây, tính từ đầu đoạn cắt) của từng mục; [duration] = độ dài đoạn cắt. */
    fun lines(marks: List<Double>, items: List<String>, duration: Double): List<TimedLine> =
        marks.mapIndexed { i, s ->
            val e = if (i + 1 < marks.size) marks[i + 1] else minOf(duration, s + 3)
            TimedLine(s, e, items.getOrElse(i) { "" })
        }

    private fun header(title: String, artist: String): String {
        val t = title.trim()
        val r = artist.trim()
        return (if (t.isNotEmpty()) "[ti:$t]\n" else "") + (if (r.isNotEmpty()) "[ar:$r]\n" else "")
    }

    fun plain(lines: List<TimedLine>, title: String = "", artist: String = ""): String {
        var t = header(title, artist) +
            lines.joinToString("\n") { "[${TimeUtils.lrcStamp(it.s)}]${it.text}" }
        if (lines.isNotEmpty()) t += "\n[${TimeUtils.lrcStamp(lines.last().e)}]"
        return t
    }

    /** LRC nâng cao: mỗi dòng lời có mốc <mm:ss.xx> cho từng chữ. */
    fun enhanced(
        lines: List<TimedLine>,
        meta: List<SyncUnit>,
        title: String = "",
        artist: String = ""
    ): String {
        class G(val line: Int, val a: MutableList<Pair<TimedLine, String>> = ArrayList())
        val groups = ArrayList<G>()
        lines.forEachIndexed { i, x ->
            val m = meta.getOrNull(i) ?: SyncUnit(i, x.text)
            if (groups.isEmpty() || groups.last().line != m.line) groups.add(G(m.line))
            groups.last().a.add(x to m.word)
        }
        return header(title, artist) + groups.joinToString("\n") { g ->
            val end = g.a.last().first.e
            "[${TimeUtils.lrcStamp(g.a.first().first.s)}]" +
                g.a.joinToString(" ") { "<${TimeUtils.lrcStamp(it.first.s)}>${it.second}" } +
                " <${TimeUtils.lrcStamp(end)}>"
        }
    }

    fun srt(lines: List<TimedLine>): String =
        lines.mapIndexed { i, x ->
            "${i + 1}\n${TimeUtils.srtStamp(x.s)} --> ${TimeUtils.srtStamp(x.e)}\n${x.text}\n"
        }.joinToString("\n")
}
