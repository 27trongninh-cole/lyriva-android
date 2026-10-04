package com.lyriva.ninfinity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Bộ icon vẽ bằng path (cùng nét với bản web), không cần thư viện icon. */
object Ic {
    const val WAVE = "M4 10v4M8 6v12M12 3v18M16 7v10M20 10v4"
    const val FILM = "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zM10 9l5 3-5 3z"
    const val MUSIC = "M9 18V5l11-2v13M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0M14 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
    const val TEXT = "M4 6h16M4 12h16M4 18h10"
    const val AA = "M4 7V5h16v2M12 5v14M9 19h6"
    const val CROP = "M6 2v14a2 2 0 0 0 2 2h14M2 6h14a2 2 0 0 1 2 2v14"
    const val IMG = "M6 3h12a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3zM7.4 9a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0M21 16l-5-5-9 9"
    const val UP = "M12 16V4M7 9l5-5 5 5M4 20h16"
    const val PLAY = "M7 4.5v15l13-7.5z"
    const val PAUSE = "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"
    const val BACK = "M15 5l-7 7 7 7"
    const val X = "M6 6l12 12M18 6L6 18"
    const val UNDO = "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3"
    const val RW = "M11 6l-7 6 7 6zM20 6l-7 6 7 6z"
    const val STOP = "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"
    const val CHECK = "M5 12l5 5 9-10"
    const val GRID = "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z"
    const val DL = "M12 4v12M7 11l5 5 5-5M4 20h16"
    const val SUB = "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM7 12h5M14 12h3M7 15.5h2.5M12 15.5h5"
    const val ARROW = "M5 12h14M13 6l6 6-6 6"
    const val SPARK = "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM18 16l.8 2.2L21 19l-2.2.8L18 22l-.8-2.2L15 19l2.2-.8z"
    const val EXPAND = "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
}

@Composable
fun Ico(d: String, color: Color, size: Dp = 22.dp, filled: Boolean = false, modifier: Modifier = Modifier) {
    val path = remember(d) {
        try {
            PathParser().parsePathString(d).toPath()
        } catch (e: Exception) {
            Path()
        }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, Offset.Zero) {
            if (filled) {
                drawPath(path, color, style = Fill)
            } else {
                drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
