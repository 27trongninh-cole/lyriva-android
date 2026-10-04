package com.lyriva.ninfinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LrcParserTest {
    @Test fun plainLrcWithMeta() {
        val r = LrcParser.parse("[ti:Song]\n[ar:Me]\n[00:12.34]Some lyrics\n[00:15.80]Next")
        assertEquals("Song", r.meta["ti"])
        assertEquals("Me", r.meta["ar"])
        assertEquals(2, r.items.size)
        assertEquals(12.34, r.items[0].t, 1e-9)
        assertEquals("Some lyrics", r.items[0].s)
        assertNull(r.items[0].w)
        assertEquals(15.80, r.items[1].t, 1e-9)
    }

    @Test fun enhancedLrcWordTiming() {
        val r = LrcParser.parse("[00:10.00]<00:10.00>Hello <00:11.00>world <00:12.00>")
        val l = r.items[0]
        assertEquals("Hello world", l.s)
        val w = l.w
        assertNotNull(w)
        assertEquals(2, w!!.size)
        assertEquals(11.0, w[0].e, 1e-9)
        assertEquals(12.0, w[1].e, 1e-9)
        assertEquals(2.5, LrcParser.highlightChars(l, 10.5), 1e-9)
        assertEquals(6 + 5 * (0.5 / 1.0), LrcParser.highlightChars(l, 11.5), 1e-9)
    }

    @Test fun trailingTagIsEndOfLastWord() {
        val r = LrcParser.parse("[00:10.00]<00:10.00>Whole line here <00:14.00>")
        val w = r.items[0].w!!
        assertEquals(1, w.size)
        assertEquals(14.0, w[0].e, 1e-9)
        // giữa dòng thì tô khoảng nửa câu, không phải đã tô hết
        assertEquals(true, LrcParser.highlightChars(r.items[0], 12.0) < r.items[0].s.length)
    }

    @Test fun offsetShiftsTimes() {
        val r = LrcParser.parse("[offset:500]\n[00:10.00]A")
        assertEquals(9.5, r.items[0].t, 1e-9)
    }

    @Test fun multipleTimestampsAreSorted() {
        val r = LrcParser.parse("[00:20.00][00:05.00]Chorus\n[00:10.00]Verse")
        assertEquals(listOf(5.0, 10.0, 20.0), r.items.map { it.t })
        assertEquals("Chorus", r.items[0].s)
    }

    @Test fun ignoresGarbageLines() {
        val r = LrcParser.parse("hello\n\n[00:01.00]ok\r\n[bad]")
        assertEquals(1, r.items.size)
    }
}

class HideTest {
    @org.junit.Test fun hidesPairInsideLine() {
        val r = LrcParser.parse("[00:01.00]Hello //secret// world")
        org.junit.Assert.assertEquals("Hello world", r.items[0].s)
    }

    @org.junit.Test fun hidesAcrossLines() {
        val r = LrcParser.parse("[00:01.00]one //two\n[00:02.00]three\n[00:03.00]four// five")
        org.junit.Assert.assertEquals(listOf("one", "", "five"), r.items.map { it.s })
    }

    @org.junit.Test fun linesHelper() {
        org.junit.Assert.assertEquals(listOf("a b"), Hide.lines(listOf("a //x// b")))
    }
}
