package com.lyriva.ninfinity.core

enum class SyncMode { LINE, WORD, GROW }

/** Một mục cần đánh dấu: thuộc dòng [line], chữ [word]. */
class SyncUnit(val line: Int, val word: String)

class BuiltLyrics(val items: List<String>, val meta: List<SyncUnit>)

/** Biến lời nhập tay thành danh sách mục để đồng bộ (cả câu / từng chữ / tăng dần). */
object LyricsBuilder {
    private val WS = Regex("\\s+")

    fun build(text: String, mode: SyncMode): BuiltLyrics {
        val items = ArrayList<String>()
        val meta = ArrayList<SyncUnit>()
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { n, l ->
            if (mode == SyncMode.LINE) {
                meta.add(SyncUnit(n, l))
                items.add(l)
            } else {
                val w = l.split(WS)
                w.forEachIndexed { i, x ->
                    meta.add(SyncUnit(n, x))
                    items.add(if (mode == SyncMode.WORD) x else w.subList(0, i + 1).joinToString(" "))
                }
            }
        }
        return BuiltLyrics(items, meta)
    }
}
