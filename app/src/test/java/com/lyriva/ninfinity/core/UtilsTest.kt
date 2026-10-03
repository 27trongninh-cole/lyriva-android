package com.lyriva.ninfinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilsTest {
    @Test fun parseTime() {
        assertEquals(65.5, TimeUtils.parse("1:05.50")!!, 1e-9)
        assertEquals(65.5, TimeUtils.parse("65,5")!!, 1e-9)
        assertNull(TimeUtils.parse("abc"))
        assertNull(TimeUtils.parse(""))
    }

    @Test fun formatTime() {
        assertEquals("1:05.50", TimeUtils.fmt(65.5))
        assertEquals("00:03.50", TimeUtils.lrcStamp(3.5))
        assertEquals("01:02:03,004", TimeUtils.srtStamp(3723.004))
        assertEquals("1:05", TimeUtils.fmtShort(65.9))
    }

    @Test fun artistLine() {
        assertEquals("ARCTIC MONKEYS • JAY-Z • FOO", TextUtils.artistLine("Arctic Monkeys, Jay-Z - Foo"))
    }

    @Test fun fileStem() {
        assertEquals("I_Wanna_Be_Yours", TextUtils.fileStem("  I Wanna: Be  Yours? "))
        assertEquals("karaoke", TextUtils.fileStem("///"))
    }
}
