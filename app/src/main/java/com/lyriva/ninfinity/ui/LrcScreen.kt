package com.lyriva.ninfinity.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyriva.ninfinity.AppViewModel
import com.lyriva.ninfinity.audio.AudioDecoder
import com.lyriva.ninfinity.core.LrcWriter
import com.lyriva.ninfinity.core.LyricsBuilder
import com.lyriva.ninfinity.core.SyncMode
import com.lyriva.ninfinity.core.TextUtils
import com.lyriva.ninfinity.core.TimeUtils
import com.lyriva.ninfinity.core.WavWriter
import com.lyriva.ninfinity.export.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tab Tạo LRC: 3 trang cố định (Chọn nhạc → Lời → Xuất). Mỗi trang vừa khít một màn hình. */
@Composable
fun LrcScreen(
    vm: AppViewModel,
    page: Int,
    setPage: (Int) -> Unit,
    openSync: () -> Unit,
    goVideo: () -> Unit
) {
    when (page) {
        0 -> PageSource(vm) { setPage(1) }
        1 -> PageLyrics(vm, openSync) { setPage(2) }
        else -> PageExport(vm, goVideo)
    }
}

private val pagePad = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)

@Composable
private fun PageSource(vm: AppViewModel, next: () -> Unit) {
    val p = vm.project
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val name = displayName(ctx, uri)
            val isVideo = ctx.contentResolver.getType(uri)?.startsWith("video/") == true
            vm.update {
                it.copy(
                    audioUri = uri.toString(), sourceName = name, sourceIsVideo = isVideo,
                    cutStart = 0.0, cutEnd = 0.0, marks = emptyList(),
                    bgUri = if (isVideo) uri.toString() else it.bgUri, bgOffset = 0.0
                )
            }
            vm.analyze(uri.toString())
        }
    }
    LaunchedEffect(p.audioUri) {
        val u = p.audioUri
        if (u != null && vm.peaks?.uri != u && !vm.analyzing) vm.analyze(u)
    }

    val player = rememberPlayer(p.audioUri)
    var playing by remember { mutableStateOf(false) }
    val eNow by rememberUpdatedState(p.cutEnd)
    val pos = rememberPlayerPos(player) { t ->
        if (playing && eNow > 0 && t >= eNow) {
            player?.pause()
            playing = false
        }
    }
    val total = vm.peaks?.takeIf { it.uri == p.audioUri }?.duration ?: p.cutEnd

    Column(pagePad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p.audioUri == null) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(R14).background(Lc.Card).border(1.dp, Lc.Line, R14)
                    .clickable { pick.launch(arrayOf("audio/*", "video/*")) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(Ic.UP, Lc.Acc, 40.dp)
                    Text("Chọn nhạc hoặc video", color = Lc.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("MP3, WAV, M4A, MP4…", color = Lc.Mute, fontSize = 13.sp)
                }
            }
        } else {
            FileCard(
                if (p.sourceIsVideo) Ic.FILM else Ic.MUSIC, p.sourceName,
                (if (p.sourceIsVideo) "Video" else "Nhạc") + " • " + TimeUtils.fmtShort(total),
                true, "Đổi"
            ) { pick.launch(arrayOf("audio/*", "video/*")) }
            if (p.sourceIsVideo) {
                Box(Modifier.fillMaxWidth().height(168.dp).clip(R12).background(Lc.Screen)) {
                    VideoSurface(player, p.audioUri, Modifier.fillMaxSize())
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val pk = vm.peaks?.takeIf { it.uri == p.audioUri }
                CutWaveform(
                    pk, p.cutStart, p.cutEnd, pos,
                    { s, e -> vm.update { it.copy(cutStart = s, cutEnd = e, marks = emptyList()) } },
                    Modifier.fillMaxSize()
                )
                if (vm.analyzing) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Đang phân tích sóng nhạc… ${(vm.analyzeProgress * 100).toInt()}%",
                            color = Lc.Mute, fontSize = 13.sp
                        )
                    }
                }
                val err = vm.analyzeError
                if (err != null && pk == null && !vm.analyzing) {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(err, color = Lc.Mute, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 4)
                            Btn("Thử lại", { p.audioUri?.let { vm.analyze(it) } }, Modifier.width(120.dp), small = true)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("Bắt đầu", p.cutStart, { x ->
                    vm.update { it.copy(cutStart = x.coerceIn(0.0, maxOf(0.0, it.cutEnd - 0.2)), marks = emptyList()) }
                }, Modifier.weight(1f))
                TimeField("Kết thúc", p.cutEnd, { x ->
                    vm.update { it.copy(cutEnd = x.coerceIn(it.cutStart + 0.2, maxOf(total, it.cutStart + 0.2)), marks = emptyList()) }
                }, Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Label("Đoạn cắt")
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        Text(TimeUtils.fmt(maxOf(0.0, p.cutEnd - p.cutStart)), color = Lc.Acc, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Btn(
                if (playing) "Dừng" else "Nghe thử đoạn cắt",
                {
                    val pl = player
                    if (pl != null) {
                        if (playing) {
                            pl.pause()
                            playing = false
                        } else {
                            pl.seekTo((p.cutStart * 1000).toLong())
                            pl.playWhenReady = true
                            playing = true
                        }
                    }
                },
                Modifier.fillMaxWidth(), small = true,
                icon = if (playing) Ic.STOP else Ic.PLAY
            )
        }
        Btn(
            "Tiếp tục", next, Modifier.fillMaxWidth(), primary = true,
            enabled = p.audioUri != null && p.cutEnd > p.cutStart, icon = Ic.ARROW, iconRight = true
        )
    }
}

@Composable
private fun PageLyrics(vm: AppViewModel, toSync: () -> Unit, toExport: () -> Unit) {
    val p = vm.project
    val built = remember(p.lyricsText, p.syncMode) { LyricsBuilder.build(p.lyricsText, p.syncMode) }
    val nLines = remember(p.lyricsText) { p.lyricsText.lines().count { it.isNotBlank() } }
    Column(pagePad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead("Lời bài hát", "$nLines dòng")
        TextArea(
            p.lyricsText, { v -> vm.update { it.copy(lyricsText = v, marks = emptyList()) } },
            Modifier.weight(1f), "Dán lời bài hát vào đây, mỗi dòng một câu…"
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Label("Kiểu hiển thị")
            Seg(listOf("Cả câu", "Từng chữ", "Tăng dần"), p.syncMode.ordinal, { i ->
                vm.update { it.copy(syncMode = SyncMode.values()[i], marks = emptyList()) }
            })
            Note(
                when (p.syncMode) {
                    SyncMode.LINE -> "Cả câu hiện cùng lúc khi tới lượt hát."
                    SyncMode.WORD -> "Từng chữ hiện lần lượt, đánh dấu mỗi chữ."
                    SyncMode.GROW -> "Câu dài dần ra theo từng chữ được hát."
                }, 2
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Label("Cách bắt đầu đồng bộ")
            Seg(listOf("Chạm đầu: phát nhạc", "Chạm đầu: đánh dấu"), if (p.firstTapMarks) 1 else 0, { i ->
                vm.update { it.copy(firstTapMarks = i == 1) }
            })
            Note(
                if (p.firstTapMarks) "Chạm đầu tiên vừa phát nhạc vừa đánh dấu mục đầu."
                else "Chạm đầu tiên chỉ phát nhạc, các lần sau mới đánh dấu.", 2
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Xuất file", toExport, Modifier.width(112.dp), enabled = p.marks.isNotEmpty())
            Btn(
                "Bắt đầu đồng bộ", toSync, Modifier.weight(1f), primary = true,
                enabled = p.audioUri != null && built.items.isNotEmpty() && p.cutEnd > p.cutStart,
                icon = Ic.ARROW, iconRight = true
            )
        }
    }
}

@Composable
private fun PageExport(vm: AppViewModel, goVideo: () -> Unit) {
    val p = vm.project
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val built = remember(p.lyricsText, p.syncMode) { LyricsBuilder.build(p.lyricsText, p.syncMode) }
    var xl by rememberSaveable { mutableStateOf(true) }
    var xe by rememberSaveable { mutableStateOf(false) }
    var xs by rememberSaveable { mutableStateOf(false) }
    var xw by rememberSaveable { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var useEnh by rememberSaveable { mutableStateOf(true) }
    val ready = p.marks.isNotEmpty() && p.marks.size <= built.items.size
    val stem = TextUtils.fileStem(p.title.ifBlank { p.sourceName.substringBeforeLast('.') })
    val dur = maxOf(0.0, p.cutEnd - p.cutStart)

    Column(pagePad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(if (ready) "Đã đồng bộ" else "Chưa đồng bộ")
        Note(
            if (ready) "${p.marks.size}/${built.items.size} mục • đoạn ${TimeUtils.fmtShort(dur)}"
            else "Quay lại bước Lời và bấm Bắt đầu đồng bộ.", 1
        )
        Label("Chọn file muốn xuất")
        SwitchLine("LRC", "Lời kèm mốc thời gian (.lrc)", xl, card = true) { xl = it }
        SwitchLine("LRC nâng cao", "Có mốc cho từng chữ", xe, card = true) { xe = it }
        SwitchLine("SRT", "Phụ đề (.srt)", xs, card = true) { xs = it }
        SwitchLine("Nhạc đã cắt", "File WAV đúng đoạn đã chọn", xw, card = true) { xw = it }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Label("Dựng video bằng")
            Seg(listOf("LRC nâng cao", "LRC thường"), if (useEnh) 0 else 1, { i -> useEnh = i == 0 })
        }
        Btn(
            "Dựng video ngay", {
                val lines = LrcWriter.lines(p.marks, built.items, dur)
                vm.update {
                    it.copy(
                        lrcText = if (useEnh) LrcWriter.enhanced(lines, built.meta, it.title, it.artist)
                        else LrcWriter.plain(lines, it.title, it.artist),
                        bgUri = if (it.sourceIsVideo) it.audioUri else it.bgUri,
                        bgOffset = if (it.sourceIsVideo) it.cutStart else it.bgOffset
                    )
                }
                goVideo()
            },
            Modifier.fillMaxWidth(), primary = true, enabled = ready, icon = Ic.ARROW, iconRight = true
        )
        Note(msg, 1, center = true)
        Btn(
            if (busy) "Đang lưu…" else "Tải xuống", {
                if (!busy) {
                    busy = true
                    msg = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                val lines = LrcWriter.lines(p.marks, built.items, dur)
                                var n = 0
                                if (xl) {
                                    MediaSaver.saveDownload(ctx, "$stem.lrc", "text/plain") {
                                        it.write(LrcWriter.plain(lines, p.title, p.artist).toByteArray())
                                    } ?: error("Không lưu được LRC")
                                    n++
                                }
                                if (xe) {
                                    MediaSaver.saveDownload(ctx, "${stem}_enhanced.lrc", "text/plain") {
                                        it.write(LrcWriter.enhanced(lines, built.meta, p.title, p.artist).toByteArray())
                                    } ?: error("Không lưu được LRC nâng cao")
                                    n++
                                }
                                if (xs) {
                                    MediaSaver.saveDownload(ctx, "$stem.srt", "application/x-subrip") {
                                        it.write(LrcWriter.srt(lines).toByteArray())
                                    } ?: error("Không lưu được SRT")
                                    n++
                                }
                                if (xw) {
                                    val pcm = AudioDecoder.decodeRange(
                                        ctx, android.net.Uri.parse(p.audioUri), p.cutStart, p.cutEnd
                                    )
                                    MediaSaver.saveDownload(ctx, "$stem.wav", "audio/wav") {
                                        WavWriter.write(it, pcm.samples, pcm.sampleRate, pcm.channels)
                                    } ?: error("Không lưu được WAV")
                                    n++
                                }
                                n
                            }
                        }
                        msg = r.fold(
                            { if (it == 0) "Hãy bật ít nhất một loại file." else "Đã lưu $it file vào Download/LYRIVA" },
                            { "Lỗi: " + (it.message ?: "không lưu được") }
                        )
                        busy = false
                    }
                }
            },
            Modifier.fillMaxWidth(), enabled = ready && !busy, icon = Ic.DL
        )
    }
}
