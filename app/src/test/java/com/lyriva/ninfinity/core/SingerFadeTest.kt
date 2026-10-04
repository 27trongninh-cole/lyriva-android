package com.lyriva.ninfinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingerFadeTest {
    private val text = """
        Tên nhóm: CORTIS
        James: #123456
        Martin: #234567

        Tên nhóm: Khác
        James: #FF0000
    """.trimIndent()

    @Test fun parsesGroups() {
        val g = SingerPalette.parse(text)
        assertEquals(listOf("CORTIS", "Khác"), g.map { it.name })
        assertEquals(0x123456, g[0].members["james"])
        assertEquals(2, g[0].members.size)
    }

    @Test fun resolvePrefersActiveGroup() {
        val g = SingerPalette.parse(text)
        assertEquals(0x123456, SingerPalette.resolve("JAMES", g, ""))
        assertEquals(0xFF0000, SingerPalette.resolve("james", g, "Khác"))
        assertNull(SingerPalette.resolve("Nobody", g, ""))
    }

    @Test fun accentInsensitiveAndSplit() {
        val g = SingerPalette.parse("Nhóm: X\nĐức: #abcdef")
        assertEquals(0xABCDEF, SingerPalette.resolve("duc", g, ""))
        assertEquals(listOf("A", "B", "C"), SingerPalette.split("A, B & C"))
    }

    @Test fun fadeGain() {
        assertEquals(0.5f, Fade.gain(1.0, 10.0, 2.0, 0.0), 1e-6f)
        assertEquals(0.25f, Fade.gain(9.5, 10.0, 0.0, 2.0), 1e-6f)
        assertEquals(1f, Fade.gain(5.0, 10.0, 2.0, 2.0), 1e-6f)
    }

    @Test fun fadeApplyScalesEdges() {
        val s = ShortArray(200) { 1000 }
        Fade.apply(s, 1, 100, 1.0, 1.0)
        assertEquals(0, s[0].toInt())
        assertEquals(1000, s[100].toInt())
        assertEquals(true, s[199] < s[150])
    }
}
