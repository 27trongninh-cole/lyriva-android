package com.lyriva.ninfinity.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyriva.ninfinity.AppViewModel
import com.lyriva.ninfinity.audio.PeakData
import com.lyriva.ninfinity.core.LyricsBuilder
import com.lyriva.ninfinity.core.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Composable
private fun TimeText(pos: State<Double>, s: Double) {
    Text(
        TimeUtils.fmt(pos.value - s), color = Lc.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        style = Tnum, maxLines = 1
    )
}

@Composable
private fun RollingWave(peaks: PeakData?, pos: State<Double>, s: Double, e: Double, marks: List<Double>, modifier: Modifier) {
    Canvas(modifier.clip(R12).background(Lc.Screen)) {
        val pk = peaks ?: return@Canvas
        val c = pos.value
        val win = 3.0
        val t0 = c - win
        val w = size.width
        val h = size.height
        val n = max(1, (w / 4f).toInt())
        for (i in 0 until n) {
            val t = t0 + (i + 0.5) / n * 2 * win
            if (t < 0 || t > pk.duration) continue
            val k0 = (t * pk.perSec).toInt()
            var m = 0.02f
            for (k in (k0 - 1)..(k0 + 2)) if (k >= 0 && k < pk.peaks.size && pk.peaks[k] > m) m = pk.peaks[k]
            val inCut = t >= s && t <= e
            val col = (if (t < c) Lc.Acc else Lc.Mute).copy(alpha = if (inCut) 1f else 0.25f)
            val x = i * 4f + 2f
            drawLine(col, Offset(x, h / 2f - m * h * 0.45f), Offset(x, h / 2f + m * h * 0.45f), 2.5f)
        }
        for (m in marks) {
            val x = (((s + m) - t0) / (2 * win) * w).toFloat()
            if (x in 0f..w) drawLine(Lc.Ink.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, h), 2f)
        }
        drawLine(Color.White, Offset(w / 2f, 0f), Offset(w / 2f, h), 2f)
    }
}

/** Màn đồng bộ toàn màn hình: chạm theo nhịp để đánh dấu từng mục. */
@Composable
fun SyncScreen(vm: AppViewModel, onClose: () -> Unit, onDone: () -> Unit) {
    val p = vm.project
    val s = p.cutStart
    val e = p.cutEnd
    val built = remember { LyricsBuilder.build(p.lyricsText, p.syncMode) }
    val n = built.items.size
    val player = rememberPlayer(p.audioUri)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rates = remember { listOf(1f, 0.75f, 0.5f, 0.35f, 0.25f) }
    var rateIdx by remember { mutableStateOf(0) }
    var latency by remember { mutableStateOf(p.latencyMs) }
    val marks = remember { mutableStateListOf<Double>() }
    var active by remember { mutableStateOf(true) }
    var waiting by remember { mutableStateOf(true) }
    var hit by remember { mutableStateOf(false) }
    var playingUi by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        player?.seekTo((s * 1000).toLong())
        player?.pause()
    }

    fun finish(done: Boolean) {
        if (!active) return
        active = false
        waiting = false
        player?.pause()
        player?.setPlaybackSpeed(1f)
        val m = marks.toList()
        vm.update { it.copy(marks = if (m.isNotEmpty()) m else it.marks, latencyMs = latency) }
        if (done) onDone() else onClose()
    }

    fun mark() {
        if (!active || marks.size >= n) return
        val pl = player ?: return
        if (waiting) {
            waiting = false
            pl.seekTo((s * 1000).toLong())
            pl.setPlaybackSpeed(rates[rateIdx])
            pl.playWhenReady = true
            if (p.firstTapMarks) {
                marks.add(0.0)
                if (marks.size >= n) scope.launch { delay(500); finish(true) }
            }
            return
        }
        if (!pl.playWhenReady) return
        val lat = latency / 1000.0 * rates[rateIdx]
        val prev = marks.lastOrNull() ?: 0.0
        marks.add(max(prev, pl.currentPosition / 1000.0 - s - lat))
        if (marks.size >= n) scope.launch { delay(500); finish(true) }
    }

    fun tap() {
        mark()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        hit = true
        scope.launch { delay(90); hit = false }
    }

    fun undo() {
        if (!active || marks.isEmpty()) return
        marks.removeAt(marks.size - 1)
        val base = (marks.lastOrNull() ?: 0.0) + s
        player?.seekTo((max(s, base - 0.5) * 1000).toLong())
    }

    fun seekBy(d: Double) {
        val pl = player ?: return
        if (!active || waiting) return
        pl.seekTo(((pl.currentPosition / 1000.0 + d).coerceIn(s, e) * 1000).toLong())
    }

    fun toggle() {
        if (waiting) {
            mark()
            return
        }
        val pl = player ?: return
        if (pl.playWhenReady) pl.pause() else pl.play()
    }

    val pos = rememberPlayerPos(player) { t ->
        playingUi = player?.playWhenReady == true && !waiting
        if (active && !waiting && e > 0 && t >= e) finish(marks.size >= n)
    }
    BackHandler { finish(false) }

    val idx = marks.size
    val cur = buildAnnotatedString {
        if (idx >= n) {
            append("Hết lời")
        } else {
            val g = built.meta[idx].line
            var a = idx
            while (a > 0 && built.meta[a - 1].line == g) a--
            var b = idx
            while (b + 1 < n && built.meta[b + 1].line == g) b++
            for (j in a..b) {
                val st = when {
                    j < idx -> SpanStyle(color = Lc.Acc)
                    j == idx -> SpanStyle(color = Lc.OnAcc, background = Lc.Acc)
                    else -> SpanStyle(color = Lc.Ink)
                }
                withStyle(st) { append(built.meta[j].word) }
                if (j < b) append(" ")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Lc.Bg).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.BACK, { finish(false) })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { TimeText(pos, s) }
            Btn("Xong", { finish(marks.size >= n) }, Modifier.width(88.dp), primary = true, small = true)
        }
        if (p.sourceIsVideo) {
            Box(Modifier.fillMaxWidth().height(96.dp).clip(R12).background(Lc.Screen)) {
                VideoSurface(player, Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier.fillMaxWidth().height(92.dp).clip(R14).background(Lc.Card).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(cur, color = Lc.Ink, fontSize = 22.sp, lineHeight = 30.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        RollingWave(vm.peaks?.takeIf { it.uri == p.audioUri }, pos, s, e, marks, Modifier.fillMaxWidth().height(56.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.RW, { seekBy(-2.0) })
            IconBtn(if (playingUi) Ic.PAUSE else Ic.PLAY, { toggle() }, filled = true)
            IconBtn(Ic.UNDO, { undo() })
            Spacer(Modifier.weight(1f))
            Text("Bù", color = Lc.Mute, fontSize = 12.sp)
            TextBtn("−") { latency = max(0, latency - 10) }
            Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) {
                Text("${latency}ms", color = Lc.Ink, fontSize = 14.sp, style = Tnum, maxLines = 1)
            }
            TextBtn("+") { latency = min(600, latency + 10) }
        }
        Seg(rates.map { (if (it == 1f) "1" else it.toString().trimEnd('0')) + "×" }, rateIdx, { i ->
            rateIdx = i
            if (!waiting) player?.setPlaybackSpeed(rates[i])
        })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(R14).background(Lc.Card)) {
            val rows = max(1, (maxHeight / 34.dp).toInt())
            val focus = min(idx, n - 1)
            val start = (focus - rows / 2).coerceIn(0, max(0, n - rows))
            Column(Modifier.fillMaxSize()) {
                for (i in start until min(n, start + rows)) {
                    val isCur = i == idx
                    val done = i < idx
                    Row(
                        Modifier.fillMaxWidth().height(34.dp).background(if (isCur) Lc.Hl else Color.Transparent)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${i + 1}", color = Lc.Mute, fontSize = 12.sp, modifier = Modifier.width(34.dp), maxLines = 1)
                        Text(
                            built.items[i], color = if (done) Lc.Acc else if (isCur) Lc.Ink else Lc.Mute,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Text(
                            marks.getOrNull(i)?.let { TimeUtils.fmt(it) } ?: "", color = Lc.Mute,
                            fontSize = 12.sp, style = Tnum, maxLines = 1,
                            textAlign = TextAlign.End, modifier = Modifier.width(64.dp)
                        )
                    }
                }
            }
        }
        val tapNow by rememberUpdatedState(newValue = { tap() })
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp))
                .background(if (hit) Lc.Acc else Lc.Hl)
                .border(1.dp, Lc.Acc.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .pointerInput(Unit) { detectTapGestures(onPress = { tapNow() }) },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
                val big = when {
                    waiting -> "Chạm để bắt đầu"
                    idx >= n -> "Hết lời"
                    else -> built.meta[idx].word
                }
                Text(
                    big, color = if (hit) Lc.OnAcc else Lc.Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
                Text(
                    if (waiting) "Nhạc phát từ điểm bắt đầu cắt" else "Chạm khi chữ này vang lên",
                    color = if (hit) Lc.OnAcc else Lc.Mute, fontSize = 12.sp, maxLines = 1
                )
            }
        }
    }
}
