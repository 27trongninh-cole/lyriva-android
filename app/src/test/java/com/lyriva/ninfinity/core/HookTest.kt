package com.lyriva.ninfinity.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookTest {
    @Test fun parsesWordsAndHighlight() {
        val (w, h) = Hook.parse("Can you *rap* this?")
        assertEquals(listOf("Can", "you", "rap", "this?"), w)
        assertEquals(listOf(false, false, true, false), h.toList())
        val (w2, h2) = Hook.parse("*Do you* remember")
        assertEquals(listOf("Do", "you", "remember"), w2)
        assertEquals(listOf(true, true, false), h2.toList())
    }

    @Test fun oneNotePerWordAndDeterministic() {
        val a = Hook.notes(5, 42)
        assertEquals(5, a.size)
        assertEquals(a.toList(), Hook.notes(5, 42).toList())
        for (i in 1 until a.size) assertTrue(a[i] != a[i - 1])
        assertTrue(a.all { it in 72..96 })
    }

    @Test fun specNullWhenOffOrEmpty() {
        assertNull(Hook.spec(false, "hi", 0.35, 0, 1, 0.7f))
        assertNull(Hook.spec(true, "   ", 0.35, 0, 1, 0.7f))
        assertNotNull(Hook.spec(true, "hi", 0.35, 0, 1, 0.7f))
    }

    @Test fun synthAndMix() {
        val spec = Hook.spec(true, "a b c d", 0.3, 1, 7, 0.7f)!!
        val s = Hook.synth(spec, 44100)
        assertTrue(s.size > 44100)
        assertTrue(s.any { it.toInt() != 0 })
        val pcm = ShortArray(44100 * 2 * 4) { 10000 }
        Hook.mixInto(pcm, 2, 44100, spec)
        assertTrue(pcm.any { it.toInt() != 10000 })
        assertEquals(10000, pcm[pcm.size - 1].toInt())
        assertEquals(1f, Hook.duck(10.0, spec), 1e-6f)
        assertEquals(0.5f, Hook.duck(0.6, spec), 1e-6f)
    }
}
