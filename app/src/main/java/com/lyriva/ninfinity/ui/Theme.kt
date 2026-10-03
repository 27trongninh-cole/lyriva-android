package com.lyriva.ninfinity.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Bảng màu LYRIVA. */
object Lc {
    val Bg = Color(0xFF0E0E0F)
    val Card = Color(0xFF161617)
    val Card2 = Color(0xFF1D1D1F)
    val Ink = Color(0xFFF5F2EA)
    val Mute = Color(0xFF858585)
    val Line = Color(0xFF2A2A2D)
    val Acc = Color(0xFFA78BFA)
    val Hl = Color(0xFF2A2340)
    val OnAcc = Color(0xFF111111)
    val Screen = Color(0xFF111111)
}

@Composable
fun LyrivaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lc.Acc,
            onPrimary = Lc.OnAcc,
            background = Lc.Bg,
            onBackground = Lc.Ink,
            surface = Lc.Card,
            onSurface = Lc.Ink
        ),
        content = content
    )
}
