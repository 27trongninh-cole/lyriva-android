package com.lyriva.ninfinity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lyriva.ninfinity.audio.PeakData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Sóng nhạc cả bài, kéo hai đầu để chọn đoạn cắt [s, e]. */
@Composable
fun CutWaveform(
    peaks: PeakData?,
    s: Double,
    e: Double,
    pos: State<Double>?,
    onChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val sNow by rememberUpdatedState(s)
    val eNow by rememberUpdatedState(e)
    val cb by rememberUpdatedState(onChange)
    Canvas(
        modifier.clip(RoundedCornerShape(14.dp)).background(Lc.Screen)
            .border(1.dp, Lc.Line, RoundedCornerShape(14.dp))
            .pointerInput(peaks) {
                val pk = peaks ?: return@pointerInput
                val dur = pk.duration
                var which = 0
                fun apply(t0: Double) {
                    val t = t0.coerceIn(0.0, dur)
                    if (which == 1) cb(min(t, eNow - 0.2).coerceAtLeast(0.0), eNow)
                    else cb(sNow, max(t, sNow + 0.2).coerceAtMost(dur))
                }
                detectDragGestures(
                    onDragStart = { o ->
                        val t = o.x / size.width * dur
                        which = if (abs(t - sNow) <= abs(t - eNow)) 1 else 2
                        apply(t)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        apply(change.position.x / size.width * dur)
                    }
                )
            }
    ) {
        val pk = peaks ?: return@Canvas
        val w = size.width
        val h = size.height
        val dur = pk.duration
        if (dur <= 0.0) return@Canvas
        val n = max(1, (w / 3f).toInt())
        val arr = pk.peaks
        for (i in 0 until n) {
            val i0 = (i.toDouble() / n * dur * pk.perSec).toInt()
            val i1 = max(i0 + 1, ((i + 1).toDouble() / n * dur * pk.perSec).toInt())
            var m = 0f
            var k = i0
            while (k < i1 && k < arr.size) {
                if (arr[k] > m) m = arr[k]
                k++
            }
            m = max(m, 0.02f)
            val tAt = (i + 0.5) / n * dur
            val inCut = tAt >= s && tAt <= e
            val x = i * 3f + 1.5f
            drawLine(
                if (inCut) Lc.Acc else Lc.Mute.copy(alpha = 0.35f),
                Offset(x, h / 2f - m * h * 0.45f), Offset(x, h / 2f + m * h * 0.45f), 2f
            )
        }
        val xs = (s / dur * w).toFloat()
        val xe = (e / dur * w).toFloat()
        drawLine(Lc.Ink, Offset(xs, 0f), Offset(xs, h), 4f)
        drawLine(Lc.Ink, Offset(xe, 0f), Offset(xe, h), 4f)
        drawCircle(Lc.Ink, 7f, Offset(xs, h / 2f))
        drawCircle(Lc.Ink, 7f, Offset(xe, h / 2f))
        val pp = pos?.value
        if (pp != null && pp in 0.0..dur) {
            val x = (pp / dur * w).toFloat()
            drawLine(Color.White.copy(alpha = 0.9f), Offset(x, 0f), Offset(x, h), 2f)
        }
    }
}
