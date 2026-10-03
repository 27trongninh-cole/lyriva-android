package com.lyriva.ninfinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcWriterTest {
    @Test fun plainAndSrt() {
        val lines = LrcWriter.lines(listOf(0.0, 3.5), listOf("a", "b"), 10.0)
        assertEquals(3.5, lines[0].e, 1e-9)
        assertEquals(6.5, lines[1].e, 1e-9)
        assertEquals("[ti:T]\n[00:00.00]a\n[00:03.50]b\n[00:06.50]", LrcWriter.plain(lines, "T", ""))
        assertEquals(
            "1\n00:00:00,000 --> 00:00:03,500\na\n\n2\n00:00:03,500 --> 00:00:06,500\nb\n",
            LrcWriter.srt(lines)
        )
    }

    @Test fun enhancedGroupsWordsByLine() {
        val b = LyricsBuilder.build("Hello world", SyncMode.WORD)
        val lines = LrcWriter.lines(listOf(1.0, 2.0), b.items, 10.0)
        assertEquals(
            "[00:01.00]<00:01.00>Hello <00:02.00>world <00:05.00>",
            LrcWriter.enhanced(lines, b.meta)
        )
    }

    @Test fun enhancedOutputParsesBack() {
        val b = LyricsBuilder.build("Hello world", SyncMode.WORD)
        val lines = LrcWriter.lines(listOf(1.0, 2.0), b.items, 10.0)
        val r = LrcParser.parse(LrcWriter.enhanced(lines, b.meta))
        assertEquals("Hello world", r.items[0].s)
        assertEquals(2, r.items[0].w!!.size)
    }
}
