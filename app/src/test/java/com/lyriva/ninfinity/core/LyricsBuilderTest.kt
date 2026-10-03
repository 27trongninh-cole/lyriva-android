package com.lyriva.ninfinity.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsBuilderTest {
    private val text = "Trước mắt em\n\n  xin chào  "

    @Test fun lineMode() {
        val b = LyricsBuilder.build(text, SyncMode.LINE)
        assertEquals(listOf("Trước mắt em", "xin chào"), b.items)
        assertEquals(listOf(0, 1), b.meta.map { it.line })
    }

    @Test fun wordMode() {
        val b = LyricsBuilder.build(text, SyncMode.WORD)
        assertEquals(listOf("Trước", "mắt", "em", "xin", "chào"), b.items)
        assertEquals(listOf(0, 0, 0, 1, 1), b.meta.map { it.line })
    }

    @Test fun growMode() {
        val b = LyricsBuilder.build(text, SyncMode.GROW)
        assertEquals(listOf("Trước", "Trước mắt", "Trước mắt em", "xin", "xin chào"), b.items)
        assertEquals(listOf("Trước", "mắt", "em", "xin", "chào"), b.meta.map { it.word })
    }
}
